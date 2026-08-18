## 概述

## 变更内容
-

## 测试与验证
- 构建与测试：
  - `./gradlew :core:assembleRelease :full:assembleRelease :producer:assembleRelease`
  - `./gradlew :integration-tests:test`
- 关键用例：

## 风险与兼容性
- 普通版 minSdk=21；API16 兼容版通过 `API16_VARIANT=true` 构建，minSdk=16
- 依赖更新说明：

## 文档更新
- README / CHANGELOG 是否已更新
