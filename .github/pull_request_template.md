## 概述

## 变更内容
-

## 测试与验证
- 构建与测试：
  - `./gradlew :logger-spi:assemble :producer-native-stub:assembleRelease :producer-native:assembleRelease -PVE_TLS_C_SDK_DIR=/path/to/ve-tls-c-sdk`
  - `./gradlew :integration-tests:test`
- 关键用例：

## 风险与兼容性
- minSdk=19 保持不变
- 依赖更新说明：

## 文档更新
- README / CHANGELOG 是否已更新
