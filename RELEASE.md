## GitHub Release

- 发布入口：GitHub Actions 的 `Maven Central Publish` workflow
- 发布源：workflow 的 `ref` 输入指定的 Git 分支、tag 或 commit
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
2. 打开 GitHub 仓库的 **Actions -> Maven Central Publish -> Run workflow**。
3. 填写以下输入：
   - `version`：`2.0.4`
   - `ref`：已通过 CI 的发布源，例如 `fix/2.0.3-version-resource-conflict`
   - `api16_variant`：`false`
4. workflow 会重新构建、运行 core 测试、签名并发布 `core`、`producer`、`full` 三个 Maven 坐标。
5. 在 Maven Central Portal 确认 deployment 完成后，再创建对应的 GitHub Release 和 tag `v2.0.4`。

## 发布 API16 版本

使用同一个已验证的 `ref`，重新运行 workflow：

- `version`：`2.0.4`
- `api16_variant`：`true`

workflow 发布的坐标会自动使用 `2.0.4-api16` 后缀，并只在 API16 版本的 core POM 中声明 Conscrypt。普通版和 API16 版必须分别运行，不能在同一次 workflow 中混合发布。

## 发布前检查

- `ref` 中的 `tls-android-modules/gradle.properties` 基础版本应与 workflow 的 `version` 一致。
- 普通版构建参数为 `API16_VARIANT=false`，API16 版构建参数为 `API16_VARIANT=true`。
- 同一个 Maven 版本只发布一次；如果 deployment 已提交，不要重复运行相同版本。
- GitHub Actions 失败时，以 workflow 日志为准，不依据未上传到 GitHub 的外部构建结果判断发布成功。
