# 发布指南

当前标准发布版本为 **2.1.3**，主发布坐标为 `io.github.volcengine-tls:tls-android-producer`。
只发布 Producer AAR，不将历史 `core`、`full`、测试 App 或客户定制包加入本次发布。
最低 Android API 19，C Core 使用[固定版本清单](tls-android-modules/producer-native/ve-tls-c-sdk.version)。

## 发布门禁

1. 从准备发布的干净 commit 构建；确认版本号、CHANGELOG、Java User-Agent 和 C Core SHA 一致。
2. 完成[贡献指南](CONTRIBUTING.md)的单元测试、lint、文档检查和 release 构建。
3. 在 API 19 和当前 Android 版本验证 JNI、HTTPS、凭证轮转、关闭与 WAL 恢复；实际目标的 TLS 兼容性单独验证。
4. 检查 AAR：四个 ABI、正确 minSdk、仅发布库内容，没有测试 Manifest、调试资源、配置文件或凭证。
5. 检查 POM、Gradle module metadata、sources JAR 与 AAR 的版本和依赖一致；在独立接入工程验证解析和运行。
6. 完成安全与第三方许可证审查。没有完整的当前版本测量证据时，不发布性能或包体积数字。

CI 成功不能替代设备和真实发送验收。将详细运行日志保存在 CI artifact 或外部发布记录中，
不要把个人机器路径、内部地址、凭证和逐次操作记录提交到对外文档。

16 KB ELF 对齐、APK 打包对齐和真实页大小环境运行均需通过，宿主要求见[兼容性](docs/reference.md#兼容性)。
统一入口为 `bash tools/ci-release.sh`；设置 `RUN_DEVICE_TESTS=1`、`ANDROID_SERIAL` 和
`EXPECTED_PAGE_SIZE` 可增加指定设备的 release 回归。CI 不执行真实发送或远端发布。

## 打包预检

准备 JDK 17、Android 工具链、匹配 SHA 的 C Core checkout，以及 Maven CLI。
环境变量配置见[贡献指南](CONTRIBUTING.md)。在仓库根目录运行：

```bash
DRY_RUN=1 bash tls-android-modules/scripts/publish-central-mvn.sh
```

该入口只打包并检查 Maven packaging，不上传、不签名。不要把生成目录或过程日志提交到仓库。
预检通过不表示 Central 认证、GPG 签名或上传已经通过。

## 上传与确认

正式上传前，由发布负责人确认上述门禁，并准备 Central Portal 已验证的 namespace、
`~/.m2/settings.xml` 中的 `central` token 和可用的 GPG 签名配置。
不得把 token、签名私钥或口令写入仓库、文档示例或命令行历史。

签名口令使用受保护的 `MAVEN_GPG_PASSPHRASE` 环境变量或预先解锁的 gpg-agent。
旧 `PGP_PASSPHRASE` 仍可作为兼容输入，但不能同时设置两个变量；不要使用 `-Dgpg.passphrase`。
环境变量仍可能被有权限检查进程的工具读取，因此签名应运行在隔离的可信 runner，禁止输出环境变量或开启 Maven 调试日志。

以下命令会执行签名和远端部署，只能在发布审批完成后运行：

```bash
bash tls-android-modules/scripts/publish-central-mvn.sh
```

发布负责人按组织流程完成 Portal 审批、签名和上传后，再创建对应 tag 和 Release 记录。
不要复用已经发布的版本号覆盖产物。

历史 OSSRH/多模块发布入口不作为本次推荐发布流程。不要运行无模块限定的 Gradle 全仓发布任务。

## API 16 定向包

标准 `tls-android-producer` 以 API 19 为最低支持版本。API 16 特殊包只用于指定维护交付，不属于标准发布，也不构成公开的 API 16 稳定兼容承诺。标准发布和特殊包必须分开处理。

从 `tls-android-modules` 目录执行以下命令，定向构建 AAR、POM 和 Gradle module metadata：

```bash
./gradlew :producer-native:assembleRelease \
  :producer-native:generatePomFileForReleasePublication \
  :producer-native:generateMetadataFileForReleasePublication \
  -PMIN_SDK_OVERRIDE=16 \
  -PVERSION_SUFFIX=-legacy16
```

需要验证本地 Maven 产物时，在同一目录执行：

```bash
./gradlew :producer-native:publishReleasePublicationToMavenLocal \
  -PMIN_SDK_OVERRIDE=16 \
  -PVERSION_SUFFIX=-legacy16
```

定制产物使用独立的 `artifactId`/`version` 交付，接入方式见 [Producer 接入示例](producer-integration-sample/README.md)。以上命令不执行远端发布。
