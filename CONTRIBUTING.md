## 贡献指南
- 提交前请运行：
  - `./gradlew :core:assembleRelease :full:assembleRelease :producer:assembleRelease`
  - `./gradlew :integration-tests:test`
- 代码风格：保持现有模块风格与命名；避免在代码中添加多余注释；日志使用 TlsLogger
- 变更说明：请在 PR 中简述动机、方案与测试结果，并更新 CHANGELOG
- 兼容性：遵循 minSdk=19；避免引入不兼容依赖；必要时在 README 说明
