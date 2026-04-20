<h1 align="center"><img src="https://iam.volccdn.com/obj/volcengine-public/pic/volcengine-icon.png"></h1>

# Volcengine TLS Android SDK

该仓库提供在 Android/Java 环境下访问火山引擎 TLS（日志服务）的 SDK 与示例，支持同步 Client API 与异步 Producer 发送。已针对移动端体积与稳定性做优化：可选网络栈（OkHttp 3.x）、Producer-native 压缩（NONE/LZ4）、R8 裁剪与安全日志映射。

从 0 到 1 接入文档请优先阅读：
- SDK 使用指南（推荐）：https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/SDK_USAGE_GUIDE.md
- 本地配置文件模板（不含真实密钥）：https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls_config.properties.example

## 前置准备
### 服务开通
请先在火山引擎控制台开通日志服务：https://console.volcengine.com/
进入日志服务控制台后，按向导创建项目与主题，确保具备写入权限。

### 获取安全凭证
Access Key（AK/SK）是访问火山引擎服务的安全凭证，包含 Access Key ID 与 Secret Access Key。
创建与管理入口：
- 访问控制：https://console.volcengine.com/iam
- 访问密钥：https://console.volcengine.com/iam/keymanage/
更多说明参考文档：https://www.volcengine.com/docs/6291/65568
如需临时鉴权，可使用安全令牌（ST）。

## 模块结构
- `tls-android-modules/core`：公共模型、HTTP 与签名实现、protobuf（javalite）生成代码、统一日志映射工具
- `tls-android-modules/full`：完整 Client API（创建/检索/删除等）与 Producer 发送实现
- `tls-android-modules/producer-native`：正式 Producer 模块，基于 `ve-tls-c-sdk`，提供 TLS-style `LogProducerClient` / `LogProducerConfig`
- `tls-android-modules/app`：演示 App（使用 `LogProducerClient`），含 BenchmarkActivity 压测页
- `tls-android-modules/app-empty`：最简 UI，便于体积对比
- `android-example`：Java 控制台示例（QuickStart、ConsumerDemo、full producer 示例等）

## 选择指南（不同场景用哪个包）
- 只需要发送日志（建议）
  - 依赖 `io.github.volcengine-tls:tls-android-producer-native`
  - 使用 `LogProducerClient.addLog(Log)` / `destroyLogProducer()` 的正式 Producer 接口
- 需要 native persistent / recover 能力
  - 依赖 `io.github.volcengine-tls:tls-android-producer-native`
  - 适合需要与 `ve-tls-c-sdk` persistent 语义对齐的 Android SDK、宿主 App 和嵌入式 Android 场景
- 需要完整管理能力（创建 Project/Topic/Index、检索等）
  - 依赖 `io.github.volcengine-tls:tls-android-full`（携带完整 Client API），自动依赖 `tls-android-core`
  - 体积较大，适合工具类或后管应用

## 获取与安装
### 方式 A：从 Maven Central 使用（推荐）
在应用项目的仓库中确保包含 `mavenCentral()`，然后直接添加依赖：

```groovy
dependencies {
  // native producer（推荐，正式 Producer 模块）
  implementation 'io.github.volcengine-tls:tls-android-producer-native:2.0.4'
  // 如需完整能力（管理+发送）
  // implementation 'io.github.volcengine-tls:tls-android-full:2.0.4'
}
```

说明：
- `tls-android-producer-native` / `tls-android-full` 会自动拉取 `tls-android-core`，无需手动声明 core。
- 从 2.0.1 起已发布 Gradle Module Metadata（`.module`），Gradle/AGP 可直接解析到 AAR 变体，无需 `@aar`。
- 如果你的 App 必须支持 `minSdk=16`：请使用 `2.0.4-api16`（兼容构建版本，低版本系统的 HTTPS/TLS 兼容性需自行验证）。

### 方式 B：源码方式接入（仓库开发/二次开发）
在工程的 `settings.gradle` 中包含需要的模块：

```groovy
include ':core', ':full', ':producer-native', ':app'
project(':producer-native').projectDir = new File('tls-android-modules/producer-native')
```

应用侧 `build.gradle`：

```groovy
dependencies {
  implementation project(':producer-native') // 或者 ':full'（按你的场景）
}
```

ProGuard/R8 规则已在 `tls-android-modules/app/proguard-rules.pro` 示例配置中给出，生产集成时请按需拷贝或增删。

## 快速开始
### 方式 A：异步 Producer 发送（推荐）

```java
import com.volcengine.tls.android.producer.Log;
import com.volcengine.tls.android.producer.LogProducerClient;
import com.volcengine.tls.android.producer.LogProducerConfig;

LogProducerConfig cfg = new LogProducerConfig()
    .setEndpoint(System.getenv("endPoint"))
    .setRegion(System.getenv("region"))
    .setAccessKeyId(System.getenv("ak"))
    .setAccessKeySecret(System.getenv("sk"))
    .setSecurityToken(System.getenv("token")) // 可选
    .setTopicId(System.getenv("topicId"))
    .setCompressType(LogProducerConfig.CompressType.LZ4)
    .setSendThreadCount(2)
    .setRetryCount(3)
    .setPacketLogBytes(256 * 1024)
    .setPacketLogCount(512)
    .setPacketTimeoutMs(1000);

LogProducerClient client = new LogProducerClient(cfg, result -> {
  if (result.isSuccess()) {
    // ok
  }
});

Map<String,String> kv = new HashMap<>();
kv.put("key", "value");
Log log = new Log()
    .putContents(kv)
    .setLogTime(System.currentTimeMillis());
client.addLog(log);
client.destroyLogProducer();
```

更多示例参见：[LogProducerClient.java](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/LogProducerClient.java)

### 方式 A-2：Native Producer（persistent/recover）

```java
import com.volcengine.tls.android.producer.Log;
import com.volcengine.tls.android.producer.LogProducerClient;
import com.volcengine.tls.android.producer.LogProducerConfig;

LogProducerConfig cfg = new LogProducerConfig()
    .setEndpoint(BuildConfig.TLS_ENDPOINT)
    .setRegion(BuildConfig.TLS_REGION)
    .setAccessKeyId(BuildConfig.TLS_AK)
    .setAccessKeySecret(BuildConfig.TLS_SK)
    .setTopicId(BuildConfig.TLS_TOPIC_ID)
    .setPersistent(true)
    .setPersistentFilePath(getFilesDir() + "/tls-native-persistent")
    .setPersistentMaxFileCount(4)
    .setPersistentMaxFileSize(1024 * 1024)
    .setPersistentMaxLogCount(1024);

LogProducerClient client = new LogProducerClient(cfg);

Map<String, String> kv = new HashMap<>();
kv.put("level", "info");
kv.put("message", "native producer");
Log log = new Log().putContents(kv).setLogTime(System.currentTimeMillis());
client.addLog(log, 1);
client.destroyLogProducer();
```

native API 参考：
- [LogProducerConfig.java](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/LogProducerConfig.java)
- [LogProducerClient.java](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/LogProducerClient.java)

### 方式 B：同步 Client API（完整能力）

```java
ClientConfig cfg = new ClientConfig(
  System.getenv("endPoint"),
  System.getenv("region"),
  System.getenv("ak"),
  System.getenv("sk"),
  System.getenv("token")
);
TLSLogClient client = com.volcengine.model.tls.ClientBuilder.newClient(cfg);

PutLogsRequestV2 req = new PutLogsRequestV2(/* logs */ , System.getenv("topicId"), null, "lz4", "source", "file");
PutLogsResponse resp = client.putLogsV2(req);
client.destroy();
```

参考完整示例：[TLSLogClientImpl](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/full/src/main/java/com/volcengine/service/tls/TLSLogClientImpl.java)

### 新手三步接入（Android 应用）
- 第一步：添加依赖与权限
  ```groovy
  dependencies {
    // native producer（推荐，正式 Producer 模块）
    implementation 'io.github.volcengine-tls:tls-android-producer-native:2.0.4'
    // 如需完整能力（管理+发送）
    // implementation 'io.github.volcengine-tls:tls-android-full:2.0.4'
  }
  ```
  ```xml
  <!-- AndroidManifest.xml -->
  <uses-permission android:name="android.permission.INTERNET" />
  ```
- 第二步：初始化并启动
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
      .setRetryCount(3)
      .setPacketLogBytes(256 * 1024)
      .setPacketLogCount(512)
      .setPacketTimeoutMs(1000);
  LogProducerClient client = new LogProducerClient(cfg);
  ```
- 第三步：写入日志与关闭
  ```java
  Map<String,String> kv = new HashMap<>();
  kv.put("key", "value");
  Log log = new Log().putContents(kv).setLogTime(System.currentTimeMillis());
  client.addLog(log);
  client.destroyLogProducer();
  ```

### Android 项目配置来源建议
- 避免在 Android 端使用 System.getenv 读取敏感配置；推荐：
  - BuildConfig 常量（通过 build.gradle 注入）
  - 应用的资源或配置文件（例如 res/raw 或 assets），并在发布流程中安全管理
- 联调使用 http 时需配置 network_security_config 以允许明文流量（示例 app 已提供）
- 仅在需要时启用 `enableTimeNs`，避免每条日志调用 nanoTime 的额外开销
- R8/混淆：保留 okhttp/okio/protobuf 与 com.volcengine.*，示例规则可复用并按运行日志修正

### 接入必填信息
- endpoint（必填）：形如 `https://tls-cn-xxx.volces.com`
- region（必填）：例如 `cn-xxx`
- accessKeyId / accessKeySecret（必填）：AK/SK 凭证
- topicId（必填）：日志主题 ID
- securityToken（可选）：临时鉴权场景使用
- compressType（建议）：`NONE` 或 `LZ4`，默认 `LZ4`

### 常见易错点与排查
- Android 12 导出要求：含 `intent-filter` 的 Activity 必须声明 `android:exported="true"`
- 网络安全：联调使用 `http` 时需配置 `network_security_config` 允许明文流量
- 权限：`INTERNET` 必须声明（否则无法联网）
  ```xml
  <uses-permission android:name="android.permission.INTERNET" />
  ```
- 依赖：Producer-native 仅公开 `NONE/LZ4`，默认 `LZ4`；如需旧 lite/full 路径，请按对应模块说明处理
- R8/混淆：避免宽泛 keep 整个 `com.volcengine.*`，让 R8 移除未用代码；按日志最小化补充第三方库 keep
- 时间戳：仅在需要纳秒级时间时开启 `enableTimeNs`；否则禁用以降低开销
- 配置来源：不要在 Android 端使用 `System.getenv`；使用 BuildConfig/受控配置文件并妥善管理敏感信息
- 本地签名与构建：受限环境无法写入 `~/.android` 时，使用项目自带 `debug.keystore` 并在 `signingConfigs` 指定

### SDK 内部日志（TlsLogger Provider）
- SDK 内部日志用于排查网络/序列化/重试等运行问题，默认不依赖 slf4j。
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
- 完整示例：桥接到 SLF4J
  - 依赖（仅 app 引入，SDK 本身不依赖）：
    ```groovy
    dependencies {
      implementation 'org.slf4j:slf4j-api:1.7.36'
      // Android 示例用 simple 便于快速验证；正式项目建议使用你们现有的 slf4j 绑定实现
      debugImplementation 'org.slf4j:slf4j-simple:1.7.36'
    }
    ```
  - 初始化（Application.onCreate）：
    ```java
    import com.volcengine.util.TlsLogger;
    import com.volcengine.util.TlsLoggerFactory;
    import com.volcengine.util.TlsLoggerProvider;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    
    TlsLoggerFactory.setProvider(new TlsLoggerProvider() {
      @Override public TlsLogger getLogger(String name) {
        return new TlsLogger() {
          private final Logger l = LoggerFactory.getLogger(name);
          @Override public void debug(String msg) { l.debug(msg); }
          @Override public void debug(String format, Object... args) { l.debug(format, args); }
          @Override public void info(String msg) { l.info(msg); }
          @Override public void info(String format, Object... args) { l.info(format, args); }
          @Override public void warn(String msg) { l.warn(msg); }
          @Override public void warn(String format, Object... args) { l.warn(format, args); }
          @Override public void error(String msg) { l.error(msg); }
          @Override public void error(String msg, Throwable t) { l.error(msg, t); }
          @Override public void error(String format, Object... args) { l.error(format, args); }
        };
      }
    });
    ```
- 完整示例：桥接到 Timber（可选）
  - 依赖（仅 app 引入）：
    ```groovy
    dependencies { implementation 'com.jakewharton.timber:timber:5.0.1' }
    ```
  - 初始化（Application.onCreate）：
    ```java
    import com.volcengine.util.TlsLogger;
    import com.volcengine.util.TlsLoggerFactory;
    import com.volcengine.util.TlsLoggerProvider;
    import timber.log.Timber;
    
    TlsLoggerFactory.setProvider(new TlsLoggerProvider() {
      @Override public TlsLogger getLogger(String name) {
        return new TlsLogger() {
          @Override public void debug(String msg) { Timber.tag(name).d("%s", msg); }
          @Override public void debug(String format, Object... args) { Timber.tag(name).d(format, args); }
          @Override public void info(String msg) { Timber.tag(name).i("%s", msg); }
          @Override public void info(String format, Object... args) { Timber.tag(name).i(format, args); }
          @Override public void warn(String msg) { Timber.tag(name).w("%s", msg); }
          @Override public void warn(String format, Object... args) { Timber.tag(name).w(format, args); }
          @Override public void error(String msg) { Timber.tag(name).e("%s", msg); }
          @Override public void error(String msg, Throwable t) { Timber.tag(name).e(t, "%s", msg); }
          @Override public void error(String format, Object... args) { Timber.tag(name).e(format, args); }
        };
      }
    });
    ```
- 规则与回退：
  - Provider 返回 `null` 或抛异常时，会自动回退到默认实现（Android Log / JUL）
  - `setProvider(...)` 会清空 logger 缓存；建议只设置一次，不要在运行中频繁切换
- 格式化能力与差异：
  - SDK 的 `TlsLogger.*(String format, Object... args)` 支持 `{}` 占位符格式化；最后一个参数为 `Throwable` 时会被当作异常
  - Android 默认 logger：仅 `error(msg, t)` / `error(format, args...)` 会附带 `Throwable`（debug/info/warn 不附带异常重载）
  - JUL 默认 logger：若最后一个参数为 `Throwable`，debug/info/warn/error 都会以 `logger.log(level, msg, t)` 输出异常

### 发布后快速验证
- 构造 `LogProducerClient` 并调用 `addLog(Log)`，回调 `LogProducerResult.isSuccess()` 为 `true`
- 在服务端查询对应 `topicId` 的最新日志，确认字段（logTime/contents/group tags）与期望一致

## 本次更新与迁移指南（1.1.5 → 2.0.x）
- 依赖升级：使用 `io.github.volcengine-tls:tls-android-producer-native:2.0.4`（正式 Producer 模块）或 `io.github.volcengine-tls:tls-android-full:2.0.4`（完整能力）。
- API 迁移：Producer 发送从 `start()/close()/sendLog(Map)` 切换为 `LogProducerClient.addLog(Log)` / `destroyLogProducer()`
  - 入口：`Log` 承载内容与 `logTime`
  - `producer-native` 公共 `CompressType` 仅支持 `NONE/LZ4`，默认 `LZ4`
  - 参考：
    - LogProducerClient.java：https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/LogProducerClient.java
    - LogProducerConfig.java：https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/LogProducerConfig.java
    - settings.gradle（模块映射）：https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/settings.gradle
- 时间戳行为：如需自定义时间，请通过 `Log#setLogTime(long)` 设置毫秒时间。
- 性能优化：Map→LogItem 转换移除中间合并用的 HashMap，保留覆盖语义，减少 CPU 与 GC 开销。
- 最低支持版本：Android 4.4（API 19）。

## 日志映射与字段
统一由公共工具完成：[core/AdaptorUtil.java](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/core/src/main/java/com/volcengine/model/tls/util/AdaptorUtil.java)

- Log.Contents：写入每条日志的 `key/value`
- Log.Time：优先使用用户传入的毫秒时间；未提供时在发送侧自动生成并归一化
- Log.TimeNs（可选）：
  - 用户可传入 `timeNs`
  - 或通过开关 `enableTimeNs=true` 自动填充（次秒内纳秒值）
- LogGroup.Source、FileName：按需填充
- LogGroup.LogTags（可选）：组级标签（`groupTags`）在 producer 与 full 均支持

## 压缩与体积优化
- 压缩：Producer-native 仅支持 `NONE` 与 `LZ4`
  - `LZ4`：默认值，适合常规发送场景
  - `NONE`：关闭压缩
- 体积：默认网络栈为 OkHttp 3.12.13 + Okio 1.17.5（Java），结合 R8 可获得较小 APK
- R8：示例 `app` 已开启 R8（minify/shrink），同时提供 keep 规则；如遇运行问题按日志定向补充

## 基准测试（Benchmark）
演示 App 中提供压测页：通过两种压缩模式（`NONE` / `LZ4`）对比发送吞吐与延迟分布，并导出报告。
- 启动入口：[AndroidManifest.xml](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/app/src/main/AndroidManifest.xml)
- 页面逻辑：[BenchmarkActivity.java](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/app/src/main/java/com/volcengine/tls/android/demo/BenchmarkActivity.java)
- 报告导出路径：`/sdcard/Android/data/<app>/files/benchmark/`

## 运行示例（控制台）
- Producer-native 当前不提供独立 JVM 控制台脚本；请使用 Android 演示 App，或仓库内的 producer-native Gradle / 单元测试流程。
- 运行 Producer（full）：
  ```bash
  endPoint="https://tls-cn-xxx.volces.com" region="cn-xxx" ak="..." sk="..." token="" topicId="..." \
  bash android-example/run-full-producer-demo.sh
  ```
- 运行 QuickStart（full 客户端，创建/写入/检索/清理）：
  ```bash
  endPoint="https://tls-cn-xxx.volces.com" region="cn-xxx" ak="..." sk="..." token="" \
  bash android-example/run-quickstart.sh
  ```
 - 运行 DeleteResource（full 客户端，批量删除测试资源）：
  ```bash
  endPoint="https://tls-cn-xxx.volces.com" region="cn-xxx" ak="..." sk="..." token="" \
  bash android-example/run-delete-resource.sh
  ```

## 集成测试
- 环境变量要求：endPoint、region、ak、sk（token 可选），部分用例需 topicId。
- 最低支持 Android 版本：4.4（API 19）。SDK 库模块已统一设置 minSdk=19。
- 运行方式：
  ```bash
  cd tls-android-modules
  ./gradlew :core:assembleRelease :full:assembleRelease :producer-native:assembleRelease
  ./gradlew :integration-tests:test
  ```

## 接入说明
- Gradle 配置（应用/库）：
  ```gradle
  android {
    defaultConfig { minSdk 19 }
  }
  dependencies {
    implementation project(':producer-native')
    // 或使用 full 模块：
    // implementation project(':full')
  }
  ```
- User-Agent：SDK 自动注入统一标识，格式为 volc-tls-android/{module}/v{version}

## CI
- 提供 GitHub Actions 工作流，包含构建 AAR 与运行集成测试
- 路径：.github/workflows/android-ci.yml
- 说明：
  - 测试用例通过 Assumptions 检查环境变量，不满足时自动跳过，避免本地误触发。
  - 创建/修改/检索链路参考：[ResourceCRUDIntegrationTest.java](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/integration-tests/src/test/java/com/volcengine/integration/ResourceCRUDIntegrationTest.java)
  - 搜索/直方图/分片参考：[SearchIntegrationTest.java](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/integration-tests/src/test/java/com/volcengine/integration/SearchIntegrationTest.java)

## 常见问题
- R8 开启后发送卡住
  - 确认 keep 规则完整（okhttp/okio/protobuf/com.volcengine.*）
  - 观察 release 下 Logcat
- 服务端解压异常
  - 头部含义：`x-tls-compresstype`（NONE/LZ4），`x-tls-bodyrawsize`（压缩前长度）
  - 服务端请按压缩前长度作为校验值，并采用有界读取避免内存膨胀

## 代码参考
- 发送管线（native/full）：
  - [LogProducerClient.java (native)](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/LogProducerClient.java)
  - [LogProducerConfig.java (native)](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/LogProducerConfig.java)
- 公共映射工具：
  - [AdaptorUtil.java](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/core/src/main/java/com/volcengine/model/tls/util/AdaptorUtil.java)
- 压缩与发送：
  - [BaseServiceImpl.java](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/tls-android-modules/core/src/main/java/com/volcengine/service/BaseServiceImpl.java)

如需进一步体积优化、开关纳秒时间、或自定义日志映射策略，请在 Issue 中说明你的场景，我们会协助提供建议或改造示例。
