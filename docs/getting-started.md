# 开始接入

本页覆盖安装、快速开始和服务端写入验证。Producer 只负责异步日志写入，不提供 Project/Topic/Index 管理、查询或消费 API。

## 安装

### 支持范围

- 标准 Producer SDK 最低支持 Android API 19。
- 宿主应用需要网络访问权限；Producer AAR 的 manifest 会声明 `android.permission.INTERNET`，接入后仍应检查合并结果。
- Producer-native 公开压缩类型只有 `NONE` 和 `LZ4`，默认是 `LZ4`。

### 标准依赖

本文对应 SDK 版本为 `2.1.4`。使用 Maven 仓库时，标准依赖坐标如下：

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation "io.github.volcengine-tls:tls-android-producer:2.1.4"
}
```

### 使用项目内模块

仓库内的 Android Gradle 工程已经包含 `producer-native`。在同一工程中引用模块：

```groovy
dependencies {
    implementation project(':producer-native')
}
```

完成宿主应用的 debug/release 编译，并确认 native 库能够随目标 ABI 打包。

### 安装后检查

1. 确认 `minSdk` 不低于 19。
2. 确认 endpoint 使用 HTTPS，且宿主没有通过网络安全配置放宽证书校验。
3. 确认运行时凭据可以在创建 client 前取得，不把 AK/SK 写入代码或文件。
4. 按[快速开始](#快速开始)写入一条带唯一 `event_id` 的日志，再按[验证写入](#验证写入)完成服务端检查。

API 19 的 TLS 行为和 endpoint 要求见[参考](reference.md#兼容性)与[使用指南](usage.md#网络超时与重试)。

## 快速开始

以下示例展示创建、写入、观察异步结果和关闭。`runtimeCredentials` 是业务自己的运行时凭据提供器，不是 SDK 类型，也不应替换成硬编码字符串。

### 创建 client

```java
import com.volcengine.tls.android.producer.LogProducerClient;
import com.volcengine.tls.android.producer.LogProducerConfig;

LogProducerConfig config = new LogProducerConfig()
        .setEndpoint("https://your-tls-endpoint")
        .setRegion("your-region")
        .setTopicId("your-topic-id")
        .setAccessKeyId(runtimeCredentials.accessKeyId)
        .setAccessKeySecret(runtimeCredentials.accessKeySecret)
        .setSecurityToken(runtimeCredentials.securityToken)
        .setCompressType(LogProducerConfig.CompressType.LZ4)
        .setDestroyFlusherWaitMs(1000)
        .setDestroySenderWaitMs(4000);

LogProducerClient client = new LogProducerClient(config, result -> {
    if (result.isSuccess()) {
        return;
    }
    android.util.Log.w("TLSProducer", result.getFailureSummary());
});
```

构造函数会复制 config 并立即冻结原对象；继续调用原 config 的 setter 会抛出 `IllegalStateException`。native producer 仍是惰性创建，首次 `addLog` 或运行时更新操作才会创建 native 实例。

### 写入日志

```java
import com.volcengine.tls.android.producer.Log;

Log log = new Log()
        .putContent("event_id", "your-stable-event-id")
        .putContent("level", "info")
        .putContent("message", "hello tls")
        .setLogTime(System.currentTimeMillis());

client.addLog(log);
```

`addLog(log, 1)` 只是请求尽快 flush，不等待服务端响应，也不会把 Buffered WAL 变成 Sync WAL。`addLog` 返回表示当前可靠性模式完成本地接收步骤；服务端结果通过 callback 返回。

### 关闭

先停止新增日志，再触发关闭并按需等待：

```java
client.destroyLogProducer();
boolean closed = client.awaitDestroy(6000);
```

示例在构造前设置了 `1000 ms` 的 flusher 预算和 `4000 ms` 的 sender 预算；默认值都是 `0`。`awaitDestroy` 只等待已经启动的 Java/native 销毁工作，不会增加 native 内部预算，也不表示日志已经送达服务端；返回 `false` 不表示 native 已被强制终止。销毁后 client 不可再次写入或更新。

内存模式耗尽单轮重试预算后报告终态失败；persistent 模式的可重试失败会保留记录并进入后续跨轮处理，见[使用指南](usage.md#网络超时与重试)。

需要 WAL、恢复和重复处理时，继续阅读[持久化与恢复](usage.md#持久化与恢复)；需要凭证轮转时，阅读[身份认证](usage.md#身份认证)。

## 验证写入

一次完整验证要区分三个阶段：本地接收、批次 callback 和服务端查询。不要把 `addLog` 正常返回直接当成远端成功。

### 写入唯一标识

为每条验证日志生成不会复用的事件 ID：

```java
String marker = "android-sdk-<unique-id>";

client.addLog(new Log()
        .putContent("event_id", marker)
        .putContent("scenario", "quick-start")
        .putContent("expected_count", "1"), 1);
```

验证数据和排查日志都不要写入 AK、SK、STS token、Authorization 或完整请求体。

### 记录 callback

在 callback 中只记录非敏感诊断字段：

- `result.isSuccess()` 和 `result.getCode()`；
- `result.getFailureKind()`、`result.isRetryable()`；
- `result.getHttpCode()`、`result.getTransportKind()`、`result.getTransportCode()`；
- `result.getRequestId()`、`result.getErrorCode()`；
- `result.getLogBytes()`、`result.getCompressedBytes()`；
- 有效时的 `result.getStartId()` 和 `result.getEndId()`。

只有成功 callback 才表示对应批次收到了服务端成功响应。persistent 模式的认证失败 retain 不发终态失败 callback，见[使用指南](usage.md#身份认证)。

### 在服务端查询

在目标 Topic 的检索页面中选择覆盖发送时间的范围，用事件 ID 精确查询：

```text
event_id:"android-sdk-<unique-id>"
```

核对事件是否存在、字段值和时间戳是否正确。at-least-once 模式允许总命中数大于唯一事件数；验证时应同时统计唯一 ID、总命中数、重复数和缺失数。

### 恢复验证

对 persistent 场景，另建测试 Topic 做以下验证：

1. 写入连续且唯一的事件 ID。
2. 在网络不可用或进程异常结束的情况下保留 WAL。
3. 使用相同的应用私有目录重新创建 client。
4. 恢复网络，调用 `client.updateEndpoint(originalEndpoint, originalRegion, originalTopicId)`，三个值必须与创建时相同，以触发 native 创建和 WAL recover；仅构造 client 不会启动恢复。
5. 按测试场景设置有界等待，观察 callback 后查询服务端；等待超时应进入[故障排查](operations.md#故障排查)，不能直接判定日志已丢失。
6. 分别统计唯一事件、总命中、重复和缺失。

不要用 callback 次数代替服务端去重结果，也不要把一次设备验证结果当成吞吐承诺。
