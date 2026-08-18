# TLS Android SDK 使用指南（面向客户）

本指南面向第一次接入 TLS 日志服务的开发者，目标是让你从 0 到 1 跑通：创建资源 → 集成 SDK → 发送日志 → 控制台验证。

## 你需要先知道的两件事

- SDK 依赖坐标（Maven Central）
  - 只需要发送日志（推荐）：`io.github.volcengine-tls:tls-android-producer:2.0.4`
  - 需要完整管理能力（创建 Project/Topic/Index、检索等）：`io.github.volcengine-tls:tls-android-full:2.0.4`
  - 如果你的 App 必须支持 `minSdk=16`：使用 `2.0.4-api16`（仅提供兼容构建版本，低版本系统的 HTTPS/TLS 兼容性需自行验证）
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
   - `compress`：`lz4` 或 `zlib`

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
- `minSdk >= 19`
- `compileSdk/targetSdk` 使用你当前项目的版本（示例工程使用 34）

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
  // 轻量发送（推荐）
  implementation 'io.github.volcengine-tls:tls-android-producer:2.0.4'
  // 仅当使用 lz4 压缩时引入
  implementation 'net.jpountz.lz4:lz4:1.3.0'
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

### 5. 初始化并启动 Producer

在 `Application` 或首个页面创建并启动（建议全局单例）：

```java
import com.volcengine.tls.android.producer.LogProducerClient;
import com.volcengine.tls.android.producer.LogProducerConfig;

LogProducerConfig cfg = new LogProducerConfig()
    .setEndpoint(BuildConfig.TLS_ENDPOINT)
    .setRegion(BuildConfig.TLS_REGION)
    .setAccessKeyId(BuildConfig.TLS_AK)
    .setAccessKeySecret(BuildConfig.TLS_SK)
    .setSecurityToken(BuildConfig.TLS_TOKEN) // 可为空
    .setTopicId(BuildConfig.TLS_TOPIC_ID)
    .setCompressType("lz4") // 或 "zlib"
    .setSendThreadCount(2)
    .setRetryCount(3)
    .setPacketLogBytes(256 * 1024)
    .setPacketLogCount(512)
    .setPacketTimeout(1000);

LogProducerClient client = new LogProducerClient(cfg);
client.start();
```

### 6. 发送一条日志（验证链路）

```java
import java.util.HashMap;
import java.util.Map;

Map<String, String> kv = new HashMap<>();
kv.put("key", "value");
kv.put("app", "demo");
kv.put("ts", String.valueOf(System.currentTimeMillis()));

client.sendLog(kv, result -> {
  if (result.isSuccess()) {
    // 发送成功
  } else {
    // 发送失败：可从 result.getAttempts() 里拿到 httpCode / errorCode / errorMessage
  }
});
```

如果你希望自定义日志时间：

```java
client.sendLog(kv, System.currentTimeMillis(), r -> {});
client.sendLog(kv, System.currentTimeMillis(), 123456789, r -> {});
```

### 7. 关闭与资源释放

在 `onDestroy` 或应用退出时关闭：

```java
client.close();
```

### 8. 控制台验证

在 TLS 控制台按 `topicId` 查询最新日志，检查：
- 是否能看到你写入的 key/value
- 时间字段是否正确
- 压缩类型是否与配置一致（lz4/zlib）

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

### 9.1 依赖冲突与版本约束（强烈建议）

Android 工程里常见的崩溃类型是“依赖版本不兼容”（`NoSuchMethodError` / `NoClassDefFoundError` / `Duplicate class`）。建议在接入前先确认以下关键依赖与 SDK 保持同一条线，避免被其他库“升级/降级”后产生运行时不兼容。

#### 关键依赖版本（SDK 基准）

- OkHttp：`com.squareup.okhttp3:okhttp:3.12.13`
- Okio：`com.squareup.okio:okio:1.17.5`
- Protobuf（Lite）：`com.google.protobuf:protobuf-javalite:3.23.2`
- LZ4（可选，仅当 compress=lz4 时需要）：`net.jpountz.lz4:lz4:1.3.0`
- Guava（仅 Full 使用）：建议使用 `com.google.guava:guava:* -android` 变体（例如 `31.1-android`）

#### 兼容性约束与注意事项

- OkHttp/Okio：
  - 如果你的 App 或其他 SDK 使用 OkHttp 4.x/Okio 2.x/3.x，可能触发方法缺失或重复类问题。建议全工程统一到同一主版本线。
- Protobuf：
  - 本 SDK 使用 `protobuf-javalite`。如果你同时引入 `protobuf-java`/`protobuf-java-util` 且版本不一致，可能出现 `Duplicate class` 或运行时方法缺失。
- Guava：
  - Android 环境建议用 `guava:*-android` 变体，避免与 `guava-jre` 混用。

#### 推荐排查命令（Gradle）

在你的 App 工程执行以下命令定位“是谁带入了不兼容版本”：

```bash
./gradlew :app:dependencyInsight --dependency okhttp --configuration debugRuntimeClasspath
./gradlew :app:dependencyInsight --dependency okio --configuration debugRuntimeClasspath
./gradlew :app:dependencyInsight --dependency protobuf-javalite --configuration debugRuntimeClasspath
./gradlew :app:dependencyInsight --dependency protobuf-java --configuration debugRuntimeClasspath
./gradlew :app:dependencyInsight --dependency guava --configuration debugRuntimeClasspath
```

#### 推荐约束方式（Gradle）

如果你希望强制全工程对齐版本，可在 App 的 `dependencies` 中使用约束（示例以 SDK 基准版本为例）：

```groovy
dependencies {
  constraints {
    implementation("com.squareup.okhttp3:okhttp:3.12.13")
    implementation("com.squareup.okio:okio:1.17.5")
    implementation("com.google.protobuf:protobuf-javalite:3.23.2")
    // Full 使用时可加：
    // implementation("com.google.guava:guava:31.1-android")
  }
}
```

#### Producer（只发送日志）模板

```pro
-keep class com.volcengine.tls.android.producer.** { *; }
-keep class com.volcengine.model.tls.producer.** { *; }
-keep class com.volcengine.model.tls.pb.** { *; }
-keep class com.volcengine.model.tls.exception.LogException { *; }
-keep class com.volcengine.service.tls.** { *; }
-keep class com.volcengine.http.** { *; }
-keep class com.volcengine.util.** { *; }

-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.**

 # 如果使用 lz4 压缩，保留下面两行；只用 zlib 可删除
 -keep class net.jpountz.** { *; }
 -dontwarn net.jpountz.**
```

#### Full（管理 + 发送）模板（在 Producer 基础上增加）

```pro
-keep class com.alibaba.fastjson.** { *; }
-keepclassmembers class ** { @com.alibaba.fastjson.annotation.JSONField *; }
-keepclassmembers class com.volcengine.model.tls.** { *; }

-dontwarn java.awt.**
-dontwarn javax.money.**
-dontwarn org.javamoney.**
-dontwarn org.joda.time.**
-dontwarn org.joda.time.format.**

-dontwarn springfox.documentation.**
-dontwarn javax.ws.rs.**
-dontwarn org.glassfish.jersey.**
-dontwarn javax.servlet.**
-dontwarn javax.servlet.http.**
-dontwarn org.springframework.**
-dontwarn org.springframework.core.**
-dontwarn org.springframework.http.**
-dontwarn org.springframework.http.converter.**
-dontwarn org.springframework.http.server.**
-dontwarn org.springframework.messaging.**
-dontwarn org.springframework.util.**
-dontwarn org.springframework.web.**
-dontwarn retrofit2.**
```

验证建议：
- 先 `assembleRelease` 确保无 R8 “Missing class” 报错
- 再用 release 包跑一次“初始化 + 发送一条日志 + 控制台查询”，如果仍有反射/序列化相关崩溃，再按堆栈最小化补 keep

## Android 应用接入（可选：Full 同步 Client API）

如果你需要在客户端里做资源管理或同步调用（不推荐在移动端做大量管理类操作），使用 Full 包：

```groovy
dependencies {
  implementation 'io.github.volcengine-tls:tls-android-full:2.0.4'
  implementation 'net.jpountz.lz4:lz4:1.3.0'
}
```

示例（写入日志）：

```java
import com.volcengine.model.tls.ClientBuilder;
import com.volcengine.model.tls.ClientConfig;
import com.volcengine.model.tls.request.PutLogsRequestV2;
import com.volcengine.model.tls.response.PutLogsResponse;
import com.volcengine.service.tls.TLSLogClient;

ClientConfig cfg = new ClientConfig(
  BuildConfig.TLS_ENDPOINT,
  BuildConfig.TLS_REGION,
  BuildConfig.TLS_AK,
  BuildConfig.TLS_SK,
  BuildConfig.TLS_TOKEN
);

TLSLogClient client = ClientBuilder.newClient(cfg);
PutLogsRequestV2 req = new PutLogsRequestV2(/* logs */ , BuildConfig.TLS_TOPIC_ID, null, "lz4", "source", "file");
PutLogsResponse resp = client.putLogsV2(req);
client.destroy();
```

## Java 环境（命令行）QuickStart（创建资源 + 写入 + 检索 + 清理）

仓库提供了可直接运行的 Java 示例：
- [android-example/README.md](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/android-example/README.md)
- QuickStart 源码：[QuickStart.java](https://github.com/volcengine/ve-tls-android-sdk/blob/master-2.0/android-example/src/main/java/com/volcengine/example/tls/QuickStart.java)

运行方式（在仓库根目录）：

```bash
endPoint="https://tls-cn-xxx.volces.com" \
region="cn-xxx" \
ak="YOUR_AK" \
sk="YOUR_SK" \
token="" \
bash android-example/run-quickstart.sh
```

## 常见问题排查

### 1）Android 9+ 使用 http 访问失败

如果你的 `endpoint` 是 `http://...`（不推荐），Android 9+ 默认禁用明文流量，需要配置 `network_security_config` 允许 cleartext。

### 2）依赖冲突（okhttp/okio/protobuf 版本冲突）

优先保持 App 里已有依赖版本不变，通过 Gradle 统一版本（示例写法）：

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
