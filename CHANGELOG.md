## 2.0.4
- 普通版面向 API21+，不再传递 Conscrypt 依赖，减小应用包体积
- 新增 `2.0.4-api16` 兼容版本，面向 API16+ 并通过 Conscrypt 修复旧系统 TLS 握手
- 发布脚本支持普通版/API16 版的 AAR、POM 和 Gradle Module Metadata
- API16 版本支持通过 AAB 或 ABI 拆分降低单设备下载体积

## 2.0.0
- 统一日志映射到 core.AdaptorUtil，producer-lite 与 full 保持一致
- 新增 TimeNs 支持与开关；支持用户自填时间与纳秒，归一化至毫秒
- 修复 demo 与脚本，提供 lite/full 运行与集成测试模块
- 新增真实环境用例：资源 CRUD、搜索/直方图/分片、Kafka、下载任务、规则与机器组、分页筛选
- 统一 Android namespace；加入 Lombok 全局配置，收敛构建警告
- 最低版本提升至 Android 4.4（API 19）
- 注入统一 User-Agent：volc-tls-android/{module}/v{version}
- 新增 GitHub Actions 工作流：构建 AAR 与运行集成测试
