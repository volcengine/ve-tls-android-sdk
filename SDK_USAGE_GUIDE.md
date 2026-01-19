# TLS Android SDK 使用指南（面向客户）

本指南面向第一次接入 TLS 日志服务的开发者，目标是让你从 0 到 1 跑通：创建资源 → 集成 SDK → 发送日志 → 控制台验证。

## 你需要先知道的两件事

- SDK 依赖坐标（Maven Central）
  - 只需要发送日志（推荐）：`io.github.volcengine-tls:tls-android-producer:2.0.1`
  - 需要完整管理能力（创建 Project/Topic/Index、检索等）：`io.github.volcengine-tls:tls-android-full:2.0.1`
- 必要参数（后面会用到）
  - `endpoint`：TLS 接入域名，形如 `https://tls-cn-xxx.volces.com`
  - `region`：地域标识，例如 `cn-xxx`
  - `topicId`：日志主题 ID（Producer 发日志必填）
  - `accessKeyId/accessKeySecret`：AK/SK
  - `securityToken`：STS 临时凭证可选（没有就传空/不设置）

## 第 0 步：在控制台准备资源与凭证

### 0.1 开通并进入 TLS 控制台

在火山引擎控制台开通日志服务并进入 TLS 控制台。

### 0.2 创建 Project / Topic（至少要拿到 topicId）

在 TLS 控制台创建：
- Project（项目）
- Topic（主题）

记录 Topic 的 `topicId`，后面发送日志会用到。

### 0.3 获取 AK/SK（或 STS）

在访问控制（IAM）里创建并获取：
- AccessKeyId（AK）
- SecretAccessKey（SK）

如果你使用 STS 临时鉴权，还需要拿到 `securityToken`（可选）。

## Android 应用接入（推荐：Producer 异步发送）

### 1. 创建 Android 工程

使用 Android Studio 新建应用工程即可，建议：
- `minSdk >= 19`
- `compileSdk/targetSdk` 使用你当前项目的版本（示例工程使用 34）

### 2. 配置 Maven Central

在 `settings.gradle`（或 `settings.gradle.kts`）确保仓库包含 `mavenCentral()`：

```groovy
dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
  }
}
```

### 3. 添加依赖与权限

在 App 模块的 `build.gradle` 中添加依赖：

```groovy
dependencies {
  // 轻量发送（推荐）
  implementation 'io.github.volcengine-tls:tls-android-producer:2.0.1'
  // 使用 lz4 压缩时引入，否则可省略（不用 lz4 时可将 compressType 设为 zlib）
  implementation 'net.jpountz.lz4:lz4:1.3.0'
}
```

在 `AndroidManifest.xml` 添加网络权限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### 4. 配置管理（避免把 AK/SK 写死在代码里）

不建议把 AK/SK 明文写在代码仓库中。常用做法：

#### 方式 A：BuildConfig 注入（最简单）

在 `app/build.gradle` 的 `defaultConfig` 里注入：

```groovy
android {
  defaultConfig {
    buildConfigField 'String', 'TLS_ENDPOINT', '"https://tls-cn-example.volces.com"'
    buildConfigField 'String', 'TLS_REGION', '"cn-example"'
    buildConfigField 'String', 'TLS_AK', '"AK_EXAMPLE"'
    buildConfigField 'String', 'TLS_SK', '"SK_EXAMPLE"'
    buildConfigField 'String', 'TLS_TOKEN', '""'
    buildConfigField 'String', 'TLS_TOPIC_ID', '"topic-example"'
  }
}
```

#### 方式 B：本地配置文件（只放本机，不提交仓库）

可以放到 `filesDir`/`externalFilesDir` 下（示例 App 会从 `tls_config.properties` 读取，参考 [ConfigLoader.java](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/app/src/main/java/com/volcengine/tls/android/demo/ConfigLoader.java)）。

仓库提供了一个不含真实密钥的模板文件，可直接拷贝后改值：
- `tls_config.properties.example`：https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls_config.properties.example

把文件重命名为 `tls_config.properties` 后放到以下任一位置（示例 App 会按顺序读取）：
- 外部私有目录（推荐，方便 adb 写入）：`/sdcard/Android/data/<你的包名>/files/tls_config.properties`
- 内部私有目录：`/data/data/<你的包名>/files/tls_config.properties`

使用 adb 写入示例：

```bash
adb push tls_config.properties /sdcard/Android/data/<你的包名>/files/tls_config.properties
```

### 5. 初始化并启动 Producer

在 `Application` 或首个页面创建并启动（建议全局单例）：

```java
import com.volcengine.tls.android.producer.LogProducerClient;
import com.volcengine.tls.android.producer.LogProducerConfig;

LogProducerConfig cfg = new LogProducerConfig()
    .setEndpoint(BuildConfig.TLS_ENDPOINT)
    .setRegion(BuildConfig.TLS_REGION)
    .setAccessKeyId(BuildConfig.TLS_AK)
    .setAccessKeySecret(BuildConfig.TLS_SK)
    .setSecurityToken(BuildConfig.TLS_TOKEN) // 可为空
    .setTopicId(BuildConfig.TLS_TOPIC_ID)
    .setCompressType("lz4") // 或 "zlib"
    .setSendThreadCount(2)
    .setRetryCount(3)
    .setPacketLogBytes(256 * 1024)
    .setPacketLogCount(512)
    .setPacketTimeout(1000);

LogProducerClient client = new LogProducerClient(cfg);
client.start();
```

### 6. 发送一条日志（验证链路）

```java
import java.util.HashMap;
import java.util.Map;

Map<String, String> kv = new HashMap<>();
kv.put("key", "value");
kv.put("app", "demo");
kv.put("ts", String.valueOf(System.currentTimeMillis()));

client.sendLog(kv, result -> {
  if (result.isSuccess()) {
    // 发送成功
  } else {
    // 发送失败：可从 result.getAttempts() 里拿到 httpCode / errorCode / errorMessage
  }
});
```

如果你希望自定义日志时间：

```java
client.sendLog(kv, System.currentTimeMillis(), r -> {});
client.sendLog(kv, System.currentTimeMillis(), 123456789, r -> {});
```

### 7. 关闭与资源释放

在 `onDestroy` 或应用退出时关闭：

```java
client.close();
```

### 8. 控制台验证

在 TLS 控制台按 `topicId` 查询最新日志，检查：
- 是否能看到你写入的 key/value
- 时间字段是否正确
- 压缩类型是否与配置一致（lz4/zlib）

建议你按下面“截图式步骤”逐步核对（不同控制台 UI 可能略有差异，但路径一致）：

1. 打开火山引擎控制台并进入日志服务（TLS）
   - 截图点：顶部服务入口与左侧导航栏
2. 进入你创建的 Project
   - 截图点：Project 列表中目标项目的名称/ID
3. 进入 Topic 列表，找到 `topicId` 对应的 Topic
   - 截图点：Topic 列表中的 TopicName 与 TopicId
4. 打开“日志查询/检索”（Search/Query）
   - 截图点：检索页面的 Topic 选择框与时间范围选择器
5. 输入查询条件并执行
   - 建议先用 `*`（全量）或按你写入的字段过滤（例如 `key:value`）
   - 截图点：查询语句与返回的第一条日志详情
6. 在结果里确认关键字段
   - `key/value` 是否一致
   - `ts/time` 是否在你刚发送的时间附近
   - 来源/文件名（如你设置了 source/file）是否符合预期

### 9. R8/混淆（Release 建议做）

Release 打包开启 R8 后，如遇运行时反射/序列化相关问题，按需添加 keep。示例规则可直接参考：
- [proguard-rules.pro](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/app/proguard-rules.pro)

最小建议（按你项目的实际依赖增删）：
- `com.volcengine.*`
- `okhttp3.*` / `okio.*`
- `com.google.protobuf.*`
- 如果使用 lz4：`net.jpountz.*`

## Android 应用接入（可选：Full 同步 Client API）

如果你需要在客户端里做资源管理或同步调用（不推荐在移动端做大量管理类操作），使用 Full 包：

```groovy
dependencies {
  implementation 'io.github.volcengine-tls:tls-android-full:2.0.1'
  implementation 'net.jpountz.lz4:lz4:1.3.0'
}
```

示例（写入日志）：

```java
import com.volcengine.model.tls.ClientBuilder;
import com.volcengine.model.tls.ClientConfig;
import com.volcengine.model.tls.request.PutLogsRequestV2;
import com.volcengine.model.tls.response.PutLogsResponse;
import com.volcengine.service.tls.TLSLogClient;

ClientConfig cfg = new ClientConfig(
  BuildConfig.TLS_ENDPOINT,
  BuildConfig.TLS_REGION,
  BuildConfig.TLS_AK,
  BuildConfig.TLS_SK,
  BuildConfig.TLS_TOKEN
);

TLSLogClient client = ClientBuilder.newClient(cfg);
PutLogsRequestV2 req = new PutLogsRequestV2(/* logs */ , BuildConfig.TLS_TOPIC_ID, null, "lz4", "source", "file");
PutLogsResponse resp = client.putLogsV2(req);
client.destroy();
```

## Java 环境（命令行）QuickStart（创建资源 + 写入 + 检索 + 清理）

仓库提供了可直接运行的 Java 示例：
- [android-example/README.md](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/android-example/README.md)
- QuickStart 源码：[QuickStart.java](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/android-example/src/main/java/com/volcengine/example/tls/QuickStart.java)

运行方式（在仓库根目录）：

```bash
endPoint="https://tls-cn-xxx.volces.com" \
region="cn-xxx" \
ak="YOUR_AK" \
sk="YOUR_SK" \
token="" \
bash android-example/run-quickstart.sh
```

## 常见问题排查

### 1）Android 9+ 使用 http 访问失败

如果你的 `endpoint` 是 `http://...`（不推荐），Android 9+ 默认禁用明文流量，需要配置 `network_security_config` 允许 cleartext。

### 2）依赖冲突（okhttp/okio/protobuf 版本冲突）

优先保持 App 里已有依赖版本不变，通过 Gradle 统一版本（示例写法）：

```groovy
implementation('com.squareup.okhttp3:okhttp') {
  version { strictly("3.12.13") }
}
```

### 3）Release 运行崩溃（R8 误删）

先确认 Release 构建能通过，然后按崩溃日志补充 keep；建议直接对照示例 keep 规则最小化调整。

### 4）发送失败（401/403/400/5xx）

优先检查：
- `endpoint/region` 是否匹配
- `topicId` 是否正确
- AK/SK 是否有写入权限
- STS token 是否过期

Producer 回调 `result.getAttempts()` 里通常能看到 httpCode 与 errorMessage（示例实现可参考 [MainActivity.java](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/app/src/main/java/com/volcengine/tls/android/demo/MainActivity.java)）。
