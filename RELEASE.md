# 发布指南

当前准备发布版本为 **2.1.5（待发布）**，主发布坐标为 `io.github.volcengine-tls:tls-android-producer`。
只发布 Producer AAR，不将历史 `core`、`full` 或测试 App 加入本次发布。
最低 Android 4.1（API 16），C Core 继续使用[固定版本清单](tls-android-modules/producer-native/ve-tls-c-sdk.version)中的 v0.3.2。
API 16-20 使用系统 TLS 1.2 兼容路径，服务端需支持可协商的 `ECDHE_RSA` CBC 套件；主线发布物不引入 Conscrypt。证书和主机名校验不能关闭，且不承诺任意 HTTPS endpoint 都兼容。

本文用于发布前准备。发布动作完成后，发布负责人必须按上传与确认流程校验 Central Portal 中的坐标、版本和依赖，再创建 tag 与 Release 记录；发布前不要将 2.1.5 视为已发布或可下载。

## 发布门禁

1. 从准备发布的干净 commit 构建；确认版本号、CHANGELOG、Java User-Agent 和 C Core SHA 一致。
2. 完成[贡献指南](CONTRIBUTING.md)的单元测试、lint、文档检查和 release 构建。
3. 在 API 16、API 19 和当前 Android 版本验证 JNI、HTTPS、凭证轮转、关闭与 WAL 恢复；API 16-20 的目标 endpoint 需单独验证 TLS 兼容性。
4. 检查 AAR：四个 ABI、正确 minSdk、仅发布库内容，没有测试 Manifest、调试资源、配置文件或凭证。
5. 检查 POM、Gradle module metadata、sources JAR 与 AAR 的版本和依赖一致；在独立接入工程验证解析和运行。
6. 完成安全与第三方许可证审查。没有完整的当前版本测量证据时，不发布性能或包体积数字。

CI 成功不能替代设备和真实发送验收。将详细运行日志保存在 CI artifact 或外部发布记录中，
不要把个人机器路径、内部地址、凭证和逐次操作记录提交到对外文档。

16 KB ELF 对齐、APK 打包对齐和真实页大小环境运行均需通过，宿主要求见[兼容性](docs/reference.md#兼容性)。
统一入口为 `bash tools/ci-release.sh`；设置 `RUN_DEVICE_TESTS=1`、`ANDROID_SERIAL`、
`EXPECTED_API`（16、19 或 35）和 `EXPECTED_PAGE_SIZE`（4096 或 16384）可增加指定设备的 release 回归。CI 不执行真实发送或远端发布。

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
