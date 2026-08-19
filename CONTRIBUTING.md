## 贡献指南
- 提交前请运行：
  - `cd tls-android-modules && ./gradlew :producer-native:assembleRelease :producer-native:lintRelease :producer-native:testDebugUnitTest`
  - `cd consumer-sample && ../tls-android-modules/gradlew :app:assembleRelease`
- 代码风格：保持现有模块风格与命名；避免在代码中添加多余注释；日志使用 TlsLogger
- 变更说明：请在 PR 中简述动机、方案与测试结果，并更新 CHANGELOG
- 兼容性：遵循 minSdk=16；避免引入不兼容依赖；API16-20 的 TLS 兼容性必须通过系统 JSSE 和服务端 CBC 配置验证
