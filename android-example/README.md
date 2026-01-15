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
- 运行 ProducerDemo（发送日志到指定 Topic）：
  - `endPoint=... region=... ak=... sk=... token=... bash android-example/run-producer-demo.sh`
- 运行 ConsumerDemo（消费指定 Project/Topic 的日志）：
  - `endPoint=... region=... ak=... sk=... projectId=... topicId=... token=... bash android-example/run-consumer-demo.sh`

### 可配置项（通过环境变量）
- ProducerDemo：
  - `TOPIC_TTL`：Topic 生存时间，默认 `7`
  - `INDEX_WAIT_SECONDS`：创建索引后的等待秒数，默认 `60`
  - `PRODUCE_COUNT`：发送日志条数，默认 `20`
  - `DO_SEARCH`：是否在写入后执行检索验证，默认 `true`
  - `SEARCH_WINDOW_SECONDS`：检索时间窗口（秒），默认 `60`
