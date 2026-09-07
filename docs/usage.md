# 使用指南

本页覆盖持久化恢复、身份认证、生命周期以及网络、超时与重试。

## 持久化与恢复

Persistent 模式用于在进程异常退出、设备重启或暂时离线后恢复未确认记录。它提供 at-least-once 基础，不提供 exactly-once。

### 模式选择

| 模式 | 本地接收边界 | 进程重启恢复 | 主要取舍 |
| --- | --- | --- | --- |
| 内存 | 进入受限内存路径 | 不保证 | 开销较低，但退出后可能丢失 |
| `BUFFERED_WAL` | WAL record 写入成功，仍可能停留在 OS page cache | 支持 | 适合常规恢复，突然掉电有未同步窗口 |
| `SYNC_WAL` | WAL record `write` 和 `fsync` 成功 | 支持 | 本地持久性更强，但每条写入承担同步 IO |

### 推荐配置

`persistentFilePath` 是目录，不是单个文件。目录必须位于应用私有存储，不要放到共享或外部存储。下面的 `context` 表示宿主已有的 `Context`；API 21 及以上优先使用不参与自动备份的 `getNoBackupFilesDir()`，API 19 使用 `getFilesDir()`：

```java
import android.os.Build;

import java.io.File;

File appStorage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
        ? context.getNoBackupFilesDir()
        : context.getFilesDir();
File walDirectory = new File(appStorage, "tls-producer/main");

LogProducerConfig config = new LogProducerConfig()
        .setEndpoint("https://your-tls-endpoint")
        .setRegion("your-region")
        .setTopicId("your-topic-id")
        .setAccessKeyId(runtimeCredentials.accessKeyId)
        .setAccessKeySecret(runtimeCredentials.accessKeySecret)
        .setSecurityToken(runtimeCredentials.securityToken)
        .setPersistent(true)
        .setPersistentFilePath(walDirectory.getAbsolutePath())
        .setPersistentDurability(LogProducerConfig.PersistentDurability.BUFFERED_WAL)
        .setPersistentMaxFileCount(4)
        .setPersistentMaxFileSize(1024 * 1024)
        .setPersistentMaxLogCount(4096)
        .setPersistentOverflowPolicy(
                LogProducerConfig.PersistentOverflowPolicy.REJECT_NEW);
```

开启 persistent 时，`persistentFilePath` 必须非空，`persistentMaxFileCount`、`persistentMaxFileSize` 和 `persistentMaxLogCount` 必须显式设置为正数；构造器不会从 `Context` 推导 WAL 目录。API 19 使用 `getFilesDir()` 时，应在宿主应用的备份规则中排除 WAL 目录。

### 目录所有权

- 同一逻辑数据流在重启后复用同一目录，才能找到 backlog。
- 一个活跃 client 独占一个 WAL 目录；同一进程的多个 client 必须使用不同目录。
- persistent 快照会把 sender 数归一为 `1`，即使原 config 设置了更多 sender。
- 非主进程可能在基础目录下追加进程名，以避免不同进程直接共用同一路径；这不能替代同一进程内的目录隔离。
- 不要手工删除、移动或编辑 `manifest`、`checkpoint`、`lease` 和 segment 文件。应用备份策略如会复制该私有目录，应将 WAL 排除在备份范围外。

### 恢复时机

构造 client 不会立即创建 native producer、打开 WAL 或执行 recover。首次 `addLog`、`updateEndpoint` 或 `resetSecurityToken` 触发 native 创建时，persistent binding 才会执行恢复。当前没有独立公开的 `start()` 或 `recover()` 方法。

恢复使用当前 config、当前凭证和当前目标。WAL 不绑定写入时的 endpoint、region 或 topic；如果目标身份改变，见[网络、超时与重试](#网络超时与重试)中的切换风险。

### 接收边界与重复

- `addLog` 正常返回只表示当前模式完成了本地接收步骤，不代表服务端成功。
- 服务端已接收但响应丢失、请求超时、进程在远端成功与 checkpoint 之间退出，或恢复未确认记录时，都可能产生重复。
- `SYNC_WAL` 的 `write` 成功但 `fsync` 失败时，`addLog` 可能抛异常；磁盘上仍可能留下可恢复记录，立即重试可能造成重复。
- 成功 callback 表示对应请求进入服务端成功路径，不表示本地 persistent checkpoint 已经 durable；checkpoint 保存或进程退出边界仍可能导致重发。
- 使用稳定的 `event_id` 或业务主键，在消费侧去重。

#### append 后内存入队失败

persistent 路径可能先完成 WAL append，再执行内存发送队列入队。如果后续内存入队失败，`addLog` 可以抛异常，但记录仍可能已经落盘。仅存在 WAL 的记录不会在同一 client 中自动重新入队；应停止并重建 client，使用相同私有目录执行 recover。调用方重试同一事件时必须沿用稳定业务主键。

### 水位与回收

bytes、records、segments 三个已配置维度中任一维度达到 high watermark，就会触发压力回收；SDK 尝试回收到 low watermark，或直到没有可回收的已确认 closed segment。active segment、未确认 segment 和恢复游标所在 segment 受保护，不会被正常水位回收。

### 选择 Sync WAL

必须在构造 `LogProducerClient` 前设置 durability：

```java
// 必须在构造 LogProducerClient 前设置；构造后 config 已冻结。
config.setPersistentDurability(LogProducerConfig.PersistentDurability.SYNC_WAL);
LogProducerClient client = new LogProducerClient(config);
```

`setPersistentForceFlush(true)` 是兼容 API，会映射为 `SYNC_WAL`。显式设置 `BUFFERED_WAL` 后再设置 `persistentForceFlush=true` 会因语义冲突而失败；durability 和 force-flush 都是 create-time 配置，构造后不能修改。

### 容量与溢出

默认高水位为 `85`、低水位为 `70`，默认溢出策略为 `REJECT_NEW`。`BLOCK` 会阻塞调用线程，不要在 Android 主线程使用；`DROP_OLDEST_UNACKED` 和 `DROP_NEWEST_SAMPLE` 会破坏完整的 at-least-once 语义，只有业务明确接受丢失时才使用。

## 身份认证

Producer 使用 config 中的 AK/SK 和可选 STS token 为请求签名。SDK 不替业务获取或刷新凭证；应用应从自己的安全服务在运行时取得短期凭证。

### 运行时凭证

示例只展示 API 形状，不提供硬编码凭证：

```java
LogProducerConfig config = new LogProducerConfig()
        .setEndpoint("https://your-tls-endpoint")
        .setRegion("your-region")
        .setTopicId("your-topic-id")
        .setAccessKeyId(runtimeCredentials.accessKeyId)
        .setAccessKeySecret(runtimeCredentials.accessKeySecret)
        .setSecurityToken(runtimeCredentials.securityToken);
```

不要把长期 AK/SK 放入源码、BuildConfig、资源文件、外部存储、URL、日志或崩溃信息。优先使用短期、最小权限的 STS，并使用 HTTPS。

### 提前刷新 STS

业务层应根据凭证过期时间提前获取下一组凭证，再一次性更新：

```java
client.resetSecurityToken(
        runtimeCredentials.accessKeyId,
        runtimeCredentials.accessKeySecret,
        runtimeCredentials.securityToken);
```

`resetSecurityToken` 更新 AK、SK 和 token 的整组值。不要等待认证失败 callback 触发刷新：persistent 默认按 retain 处理认证失败，待发送记录会被保留，且不会发终态失败 callback。刷新成功后，同一 client 会恢复这些记录，成功路径只发对应的成功结果。

Android public API 不暴露认证失败后的 drop 策略；persistent 认证失败按 retain 语义处理。不要自行假设有一个可切换的认证丢弃配置。

### 失败判断

认证失败通常表现为 HTTP `401/403` 或 `AUTH_ERROR`。`httpCode`、`errorCode` 和 `requestId` 可用于诊断，但诊断记录中不得包含凭证或 Authorization。网络失败、TLS 握手失败和超时不能直接证明凭证错误。

## 生命周期

Producer 是长生命周期对象，建议由 `Application` 级组件或业务服务持有，不要随着 Activity 重建反复创建和销毁。

### 创建与配置冻结

`new LogProducerClient(config, callback)` 会复制 config 并立即冻结原对象。构造后继续调用原 config 的 setter 会抛出 `IllegalStateException`。压缩、批量、内存、sender、重试、persistent、WAL 目录、容量、callback 线程和 destroy 等参数都是 create-time 配置；修改它们需要创建新的 client。

native producer 采用惰性创建。只构造 client 不会打开 WAL 或开始恢复；首次 `addLog`、`updateEndpoint` 或 `resetSecurityToken` 才会创建 native 实例。并发首次访问由 client 串行化，不应自行创建多个实例来“预热”同一目录。

### 正常关闭

停止业务写入后调用：

```java
client.destroyLogProducer();
boolean closed = client.awaitDestroy(6000);
```

`destroyLogProducer` 是一次性操作；之后不能再次调用 `addLog`、`updateEndpoint` 或 `resetSecurityToken`。`awaitDestroy` 的等待时间只影响 Java 侧等待，不会把 native 的 flusher/sender 预算无限延长。需要拆分预算时，在创建前设置 `setDestroyFlusherWaitMs` 和 `setDestroySenderWaitMs`，不要同时混用总预算。

正常关闭不等于所有日志已经被服务端确认。persistent 模式下未确认 WAL 可由下一次使用同一目录的 client 恢复；崩溃、强制终止或系统直接结束进程时不能依赖关闭回调。

### callback 线程

`setCallbackFromSenderThread(false)` 是默认值，callback 通过主线程分发；分发条件不可用时会显式报告失败，不会静默退回 sender 线程。开启 `true` 后 callback 直接运行在 sender 线程，必须保持短小、无阻塞、无重入销毁。callback 中只做轻量状态记录，凭证和完整请求信息不要输出。

## 网络、超时与重试

Producer 负责签名、聚合、压缩、发送和批次级重试。应用不要在 Producer 下方再叠加无法观测的透明 HTTP 重试，否则同一批次可能被重复发送更多次。

### Endpoint 与 TLS

文档支持的 endpoint 是 HTTPS origin，例如：

```text
https://tls-cn-beijing.volces.com
```

不要在生产环境使用 `http://`，也不要关闭证书或主机名校验。API 19 的连接使用系统 JSSE 和 SDK 的 TLS 1.2 兼容路径；服务端应保留该系统能够协商的 TLS 套件。SDK 不修改应用全局 TLS Provider。

### 默认超时

| API | Java 字段默认值 | 有效行为 |
| --- | ---: | --- |
| `setConnectTimeoutMs` | `0` | 交给 native，默认建连超时 `10000 ms` |
| `setRequestTimeoutMs` | `0` | 交给 native，默认单次请求超时 `50000 ms` |
| `setRetryTotalTimeoutMs` | `90000` | 单轮请求和重试总预算 |

网络请求超时、单轮重试预算和 `awaitDestroy` 等待时间不是同一个概念。过短的 request timeout 会增加响应不确定和重复发送的概率。

### 重试语义

默认 `retryMaxAttempts=0` 表示不按次数截断，但仍受 `retryTotalTimeoutMs` 限制；默认退避间隔为 `500 ms`，上限为 `10000 ms`。Java setter 的合法范围见[配置参考](reference.md#配置参考)。

固定 C core `v0.3.2` 将 HTTP `429/500/502/503/504` 标为可重试。persistent 模式的可重试错误耗尽当前轮预算后进入跨轮指数退避，跨轮单次延迟上限为 `5 分钟`；`close` 不等待该跨轮计时器，未确认 WAL 会保留到恢复。永久 TLS、主机名或非法 URL 错误不会自动重试；修正 endpoint 或配置后应重建 client，并使用相同 WAL 目录 recover。memory 模式耗尽单轮预算后会报告终态结果。

所有超时和重试都属于 at-least-once 边界。为业务事件设置稳定 `event_id`，不要用发送调用次数推断服务端唯一日志数。

### 更新发送目标

`updateEndpoint(newEndpoint, newRegion, newTopicId)` 会让后续请求收敛到新目标，但存在以下风险：

- 已进入 native 发送路径的请求可能仍使用旧目标。
- 尚未发送的 persistent backlog 以及后续 recover 记录可能被发送到新目标。
- WAL 不会按历史 endpoint、region 或 topic 自动隔离。

如果不能接受旧 backlog 改投新目标，应停止旧 client，为新目标创建新的 client 和新的私有 WAL 目录。
