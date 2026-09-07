# 参考

本文对应 SDK 版本为 `2.1.4`，覆盖配置、API 与错误、兼容性以及隐私与凭证边界。

## 配置参考

以下默认值按当前 `producer-native` Java `LogProducerConfig` 源码核对；`connectTimeoutMs` 和 `requestTimeoutMs` 的有效 native 默认值来自固定 C core。

### 目标与日志属性

| API | Java 默认值 | 约束或说明 |
| --- | --- | --- |
| `setEndpoint` | `null` | 创建时必填；使用 HTTPS endpoint |
| `setRegion` | `null` | 创建时必填 |
| `setProjectId` | `null` | 可选字段 |
| `setTopicId` | `null` | 创建时必填 |
| `setAccessKeyId` / `setAccessKeySecret` | `null` | 真实发送需要有效运行时凭证 |
| `setSecurityToken` | `null` | STS token，可选 |
| `setHashKey` | `null` | 非空时需为合法 32 位小写十六进制 shard key，且不能全为 `f` |
| `setSource` | `null` | LogGroup 的 `__source__` |
| `addTag(key, value)` | 空 | key/value 不能为 null；重复 key 按添加顺序保留 |
| `setEnableTimeNs` | `false` | 当前 Java `Log` 主要提供毫秒时间 |

### 聚合、内存与重试

| API | Java 默认值 | 约束或说明 |
| --- | ---: | --- |
| `setCompressType` | `LZ4` | 只有 `NONE`、`LZ4` |
| `setPacketLogBytes` | `1048576` | 单个聚合包原始日志字节上限 |
| `setPacketLogCount` | `1024` | 单个聚合包日志条数上限 |
| `setPacketTimeoutMs` | `3000` | 聚合等待时间，单位 ms |
| `setMaxBufferLimit` | `67108864` | 单 client producer 内存预算，不等于应用总内存 |
| `setSendThreadCount` | `1` | persistent 创建时强制归一为 `1` |
| `setRetryMaxAttempts` | `0` | 范围 `[0, 50]`；`0` 表示只受总超时限制 |
| `setRetryTotalTimeoutMs` | `90000` | 必须大于 `0` |
| `setRetryInitialIntervalMs` | `500` | 范围 `[100, 30000]` ms |
| `setRetryMaxIntervalMs` | `10000` | 范围 `[1000, 60000]` ms，且不小于 initial |
| `setConnectTimeoutMs` | `0` | `0` 交给 native，默认 `10000 ms` |
| `setRequestTimeoutMs` | `0` | `0` 交给 native，默认 `50000 ms` |

### Persistent

| API | Java 默认值 | 约束或说明 |
| --- | ---: | --- |
| `setPersistent` | `false` | 开启 WAL 和恢复 |
| `setPersistentFilePath` | `null` | WAL 目录；persistent 时必须非空 |
| `setPersistentDurability` | `BUFFERED_WAL` | 可选 `BUFFERED_WAL`、`SYNC_WAL` |
| `setPersistentForceFlush` | `false` | deprecated；`true` 映射为 `SYNC_WAL` |
| `setPersistentMaxFileCount` | `0` | persistent 时必须大于 `0` |
| `setPersistentMaxFileSize` | `0` | persistent 时必须大于 `0` |
| `setPersistentMaxLogCount` | `0` | persistent 时必须大于 `0` |
| `setPersistentMaxBytes` | `0` | 总字节上限；`0` 使用基础容量推导 |
| `setPersistentMaxRecords` | `0` | 总记录上限；`0` 沿用记录上限 |
| `setPersistentMaxSegments` | `0` | 总 segment 上限；`0` 沿用文件数上限 |
| `setPersistentHighWatermarkPct` | `85` | 范围 `[1, 100]` |
| `setPersistentLowWatermarkPct` | `70` | 范围 `[1, 100]`，persistent 时必须小于 high |
| `setPersistentOverflowPolicy` | `REJECT_NEW` | 另有 `BLOCK`、`DROP_OLDEST_UNACKED`、`DROP_NEWEST_SAMPLE` |
| `setPersistentSampleEveryN` | `10` | sample 策略参数，必须大于 `0` |
| `setPersistentBlockTimeoutMs` | `1000` | block 等待时间，必须大于 `0` |

`persistentFilePath` 应使用应用私有目录。一个活跃 client 独占一个目录；persistent sender 固定为单 sender。完整语义见[持久化与恢复](usage.md#持久化与恢复)。

### 生命周期与回调

| API | Java 默认值 | 约束或说明 |
| --- | ---: | --- |
| `setDestroyWaitMs` | `0` | legacy 总关闭预算 |
| `setDestroyFlusherWaitMs` | `0` | split destroy 的 flusher 预算 |
| `setDestroySenderWaitMs` | `0` | split destroy 的 sender 预算 |
| `setCallbackFromSenderThread` | `false` | 默认通过主线程分发 |

`setDestroyWaitMs` 会清除 split destroy 配置；split 模式下应同时考虑 flusher 和 sender 的预算。配置在 client 构造时冻结。

### Log API

- `new Log()` 的默认时间是创建时的当前毫秒时间。
- `putContent(key, value)` 的 key 不能为 null；null value 会规范化为空串。
- `putContents(map)` 批量调用 `putContent`，null map 不添加内容。
- `setLogTime(milliseconds)` 设置日志毫秒时间。
- `addLog(log, 1)` 请求尽快 flush，但不等待服务端成功。

## API 与错误

### 主要 API

| 类型 | 方法 | 含义 |
| --- | --- | --- |
| `LogProducerClient` | `addLog(log)` | 异步接收一条日志 |
| `LogProducerClient` | `addLog(log, 1)` | 接收并请求尽快 flush，不等待远端 |
| `LogProducerClient` | `resetSecurityToken(ak, sk, token)` | 更新整组凭证并恢复 persistent retain 记录 |
| `LogProducerClient` | `updateEndpoint(endpoint, region, topicId)` | 更新后续发送目标，存在 backlog 改投风险 |
| `LogProducerClient` | `destroyLogProducer()` | 一次性触发异步销毁 |
| `LogProducerClient` | `awaitDestroy(timeoutMs)` | 等待已经启动的销毁工作 |
| `Log` | `putContent` / `putContents` | 添加一个或多个 key/value |
| `Log` | `setLogTime(milliseconds)` | 设置日志时间 |

### `LogProducerResult.Code`

| Code | 归类 | 说明 |
| --- | --- | --- |
| `OK` | success | 对应批次收到服务端成功响应 |
| `INVALID` | validation | 参数或请求无效 |
| `DROP_ERROR` | unknown | 本地处理或丢弃路径错误 |
| `PERSISTENT_ERROR` | persistence | WAL 或持久化操作失败 |
| `CLOSED` | lifecycle | client 已销毁或正在关闭 |
| `TIMEOUT` | timeout | 本地或关闭等待超时 |
| `AUTH_ERROR` | auth | 通常为 HTTP `401/403` |
| `NETWORK_ERROR` | transport | 无可用 HTTP 响应或传输失败 |
| `SERVER_ERROR` | HTTP | 非认证的 HTTP 服务错误 |
| `UNKNOWN_ERROR` | unknown | 未分类错误 |

`getFailureKind()` 会把结果归类为 `NONE`、`VALIDATION`、`PERSISTENCE`、`LIFECYCLE`、`TIMEOUT`、`AUTH`、`HTTP`、`TRANSPORT` 或 `UNKNOWN`。

### 诊断字段

优先记录 `getCode()`、`getFailureKind()`、`isRetryable()`、`getHttpCode()`、`getTransportKind()`、`getTransportCode()`、`getErrorCode()` 和 `getRequestId()`。`getLogBytes()`、`getCompressedBytes()` 以及有效的 `getStartId()`/`getEndId()` 可用于批次核对。

`httpCode` 为 `0` 或非正值时，通常表示没有可用的 HTTP 响应；应结合 transport 字段判断，不能把 HTTP 0 当成服务端状态码。

### 同步异常和异步结果

- `addLog` 正常返回：只完成当前模式的本地接收步骤，不表示远端成功。
- `addLog` 抛出 `RuntimeException`：可能发生在参数、生命周期、native 创建、内存、WAL、`fsync` 或入队阶段；persistent 模式下异常后仍可能已有记录落盘。
- callback 成功：对应批次收到服务端成功响应，但 persistent checkpoint 边界仍可能造成重复。
- callback 失败：查看错误和 request ID；persistent 认证 retain 不发终态失败 callback，不能依赖它触发凭证刷新。

不要在 callback 中做耗时或阻塞工作，也不要输出凭证、Authorization 或完整业务 payload。

## 兼容性

### 平台

- 标准 Producer SDK 兼容范围从 Android API 19 开始。
- producer-native 使用 Java 8 source/target compatibility；宿主仍需按自己的 Android Gradle Plugin 和构建链选择 compile/target SDK。
- API 19 的连接使用 Android 系统 JSSE，并通过 SDK 的 TLS 1.2 兼容路径建立连接；API 20 及以上使用平台 TLS 能力。
- 宿主应用需要网络访问权限，连接应使用 HTTPS。SDK 不提供关闭证书或主机名校验的公共配置。

API 19 设备的最终可用 TLS 套件取决于系统和服务端协商结果。应在实际支持的 API 19 设备或等价真实环境上验证 endpoint，不要用开发机或模拟器结果替代兼容性结论。

### API 16 定向包

标准 `tls-android-producer` 仍以 API 19 为最低支持版本。API 16 只能通过维护者定向构建特殊包，不作为标准 SDK 的稳定兼容承诺；构建方式见[发布指南的 API 16 定向包](../RELEASE.md#api-16-定向包)。

### 页大小与 ELF 对齐

64 位原生库采用 16 KB ELF 对齐，同时兼容 4 KB 页设备；最低 API 19 和原有 32 位 ABI 保持不变。宿主应用仍需正确打包 native 库，并在目标设备上验证，不能仅凭 Android API 级别判断页大小。
推荐宿主使用 AGP 8.5.1 或更新版本获得正确的未压缩 native 库 ZIP 对齐。
如暂时保留较旧 AGP，可在宿主应用启用压缩 native 库：

```groovy
android {
    packagingOptions {
        jniLibs {
            useLegacyPackaging true
        }
    }
}
```

ELF 对齐与 APK/AAB 打包对齐是不同门禁；重新压缩 APK 不能修复第三方 SDK 本身的错误 ELF 对齐。
背景和验收要求见 [Android 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes)。

### 公共模块边界

当前推荐接入面只有 `tls-android-producer`。它负责异步日志写入，不包含 Project/Topic/Index 管理、查询、消费或分析接口。仓库中的历史 `core`、`full` 和其他模块不属于本文的推荐 API。

### 压缩和依赖

`LogProducerConfig.CompressType` 只公开 `NONE` 与 `LZ4`，默认 `LZ4`。不要把旧模块或其他平台的压缩、生命周期、认证配置名直接复制到 Android Producer。

## 隐私与凭证

Producer 会在 Java/native 内存中使用凭证完成请求签名。文档只承诺 SDK 的调用边界，不承诺能够覆盖宿主日志、崩溃采集、代理、调试工具或设备内存的全部泄漏面。

### 凭证处理

- 通过业务运行时凭据提供器传入短期、最小权限 STS。
- 不把 AK/SK/token 写入源码、BuildConfig、资源文件、外部存储、URL、日志或异常文本。
- 不记录 Authorization、完整请求体或包含凭证的调试输出。
- 过期前刷新整组凭证，并调用 `resetSecurityToken`；不要等待 persistent retain 的失败 callback。
- 仅使用 HTTPS，并保持证书和主机名校验。

### WAL 与业务数据

Persistent WAL 用于恢复业务日志，可能包含原始业务字段。WAL 目录必须位于应用私有存储，并按应用备份策略避免复制到其他设备。不要手工读取、复制或上传 WAL 文件。

WAL 的 at-least-once 恢复语义意味着业务日志可能重复；需要去重时写入稳定 `event_id`。不要把日志 payload 本身当作凭证存储，也不要把凭证放进日志字段。

### 调试和排查

排查只保留时间、目标标识、错误分类、HTTP/transport 信息和 request ID。调用方自定义网络拦截器、日志实现或崩溃收集器时，也必须过滤凭证、Authorization、STS token 和完整 payload。
