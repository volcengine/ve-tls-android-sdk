# 贡献指南

本指南面向 SDK 维护者。应用接入见[文档首页](docs/README.md)。当前发布与兼容性检查以 `producer-native` 为范围。

## 构建环境

- JDK 17、Python 3.9+、Android SDK Platform 34、NDK `21.4.7075529`；使用仓库 Gradle Wrapper。
- 通过 `JAVA_HOME`、`ANDROID_HOME` 和 `VE_TLS_C_SDK_DIR` 提供环境，不提交个人路径。
- C Core checkout 必须匹配 [版本清单](tls-android-modules/producer-native/ve-tls-c-sdk.version) 的完整 SHA，且没有 tracked 修改。
- 保持最低 Android 4.1（API 16）；模拟器结果不能替代真实设备、网络和 TLS 兼容性验证。

在仓库根目录运行：

```bash
bash tools/ci-release.sh
```

此入口统一执行脚本测试、文档检查、Java 单测、lint、AAR 16 KB 对齐检查及 `producer-integration-sample`（显示名称：Producer 接入示例）的本地构建。
仅写本机 Maven 缓存，不上传；本机配置了签名凭据时 Gradle 可能执行本地签名。设备回归可加 `RUN_DEVICE_TESTS=1`、`ANDROID_SERIAL`
和 `EXPECTED_API=16`、`19` 或 `35`，以及 `EXPECTED_PAGE_SIZE=16384`（16 KB）或 `4096`（4 KB）；不会运行真实发送测试。

## 设备与真实发送

选择专用测试设备，将 `ANDROID_SERIAL` 设为其 adb serial。安装 instrumentation APK 并仅运行不需要真实凭证的测试：

```bash
TEST_PACKAGE=com.volcengine.tls.android.producer
TEST_CLASSES="$TEST_PACKAGE.ProducerNativeLoadInstrumentedTest,$TEST_PACKAGE.ProducerRecoveryInstrumentedTest,$TEST_PACKAGE.ProducerLifecycleInstrumentedTest,$TEST_PACKAGE.ProducerRealBenchmarkConfigInstrumentedTest,$TEST_PACKAGE.ProducerRealBenchmarkMathInstrumentedTest,$TEST_PACKAGE.ProducerRealBenchmarkDeliveryInstrumentedTest"
tls-android-modules/gradlew -p tls-android-modules \
  :producer-native:connectedReleaseAndroidTest -PTEST_BUILD_TYPE=release \
  -Pandroid.testInstrumentationRunnerArguments.class="$TEST_CLASSES"
```

真实发送必须使用授权的独立测试主题和短期凭证。准备仓库外的私有环境文件，
字段包括 `VE_TLS_ENDPOINT`、`VE_TLS_REGION`、`VE_TLS_TOPIC_ID`、`VE_TLS_ACCESS_KEY_ID`、
`VE_TLS_ACCESS_KEY_SECRET` 和可选 `VE_TLS_SECURITY_TOKEN`，然后运行：

```bash
python3 android-example/run-producer-native-real-validation.py \
  --config-env "$TLS_TEST_ENV" --serial "$ANDROID_SERIAL" --output "$TLS_TEST_OUTPUT"
```

先确认 APK 已安装、输出目录在仓库外、变量指向正确设备和测试数据。脚本通过 `run-as` 临时写入应用私有目录并清理，
不应使用外部存储传递凭证。可加 `--benchmark` 进行资源评估，口径见[性能评估](docs/operations.md#性能评估方法)。
不要提交凭证、原始业务日志、测试报告、生成二进制或个人机器操作记录。

持续发送兼容性回归可在同一个 release instrumentation APK 上单独执行：

```bash
adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
  -e class com.volcengine.tls.android.producer.ProducerRealSustainedSendInstrumentedTest \
  -e sustainedDurationS 30 -e sustainedRateLps 1 \
  com.volcengine.tls.android.producer.test/androidx.test.runner.AndroidJUnitRunner
```

先按真实发送测试要求将 `real_tls.properties` 注入测试应用私有目录。默认每种模式只按 1 条/秒发送 30 秒，memory、Buffered WAL、Sync WAL 三种模式合计 90 条；通过参数可以进一步缩短。日志带唯一 `validation_run` 和序号，并校验成功回调覆盖所有已接收日志。报告位于测试应用的外部 files 目录 `sustained/`，没有外部存储时回退到私有 files 目录。测试后清理凭证，并用服务端日志查询核对 `validation_run` 和序号。此测试不进入无凭证 CI，也不是公开性能基准。

## 提交与验收

保持变更最小；行为修复应覆盖失败路径和回归测试。修改公共 API 或默认值时同步更新配置参考和 CHANGELOG。
PR 说明分别列出静态检查、单元测试、JNI/设备测试和真实发送结果，未执行的检查不能标成通过。
不要将临时设备数据当成公开性能承诺。发布步骤见 [RELEASE](RELEASE.md)。
