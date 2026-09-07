# Android Producer 使用入口

本页只保留迁移入口，不再重复旧版 `core`/`full`、外部凭证文件或 BuildConfig 密钥教程。新接入统一使用 `tls-android-producer` 的 producer-native API。

## 推荐阅读顺序

1. [开始接入](docs/getting-started.md)：安装、快速开始和写入验证。
2. [使用指南](docs/usage.md)：WAL 目录、恢复、认证、生命周期和重试语义。
3. [参考](docs/reference.md)：当前 Java public API 默认值、错误和兼容性边界。
4. [运维](docs/operations.md)：故障排查和性能评估方法。

## 从旧文档迁移

- 发送日志只使用 `LogProducerConfig`、`LogProducerClient`、`Log` 和 `LogProducerResult`。
- 凭证由业务在运行时提供给 `runtimeCredentials` 占位符，再传入 `setAccessKeyId`、`setAccessKeySecret` 和 `setSecurityToken`；不要把凭证写入源码、BuildConfig、资源文件、外部存储或日志。
- `LogProducerClient` 构造时冻结 config；压缩、批量、重试、persistent 和 WAL 目录等参数不能在原 client 上修改。
- native producer 惰性创建；`new LogProducerClient(...)` 不会单独启动恢复。
- Project/Topic/Index 管理、查询和消费不属于本 Producer 的公共 API，应使用对应 Java SDK 或服务端能力。

旧页面中的版本、参数名和凭证存储建议可能已过期；以本目录的主题指南和当前源码为准。
