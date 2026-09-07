# TLS Android Producer SDK

面向 Android 日志写入场景的轻量 Producer SDK。SDK 基于 native producer，提供异步写入、批量聚合、LZ4 压缩、退避重试、本地 WAL 持久化和进程重启后的断点续传。

> 本仓库后续只维护 `tls-android-producer` 写入能力。Project/Topic/Index 管理、查询、消费和分析等全量 TLS API 请使用 Java SDK；仓库中的历史 `core`、`full` 和其他模块不代表当前推荐接入面。

## 快速导航

- [能力概览](#能力概览)
- [选择可靠性模式](#选择可靠性模式)
- [5 分钟接入](#5-分钟接入)
- [开启持久化和断点续传](#开启持久化和断点续传)
- [持久化语义与边界](#持久化语义与边界)
- [失败处理与回调](#失败处理与回调)
- [配置参考](#配置参考)
- [运行时更新与生命周期](#运行时更新与生命周期)
- [常见问题](#常见问题)

## 能力概览

| 能力 | 当前行为 |
| --- | --- |
| 异步写入 | `addLog` 把日志交给 native producer，聚合和网络发送在后台执行 |
| 批量聚合 | 按原始日志字节数、日志条数和等待时间触发发送 |
| 压缩 | Android 公共 API 支持 `LZ4` 和 `NONE`，默认 `LZ4` |
| 退避重试 | 按总时间、最大尝试次数和退避间隔控制单轮重试 |
| 内存背压 | 单 client 默认最多使用 `64 MiB` producer 内存预算，超限时同步失败 |
| 持久化 WAL | 支持 buffered WAL 和逐条 sync WAL 两种落盘强度 |
| 断点续传 | 使用相同目录重建 client 时自动恢复未确认记录 |
| At-least-once | persistent 模式优先保证不漏发，崩溃和 checkpoint 边界可能产生重复 |
| 容量治理 | 支持 bytes、records、segments 三维上限和 high/low watermark 回收 |
| 溢出策略 | 支持拒绝、限时阻塞、丢最旧未确认记录和新日志采样 |
| 动态更新 | 支持更新 endpoint/region/topic 和轮转 AK/SK/STS token |
| 多进程 | 非主进程会自动使用进程隔离的子目录；同进程多个 client 仍需独立目录 |
| 结果诊断 | 回调提供 HTTP、transport、requestId、重试属性和日志 ID 范围 |

## 选择可靠性模式

Android SDK 对外提供三档可靠性模式。不存在一个同时拥有最低延迟、最低 IO 和最强可靠性的配置，应按日志价值选择。

| 模式 | 配置 | `addLog` 正常返回的主要边界 | 进程崩溃后补传 | 突然掉电保护 | 开销与建议 |
| --- | --- | --- | --- | --- | --- |
| 内存模式 | `setPersistent(false)` | 日志进入受限内存队列 | 不保证 | 不保证 | 开销最低，适合可丢的普通日志 |
| Buffered WAL | persistent + `BUFFERED_WAL` | WAL record `write` 成功，可能仍在 OS page cache | 支持 | 存在未同步窗口 | 推荐的断点续传默认档 |
| Sync WAL | persistent + `SYNC_WAL` | WAL record `write` 和文件 `fsync` 成功 | 支持 | 最强 | 每条日志同步文件，IO 和写入延迟最高，仅用于关键日志 |

三种模式都不提供 exactly-once。persistent 模式提供的是 at-least-once：服务端可能收到重复日志，业务应使用事件 ID、请求 ID 或其他业务主键做消费侧去重。

推荐选择：

- 埋点、调试日志、可采样日志：内存模式。
- 需要进程崩溃后补传，但可接受极端掉电窗口：Buffered WAL。
- 审计、计费等必须尽量缩小掉电丢失窗口的日志：Sync WAL，并先在目标设备上评估 IO 和耗电。

## 5 分钟接入

### 1. 添加依赖

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.volcengine-tls:tls-android-producer:2.1.3'
}
```

SDK 要求 Android API 19 及以上，宿主应用需要网络权限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

API 19-20 使用系统 `HttpsURLConnection`/JSSE。服务端需要保留这些系统能够协商的 TLS 套件，例如 `TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA`，同时应继续保留现代 GCM/ChaCha 套件。SDK 不修改应用全局 TLS Provider，也不公开关闭证书校验的配置。

### 2. 创建 client

```java
import com.volcengine.tls.android.producer.LogProducerClient;
import com.volcengine.tls.android.producer.LogProducerConfig;

LogProducerConfig config = new LogProducerConfig()
        .setEndpoint("https://your-tls-endpoint")
        .setRegion("your-region")
        .setTopicId("your-topic-id")
        .setAccessKeyId("your-access-key-id")
        .setAccessKeySecret("your-access-key-secret")
        .setSecurityToken("your-sts-token")
        .setCompressType(LogProducerConfig.CompressType.LZ4)
        .setDestroyFlusherWaitMs(1000)
        .setDestroySenderWaitMs(4000);

LogProducerClient client = new LogProducerClient(config, result -> {
    if (!result.isSuccess()) {
        android.util.Log.w("TLSProducer", result.getFailureSummary());
    }
});
```

生产应用不要在 APK 中长期固化 AK/SK。优先由服务端签发短期 STS 凭证，并在过期前调用 `resetSecurityToken` 轮转。

`LogProducerClient` 构造时会冻结并复制 config。构造后继续调用原 config 的 setter 会抛出 `IllegalStateException`，不会动态改变已创建 client。

### 3. 写入日志

```java
import com.volcengine.tls.android.producer.Log;

Log log = new Log()
        .putContent("level", "info")
        .putContent("event_id", "your-stable-event-id")
        .putContent("message", "hello android producer")
        .setLogTime(System.currentTimeMillis());

try {
    client.addLog(log);
} catch (RuntimeException e) {
    android.util.Log.w("TLSProducer", "addLog failed", e);
}
```

`addLog(log, 1)` 中的 `1` 只提示 producer 尽快 flush，不等于同步等待服务端成功，也不把 Buffered WAL 自动提升为 Sync WAL。

### 4. 关闭 client

先停止业务写入，再触发异步销毁，并做有界等待：

```java
client.destroyLogProducer();
boolean closed = client.awaitDestroy(6000);
```

`setDestroyFlusherWaitMs` 和 `setDestroySenderWaitMs` 控制 native flusher/sender 的关闭预算；`awaitDestroy` 只等待已经启动的销毁任务，不会增加 native 内部预算。也可以用 `setDestroyWaitMs` 配置一个兼容的总预算，但不要和拆分预算混用。

建议在 Application 级组件中复用 client，不要跟随 Activity 重建。`destroyLogProducer` 后该 client 不能再次写入。

## 开启持久化和断点续传

下面是推荐的 Buffered WAL 配置。`persistentFilePath` 是目录，不是单个 `.dat` 文件。

```java
import android.content.Context;
import android.os.Build;

import java.io.File;

File storageRoot = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
        ? context.getNoBackupFilesDir()
        : context.getFilesDir();
File walDirectory = new File(storageRoot, "tls-producer/main");

LogProducerConfig config = new LogProducerConfig()
        .setEndpoint("https://your-tls-endpoint")
        .setRegion("your-region")
        .setTopicId("your-topic-id")
        .setAccessKeyId("your-access-key-id")
        .setAccessKeySecret("your-access-key-secret")
        .setSecurityToken("your-sts-token")
        .setPersistent(true)
        .setPersistentFilePath(walDirectory.getAbsolutePath())
        .setPersistentDurability(
                LogProducerConfig.PersistentDurability.BUFFERED_WAL)
        .setPersistentMaxFileCount(10)
        .setPersistentMaxFileSize(1024 * 1024)
        .setPersistentMaxLogCount(65536)
        .setPersistentHighWatermarkPct(85)
        .setPersistentLowWatermarkPct(70)
        .setPersistentOverflowPolicy(
                LogProducerConfig.PersistentOverflowPolicy.REJECT_NEW)
        .setDestroyFlusherWaitMs(1000)
        .setDestroySenderWaitMs(4000);

LogProducerClient client = new LogProducerClient(config, result -> {
    if (!result.isSuccess()) {
        android.util.Log.w("TLSProducer", result.getFailureSummary());
    }
});
```

如果需要 Sync WAL，只修改 durability：

```java
config.setPersistentDurability(
        LogProducerConfig.PersistentDurability.SYNC_WAL);
```

旧 API `setPersistentForceFlush(true)` 兼容映射为 `SYNC_WAL`，新接入应使用 `setPersistentDurability`。显式配置 `BUFFERED_WAL` 后再设置 `persistentForceFlush=true` 会因语义冲突而失败。

### 断点续传如何发生

1. `addLog` 先把记录追加到本地 WAL，再进入内存发送路径。
2. 服务端请求成功后，SDK 推进连续 checkpoint，并回收可安全删除的已确认 segment。
3. 网络失败、认证失败、重试预算耗尽或内部队列暂时失败时，已持久化记录不会被隐式确认。
4. App 进程重启后，使用相同配置和相同持久化目录创建 client。
5. 第一次触发 native producer 创建时，Android binding 会先自动 recover 未确认记录，再处理本次新操作。

当前 Java API 是惰性创建：仅执行 `new LogProducerClient(...)` 不会立即打开 WAL 或开始补传。通常第一次 `addLog` 会触发创建和自动 recover；`updateEndpoint`、`resetSecurityToken` 也会触发创建。当前没有单独公开的 `start()`/`recover()` 方法，因此“App 启动后即使没有新日志也立即 drain backlog”不是当前 API 的保证。

### 目录使用规则

- 同一个逻辑 client 在重启前后必须复用同一目录，否则找不到原 backlog。
- 同一进程的多个活跃 client 必须使用不同目录，不能把一个目录当成多写者队列。
- 非主进程会在配置目录下追加清洗后的进程名，避免不同 Android 进程直接共用 WAL。
- 推荐使用应用私有且不参与云备份的目录，避免 WAL 被复制到另一设备。API 19 可回退到 `filesDir`，并在备份规则中排除该目录。
- 不要清理、移动或手工修改 `manifest`、`checkpoint`、`lease` 和 `seg-*.log` 文件。

### 容量和溢出策略

示例配置的主要上限是约 `10 MiB`、`65536` 条记录和 `10` 个 segment。也可以用以下 API 独立覆盖三维总上限：

- `setPersistentMaxBytes`
- `setPersistentMaxRecords`
- `setPersistentMaxSegments`

任一已配置维度达到 high watermark 会触发压力回收；SDK 尝试回收到 low watermark，或直到没有可安全回收的已确认 closed segment。active segment、未 durable ACK segment 和恢复游标所在 segment 不会被正常水位回收。

| 策略 | 空间不足时的行为 | 数据语义 |
| --- | --- | --- |
| `REJECT_NEW` | 拒绝新日志，保留旧 WAL | 默认推荐，不静默删除已接受日志 |
| `BLOCK` | 最多等待 `persistentBlockTimeoutMs` 后失败 | 不删除旧 WAL，但会阻塞调用线程 |
| `DROP_OLDEST_UNACKED` | 删除最旧未确认 closed segment 腾空间 | 明确破坏完整的 at-least-once |
| `DROP_NEWEST_SAMPLE` | 按 `persistentSampleEveryN` 采样保留新日志 | 会丢新日志，适合明确接受采样的场景 |

不要在 Android 主线程使用 `BLOCK`。除非业务已经书面接受数据损失，否则保持 `REJECT_NEW`。

## 持久化语义与边界

### At-least-once，不是 exactly-once

- 服务端成功才推进 checkpoint；失败不会被统一当成已处理。
- callback 成功表示请求进入服务端成功路径，不表示本地 checkpoint 已经 durable 落盘。
- checkpoint 保存失败或进程在成功回调附近崩溃时，重启可能重发已经到达服务端的日志。
- 业务不能接受重复时，必须使用稳定的 `event_id` 或业务主键去重。

### Buffered WAL 与 Sync WAL

- Buffered WAL 在 segment rotation、flush 和正常 close 时同步文件。进程崩溃后通常可恢复已经写入 page cache 的记录，但突然掉电仍有未同步窗口。
- Sync WAL 每次 append 都同步文件；只有 `write + fsync` 成功才正常返回，因此可靠性更强，但每条写入都承担同步 IO 成本。
- Sync WAL 中如果 `write` 成功但 `fsync` 失败，`addLog` 会抛异常，磁盘上仍可能留下可恢复记录；调用方立即重试可能形成重复。

### 失败后的后续重试

单轮重试受 `retryMaxAttempts` 和 `retryTotalTimeoutMs` 约束。当前源码 `2.1.3`（待发布）固定 C Core `v0.3.2`，SHA 为 `1d41ec4edb850ee7dd0b7f63c49738d6a9669c21`。Persistent 的暂时网络故障或 HTTP `429/500/502/503/504` 耗尽单轮预算后，会在同一 client 内自动开启下一轮，跨轮指数退避最长 5 分钟。关闭不等待跨轮退避计时器，未 ACK 的 WAL 保留到下次创建 client 时恢复。Memory 模式仍在单轮预算耗尽后报告终态失败。

证书/主机名验证失败、TLS 握手/协议错误、非法 URL 等永久错误不会自动重试；修正后重新创建 client 恢复 WAL。升级保持 WAL 格式不变，默认资源配置和 API 19 最低支持版本也不变。

认证失败默认按 retain 处理，不推进 persistent checkpoint，也不发终态失败 callback。修正凭证后调用 `resetSecurityToken(...)`，同一 client 会恢复发送被保留的记录，成功后只回调一次成功。调用方必须按 STS 过期时间提前获取新凭证并调用该接口，不能依赖失败 callback 触发刷新。Android 暂无独立的认证失败通知，Core 内部失败指标也不透出到 Java；没有 callback 不代表没有请求错误。

### 更新发送目标

WAL backlog 不绑定写入时的 endpoint、region 或 topic。调用 `updateEndpoint` 表示接入方接受以下风险：

- 已进入发送路径的请求可能仍使用旧目标。
- 后续请求会收敛到新目标。
- 尚未发送的旧 backlog 以及后续 recover 记录可能发送到新目标。

如果业务不能接受旧 backlog 改投新目标，应为新目标创建新的 client 和新的持久化目录。SDK 当前只告警该风险，不阻止更新。

## 失败处理与回调

同步调用和异步回调代表两个不同阶段：

| 信号 | 含义 | 建议 |
| --- | --- | --- |
| `addLog` 正常返回 | 当前可靠性模式的接收步骤完成，不代表服务端成功 | 等待 callback 或依赖 persistent 恢复 |
| `addLog` 抛异常 | 参数、生命周期、内存、磁盘、WAL 或 native 入队阶段失败 | 记录本地指标，按业务等级降级；不要无界重试 |
| callback 成功 | 对应批次进入服务端成功路径 | persistent 下仍可能因 checkpoint 边界产生重复 |
| callback 失败 | 本次发送路径结束；persistent 的认证 retain 和跨轮重试不发终态失败 | 查看 failure kind、HTTP/transport、requestId 和 retryable；失败不保证 WAL 已删除 |

persistent 模式下不能把所有 `addLog` 异常都解释为“记录一定未落盘”。append 后的内存入队失败，或 Sync WAL 的 `fsync` 失败，都可能留下后续可恢复记录。因此关键日志重试必须带稳定业务主键。

```java
LogProducerClient client = new LogProducerClient(config, result -> {
    if (result.isSuccess()) {
        return;
    }

    switch (result.getFailureKind()) {
        case AUTH:
            // Memory 认证失败可在此诊断；Persistent retain 不走此分支。
            // STS 必须按过期时间提前刷新，不能只依赖失败 callback。
            break;
        case TRANSPORT:
        case HTTP:
        case TIMEOUT:
            // 记录指标，由 SDK 完成本轮退避；业务侧避免立即无界重试。
            break;
        case PERSISTENCE:
            // 检查目录权限、剩余空间、目录是否被重复占用。
            break;
        default:
            break;
    }

    android.util.Log.w("TLSProducer", result.getFailureSummary());
});
```

可用于诊断的字段包括：

- `getFailureKind()`、`getCode()`、`isRetryable()`
- `getHttpCode()`、`getTransportKind()`、`getTransportCode()`
- `getErrorCode()`、`getErrorMessage()`、`getRequestId()`
- `getLogBytes()`、`getCompressedBytes()`
- `getStartId()`、`getEndId()`、`hasLogIdRange()`

callback 默认不在 sender 线程直接执行。不要在 callback 中做耗时工作；如果开启 `setCallbackFromSenderThread(true)`，更必须保证回调常数时间、无阻塞、无重入销毁。

## 配置参考

### 写入目标与日志属性

| 方法 | 默认值 | 说明 |
| --- | --- | --- |
| `setEndpoint` | 无 | TLS endpoint，必须包含正确协议和域名；必填 |
| `setRegion` | 无 | TLS region；必填 |
| `setProjectId` | `null` | 保留的 Project ID 字段，常规发送主要依赖 Topic |
| `setTopicId` | 无 | 目标 Topic ID；必填 |
| `setAccessKeyId` / `setAccessKeySecret` | `null` | AK/SK；真实发送需要有效授权 |
| `setSecurityToken` | `null` | STS token，临时凭证场景设置 |
| `setHashKey` | `null` | null/空串不指定；非空为 32 位小写十六进制且不能全为 f |
| `setSource` | `null` | LogGroup 的 `__source__` |
| `addTag` | 空 | 添加 LogGroup 级 tag |
| `setEnableTimeNs` | `false` | Core 纳秒字段开关；Java Log 当前仅传毫秒，没有独立纳秒余数参数 |

### 聚合、内存和重试

| 方法 | 默认值 | 说明 |
| --- | ---: | --- |
| `setCompressType` | `LZ4` | `LZ4` 或 `NONE` |
| `setPacketLogBytes` | `1048576` | 单个聚合包原始日志字节上限 |
| `setPacketLogCount` | `1024` | 单个聚合包日志条数上限 |
| `setPacketTimeoutMs` | `3000` | 聚合等待时间，单位 ms |
| `setMaxBufferLimit` | `67108864` | 单 client producer 内存预算，不等于 App 总 PSS |
| `setSendThreadCount` | `1` | sender 数；persistent 模式强制收敛为 `1` |
| `setRetryMaxAttempts` | `0` | 范围 `[0, 50]`；`0` 表示不按次数限制，仍受总超时限制 |
| `setRetryTotalTimeoutMs` | `90000` | 单轮发送和重试总预算，必须大于 `0` |
| `setRetryInitialIntervalMs` | `500` | 首次退避，范围 `[100, 30000]` ms |
| `setRetryMaxIntervalMs` | `10000` | 最大退避，范围 `[1000, 60000]` ms，且不小于 initial |
| `setConnectTimeoutMs` | `0` | `0` 使用 native 默认 `10000` ms |
| `setRequestTimeoutMs` | `0` | `0` 使用 native 默认 `50000` ms |

默认配置基线是：单包最多 `1 MiB`、最多 `1024` 条、最多等待 `3000 ms`、`64 MiB` 内存预算、`1` 个 sender、LZ4 压缩。不要为了追求吞吐盲目增加 sender；persistent 当前固定为单 sender。

### Persistent

| 方法 | 默认值 | 说明 |
| --- | ---: | --- |
| `setPersistent` | `false` | 开启 WAL 和断点续传 |
| `setPersistentFilePath` | `null` | WAL 目录；开启 persistent 后必填 |
| `setPersistentDurability` | `BUFFERED_WAL` | 选择 buffered 或逐条 sync |
| `setPersistentMaxFileCount` | `0` | segment 数量上限；开启 persistent 后必须显式大于 `0` |
| `setPersistentMaxFileSize` | `0` | 单 segment 字节上限；开启 persistent 后必须显式大于 `0` |
| `setPersistentMaxLogCount` | `0` | 单 segment 记录上限；开启 persistent 后必须显式大于 `0` |
| `setPersistentMaxBytes` | `0` | 总字节上限；`0` 按 file size x file count 推导 |
| `setPersistentMaxRecords` | `0` | 总记录上限；`0` 沿用 `persistentMaxLogCount` |
| `setPersistentMaxSegments` | `0` | 总 segment 上限；`0` 沿用 `persistentMaxFileCount` |
| `setPersistentHighWatermarkPct` | `85` | 任一容量维度达到该比例时触发压力回收 |
| `setPersistentLowWatermarkPct` | `70` | 回收目标；必须小于 high watermark |
| `setPersistentOverflowPolicy` | `REJECT_NEW` | 容量无法回收时的行为 |
| `setPersistentSampleEveryN` | `10` | `DROP_NEWEST_SAMPLE` 的采样参数 |
| `setPersistentBlockTimeoutMs` | `1000` | `BLOCK` 的最长等待时间 |

### 生命周期与回调

| 方法 | 默认值 | 说明 |
| --- | ---: | --- |
| `setDestroyWaitMs` | `0` | 兼容的总关闭预算；设置后会清除拆分预算 |
| `setDestroyFlusherWaitMs` | `0` | flusher 关闭预算；设置后启用拆分关闭 |
| `setDestroySenderWaitMs` | `0` | sender 关闭预算；设置后启用拆分关闭 |
| `setCallbackFromSenderThread` | `false` | 是否直接在 sender 线程回调 |

## 运行时更新与生命周期

构造后的 config 已冻结。当前只有两类参数可以在原 client 上更新：

```java
client.resetSecurityToken(newAk, newSk, newStsToken);
client.updateEndpoint(newEndpoint, newRegion, newTopicId);
```

- `resetSecurityToken` 用于 AK/SK/STS 轮转。
- `updateEndpoint` 更新后续发送目标，但存在已进入发送路径的旧请求和 persistent backlog 改投风险。
- 压缩、批量、缓存、sender、重试、persistent、目录、容量、overflow、回调线程和关闭预算都是 create-time 参数。修改它们需要新建 client。
- 切换 client 时先停止新写入，再销毁旧 client；persistent 场景应明确旧目录由谁继续 drain。

## 写入接口速查

| 方法 | 说明 |
| --- | --- |
| `new Log().putContent(key, value)` | 添加一个字段；`null` value 会转为空串 |
| `log.putContents(map)` | 批量添加字段 |
| `log.setLogTime(milliseconds)` | 设置日志毫秒时间戳，默认是创建 `Log` 时的当前时间 |
| `client.addLog(log)` | 异步写入一条日志 |
| `client.addLog(log, 1)` | 写入并提示尽快 flush，不等待服务端成功 |
| `client.resetSecurityToken(...)` | 动态轮转凭证 |
| `client.updateEndpoint(...)` | 动态更新发送目标 |
| `client.destroyLogProducer()` | 异步触发 close 和 destroy |
| `client.awaitDestroy(timeoutMs)` | 等待已启动的销毁任务 |

## R8 / ProGuard

AAR 已内置 [consumer-rules.pro](tls-android-modules/producer-native/consumer-rules.pro)，正常情况下不需要宿主额外 keep。不要宽泛 keep 整个 `com.volcengine.*`，否则会放大包体积；如果宿主还有二次字节码处理，应至少保留公共 producer API 和 JNI bridge 类。

## 源码构建与 C Core 版本

源码接入只需要 `producer-native` 模块：

```groovy
include ':producer-native'
```

构建会读取 `tls-android-modules/producer-native/ve-tls-c-sdk.version` 中固定的 C core full SHA，并拒绝 C checkout 的 HEAD 不匹配或 tracked tree 被修改。发布 AAR 可通过 `BuildConfig.VE_TLS_C_SDK_COMMIT` 反查实际编入的 C core commit。

默认公开产物承诺 Android API 19 及以上。低于 API 19 的构建仅用于指定客户的 best-effort 定制，不属于公开兼容性和稳定性承诺。

## 常见问题

### App 重启后为什么没有立刻补传？

client 是惰性创建。只调用构造函数不会打开 WAL；第一次 `addLog` 等操作创建 native producer 时才会自动 recover。当前没有独立公开的 `start()`/`recover()`。

### 为什么 callback 失败后磁盘文件没有删除？

这是预期行为。persistent 模式下，网络失败、认证失败和重试预算耗尽不会隐式 ACK 已持久化记录。删除它们会破坏断点续传语义。

### 为什么 `addLog` 抛异常后重启又看到了这条日志？

异常可能发生在 WAL append 之后，例如后续内存入队失败，或 Sync WAL 的 `fsync` 失败。此时记录可能仍能 recover。使用稳定业务主键处理重复，不要假设异常必然意味着磁盘中没有记录。

### 为什么 persistent 配置了多个 sender，实际仍只有一个？

Android binding 会把 persistent 模式收敛为单 sender，以保持恢复、连续 checkpoint 和发送顺序语义可控。

### 能否让多个 client 共用一个 persistent 目录？

不能。同一目录不是多写者队列。每个活跃 client 必须使用独立目录；多进程的自动子目录隔离不能替代同一进程内的 client 隔离。

### 可以动态切换 Topic 吗？

可以，但旧 backlog 可能改投新目标。能够接受该风险时调用 `updateEndpoint`；不能接受时新建 client 并使用新目录。

### 如何处理磁盘写满？

默认 `REJECT_NEW` 会保留旧 WAL 并让新写入失败。先监控本地异常和目录容量，再决定是否扩大限额或采用明确允许丢数据的 overflow policy。不要静默删除 WAL 文件。

## 性能与包体积基线

以下数据用于容量评估和版本回归，不是不同设备、网络或日志结构下的固定 SLA。

<details>
<summary>Android Producer 性能基线</summary>

测试环境：Android API 29 arm64 模拟器，4 个可用处理器；release 包；LZ4；单 client；真实环境发送；持续写入后等待 drain。

CPU 是单核等效占比，`100%` 表示约占满一个 CPU 核；内存是进程 PSS 峰值。日志量按原始日志大小估算，实际网络流量受压缩率影响。

| 模式 | 日志规格 | 发送 | 原始日志量 | 单核等效 CPU | PSS 峰值 |
| --- | --- | ---: | ---: | ---: | ---: |
| 内存缓存 | 约 200 B/条 | 200 条/秒 | 2.3 MB/分钟 | 2.2% | 23.5 MB |
| 内存缓存 | 约 200 B/条 | 500 条/秒 | 5.7 MB/分钟 | 5.1% | 24.1 MB |
| 内存缓存 | 约 700 B/条 | 200 条/秒 | 8.3 MB/分钟 | 2.0% | 24.4 MB |
| 内存缓存 | 约 700 B/条 | 500 条/秒 | 20.8 MB/分钟 | 4.3% | 24.9 MB |
| Buffered WAL | 约 200 B/条 | 200 条/秒 | 2.2 MB/分钟 | 6.0% | 24.9 MB |
| Buffered WAL | 约 200 B/条 | 500 条/秒 | 5.7 MB/分钟 | 13.2% | 24.7 MB |
| Buffered WAL | 约 700 B/条 | 200 条/秒 | 8.3 MB/分钟 | 6.4% | 24.9 MB |
| Buffered WAL | 约 700 B/条 | 500 条/秒 | 20.8 MB/分钟 | 14.3% | 25.6 MB |

业务验收应固定设备、Android 版本、release/debug、网络、endpoint、Topic、日志字段、日志大小、压缩类型和 durability。Sync WAL 必须单独测试，不能用 Buffered WAL 数据推断。

</details>

<details>
<summary>最小接入包体积基线</summary>

口径：`noProvider`、R8 和资源裁剪开启、4 个 ABI 全部打入 release APK。

| 项目 | 大小 | 说明 |
| --- | ---: | --- |
| 未接入 SDK 的空样例 APK | 45.1 KB | 对照包 |
| 接入 producer 后 APK | 300.2 KB | Java wrapper 和 4 个 ABI native 库 |
| APK 增量 | +255.2 KB | 客户接入主要关注值 |
| producer AAR | 267.8 KB | 发布 AAR，不等同于最终 APK 增量 |

增量主要包括 4 个 ABI 合计约 `221.3 KB` 的 native 库和约 `32.6 KB` 的 dex。线上建议使用 AAB 或 ABI split，让设备只下载匹配的 native 库。

</details>

## SDK 分工

| 需求 | 推荐 SDK |
| --- | --- |
| Android 异步写日志 | 本仓库 `tls-android-producer` |
| Android 持久化和断点续传写入 | 本仓库 `tls-android-producer` |
| Project/Topic/Index 管理 | Java SDK |
| 查询、消费和分析 | Java SDK |
| 非 Android 服务端接入 | Java SDK |

## Security and privacy

This project takes security seriously. For vulnerability reporting and supported versions, see [SECURITY.md](SECURITY.md).
