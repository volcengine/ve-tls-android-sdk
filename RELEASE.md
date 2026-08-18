## 发布说明
- 版本策略：SemVer（主.次.修订），当前主版本为 2.0.4（相较 1.1.5 为重大变更）
- `2.0.4`：API21+，不携带 Conscrypt
- `2.0.4-api16`：API16+，携带 Conscrypt 2.5.3，用于兼容 API16-20
- 构建产物：core/full/producer-lite AAR
- Maven 坐标：推荐使用 `io.github.volcengine-tls`（GitHub 命名空间验证更直接）
- 工作流：GitHub Actions 自动构建与测试（.github/workflows/android-ci.yml）
- 发布步骤：
  1. 更新 CHANGELOG 与版本号（如需要）
  2. 推送 Tag
  3. 触发 CI 完成构建与测试
  4. 创建 GitHub Release 并附上说明

## 本地发布（验证）
- 执行本地仓库发布：
  ```bash
  tls-android-modules/scripts/publish-local.sh
  ```
- 校验工件：
  - ~/.m2/repository/io/github/volcengine-tls/tls-android-core/2.0.4/
  - ~/.m2/repository/io/github/volcengine-tls/tls-android-producer/2.0.4/
  - ~/.m2/repository/io/github/volcengine-tls/tls-android-full/2.0.4/
- API16 版本校验对应的 `2.0.4-api16/` 目录
- 在消费工程临时启用 mavenLocal() 验证依赖解析与使用

## Gradle 发布配置模板
- 凭据与签名（任选其一方式）写入本机配置文件：
  - 路径：~/.gradle/gradle.properties

### 模板 A：内存签名（适合 CI 与本机）
```
ossrhUsername=YOUR_OSSRH_USERNAME
ossrhPassword=YOUR_OSSRH_PASSWORD
signingKey=YOUR_ASCII_ARMORED_PRIVATE_KEY
signingPassword=YOUR_PGP_PASSPHRASE
```

### 模板 B：GPG 代理签名（无需导出私钥）
```
ossrhUsername=YOUR_OSSRH_USERNAME
ossrhPassword=YOUR_OSSRH_PASSWORD
signing.gnupg.executable=gpg
signing.gnupg.keyName=YOUR_KEY_ID_OR_FINGERPRINT
signing.gnupg.passphrase=YOUR_PGP_PASSPHRASE
```

### 一键发布到 Sonatype 并自动关闭/发布
```
cd tls-android-modules
./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
```

### 必要前置
- 在 https://central.sonatype.com/ 认领并验证 groupId（例如 com.volcengine）
- 使用 JDK 17；本仓库已固定 org.gradle.java.home 指向 JDK 17

## 使用 Maven CLI 发布（不改代码）
- 生成 AAR：
  ```bash
  tls-android-modules/gradlew -p tls-android-modules :core:assembleRelease :producer:assembleRelease :full:assembleRelease
  ```
- 生成 API16 兼容版本：
  ```bash
  cd tls-android-modules
  ./gradlew -PAPI16_VARIANT=true :core:assembleRelease :producer:assembleRelease :full:assembleRelease
  ```
- 准备 POM 与 sources.jar：已提供模板于 tls-android-modules/maven-publish/
- 一键发布脚本：
  ```bash
  # DRY_RUN=1 仅构建与打 sources.jar，不上传
  cd tls-android-modules
  DRY_RUN=1 bash scripts/publish-mvn.sh
  
  # 正式发布到 Sonatype（需 ~/.m2/settings.xml 配置 serverId/用户名/密码，且本机 GPG 可用）
  bash scripts/publish-mvn.sh
  ```
- 说明：脚本使用 gpg:sign-and-deploy-file 逐个上传 core/producer/full，并调用 nexus-staging:release 自动 Close/Release。

## 使用 Maven Central Publishing 插件发布（推荐新流程）
- 适用场景：参考 Java SDK 的 Central Publishing 插件流程，通过 Central 的 staging API 发布并自动发布到 Maven Central。
- 前置要求：
  - Central Portal 已完成 groupId（例如 com.volcengine）认领与验证
  - ~/.m2/settings.xml 配置 `central` 的 Token 用户名/密码
  - 本机 GPG 可用（建议开启 loopback），且知道私钥口令（passphrase）
- 本地验证（不上传）：
  ```bash
  cd tls-android-modules
  DRY_RUN=1 bash scripts/publish-central-mvn.sh
  ```
- 正式发布（自动 publish）：
  ```bash
  cd tls-android-modules
  PGP_PASSPHRASE='YOUR_PGP_PASSPHRASE' bash scripts/publish-central-mvn.sh
  ```
- 发布工程与产物绑定：
  - 聚合 POM：tls-android-modules/maven-central-publish/pom.xml（不发布到中央仓库）
  - 发布坐标：
    - io.github.volcengine-tls:tls-android-core:2.0.4
    - io.github.volcengine-tls:tls-android-producer:2.0.4
    - io.github.volcengine-tls:tls-android-full:2.0.4

API16 版本发布时执行：

```bash
cd tls-android-modules
API16_VARIANT=1 DRY_RUN=1 bash scripts/publish-central-mvn.sh
```

确认产物和 POM 无误后，再去掉 `DRY_RUN=1` 发布。该命令生成并发布 `core`、`producer`、`full` 的 `2.0.4-api16` 坐标，并只在 API16 版本的 core POM 中声明 Conscrypt。
