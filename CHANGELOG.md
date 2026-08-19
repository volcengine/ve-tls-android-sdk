## 2.1.2
- Producer-native 默认最低支持调整为 Android 4.1（API 16）。
- API16 兼容路径继续使用系统 `HttpsURLConnection`/JSSE；服务端需开放 API16 可协商的 CBC TLS 套件，发布物不引入 Conscrypt。
- 移除 producer-native Java 公开路径中的 API19-only `java.util.Objects` 调用，并固定 API16 构建使用 NDK 21.4。
- Android 请求 User-Agent 更新为 `volc-tls-android/producer/v2.1.2`。

## 2.1.1
- 发布坐标继续使用 `io.github.volcengine-tls:tls-android-producer`，不新增独立 native 坐标。
- `2.1.x` 起作为 producer-native 主线；core/full/老 producer 后续仅沿 `2.0.x` legacy 维护线修复必要问题。
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
