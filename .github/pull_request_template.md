## 概述

## 变更内容
-

## 测试与验证
- 构建与测试：
  - `./gradlew :logger-spi:assemble :producer-native-stub:assembleRelease :producer-native:assembleRelease -PVE_TLS_C_SDK_DIR=/path/to/ve-tls-c-sdk`
  - `./gradlew :integration-tests:test -PVE_TLS_C_SDK_DIR=/path/to/ve-tls-c-sdk`
- 关键用例：

## 风险与兼容性
- minSdk=19 保持不变
- 依赖更新说明：
- 版本线确认：`2.1.x` PR 默认只发布 producer-native；core/full/老 producer 变更需确认是否属于 `2.0.x` legacy 维护线

## 文档更新
- README / CHANGELOG 是否已更新
