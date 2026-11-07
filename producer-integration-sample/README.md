# Producer接入示例

这是 Android Producer 的最小接入工程，用于验证 AAR 依赖解析、单元测试和 release 构建。样例中的
BuildConfig 占位符仅用于编译验证，不要填入真实 AK/SK 后分发 APK。

## 本地 Maven 前置

构建前请确认主线工件已发布到本机 Maven Local；本样例不会自动发布工件，也不会上传远端：

- 主线标准坐标：`io.github.volcengine-tls:tls-android-producer:2.1.5`

## 标准 API 16+ 构建

```bash
tls-android-modules/gradlew -p producer-integration-sample \
  :app:testDebugUnitTest :app:assembleRelease --console=plain
```

标准兼容承诺从 Android 4.1（API 16）起；API 16 使用与其他受支持版本相同的主线坐标，不需要额外版本后缀或 manifest 覆盖。构建参数 `MIN_SDK_OVERRIDE` 小于 16 时会明确拒绝，但不会切换依赖坐标。API 16-20 的 TLS 前提、证书和主机名校验边界见[兼容性](../docs/reference.md#兼容性)。

## 文档入口

- [开始接入](../docs/getting-started.md)
- [使用方法](../docs/usage.md)
- [API 参考](../docs/reference.md)
- [运维](../docs/operations.md)
