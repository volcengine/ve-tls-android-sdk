# Android Example for TLS

该示例演示如何在Android/Java环境下使用TLS Android SDK的主要功能，包括：
- 客户端（创建/修改/删除 Project/Topic/Index，PutLogs，SearchLogs）
- Producer 异步发送日志
- Consumer 消费日志与检查点管理

## 环境变量
- `endPoint`：TLS服务的Endpoint，例如 `https://tls-cn-beijing.volces.com`
- `region`：地域标识，例如 `cn-beijing`
- `ak`：访问密钥AK
- `sk`：访问密钥SK
- `token`：STS临时Token（可选）

## 运行方式
- 将示例中的环境变量替换为真实值或通过系统环境变量注入。
- 可直接拷贝到你的应用工程中运行，或在本仓库中作为参考示例阅读。

### 运行脚本
- 运行 QuickStart（创建资源、写入、检索、清理）：
  - `endPoint=... region=... ak=... sk=... token=... bash android-example/run-quickstart.sh`
- 运行 ConsumerDemo（消费指定 Project/Topic 的日志）：
  - `endPoint=... region=... ak=... sk=... projectId=... topicId=... token=... bash android-example/run-consumer-demo.sh`

producer-native 当前不提供独立 JVM 控制台脚本；如需验证发送链路，请使用 Android 演示 App，或仓库内的 producer-native Gradle / 单元测试流程。
