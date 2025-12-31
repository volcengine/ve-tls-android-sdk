## 发布说明
- 版本策略：SemVer（主.次.修订），当前主版本为 2.0.0（相较 1.1.5 为重大变更）
- 最低支持：Android 4.4（API 19）
- 构建产物：core/full/producer-lite AAR
- 工作流：GitHub Actions 自动构建与测试（.github/workflows/android-ci.yml）
- 发布步骤：
  1. 更新 CHANGELOG 与版本号（如需要）
  2. 推送 Tag
  3. 触发 CI 完成构建与测试
  4. 创建 GitHub Release 并附上说明
