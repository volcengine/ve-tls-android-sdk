# Producer接入示例

这是 Android Producer 的最小接入工程，用于验证 AAR 依赖解析、单元测试和 release 构建。样例中的
BuildConfig 占位符仅用于编译验证，不要填入真实 AK/SK 后分发 APK。

## 本地 Maven 前置

构建前请确认对应工件已发布到本机 Maven Local；本样例不会自动发布工件，也不会上传远端：

- 标准 API 19：`io.github.volcengine-tls:tls-android-producer:2.1.3`
- 定制 API 16：`io.github.volcengine-tls:tls-android-producer-legacy16:2.1.3-legacy16`

API 16 构建必须使用 legacy 坐标，不能与标准坐标互换。`SDK_VERSION` 不会自动推断或追加 legacy
版本后缀。

## 标准 API 19 构建

```bash
tls-android-modules/gradlew -p producer-integration-sample \
  :app:testDebugUnitTest :app:assembleRelease --console=plain
```

标准兼容承诺从 API 19 起。

## 定制 API 16 构建

```bash
tls-android-modules/gradlew -p producer-integration-sample \
  -PMIN_SDK_OVERRIDE=16 -PSDK_VERSION=2.1.3-legacy16 \
  :app:testDebugUnitTest :app:assembleRelease --console=plain
```

API 16 仅支持定制 legacy 工件，不保证兼容性稳定；`MIN_SDK_OVERRIDE` 小于 16 或不是整数时构建会拒绝。

## 文档入口

- [开始接入](../docs/getting-started.md)
- [使用方法](../docs/usage.md)
- [API 参考](../docs/reference.md)
- [运维](../docs/operations.md)
