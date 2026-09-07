# TLS Android Producer SDK

面向 Android 异步日志写入的 Producer SDK。公共模块是 `tls-android-producer`，提供批量聚合、压缩、有限重试以及可选的本地 WAL 恢复；不提供 Project/Topic 管理、查询或消费 API。

当前文档对应 SDK 版本为 `2.1.4`。

## 快速开始

安装和快速开始见[开始接入](docs/getting-started.md)。接入代码中的 `runtimeCredentials` 代表业务自己的运行时凭据提供器，不要把长期 AK/SK 写入源码、BuildConfig、资源文件或外部存储。

```java
import com.volcengine.tls.android.producer.Log;
import com.volcengine.tls.android.producer.LogProducerClient;
import com.volcengine.tls.android.producer.LogProducerConfig;

LogProducerConfig config = new LogProducerConfig()
        .setEndpoint("https://your-tls-endpoint")
        .setRegion("your-region")
        .setTopicId("your-topic-id")
        .setAccessKeyId(runtimeCredentials.accessKeyId)
        .setAccessKeySecret(runtimeCredentials.accessKeySecret)
        .setSecurityToken(runtimeCredentials.securityToken)
        .setDestroyFlusherWaitMs(1000)
        .setDestroySenderWaitMs(4000);

LogProducerClient client = new LogProducerClient(config, result -> {
    if (!result.isSuccess()) {
        android.util.Log.w("TLSProducer", result.getFailureSummary());
    }
});

client.addLog(new Log()
        .putContent("event_id", "your-stable-event-id")
        .putContent("message", "hello tls")
        .setLogTime(System.currentTimeMillis()));

client.destroyLogProducer();
client.awaitDestroy(6000);
```

`addLog` 正常返回只表示当前模式完成了本地接收步骤，不表示服务端已成功写入。服务端结果通过 callback 返回；persistent 模式仍可能因超时、恢复和 checkpoint 边界产生重复。

示例显式给 native flusher/sender 配置了 `1000 ms`/`4000 ms` 的关闭预算；这两个配置默认都是 `0`。`awaitDestroy` 只等待已经启动的销毁工作，不会增加 native 预算，也不等于日志已经送达服务端。内存模式耗尽单轮重试预算后报告终态失败；persistent 模式的可重试失败会保留记录并进入后续跨轮处理，见[网络、超时与重试](docs/usage.md#网络超时与重试)。

构造 `LogProducerClient` 时，SDK 会复制并冻结 config，但 native producer 采用惰性创建。首次 `addLog`、`updateEndpoint` 或 `resetSecurityToken` 才会触发 native 创建；构造完成不等于已经打开 WAL 或开始恢复。

## 文档导航

- [文档首页](docs/README.md)
- [开始接入](docs/getting-started.md)
- [使用指南](docs/usage.md)
- [参考](docs/reference.md)
- [运维](docs/operations.md)

旧接入资料的收敛入口见 [SDK_USAGE_GUIDE.md](SDK_USAGE_GUIDE.md)。

## License

[Apache License 2.0](LICENSE)

安全漏洞报告和支持版本见 [SECURITY.md](SECURITY.md)。
