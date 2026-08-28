# TLS Android Producer SDK

本仓库后续定位为 **Android Producer 写入 SDK**：面向 Android 端日志写入场景，基于 native producer 提供异步写入、聚合发送、压缩、重试与断点续传能力。

本仓库不再作为 Android 全量接口 SDK 维护。Project/Topic 管理、查询、索引、消费等全量接口，请使用 Java SDK；历史 core、full、OT、Trace、Crash、Network Diagnosis、OkHttp/WebView instrumentation 等模块不作为后续主要维护和客户接入口。

如果仓库中仍保留历史模块目录或发布脚本，仅作为存量代码与迁移参考，不代表这些模块会进入后续 Android SDK 发布物。后续 Android 侧发布、文档和客户支持口径都以 `tls-android-producer` 写入能力为准。

## 版本线策略

- `2.1.x` 及后续版本是 producer-native 主线，只发布并推荐使用 `io.github.volcengine-tls:tls-android-producer`。
- `2.0.x` 是历史 Android SDK 维护线，仅用于 core、full、老 producer 的必要 bugfix、安全修复和构建兼容修复。
- 新接入不要使用本仓库历史 full/core 模块；如果需要全量 TLS API，请使用 Java SDK。
- 历史模块如继续保留在仓库中，只用于存量迁移参考或内部兼容验证，不进入 `2.1.x` 发布口径。

## 适用场景

- Android App 或 Android SDK 只需要写入日志到 TLS。
- 需要 native producer 的聚合、压缩、异步发送能力。
- 需要断点续传，保证日志上传 At Least Once。
- 需要在 Android 侧控制缓存、批量大小、超时、持久化文件等写入参数。

不适合使用本仓库的场景：

- 需要创建、修改、删除 Project/Topic/Index 等管控接口。
- 需要查询、消费、分析等全量 TLS API。
- 需要纯 Java、非 Android 的服务端或工具链集成。

这些场景请使用 Java SDK，避免把 Android Producer 写入包当成全量 SDK。

## 当前维护范围

当前客户接入只应关注 Producer 写入模块。

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 异步写入 | 支持 | `addLog` 写入后由 native producer 后台发送 |
| 聚合发送 | 支持 | 按日志数、包大小、超时时间聚合 |
| 压缩 | 支持 | 默认 LZ4，也支持按需关闭压缩；源码启用 ZLIB 后可使用 ZLIB |
| 缓存上限 | 支持 | 超过上限后写入失败，调用方需处理返回码 |
| 断点续传 | 支持 | 写入本地 binlog，发送成功后删除，提供 At Least Once 语义 |
| 多客户端 | 支持 | 不同客户端必须使用不同持久化文件 |
| 全量 TLS API | 不提供 | 请使用 Java SDK |

## 性能测试

以下数据来自 `tls-android-producer` release 包的基线 benchmark，仅用于接入容量评估和回归对比，不承诺为不同设备、网络、日志结构下的固定 SLA。表格只保留客户接入时最常用的判断口径。

测试口径：

- 环境：Android API 29 arm64 模拟器，4 个可用处理器。
- 配置：LZ4 压缩，单 producer client，按目标 LPS 持续写入并等待 drain 完成。
- 日志规格：`约 200 B/条`、`约 700 B/条` 表示单条日志序列化前的近似大小。
- 发送：实际进入 producer 的写入速度，四舍五入为 `条/秒`。
- 日志量：按原始日志大小换算为 `MB/分钟`；开启 LZ4 后，实际网络上传流量通常会更低。
- CPU：换算为单核等效占比，`100%` 表示约占满 1 个 CPU 核。
- 内存：以进程 PSS 峰值为主，RSS 受系统共享库映射影响更大，仅适合辅助观察。

| 模式 | 日志规格 | 发送 | 日志量 | 单核等效 CPU | PSS 峰值 |
| --- | --- | ---: | ---: | ---: | ---: |
| 内存缓存 | 约 200 B/条 | 200 条/秒 | 2.3 MB/分钟 | 2.2% | 23.5 MB |
| 内存缓存 | 约 200 B/条 | 500 条/秒 | 5.7 MB/分钟 | 5.1% | 24.1 MB |
| 内存缓存 | 约 700 B/条 | 200 条/秒 | 8.3 MB/分钟 | 2.0% | 24.4 MB |
| 内存缓存 | 约 700 B/条 | 500 条/秒 | 20.8 MB/分钟 | 4.3% | 24.9 MB |
| 断点续传 | 约 200 B/条 | 200 条/秒 | 2.2 MB/分钟 | 6.0% | 24.9 MB |
| 断点续传 | 约 200 B/条 | 500 条/秒 | 5.7 MB/分钟 | 13.2% | 24.7 MB |
| 断点续传 | 约 700 B/条 | 200 条/秒 | 8.3 MB/分钟 | 6.4% | 24.9 MB |
| 断点续传 | 约 700 B/条 | 500 条/秒 | 20.8 MB/分钟 | 14.3% | 25.6 MB |

业务侧做性能验收时应固定以下变量，否则不同轮次无法直接比较：

- 使用 release 包，不使用 debug 包或打开额外日志。
- 使用相同设备、Android 版本、网络、endpoint、Topic、日志字段、日志大小、压缩类型和 persistent 开关。
- 至少记录发送条数、失败条数、`cpu_ms`、`wall_total_ms`、`available_processors`、`pss_peak_kb` 和上传流量。
- 高可靠场景必须单独测试 persistent 模式；不要用 memory 模式数据推断断点续传成本。

## SDK 包体积

包体积建议看“接入 SDK 后 APK 增量”，不要只看 AAR 原始大小。AAR 是发布形态，最终 APK/AAB 会受 R8、资源裁剪、ABI split、依赖传递和签名方式影响。

以下是最小接入样例的 release 包对比，口径为 `noProvider`、R8 开启、资源裁剪开启、4 个 ABI 全部打入 APK：

| 项目 | 大小 | 说明 |
| --- | ---: | --- |
| 未接入 SDK 的空样例 APK | 45.1 KB | 用于做 APK Analyzer 的 previous APK |
| 接入 producer 后 APK | 300.2 KB | 包含 producer Java wrapper 与 4 个 ABI 的 native 库 |
| APK 增量 | +255.2 KB | 客户最应该关注的包体积影响 |
| producer AAR | 267.8 KB | 发布 AAR 本身，不能直接等同于 APK 增量 |

APK 增量主要来自 native 库和少量 Java wrapper：

| 增量项 | APK 内压缩后大小 | 说明 |
| --- | ---: | --- |
| `lib/*.so` | 221.3 KB | 4 个 ABI 合计；单设备只需要其中 1 个 ABI |
| `classes.dex` | 32.6 KB | producer Java API、回调和 JNI wrapper |
| Manifest / META-INF 等 | 小于 2 KB | 对总包体积影响很小 |

要达到最小包体积，按以下顺序收敛：

1. 只接入 `tls-android-producer` 写入包，不接入 full/core 查询、消费、管控模块。
2. 使用 release 构建，并开启 `minifyEnabled true`、`shrinkResources true`。
3. 避免宽泛 keep 整个 `com.volcengine.*`，只保留 README 中给出的 producer 必要 keep 规则。
4. 使用 `noProvider` 形态，不额外引入 SLF4J provider、OkHttp 或其他日志门面依赖。
5. 默认使用 LZ4 最小 native 包；只有明确需要时再启用 ZLIB。
6. 面向线上分发时开启 ABI split 或使用 AAB，让用户设备只下载匹配 ABI 的 native 库。
7. 用 Android Studio APK Analyzer 的 `Compare with previous APK...` 对比“未接 SDK 空样例”和“接入 SDK 样例”，以最终 APK 增量作为发布口径。

如果不启用 ABI split，APK 会同时携带 `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64`；如果只面向真机发布，通常至少保留 `arm64-v8a`，再按业务兼容范围决定是否保留 `armeabi-v7a`。

## 环境要求

- Android API 19 及以上。
- API 19-20 使用系统 `HttpsURLConnection`/JSSE；为兼容这部分系统，TLS 服务端需要开放设备可协商的 CBC 套件，建议至少保留 `TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA`，同时保留现代 GCM/ChaCha 套件。SDK 不携带 Conscrypt，也不会修改应用全局 TLS Provider。
- Android 工程需声明 `INTERNET` 权限。
- 如果开启断点续传，持久化文件路径必须位于应用可写目录。

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## Gradle 接入

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.volcengine-tls:tls-android-producer:2.1.1'
}
```

如果使用源码方式接入，只依赖 producer-native 写入模块即可：

```groovy
include ':producer-native'
```

源码构建会校验 `producer-native/ve-tls-c-sdk.version` 中固定的 C core full SHA，并拒绝 HEAD 不匹配或 tracked tree 有修改的 C checkout。发布后的 AAR 可通过 `BuildConfig.VE_TLS_C_SDK_COMMIT` 反查实际编入的 C core commit。

## 混淆配置

SDK AAR 已内置 consumer rules；如果宿主工程有更严格的 R8/ProGuard 配置，按下面规则补齐，不要直接 keep 整个 `com.volcengine.*`。

```proguard
# Public producer API used by application code.
-keep class com.volcengine.tls.android.producer.Log { *; }
-keep class com.volcengine.tls.android.producer.LogProducerClient { *; }
-keep class com.volcengine.tls.android.producer.LogProducerConfig { *; }
-keep class com.volcengine.tls.android.producer.LogProducerCallback { *; }
-keep class com.volcengine.tls.android.producer.LogProducerResult { *; }
-keep class com.volcengine.tls.android.producer.LogProducerResult$* { *; }

# JNI entry points and Java classes looked up from native code by name.
-keep class com.volcengine.tls.android.producer.internal.JniNativeProducerBridge { *; }
-keep class com.volcengine.tls.android.producer.internal.NativeHttpBridge { *; }
-keep class com.volcengine.tls.android.producer.internal.NativeHttpBridge$Request { *; }
-keep class com.volcengine.tls.android.producer.internal.NativeHttpResponse { *; }
-keep class com.volcengine.tls.android.producer.internal.CallbackDispatcher { *; }

-keepclassmembers class com.volcengine.tls.android.producer.internal.JniNativeProducerBridge {
    native <methods>;
}
```

## 最小写入示例

```java
import com.volcengine.tls.android.producer.Log;
import com.volcengine.tls.android.producer.LogProducerClient;
import com.volcengine.tls.android.producer.LogProducerConfig;

LogProducerConfig config = new LogProducerConfig()
        .setEndpoint("https://your-tls-endpoint")
        .setRegion("your_region")
        .setTopicId("your_topic_id")
        .setAccessKeyId("your_access_key_id")
        .setAccessKeySecret("your_access_key_secret")
        .setSecurityToken("") // STS 场景传临时 token；长期 AK/SK 可留空
        .setCompressType(LogProducerConfig.CompressType.LZ4);

LogProducerClient client = new LogProducerClient(config, result -> {
    if (!result.isSuccess()) {
        android.util.Log.w("TLSProducer", result.getFailureSummary());
    }
});

Log log = new Log()
        .putContent("level", "info")
        .putContent("message", "hello android producer")
        .setLogTime(System.currentTimeMillis());

client.addLog(log);
```

应用退出或不再写入时释放 producer：

```java
client.destroyLogProducer();
client.awaitDestroy(3000);
```

## 配置说明

配置对象通过 `LogProducerConfig` 创建，再传入 `LogProducerClient`。`LogProducerClient` 会在构造时冻结 config，并在第一次写入时创建 native producer。

```java
LogProducerConfig config = new LogProducerConfig()
        .setEndpoint(endpoint)
        .setRegion(region)
        .setTopicId(topicId)
        .setAccessKeyId(accessKeyId)
        .setAccessKeySecret(accessKeySecret)
        .setSecurityToken(securityToken)
        .setHashKey("default-route")
        .setCompressType(LogProducerConfig.CompressType.LZ4)
        .setPacketLogBytes(1024 * 1024)
        .setPacketLogCount(1024)
        .setPacketTimeoutMs(3000)
        .setMaxBufferLimit(64 * 1024 * 1024)
        .setSendThreadCount(1)
        .setRetryMaxAttempts(3)
        .setRetryTotalTimeoutMs(90_000)
        .setRetryInitialIntervalMs(500)
        .setRetryMaxIntervalMs(10_000);

LogProducerClient client = new LogProducerClient(config);
```

关键配置建议：

- `endpoint`、`region`、`topicId`、`accessKeyId`、`accessKeySecret` 是最小必填项；STS 场景还需要 `securityToken`。
- `hashKey` 用于服务端路由和有序性控制；同一个 hashKey 的日志在服务端按同一路由处理，不同 hashKey 可提升并发分散度。
- `packetLogBytes`、`packetLogCount`、`packetTimeoutMs` 共同决定批量大小和发送延迟；吞吐优先可增大批量，低延迟优先可降低 timeout。
- `maxBufferLimit` 是单 client 内存缓存上限；写入速度长期高于发送速度时，超过上限会导致 `addLog` 抛异常。
- `retryMaxAttempts`、`retryTotalTimeoutMs`、`retryInitialIntervalMs`、`retryMaxIntervalMs` 控制 SDK 内部退避重试；业务侧不应在主线程做无界重试。

### 动态更新配置

不要通过继续修改原 `LogProducerConfig` 来做动态更新。原因有两点：

- `LogProducerClient` 构造时会把 config 冻结，后续再调用 `config.setXxx(...)` 会抛出 `IllegalStateException`。
- native producer 使用的是构造时生成的 `ConfigSnapshot`；即使绕过冻结去改 Java 对象，也不会自动同步到 native 发送路径。

当前支持在原 client 上动态更新的只有两类：

- `client.updateEndpoint(endpoint, region, topicId)`：更新后续新请求的发送目标；已经进入 native 发送路径的请求可能仍使用旧目标。
- `client.resetSecurityToken(accessKeyId, accessKeySecret, securityToken)`：事务化更新 AK/SK/STS token；persistent 认证失败默认 retain，更新成功后同一 client 会恢复发送被保留的记录。

```java
client.updateEndpoint(newEndpoint, newRegion, newTopicId);
client.resetSecurityToken(newAccessKeyId, newAccessKeySecret, newSecurityToken);
```

以下参数是 create-time 参数，运行中修改不会生效；如需变更，应创建新的 config/client，完成业务切流后销毁旧 client：

- 压缩类型、批量参数、缓存上限、发送线程数、重试策略。
- persistent 开关、持久化路径、持久化容量、强制刷盘策略。
- 默认 hashKey、source、tag、回调线程策略、连接/请求超时、destroy 等生命周期参数。

如果开启 persistent 且需要切换 endpoint/region/topicId，建议同时切换到新的 `persistentFilePath` 或直接新建 client。否则旧路径中已持久化的 backlog 可能被恢复后发送到新的目标。

### 全量参数说明

| 方法 | 默认值 | 取值与说明 | 推荐使用方式 |
| --- | --- | --- | --- |
| `setEndpoint(String)` | 无 | TLS endpoint，建议传完整协议前缀，例如 `https://...` | 必填 |
| `setRegion(String)` | 无 | TLS region | 必填 |
| `setProjectId(String)` | `null` | Project ID，当前 producer 写入路径保留字段 | 有明确业务需要时设置 |
| `setTopicId(String)` | 无 | 写入目标 Topic ID | 必填 |
| `setAccessKeyId(String)` | 无 | 访问凭证 AK | 必填 |
| `setAccessKeySecret(String)` | 无 | 访问凭证 SK | 必填 |
| `setSecurityToken(String)` | `null` | STS 临时 token；长期 AK/SK 场景可为空 | STS 场景必填 |
| `setHashKey(String)` | `null` | 默认 hashKey，创建后不支持动态修改 | 需要有序或路由分散时设置 |
| `setSource(String)` | `null` | `__source__` 字段 | 需要固定来源标识时设置 |
| `addTag(String, String)` | 空 | 写入请求附带的 tag；重复 key 按追加顺序保留 | 需要公共标签时设置 |
| `setCompressType(CompressType)` | `LZ4` | `LZ4` 或 `NONE` | 一般保持默认 |
| `setPacketLogBytes(int)` | `1048576` | 单个发送包的日志字节数上限，单位 byte | 常用 `256 KB` ~ `1 MB` |
| `setPacketLogCount(int)` | `1024` | 单个发送包的日志条数上限 | 常用 `512` ~ `1024` |
| `setPacketTimeoutMs(int)` | `3000` | 缓存日志的发送超时时间，单位 ms | 低延迟 `1000`，常规 `3000` |
| `setMaxBufferLimit(int)` | `67108864` | 单 client 内存缓存上限，单位 byte | 常用 `64 MB`，低内存设备可下调 |
| `setSendThreadCount(int)` | `1` | 发送线程数；persistent 模式下会收敛为 `1` | 默认即可 |
| `setRetryMaxAttempts(int)` | `0` | 最大尝试次数，范围 `[0, 50]`；`0` 表示不按次数限制，仅受总超时约束 | 常用 `3` |
| `setRetryTotalTimeoutMs(int)` | `90000` | 单条发送含重试的总预算，必须 `> 0`，单位 ms | 默认 `90s` |
| `setRetryInitialIntervalMs(int)` | `500` | 首次退避间隔，范围 `[100, 30000]`，单位 ms | 默认 `500ms` |
| `setRetryMaxIntervalMs(int)` | `10000` | 最大退避间隔，范围 `[1000, 60000]`，且不小于 initial interval | 默认 `10s` |
| `setEnableTimeNs(boolean)` | `false` | 是否启用纳秒时间字段；需配合带 `timeNs` 的 `addLog` 使用 | 只有需要高精度时间时开启 |
| `setPersistent(boolean)` | `false` | 是否开启断点续传 | 高可靠场景开启 |
| `setPersistentFilePath(String)` | `null` | 持久化文件路径；必须位于应用可写目录 | persistent 开启时必填 |
| `setPersistentDurability(PersistentDurability)` | `BUFFERED_WAL` | buffered 在 rotation、flush、close 时刷盘；sync 每次 append 刷盘 | 只有明确需要更强落盘边界时使用 `SYNC_WAL` |
| `setPersistentForceFlush(boolean)` | `false` | 兼容 API；`true` 映射为 `SYNC_WAL` | 新接入使用 `setPersistentDurability` |
| `setPersistentMaxFileCount(int)` | `0` | segment 文件数量上限；persistent 开启时必须显式设置为 `> 0` | 常用 `8` ~ `10` |
| `setPersistentMaxFileSize(int)` | `0` | 单个 segment 大小，单位 byte；persistent 开启时必须显式设置为 `> 0` | 常用 `1 MB` ~ `10 MB` |
| `setPersistentMaxLogCount(int)` | `0` | 单个 segment 日志数量上限；persistent 开启时必须显式设置为 `> 0` | 生产场景常用 `65536` |
| `setPersistentMaxBytes(int)` | `0` | persistent 总字节上限；`0` 按 file size × file count 推导 | 需要独立总量上限时设置 |
| `setPersistentMaxRecords(int)` | `0` | persistent 总记录上限；`0` 沿用 `persistentMaxLogCount` | 需要独立总量上限时设置 |
| `setPersistentMaxSegments(int)` | `0` | persistent 总 segment 上限；`0` 沿用 `persistentMaxFileCount` | 需要独立总量上限时设置 |
| `setPersistentHighWatermarkPct(int)` | `85` | bytes、records、segments 任一维度达到该百分比后触发回收 | 一般保持默认 |
| `setPersistentLowWatermarkPct(int)` | `70` | 触发回收后尽量降到该百分比；必须小于 high watermark | 一般保持默认 |
| `setPersistentOverflowPolicy(PersistentOverflowPolicy)` | `REJECT_NEW` | 容量无法回收到安全线时的行为 | 默认拒绝新日志，不静默丢历史数据 |
| `setPersistentSampleEveryN(int)` | `10` | `DROP_NEWEST_SAMPLE` 下每 N 条采样保留策略参数 | 仅采样策略使用 |
| `setPersistentBlockTimeoutMs(int)` | `1000` | `BLOCK` 策略的最长等待时间，单位 ms | 仅阻塞策略使用 |
| `setConnectTimeoutMs(int)` | `0` | 连接超时，单位 ms；`0` 使用 native 默认值 | 弱网场景按业务调整 |
| `setRequestTimeoutMs(int)` | `0` | 请求超时，单位 ms；`0` 使用 native 默认值 | 弱网场景按业务调整 |
| `setDestroyWaitMs(int)` | `0` | destroy 总等待预算，单位 ms | 简单场景使用 |
| `setDestroyFlusherWaitMs(int)` | `0` | flusher 销毁等待预算，单位 ms | 需要拆分等待时设置 |
| `setDestroySenderWaitMs(int)` | `0` | sender 销毁等待预算，单位 ms | 需要拆分等待时设置 |
| `setCallbackFromSenderThread(boolean)` | `false` | 是否直接从 sender 线程回调 | 回调逻辑很轻时才开启 |

### 断点续传配置

高可靠写入场景建议开启断点续传。持久化文件必须放在应用私有目录；多个 producer client 不要复用同一个文件。

```java
config.setPersistent(true);
config.setPersistentFilePath(context.getFilesDir() + "/tls-producer/log.dat");
config.setPersistentMaxFileCount(10);
config.setPersistentMaxFileSize(1024 * 1024);
config.setPersistentMaxLogCount(65536);
config.setPersistentDurability(LogProducerConfig.PersistentDurability.BUFFERED_WAL);
config.setPersistentOverflowPolicy(LogProducerConfig.PersistentOverflowPolicy.REJECT_NEW);
```

注意：

- 开启 persistent 后，发送线程数会被 Android binding 收敛为 `1`，避免本地恢复、发送确认和顺序语义变复杂。
- 多进程场景会在非主进程路径后自动追加清洗后的进程名；同一进程内的多个 client 仍不能复用同一个持久化路径。
- `SYNC_WAL` 每条 append 都执行文件同步，会显著增加 IO 成本；默认使用 `BUFFERED_WAL`。
- 旧 `setPersistentForceFlush(true)` 等价于 `SYNC_WAL`；显式 `BUFFERED_WAL` 与旧开关 `true` 冲突时配置会被拒绝。
- `DROP_OLDEST_UNACKED` 会删除尚未确认的历史日志，`DROP_NEWEST_SAMPLE` 会按采样规则丢弃新日志；两者都会破坏完整的 at-least-once 保证，只有业务明确接受数据损失时才能启用。
- 如果切换 endpoint/region/topicId，建议同步切换 `persistentFilePath`，避免旧目标的 backlog 被恢复后发送到新目标。

## 回调函数配合使用

`LogProducerCallback` 表示后台发送完成后的最终结果；当前 Java API 的 `addLog` 不返回整数码，入参非法、producer 已销毁、native 入队失败等会通过异常暴露。

- `client.addLog(log)` 正常返回：日志已进入 producer，本次调用没有同步失败。
- `client.addLog(log)` 抛异常：日志未成功进入 producer，调用方应按业务策略降级或短暂重试。
- `LogProducerCallback.onCompletion(result)`：后台发送最终结果；如果构造 client 时不传 callback，就不会收到逐条最终状态。

推荐写法：

```java
LogProducerClient client = new LogProducerClient(config, result -> {
    if (!result.isSuccess()) {
        android.util.Log.w("TLSProducer", result.getFailureSummary());
    }
});

try {
    client.addLog(log);
} catch (RuntimeException e) {
    android.util.Log.w("TLSProducer", "enqueue failed", e);
}
```

使用建议：

- callback 中不要执行耗时任务、网络请求或阻塞等待；需要复杂处理时转交给业务自己的线程池。
- 关键日志建议同时处理 `addLog` 异常和 callback 失败；只看 callback 会漏掉入队失败。
- 非关键日志可以不传 callback，以降低对象持有和回调调度成本。
- `LogProducerResult` 同时暴露 `isRetryable()`、`getStartId()` 和 `getEndId()`，用于识别最终一次失败是否仍可重试，并关联本次批量发送覆盖的日志 ID 范围。
- `LogProducerResult.getFailureSummary()` 会汇总失败类型、HTTP 状态码、错误码、错误信息和 requestId，适合直接接入业务日志。
- 当前 C callback 的 `raw_buffer` 没有长度合同且所有生产调用点均传空，checkpoint durable 状态也不属于该 callback；Android API 暂不伪造这两个字段，待 C core 冻结相应 ABI 后再对齐。

## 写入接口说明

常用写入接口：

| 方法 | 说明 |
| --- | --- |
| `new Log().putContent(key, value)` | 添加单个日志字段，`value == null` 会转为空串 |
| `new Log().putContents(map)` | 批量添加 KV 字段 |
| `log.setLogTime(System.currentTimeMillis())` | 显式指定毫秒时间戳 |
| `client.addLog(log)` | 写入一条日志，`flush=0` |
| `client.addLog(log, 1)` | 写入一条日志并提示 producer 尽快 flush |
| `client.updateEndpoint(endpoint, region, topicId)` | 动态更新后续请求的写入目标 |
| `client.resetSecurityToken(ak, sk, token)` | 动态更新 AK/SK/STS token |
| `client.destroyLogProducer()` | 异步销毁 producer |
| `client.awaitDestroy(timeoutMs)` | 等待已触发的 destroy 完成 |

应用退出、账号切换、配置切换前，建议调用 `destroyLogProducer()`，必要时再调用 `awaitDestroy(timeoutMs)` 做有界等待。

## 返回码与失败处理

`addLog` 同步抛异常时，日志没有成功进入 producer 队列，调用方应根据业务策略处理。callback 返回失败时，表示日志进入 producer 后最终发送失败。

常见处理方式：

- 非关键日志：直接丢弃并记录本地计数。
- 关键日志：业务侧短暂重试，但要避免在主线程阻塞。
- 持续失败：降低采样率或关闭非关键日志，避免放大内存与磁盘压力。
- persistent 模式持续失败：优先检查可写目录、剩余磁盘、持久化文件是否被多个 client 复用。

## 与 Java SDK 的分工

| 需求 | 推荐 SDK |
| --- | --- |
| Android 端写日志 | 本仓库 `tls-android-producer` |
| Android 端断点续传写入 | 本仓库 `tls-android-producer` |
| Project/Topic/Index 管理 | Java SDK |
| 查询、消费、分析 | Java SDK |
| 非 Android 服务端接入 | Java SDK |

后续如果没有明确的 Android 特殊适配需求，本仓库只维护 `tls-android-producer` 写入能力；管控面、读侧和其他全量 TLS API 统一由 Java SDK 承接。

## Security and privacy

This project takes security seriously.
For vulnerability reporting and supported versions, see [SECURITY.md](SECURITY.md).
