# 运维

本页覆盖故障排查和真实设备上的性能评估方法。

## 故障排查

先区分本地接收、native 创建、网络发送和服务端查询四个阶段。不要只看 `addLog` 是否抛异常。

### 无法创建或首次写入失败

- 检查 endpoint、region、topicId 是否非空且 endpoint 使用 HTTPS。
- 检查运行时凭据是否在创建前可用，不要从旧的外部凭证文件或硬编码 BuildConfig 读取。
- persistent 模式检查 `persistentFilePath` 是应用私有目录，且 file count、file size、log count 都大于 `0`。
- 构造 client 后才修改原 config 会抛 `IllegalStateException`；重新构造 client 才能使用 create-time 配置。
- native 创建失败后，client 会记住首次失败；修正配置后应创建新的 client。

### `addLog` 返回但服务端没有日志

`addLog` 正常返回只代表本地接收，不代表远端成功。检查 callback 的 `Code`、`FailureKind`、HTTP/transport 字段和 request ID，再在覆盖发送时间的范围内用唯一 `event_id` 查询 Topic。构造 client 不会立即 recover；没有新的 `addLog` 或更新操作时，不保证启动后马上 drain backlog。

### `401/403` 或认证错误

使用短期 STS，并在过期前通过 `resetSecurityToken` 更新整组凭证。persistent 默认 retain 认证失败记录，不发终态失败 callback；没有 callback 不代表没有请求错误，也不能把 callback 当成刷新触发器。

### 超时、网络或 TLS 错误

确认设备能访问 HTTPS endpoint，API 19 设备和服务端有共同的 TLS 1.2 套件。检查 `getHttpCode()`、`getTransportKind()`、`getTransportCode()` 和 `isRetryable()`。HTTP `429/500/502/503/504` 可进入重试；不要在应用层无界重试同一事件。

### 出现重复日志

这是 at-least-once 语义的可能结果，常见窗口包括请求已到达但响应丢失、超时重试、进程在成功与 checkpoint 之间退出、恢复未确认 WAL，以及 `SYNC_WAL` 的 `fsync` 失败后重新提交。使用稳定事件 ID，在消费侧去重。

### WAL 或磁盘问题

不要删除或编辑 WAL 文件。确认目录是应用私有目录、空间足够且没有被同一进程的另一个活跃 client 复用。persistent 固定单 sender；容量不足时默认 `REJECT_NEW`，`BLOCK` 不应放在主线程。

### 更新目标后数据去向异常

`updateEndpoint` 不会隔离旧 backlog。已进入发送路径的请求可能仍使用旧目标，尚未发送或 recover 的记录可能改投新目标；不能接受该风险时，为新目标创建新的 client 和新的 WAL 目录。

### 关闭后无法继续写入

`destroyLogProducer` 是一次性操作。停止后的 client 不可复用；需要继续写入时创建新的 client，并按业务决定是否复用同一 persistent 目录恢复未确认记录。

## 性能评估方法

本页只定义真实设备上的评估口径，不提供吞吐承诺、当前结果或固定的 CPU、内存、包体积数字。模拟器结果不能替代发布验收。

### 固定测试条件

每次对比都记录并固定：

- 真实 Android 设备型号、API 级别和电量/温控状态；
- debug 或 release 构建、SDK 版本和 ABI；
- 网络类型、endpoint、Topic 和服务端响应情况；
- 日志字段、key/value 字节数、事件 ID 生成方式；
- 压缩类型、批量参数、重试参数和持久化 durability；
- 预热时长、测量窗口、重复次数和数据清理方式。

内存、`BUFFERED_WAL` 和 `SYNC_WAL` 应分别测量。persistent 模式固定单 sender，不要通过增加 sender 推导吞吐结论。

### 观测指标

至少同时记录：

- `addLog` 本地调用延迟的 P50/P95/P99、接收成功数和异常数；
- callback 成功/失败批次数、HTTP/transport 错误、重试和 request ID；
- 设备进程 CPU、PSS/内存、线程数、WAL 目录大小和磁盘剩余空间；
- 业务原始字节数、成功批次原始/压缩字节数；
- 服务端唯一事件数、总命中数、重复数和缺失数。

`addLog` 的本地接收速率、callback 批次速率和服务端查询结果不是同一个指标，不要互相替代。

### 评估步骤

1. 先用带唯一 `event_id` 的小批量验证功能和服务端查询。
2. 在真实设备上预热，再分别运行内存、Buffered WAL 和 Sync WAL 场景。
3. 每个场景重复运行，报告中位数和波动范围，不只保留最好一次。
4. 单独执行离线、网络恢复、进程异常退出和磁盘压力场景，统计重复、缺失和恢复时间。
5. 只有在设备、系统、网络和日志模型都明确时，才把结果用于业务容量规划；不要把一次测量写成 SDK SLA。

配置默认值和边界见[配置参考](reference.md#配置参考)，可靠性语义见[持久化与恢复](usage.md#持久化与恢复)。
