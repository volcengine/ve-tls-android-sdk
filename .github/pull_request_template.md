## 概述

## 变更内容
-

## 测试与验证
- 构建与测试：`bash tools/ci-release.sh`
- 设备回归：`RUN_DEVICE_TESTS=1`、`ANDROID_SERIAL`、`EXPECTED_API` 和 `EXPECTED_PAGE_SIZE`
- 关键用例：

## 风险与兼容性
- minSdk=16；API16-20 依赖系统 JSSE 与服务端兼容的 CBC 套件，不带 Conscrypt
- 依赖更新说明：
- 版本线确认：`2.1.x` PR 默认只发布 producer-native；core/full/老 producer 变更需确认是否属于 `2.0.x` legacy 维护线

## 文档更新
- README / CHANGELOG 是否已更新
