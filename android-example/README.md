# Android Example for TLS

本目录只保留 producer-native 真实环境 benchmark 入口，用于验证 `tls-android-producer` 写入链路和资源消耗。

本仓库 `2.1.x` 起不再作为 Android 全量接口 SDK 维护。Project/Topic 管理、查询、索引、消费等全量 TLS API 请使用 Java SDK；历史 full/core 示例脚本不再作为新接入指导。

## 环境变量
- `tls_config.properties` 或 `CONFIG_PROPS` 指向的配置文件需包含 endpoint、region、topicId、ak、sk。
- 也可以通过 `CONFIG_ENV` 指向环境变量文件，提供 `VE_TLS_ENDPOINT`、`VE_TLS_REGION`、`VE_TLS_TOPIC_ID`、`VE_TLS_ACCESS_KEY_ID`、`VE_TLS_ACCESS_KEY_SECRET`。
- `token` / `VE_TLS_SECURITY_TOKEN`：STS 临时 Token，可选。

## 运行方式
- 确认模拟器或真机已通过 adb 连接。
- 运行 producer-native 真实环境 benchmark：
  - `CONFIG_PROPS=./tls_config.properties bash android-example/run-producer-native-real-benchmark.sh`
- 常用参数可通过环境变量覆盖，例如 `PROFILES="tls200 tls700"`、`MODE_LIST="memory persistent"`、`RATE_LIST="200 500"`、`DURATION_S=120`。
- 脚本会安装测试 APK、执行 instrumented benchmark，并把结果保存到 `android-example/target/producer-native-benchmark/`。
