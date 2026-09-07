# 历史模块与迁移

当前 Android SDK 面向异步日志写入，推荐使用 `tls-android-producer`。
接入步骤见[开始接入](docs/getting-started.md)，配置见[参考](docs/reference.md)。

仓库中的 `core`、`full`、`logger-spi` 和 JVM 示例是历史实现，不属于当前 Producer 的推荐接入面。
查询、消费、Project/Topic/Index 管理请使用服务端 Java SDK；不要把管理凭证嵌入移动应用。

迁移时请核对包名、日志模型、异步回调和关闭方法，不要直接替换依赖版本后假设行为完全一致。
旧 Java Producer 的缓存文件不属于 native WAL 格式，不承诺自动迁移；切换前应完成旧队列发送，
为新 Producer 配置独立目录。使用稳定事件 ID 处理切换期间可能发生的重复。

历史版本变化见 [CHANGELOG](CHANGELOG.md)。
