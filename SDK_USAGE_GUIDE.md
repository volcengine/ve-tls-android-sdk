# TLS Android SDK 使用指南（面向客户）

本指南面向第一次接入 TLS 日志服务的开发者，目标是让你从 0 到 1 跑通：创建资源 → 集成 SDK → 发送日志 → 控制台验证。

## 你需要先知道的两件事

- SDK 依赖坐标（Maven Central）
  - 只需要发送日志（推荐）：`io.github.volcengine-tls:tls-android-producer:2.1.2`
  - 需要完整管理能力（创建 Project/Topic/Index、检索等）：请使用 Java SDK，不再使用本仓库旧 Android full 模块。
  - 当前主发布物支持 `minSdk >= 16`；API 16-20 使用系统 JSSE，服务端需要开放 `TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA` 等兼容套件。
- 必要参数（后面会用到）
  - `endpoint`：TLS 接入域名，形如 `https://tls-cn-xxx.volces.com`
  - `region`：地域标识，例如 `cn-xxx`
  - `topicId`：日志主题 ID（Producer 发日志必填）
  - `accessKeyId/accessKeySecret`：AK/SK
  - `securityToken`：STS 临时凭证可选（没有就传空/不设置）

## 第 0 步：在控制台准备资源与凭证

### 0.1 开通并进入 TLS 控制台

在火山引擎控制台开通日志服务并进入 TLS 控制台。

### 0.2 创建 Project / Topic（至少要拿到 topicId）

在 TLS 控制台创建：
- Project（项目）
- Topic（主题）

记录 Topic 的 `topicId`，后面发送日志会用到。

### 0.3 获取 AK/SK（或 STS）

在访问控制（IAM）里创建并获取：
- AccessKeyId（AK）
- SecretAccessKey（SK）

如果你使用 STS 临时鉴权，还需要拿到 `securityToken`（可选）。

## 运行演示 App（tls-android-modules/app）

如果你想先验证“能跑起来 + 能发日志”，推荐直接运行仓库自带的演示 App（`tls-android-modules/app`）。

### 1. 下载源码并导入 Android Studio

1. 在 GitHub 下载源码：https://github.com/volcengine/ve-tls-android-sdk/tree/master-2.0
2. 用 Android Studio 打开项目（推荐直接打开仓库根目录）
3. 等待 Gradle Sync 完成

### 2. 配置 TLS 参数（推荐用 adb 写入配置文件）

1. 从模板复制一份配置文件并填入你的真实参数：
   - 模板：https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls_config.properties.example
   - 重命名为：`tls_config.properties`
2. 关键字段说明：
   - `endPoint`：你的 TLS Endpoint（建议 https）
   - `region`：地域（如 cn-xxx）
   - `ak/sk`：访问密钥
   - `topicId`：日志主题 ID
   - `token`：STS token（可空）
   - `compress`：`NONE` 或 `LZ4`，默认 `LZ4`

### 3A. 在真机上安装并运行

1. 连接 Android 设备并开启 USB 调试
2. 执行 `adb devices`，确认设备已连接
3. 写入配置文件（包名固定为 `com.volcengine.tls.android.demo`）：

```bash
adb shell mkdir -p /sdcard/Android/data/com.volcengine.tls.android.demo/files
adb push tls_config.properties /sdcard/Android/data/com.volcengine.tls.android.demo/files/tls_config.properties
```

4. 在 Android Studio 顶部设备选择框里选中你的手机
5. 点击 Run（Run 'app'），等待安装完成并自动启动

### 3B. 在 Android Studio 模拟器（虚拟机）上安装并运行

1. Android Studio → Device Manager → Create device，创建并启动一个 Emulator（建议选择带 Google APIs 的镜像）
2. 执行 `adb devices`，确认出现类似 `emulator-5554	device`
3. 写入配置文件（对模拟器同样生效）：

```bash
adb shell mkdir -p /sdcard/Android/data/com.volcengine.tls.android.demo/files
adb push tls_config.properties /sdcard/Android/data/com.volcengine.tls.android.demo/files/tls_config.properties
```

4. 回到 Android Studio 顶部设备选择框，选择该 Emulator
5. 点击 Run（Run 'app'），等待安装完成并自动启动

说明：
- 演示 App 已配置 `network_security_config` 允许明文流量，但仍建议使用 https endpoint。
- 演示 App 有两个入口页面（Launcher）：`MainActivity`（发送日志）与 `BenchmarkActivity`（压测），你可以在桌面看到两个入口图标。

### 4. 启动 App 并发送日志

1. 打开 `TLS Producer Demo`（发送日志页面）
2. 点击 “Start Sending Logs”
3. 界面会持续打印发送结果（成功/失败与重试信息）

### 5. 控制台验证

在 TLS 控制台进入对应 `topicId` 的 Topic，打开检索页：
- 时间范围选择最近 15 分钟
- 查询语句先用 `*`（全量），或按字段过滤（如 `key:value`）
- 看到你刚发送的日志后，即验证成功

## Android 应用接入（推荐：Producer 异步发送）

### 1. 创建 Android 工程

使用 Android Studio 新建应用工程即可，建议：
- `minSdk >= 16`
- `compileSdk/targetSdk` 使用你当前项目的版本（示例工程使用 34）

API 16-20 使用 Android 系统提供的 `HttpsURLConnection`/JSSE，不额外引入 Conscrypt。服务端需同时保留现代套件和至少一个 API16 可协商的 CBC 套件；推荐优先开放 `TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA`，并通过 API16 模拟器或真机验证实际 endpoint。

### 2. 配置 Maven Central

在 `settings.gradle`（或 `settings.gradle.kts`）确保仓库包含 `mavenCentral()`：

```groovy
dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
  }
}
```

### 3. 添加依赖与权限

在 App 模块的 `build.gradle` 中添加依赖：

```groovy
dependencies {
  // Producer 写入模块
  implementation 'io.github.volcengine-tls:tls-android-producer:2.1.2'
}
```

在 `AndroidManifest.xml` 添加网络权限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### 4. 配置管理（避免把 AK/SK 写死在代码里）

不建议把 AK/SK 明文写在代码仓库中。常用做法：

#### 方式 A：BuildConfig 注入（最简单）

在 `app/build.gradle` 的 `defaultConfig` 里注入：

```groovy
android {
  defaultConfig {
    buildConfigField 'String', 'TLS_ENDPOINT', '"https://tls-cn-example.volces.com"'
    buildConfigField 'String', 'TLS_REGION', '"cn-example"'
    buildConfigField 'String', 'TLS_AK', '"AK_EXAMPLE"'
    buildConfigField 'String', 'TLS_SK', '"SK_EXAMPLE"'
    buildConfigField 'String', 'TLS_TOKEN', '""'
    buildConfigField 'String', 'TLS_TOPIC_ID', '"topic-example"'
  }
}
```

#### 方式 B：本地配置文件（只放本机，不提交仓库）

可以放到 `filesDir`/`externalFilesDir` 下（示例 App 会从 `tls_config.properties` 读取，参考 [ConfigLoader.java](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/app/src/main/java/com/volcengine/tls/android/demo/ConfigLoader.java)）。

仓库提供了一个不含真实密钥的模板文件，可直接拷贝后改值：
- `tls_config.properties.example`：https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls_config.properties.example

把文件重命名为 `tls_config.properties` 后放到以下任一位置（示例 App 会按顺序读取）：
- 外部私有目录（推荐，方便 adb 写入）：`/sdcard/Android/data/<你的包名>/files/tls_config.properties`
- 内部私有目录：`/data/data/<你的包名>/files/tls_config.properties`

使用 adb 写入示例（以演示 App 包名 `com.volcengine.tls.android.demo` 为例）：

```bash
adb shell mkdir -p /sdcard/Android/data/com.volcengine.tls.android.demo/files
adb push tls_config.properties /sdcard/Android/data/com.volcengine.tls.android.demo/files/tls_config.properties
```

连接 Android Studio 模拟器（虚拟机）：

```bash
adb devices
```

如果你在 Android Studio 的 Device Manager 里启动了模拟器，这里通常会看到类似 `emulator-5554 device` 的条目；后续 `adb push/adb shell` 会默认对它生效。

### 5. 初始化并创建 Producer

在 `Application` 或首个页面创建（建议全局单例）：

```java
import com.volcengine.tls.android.producer.Log;
import com.volcengine.tls.android.producer.LogProducerClient;
import com.volcengine.tls.android.producer.LogProducerConfig;

LogProducerConfig cfg = new LogProducerConfig()
    .setEndpoint(BuildConfig.TLS_ENDPOINT)
    .setRegion(BuildConfig.TLS_REGION)
    .setAccessKeyId(BuildConfig.TLS_AK)
    .setAccessKeySecret(BuildConfig.TLS_SK)
    .setSecurityToken(BuildConfig.TLS_TOKEN) // 可为空
    .setTopicId(BuildConfig.TLS_TOPIC_ID)
    .setCompressType(LogProducerConfig.CompressType.LZ4) // 或 NONE
    .setSendThreadCount(2)
    .setRetryMaxAttempts(3)
    .setRetryTotalTimeoutMs(90_000)
    .setRetryInitialIntervalMs(500)
    .setRetryMaxIntervalMs(10_000)
    .setPacketLogBytes(256 * 1024)
    .setPacketLogCount(512)
    .setPacketTimeoutMs(1000);

LogProducerClient client = new LogProducerClient(cfg);
```

### 5.1 Producer 配置参数说明（LogProducerConfig）

下表按当前 `tls-android-producer` public API 整理，口径以 `LogProducerConfig` 实现为准，不复用旧 SDK 的参数名、单位或默认值语义。

| 参数设置 | 说明 | 取值 | 默认值与约束 |
| --- | --- | --- | --- |
| `setTopicId` | 发送目标 Topic ID | 字符串 | 必填；对应 `topicId` |
| `setHashKey` | Producer/request 级别 shard key | 字符串 | 默认空；当前不提供 per-log `hashKey` |
| `addTag` | create-time tag，随 Producer 创建时写入 | 两个字符串 `key/value` | 默认无；重复 key 按追加顺序保留；`key/value` 都不能为 `null` |
| `setSource` | `__source__` 字段值 | 字符串 | 默认空；未设置时不主动补 `Android` 常量 |
| `setCompressType` | 上传压缩类型 | `CompressType.NONE` / `CompressType.LZ4` | 默认 `LZ4` |
| `setPacketLogBytes` | 每个缓存日志包的大小上限 | 整数，单位字节 | 默认 `1024 * 1024` |
| `setPacketLogCount` | 每个缓存日志包包含日志条数上限 | 整数 | 默认 `1024` |
| `setPacketTimeoutMs` | 缓存日志的发送超时时间 | 整数，单位毫秒 | 默认 `3000` |
| `setMaxBufferLimit` | 单个 Producer Client 可使用的内存上限 | 整数，单位字节 | 默认 `64 * 1024 * 1024` |
| `setSendThreadCount` | sender 线程数 | 整数 | 默认 `1`；若开启 `persistent`，SDK 会在创建前归一到 `1` |
| `setRetryMaxAttempts` | 最大尝试次数上限 | 整数 | 默认 `0`；范围 `[0, 50]`；`0` 表示不按次数截断，只受 `retryTotalTimeoutMs` 限制 |
| `setRetryTotalTimeoutMs` | 单次请求整体重试预算 | 整数，单位毫秒 | 默认 `90000`；必须 `> 0` |
| `setRetryInitialIntervalMs` | 首次重试前的基础等待间隔 | 整数，单位毫秒 | 默认 `500`；范围 `[100, 30000]` |
| `setRetryMaxIntervalMs` | 单次等待间隔上限 | 整数，单位毫秒 | 默认 `10000`；范围 `[1000, 60000]`，且必须 `>= retryInitialIntervalMs` |
| `setPersistent` | 是否开启 persistent/recover | 布尔值 | 默认 `false`；开启后为 **at-least-once** 语义，不承诺 exactly-once |
| `setPersistentFilePath` | persistent 文件目录 | 字符串 | 默认空；开启 `persistent` 时必填；不同 target 建议使用不同目录 |
| `setPersistentForceFlush` | 是否每次 `addLog` 都强制刷盘 | 布尔值 | 默认 `false` |
| `setPersistentMaxFileCount` | persistent 文件滚动个数上限 | 整数 | 默认 `0`；开启 `persistent` 时建议显式配置，不要依赖 `0` |
| `setPersistentMaxFileSize` | 单个 persistent 文件大小上限 | 整数，单位字节 | 默认 `0`；开启 `persistent` 时建议显式配置，不要依赖 `0` |
| `setPersistentMaxLogCount` | 本地最多缓存日志条数 | 整数 | 默认 `0`；开启 `persistent` 时建议显式配置，不要依赖 `0` |
| `setConnectTimeoutMs` | 网络连接超时时间 | 整数，单位毫秒 | Java 字段默认 `0`；运行时按 native 默认 `10000ms` 处理 |
| `setRequestTimeoutMs` | 单次请求读超时 | 整数，单位毫秒 | Java 字段默认 `0`；运行时按 native 默认 `10000ms` 处理 |
| `setDestroyWaitMs` | legacy destroy 等待预算 | 整数，单位毫秒 | 默认 `0`；仅在未配置 split destroy 时生效 |
| `setDestroyFlusherWaitMs` | flusher 线程销毁等待预算 | 整数，单位毫秒 | 默认 `0`；与 `setDestroySenderWaitMs` 组成 split destroy |
| `setDestroySenderWaitMs` | sender 线程池销毁等待预算 | 整数，单位毫秒 | 默认 `0`；与 `setDestroyFlusherWaitMs` 组成 split destroy |
| `setEnableTimeNs` | 是否启用纳秒级时间戳字段 | 布尔值 | 默认 `false` |
| `setCallbackFromSenderThread` | callback 线程模式 | 布尔值 | 默认 `false`；`false` 表示主线程回调契约，条件不满足时会显式失败，不会静默退回 sender 线程 |

补充说明：
- 本 SDK 当前没有旧 Android SDK 里的这些参数：`setNtpTimeOffset`、`setMaxLogDelayTime`、`setDropDelayLog`、`setDropUnauthorizedLog`。
- 超时、destroy、retry 这组 public API 统一使用毫秒，不使用 `Sec` 后缀。
- `null key` 不允许；日志内容里的 `null value` 会按空串 `""` 发送。如果要表达“没有这个字段”，不要放这个 key。
- `setPersistentFilePath(...)` 重用同一路径时，应保持 `endpoint/region/topicId` 稳定；如果发送目标变了，请切换到新的 persistent 目录。

### 5.2 关键参数推荐值（起步建议）

如果只是先用一组稳妥配置跑通并上线观察，建议从下面这组开始：

- 通用发送场景：`LZ4`、`packetLogBytes=1024*1024`、`packetLogCount=1024`、`packetTimeoutMs=3000`、`maxBufferLimit=64*1024*1024`、`sendThreadCount=1`。
- 重试策略：`retryMaxAttempts=0`、`retryTotalTimeoutMs=90_000`、`retryInitialIntervalMs=500`、`retryMaxIntervalMs=10_000`。`0` 次数上限表示只按总时间预算截断。
- 网络超时：通常不需要显式配置；如需声明，可用 `connectTimeoutMs=10_000`、`requestTimeoutMs=10_000`。
- callback 线程：默认 `setCallbackFromSenderThread(false)`，即主线程回调契约。
- persistent/recover：先明确这是 **at-least-once** 能力，不是 exactly-once；必须显式设置 `setPersistentFilePath(...)`；`setSendThreadCount` 仍建议按 `1` 使用。
- persistent 容量参数不要留默认 `0`。仅做功能验证或保守起步时，可先用 `persistentMaxFileCount=4`、`persistentMaxFileSize=1024*1024`、`persistentMaxLogCount=1024`；生产场景应按峰值写入速率和离线容忍窗口重新估算。

### 6. 发送一条日志（验证链路）

```java
import java.util.HashMap;
import java.util.Map;

Map<String, String> kv = new HashMap<>();
kv.put("key", "value");
kv.put("app", "demo");
kv.put("ts", String.valueOf(System.currentTimeMillis()));

Log log = new Log().putContents(kv).setLogTime(System.currentTimeMillis());
client.addLog(log);
```

如果你希望自定义日志时间：

```java
Log log = new Log().putContents(kv).setLogTime(System.currentTimeMillis());
client.addLog(log, 1);
```

### 7. 关闭与资源释放

在 `onDestroy` 或应用退出时关闭：

```java
client.destroyLogProducer();
```

### 8. 控制台验证

在 TLS 控制台按 `topicId` 查询最新日志，检查：
- 是否能看到你写入的 key/value
- 时间字段是否正确
- 压缩类型是否与配置一致（NONE/LZ4）

建议你按下面“截图式步骤”逐步核对（不同控制台 UI 可能略有差异，但路径一致）：

1. 打开火山引擎控制台并进入日志服务（TLS）
   - 截图点：顶部服务入口与左侧导航栏
2. 进入你创建的 Project
   - 截图点：Project 列表中目标项目的名称/ID
3. 进入 Topic 列表，找到 `topicId` 对应的 Topic
   - 截图点：Topic 列表中的 TopicName 与 TopicId
4. 打开“日志查询/检索”（Search/Query）
   - 截图点：检索页面的 Topic 选择框与时间范围选择器
5. 输入查询条件并执行
   - 建议先用 `*`（全量）或按你写入的字段过滤（例如 `key:value`）
   - 截图点：查询语句与返回的第一条日志详情
6. 在结果里确认关键字段
   - `key/value` 是否一致
   - `ts/time` 是否在你刚发送的时间附近
   - 来源/文件名（如你设置了 source/file）是否符合预期

### 9. R8/混淆（Release 建议做）

Release 打包开启 R8 后，如遇运行时反射/序列化相关问题，按需添加 keep。示例规则可直接参考：
- [proguard-rules.pro](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/app/proguard-rules.pro)

你也可以直接复制下面的模板到你自己的 `app/proguard-rules.pro`（按你实际依赖增删）。

### 9.1 Producer 专用 keep 规则

```pro
-keep class com.volcengine.tls.android.producer.** { *; }
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
```

`tls-android-producer` AAR 不引入 OkHttp、Protobuf、Guava 或 Java LZ4 传递依赖；不要为了本 SDK 额外添加这些依赖约束。只有当你的 App 自己或其他 SDK 依赖这些库时，才按业务工程的统一依赖策略处理冲突。

验证建议：
- 先 `assembleRelease` 确保无 R8 “Missing class” 报错
- 再用 release 包跑一次“初始化 + 发送一条日志 + 控制台查询”，如果仍有反射/序列化相关崩溃，再按堆栈最小化补 keep

## 管控面与 Java SDK 分工

本仓库后续只提供 Android 端 producer 写入能力。创建 Project/Topic/Index、查询、消费、分析、服务端工具链等全量 TLS API，请使用 Java SDK；不要在新接入中继续使用本仓库旧 Android full/core 模块。

## 常见问题排查

### 1）Android 9+ 使用 http 访问失败

如果你的 `endpoint` 是 `http://...`（不推荐），Android 9+ 默认禁用明文流量，需要配置 `network_security_config` 允许 cleartext。

### 2）应用自身依赖冲突

`tls-android-producer` 不传递 OkHttp、Okio、Protobuf、Guava 或 Java LZ4 依赖。如果你的 App 或其他 SDK 自己引入了这些库，优先保持 App 里已有依赖版本不变，通过 Gradle 统一版本（示例写法）：

```groovy
implementation('com.squareup.okhttp3:okhttp') {
  version { strictly("3.12.13") }
}
```

### 3）Release 运行崩溃（R8 误删）

先确认 Release 构建能通过，然后按崩溃日志补充 keep；建议直接对照示例 keep 规则最小化调整。

### 4）发送失败（401/403/400/5xx）

优先检查：
- `endpoint/region` 是否匹配
- `topicId` 是否正确
- AK/SK 是否有写入权限
- STS token 是否过期

Producer 回调 `result.getAttempts()` 里通常能看到 httpCode 与 errorMessage（示例实现可参考 [MainActivity.java](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/app/src/main/java/com/volcengine/tls/android/demo/MainActivity.java)）。

### 5）需要查看/接管 SDK 内部日志（TlsLogger Provider）

SDK 内部日志用于排查网络/序列化/重试等运行问题，默认不依赖 slf4j。

- 默认行为（不调用 `setProvider`）：
  - Android：自动走 `android.util.Log`，tag 固定为 `TLS-SDK`，消息格式为 `[loggerName] msg`
  - 纯 Java：自动走 `java.util.logging`（JUL），logger 名为 `loggerName`，level 映射为 `FINE/INFO/WARNING/SEVERE`
- 自定义接入（推荐在 `Application.onCreate()` 里尽早设置）：
  ```java
  import com.volcengine.util.TlsLogger;
  import com.volcengine.util.TlsLoggerFactory;
  import com.volcengine.util.TlsLoggerProvider;
  
  TlsLoggerFactory.setProvider(new TlsLoggerProvider() {
    @Override public TlsLogger getLogger(String name) {
      return new MyAppTlsLogger(name);
    }
  });
  ```
- 回退与注意事项：
  - Provider 返回 `null` 或抛异常时，会自动回退到默认实现（Android Log / JUL）
  - `setProvider(...)` 会清空 logger 缓存；建议只设置一次，不要在运行中频繁切换
- 格式化能力与差异：
  - SDK 的 `TlsLogger.*(String format, Object... args)` 支持 `{}` 占位符格式化；最后一个参数为 `Throwable` 时会被当作异常
  - Android 默认 logger：仅 `error(msg, t)` / `error(format, args...)` 会附带 `Throwable`
  - JUL 默认 logger：若最后一个参数为 `Throwable`，debug/info/warn/error 都会输出异常堆栈
