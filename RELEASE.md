## GitHub Release

- 发布入口：GitHub Actions 的 `Maven Central Publish` workflow
- 发布源：版本 tag 所指向的 Git commit
- 普通版本：`2.0.4`，面向 API21+，不携带 Conscrypt
- API16 兼容版本：`2.0.4-api16`，面向 API16+，携带 Conscrypt 2.5.3
- 构建与测试：`.github/workflows/android-ci.yml`
- 发布 workflow：`.github/workflows/maven-publish.yml`

## GitHub Actions Secrets

在 GitHub 仓库的 **Settings -> Secrets and variables -> Actions** 中配置：

- `CENTRAL_USERNAME`：Maven Central Portal token 用户名
- `CENTRAL_TOKEN`：Maven Central Portal token 密码
- `GPG_PRIVATE_KEY`：ASCII armored 格式的签名私钥
- `GPG_PASSPHRASE`：签名私钥口令

Secret 只由 GitHub Actions 使用，不写入仓库文件、workflow 日志或发布产物。

## 发布 2.0.4

1. 确认发布源的 GitHub Actions CI 已通过。
2. 在 GitHub 上对已通过 CI 的发布 commit 创建并推送 tag `v2.0.4`。
3. `Maven Central Publish` 会由该 tag 自动触发，重新构建、运行 core 测试、签名并发布 `core`、`producer`、`full` 三个 Maven 坐标。
4. 在 Maven Central Portal 确认 deployment 完成后，再创建对应的 GitHub Release。

## 发布 API16 版本

使用同一个已验证的发布 commit 创建 tag `v2.0.4-api16`。workflow 会自动发布 `2.0.4-api16` 坐标，并只在 API16 版本的 core POM 中声明 Conscrypt。普通版和 API16 版必须使用不同 tag 分别发布。

## 发布前检查

- 普通版 tag 格式为 `v<major>.<minor>.<patch>`，API16 版 tag 格式为 `v<major>.<minor>.<patch>-api16`。
- workflow 从 tag 自动解析基础版本和 `API16_VARIANT`，不接受外部构建参数。
- 同一个 Maven 版本只发布一次；如果 deployment 已提交，不要重复运行相同版本。
- GitHub Actions 失败时，以 workflow 日志为准，不依据未上传到 GitHub 的外部构建结果判断发布成功。
