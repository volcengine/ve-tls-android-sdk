## 2.1.3 (Unreleased)
- 固定 C Core v0.3.2（1d41ec4edb850ee7dd0b7f63c49738d6a9669c21），保持最低 API 19。
- 持久化可重试失败耗尽单轮预算后自动继续发送，关闭保留 WAL；修复 ACK、队列和内存失败路径，优化 WAL/builder 开销。
- 显式单发送线程配置准确生效，保持 1 MiB/1024 条/3000 ms/64 MiB/LZ4 的默认资源配置。
- Java/JNI 区分暂时网络故障与证书、TLS 协议、URL 等永久错误，并提前校验 hashKey。
- 修复 API 19 畸形 URL 被误判为可重试 IO 的问题；低版本 Android 显式启用 TLS 1.2，保留系统证书和主机名校验。
- 补充 API 19 运行与三档可靠性真实发送验证，benchmark 按成功日志范围核对全量发送完成。
- Persistent 认证 retain 不回调终态失败，凭证更新后只回调成功；STS 需要调用方提前刷新。
- 新增本地 HTTP 服务驱动的 JNI 恢复、认证更新、关闭重放和失败分类测试。

## 2.1.2
- Producer-native 主发布物最低支持 Android 4.4（API 19）。
- API 19-20 使用系统 `HttpsURLConnection`/JSSE；服务端需开放低版本系统可协商的 CBC TLS 套件，发布物不引入 Conscrypt。
- 保留 API 16-18 的指定客户 best-effort 定制构建入口；该产物不使用主发布坐标，也不纳入兼容性和稳定性承诺。
- Android 请求 User-Agent 更新为 `volc-tls-android/producer/v2.1.2`。

## 2.1.1
- 发布坐标继续使用 `io.github.volcengine-tls:tls-android-producer`，不新增独立 native 坐标。
- Producer 写入路径基于 native producer，新增断点续传、退避重试、批量聚合、压缩与 Android 桥接优化。
- Android 请求 User-Agent 统一为 `volc-tls-android/producer/v2.1.1`，便于服务端识别 Android SDK 流量。
- 文档口径转向 Android Producer 写入能力；管控面、读侧和其他全量 TLS API 由 Java SDK 承接。

## 2.0.0
- 统一日志映射到 core.AdaptorUtil，producer-native 与 full 保持一致
- Producer 迁移到内部 `producer-native` 模块，公开发送 API 改为 `LogProducerClient.addLog(Log)` / `destroyLogProducer()`
- `CompressType` 公共枚举仅保留 `NONE/LZ4`，默认 `LZ4`
- 新增 TimeNs 支持与开关；支持用户自填时间与纳秒，归一化至毫秒
- 修复 demo 与脚本，提供 native/full 运行与集成测试模块
- 新增真实环境用例：资源 CRUD、搜索/直方图/分片、Kafka、下载任务、规则与机器组、分页筛选
- 统一 Android namespace；加入 Lombok 全局配置，收敛构建警告
- 最低版本提升至 Android 4.4（API 19）
- 注入统一 User-Agent：volc-tls-android/{module}/v{version}
- 新增 GitHub Actions 工作流：构建 AAR 与运行集成测试
