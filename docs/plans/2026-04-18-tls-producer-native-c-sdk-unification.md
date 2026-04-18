# TLS Producer Native C SDK Unification Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the current Java producer implementation with a TLS-style Android `producer-native` module backed by `ve-tls-c-sdk`, including persistent/recover, JNI transport, and legacy producer retirement.

**Architecture:** Add an Android binding layer inside `ve-tls-c-sdk` for config normalization, lifecycle helpers, and future platform hooks. Build one Android `producer-native` AAR that contains thin Java facade classes, an internal Java HTTP bridge, JNI glue, and a single final native shared library linked against `ve_tls_core`.

**Tech Stack:** C11, JNI, Android Gradle Plugin 8.4, CMake, JUnit 4/5, `HttpURLConnection` / `HttpsURLConnection`, pthread-based platform layer, Maven publishing.

---

## Working Rules

- Execute all commands from workspace root: `/data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer`
- Use `git -C ve-tls-c-sdk ...` for C SDK commits
- Use `git -C ve-tls-android-sdk ...` for Android SDK commits
- Keep TDD scope small: one failing test, one minimal implementation, one passing run, one commit
- Prefer deleting legacy producer code only after `producer-native` contract, build, and smoke tests are green

### Task 1: Add C SDK Android Binding Skeleton

**Files:**
- Create: `ve-tls-c-sdk/bindings/android/include/ve_tls_android_binding.h`
- Create: `ve-tls-c-sdk/bindings/android/src/ve_tls_android_binding.c`
- Create: `ve-tls-c-sdk/tests/test_android_binding.c`
- Modify: `ve-tls-c-sdk/CMakeLists.txt`
- Test: `ve-tls-c-sdk/tests/test_android_binding.c`

**Step 1: Write the failing test**

```c
static void test_android_binding_normalizes_defaults(void) {
    ve_tls_android_config_view in;
    ve_tls_android_runtime_options runtime;
    ve_tls_config out;

    memset(&in, 0, sizeof(in));
    in.endpoint = "https://tls-cn-beijing.volces.com";
    in.region = "cn-beijing";
    in.project_id = "project-id";
    in.topic_id = "topic-id";
    in.compress_type = VE_TLS_ANDROID_COMPRESS_LZ4;
    in.use_persistent = 1;
    in.send_thread_count = 4;
    in.destroy_wait_ms = 15000;

    assert(ve_tls_android_binding_build_config(&in, &out, &runtime) == VE_TLS_OK);
    assert(out.use_persistent == 1);
    assert(out.send_thread_count == 1);
    assert(strcmp(out.compress_type, "lz4") == 0);
    assert(runtime.destroy_wait_ms == 15000);
}
```

**Step 2: Run test to verify it fails**

Run:

```bash
cmake -S ve-tls-c-sdk -B /tmp/ve_tls_android_plan_build -DVE_TLS_BUILD_TESTS=ON -DVE_TLS_BUILD_TOOLS=OFF
cmake --build /tmp/ve_tls_android_plan_build --target ve_tls_test_android_binding
```

Expected: FAIL with missing target or missing symbols for `ve_tls_android_binding_*`.

**Step 3: Write minimal implementation**

```c
typedef enum {
    VE_TLS_ANDROID_COMPRESS_NONE = 0,
    VE_TLS_ANDROID_COMPRESS_LZ4 = 1
} ve_tls_android_compress_type;

typedef struct {
    const char * endpoint;
    const char * region;
    const char * project_id;
    const char * topic_id;
    const char * access_key_id;
    const char * access_key_secret;
    const char * security_token;
    const char * source;
    const char * hash_key;
    ve_tls_android_compress_type compress_type;
    int32_t send_thread_count;
    int32_t use_persistent;
    int32_t destroy_wait_ms;
} ve_tls_android_config_view;

ve_tls_result ve_tls_android_binding_build_config(
    const ve_tls_android_config_view * in,
    ve_tls_config * out,
    ve_tls_android_runtime_options * runtime
);
```

Implementation rules:

- call `ve_tls_config_init(out)`
- map `NONE -> "none"` and `LZ4 -> "lz4"`
- clamp `send_thread_count` to `1` when `use_persistent=1`
- copy `destroy_wait_ms` into runtime options instead of `ve_tls_config`

**Step 4: Run test to verify it passes**

Run:

```bash
cmake --build /tmp/ve_tls_android_plan_build --target ve_tls_test_android_binding
ctest --test-dir /tmp/ve_tls_android_plan_build --output-on-failure -R ve_tls_test_android_binding
```

Expected: PASS for `test_android_binding_normalizes_defaults`.

**Step 5: Commit**

```bash
git -C ve-tls-c-sdk add bindings/android/include/ve_tls_android_binding.h bindings/android/src/ve_tls_android_binding.c tests/test_android_binding.c CMakeLists.txt
git -C ve-tls-c-sdk commit -m "feat: add android binding skeleton"
```

### Task 2: Add Binding Helpers for Path Rewrite, Recover, and Destroy Ordering

**Files:**
- Modify: `ve-tls-c-sdk/bindings/android/include/ve_tls_android_binding.h`
- Modify: `ve-tls-c-sdk/bindings/android/src/ve_tls_android_binding.c`
- Modify: `ve-tls-c-sdk/tests/test_android_binding.c`
- Test: `ve-tls-c-sdk/tests/test_android_binding.c`

**Step 1: Write the failing test**

```c
static void test_android_binding_rewrites_process_path_and_clamps_sender(void) {
    char out_path[256];
    assert(ve_tls_android_binding_build_persistent_path(
        "/data/user/0/demo/files/tls/producer",
        "demo:push",
        out_path,
        sizeof(out_path)) == 0);
    assert(strstr(out_path, "demo_push") != NULL);
}

static void test_android_binding_recover_and_destroy_sequence(void) {
    ve_tls_android_lifecycle_trace trace = {0};
    ve_tls_android_binding_run_lifecycle_hooks(1, &trace);
    assert(trace.recover_called == 1);
    assert(trace.close_called == 1);
    assert(trace.destroy_called == 1);
    assert(trace.close_called_before_destroy == 1);
}
```

**Step 2: Run test to verify it fails**

Run:

```bash
cmake --build /tmp/ve_tls_android_plan_build --target ve_tls_test_android_binding
ctest --test-dir /tmp/ve_tls_android_plan_build --output-on-failure -R ve_tls_test_android_binding
```

Expected: FAIL with missing path helper and lifecycle helper.

**Step 3: Write minimal implementation**

```c
int ve_tls_android_binding_build_persistent_path(
    const char * base_dir,
    const char * process_name,
    char * out,
    size_t out_size
);

ve_tls_result ve_tls_android_binding_after_create(
    ve_tls_producer * producer,
    const ve_tls_android_runtime_options * runtime
);

void ve_tls_android_binding_before_destroy(
    ve_tls_producer * producer,
    const ve_tls_android_runtime_options * runtime
);
```

Implementation rules:

- main process keeps the base path unchanged
- non-main process appends a sanitized process suffix
- `after_create()` calls `ve_tls_producer_recover()` when persistent is enabled
- `before_destroy()` always calls `ve_tls_producer_close(runtime->destroy_wait_ms)` before `ve_tls_producer_destroy()`

**Step 4: Run test to verify it passes**

Run the same two commands from Step 2.

Expected: PASS for path rewrite and lifecycle ordering.

**Step 5: Commit**

```bash
git -C ve-tls-c-sdk add bindings/android/include/ve_tls_android_binding.h bindings/android/src/ve_tls_android_binding.c tests/test_android_binding.c
git -C ve-tls-c-sdk commit -m "feat: add android binding lifecycle helpers"
```

### Task 3: Scaffold the Android `producer-native` Module

**Files:**
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/build.gradle`
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/consumer-rules.pro`
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/proguard-rules.pro`
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/AndroidManifest.xml`
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/cpp/CMakeLists.txt`
- Modify: `ve-tls-android-sdk/tls-android-modules/integration-tests/build.gradle`
- Create: `ve-tls-android-sdk/tls-android-modules/maven-central-publish/producer-native/pom.xml`
- Create: `ve-tls-android-sdk/tls-android-modules/maven-publish/pom-producer-native.xml`
- Test: `ve-tls-android-sdk/tls-android-modules/producer-native/build.gradle`

**Step 1: Write the failing build check**

Run:

```bash
cd ve-tls-android-sdk/tls-android-modules
./gradlew :producer-native:assembleRelease
```

Expected: FAIL because `producer-native` project directory exists in `settings.gradle` but has no build files.

**Step 2: Add the minimal module scaffold**

```groovy
plugins {
  id 'com.android.library'
  id 'maven-publish'
  id 'signing'
}

android {
  namespace 'com.volcengine.tls.android.producer'
  compileSdk 34
  defaultConfig {
    minSdk ((findProperty('MIN_SDK_OVERRIDE') ?: 19) as int)
    targetSdk 34
    externalNativeBuild {
      cmake { cppFlags '' }
    }
  }
  externalNativeBuild {
    cmake { path 'src/main/cpp/CMakeLists.txt' }
  }
}
```

`src/main/cpp/CMakeLists.txt` should:

- `add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../../../../../ve-tls-c-sdk ...)`
- set `VE_TLS_ENABLE_CURL=OFF`
- set `VE_TLS_BUILD_TESTS=OFF`
- set `VE_TLS_BUILD_TOOLS=OFF`
- set `VE_TLS_ENABLE_LZ4=ON`
- set `VE_TLS_ENABLE_ZLIB=OFF`
- build one final shared library target named `tls_producer_jni`

**Step 3: Run build to verify it now reaches compilation**

Run:

```bash
cd ve-tls-android-sdk/tls-android-modules
./gradlew :producer-native:assembleRelease
```

Expected: FAIL later at missing Java/JNI source files, not at missing module/build-script level.

**Step 4: Wire publishing metadata**

Add `producer-native` publication files and update any publication aggregator files that enumerate module POMs.

**Step 5: Commit**

```bash
git -C ve-tls-android-sdk add tls-android-modules/producer-native tls-android-modules/integration-tests/build.gradle tls-android-modules/maven-central-publish/producer-native/pom.xml tls-android-modules/maven-publish/pom-producer-native.xml
git -C ve-tls-android-sdk commit -m "feat: scaffold producer-native android module"
```

### Task 4: Add the Public Java API and a Testable Bridge Seam

**Files:**
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/LogProducerConfig.java`
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/LogProducerClient.java`
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/Log.java`
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/LogProducerCallback.java`
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/LogProducerResult.java`
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/internal/NativeProducerBridge.java`
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/src/test/java/com/volcengine/tls/android/producer/LogProducerConfigTest.java`
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/src/test/java/com/volcengine/tls/android/producer/LogProducerResultTest.java`
- Test: `ve-tls-android-sdk/tls-android-modules/producer-native/src/test/java/com/volcengine/tls/android/producer/LogProducerConfigTest.java`

**Step 1: Write the failing tests**

```java
@Test
public void compressType_exposesOnlyNoneAndLz4() {
  assertArrayEquals(
      new LogProducerConfig.CompressType[] {
          LogProducerConfig.CompressType.NONE,
          LogProducerConfig.CompressType.LZ4
      },
      LogProducerConfig.CompressType.values());
}

@Test
public void result_isImmutableValueObject() {
  LogProducerResult result = new LogProducerResult(
      LogProducerResult.Code.OK, "rid", null, null, 200, 0, 0, 10, 8);
  assertEquals(LogProducerResult.Code.OK, result.getCode());
  assertEquals("rid", result.getRequestId());
}
```

**Step 2: Run test to verify it fails**

Run:

```bash
cd ve-tls-android-sdk/tls-android-modules
./gradlew :producer-native:testDebugUnitTest --tests "*LogProducerConfigTest" --tests "*LogProducerResultTest"
```

Expected: FAIL with missing classes.

**Step 3: Write minimal implementation**

```java
public final class LogProducerResult {
  public enum Code { OK, INVALID, DROP_ERROR, PERSISTENT_ERROR, CLOSED, TIMEOUT, AUTH_ERROR, NETWORK_ERROR, SERVER_ERROR, UNKNOWN_ERROR }
  private final Code code;
  private final String requestId;
  private final String errorCode;
  private final String errorMessage;
  private final int httpCode;
  private final int transportKind;
  private final int transportCode;
  private final long logBytes;
  private final long compressedBytes;
}
```

`LogProducerClient` should depend on an internal `NativeProducerBridge` interface so Java lifecycle logic can be tested without loading JNI.

**Step 4: Run test to verify it passes**

Run the same Gradle command from Step 2.

Expected: PASS.

**Step 5: Commit**

```bash
git -C ve-tls-android-sdk add tls-android-modules/producer-native/src/main/java tls-android-modules/producer-native/src/test/java
git -C ve-tls-android-sdk commit -m "feat: add producer-native public java api"
```

### Task 5: Implement Java Lifecycle Logic Against the Bridge Interface

**Files:**
- Modify: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/LogProducerClient.java`
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/internal/ConfigSnapshot.java`
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/internal/ProcessUtil.java`
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/src/test/java/com/volcengine/tls/android/producer/LogProducerClientBridgeTest.java`
- Test: `ve-tls-android-sdk/tls-android-modules/producer-native/src/test/java/com/volcengine/tls/android/producer/LogProducerClientBridgeTest.java`

**Step 1: Write the failing test**

```java
@Test
public void create_clonesConfigAndRewritesPersistentPathForSecondaryProcess() {
  FakeBridge bridge = new FakeBridge();
  LogProducerConfig config = new LogProducerConfig()
      .setEndpoint("https://tls-cn-beijing.volces.com")
      .setRegion("cn-beijing")
      .setProjectId("project-id")
      .setTopicId("topic-id")
      .setPersistent(true)
      .setPersistentFilePath("/data/user/0/demo/files/tls/producer")
      .setSendThreadCount(4);

  LogProducerClient client = LogProducerClient.forTest(config, bridge, "demo:push");

  assertEquals(1, bridge.createCalls);
  assertTrue(bridge.lastSnapshot.persistentFilePath.contains("demo_push"));
  assertEquals(1, bridge.lastSnapshot.sendThreadCount);
}
```

**Step 2: Run test to verify it fails**

Run:

```bash
cd ve-tls-android-sdk/tls-android-modules
./gradlew :producer-native:testDebugUnitTest --tests "*LogProducerClientBridgeTest"
```

Expected: FAIL because lifecycle logic and test-only injection path do not exist yet.

**Step 3: Write minimal implementation**

```java
final class ConfigSnapshot {
  final String endpoint;
  final String region;
  final String projectId;
  final String topicId;
  final String persistentFilePath;
  final int sendThreadCount;
  final int destroyWaitMs;
}
```

Implementation rules:

- clone the config at client creation time
- rewrite persistent path for non-main process names
- clamp sender count to `1` when persistent is enabled
- expose explicit `updateEndpoint(...)` and `resetSecurityToken(...)`
- reject `addLog(...)` after `destroyLogProducer()`

**Step 4: Run test to verify it passes**

Run the same Gradle command from Step 2.

Expected: PASS.

**Step 5: Commit**

```bash
git -C ve-tls-android-sdk add tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/LogProducerClient.java tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/internal/ConfigSnapshot.java tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/internal/ProcessUtil.java tls-android-modules/producer-native/src/test/java/com/volcengine/tls/android/producer/LogProducerClientBridgeTest.java
git -C ve-tls-android-sdk commit -m "feat: add producer-native lifecycle logic"
```

### Task 6: Implement JNI Lifecycle Bridge and Native Config Mapping

**Files:**
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/internal/JniNativeProducerBridge.java`
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/cpp/tls_producer_jni.cpp`
- Modify: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/cpp/CMakeLists.txt`
- Modify: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/LogProducerClient.java`
- Test: `ve-tls-android-sdk/tls-android-modules/producer-native/src/test/java/com/volcengine/tls/android/producer/NativeApiContractTest.java`

**Step 1: Write the failing contract test**

```java
@Test
public void jniBridge_exposesLifecycleMethods() throws Exception {
  Class<?> bridge = Class.forName("com.volcengine.tls.android.producer.internal.JniNativeProducerBridge");
  bridge.getDeclaredMethod("create", ConfigSnapshot.class, LogProducerCallback.class);
  bridge.getDeclaredMethod("updateEndpoint", long.class, String.class, String.class, String.class);
  bridge.getDeclaredMethod("resetSecurityToken", long.class, String.class, String.class, String.class);
  bridge.getDeclaredMethod("destroyAsync", long.class, int.class);
}
```

**Step 2: Run test to verify it fails**

Run:

```bash
cd ve-tls-android-sdk/tls-android-modules
./gradlew :producer-native:testDebugUnitTest --tests "*NativeApiContractTest"
```

Expected: FAIL because `JniNativeProducerBridge` does not exist.

**Step 3: Write minimal implementation**

```java
final class JniNativeProducerBridge implements NativeProducerBridge {
  static { System.loadLibrary("tls_producer_jni"); }
  private static native long nativeCreate(ConfigSnapshot snapshot, LogProducerCallback callback);
  private static native void nativeUpdateEndpoint(long handle, String endpoint, String region, String topicId);
  private static native void nativeResetSecurityToken(long handle, String ak, String sk, String token);
  private static native void nativeDestroyAsync(long handle, int destroyWaitMs);
}
```

```cpp
JNIEXPORT jlong JNICALL
Java_com_volcengine_tls_android_producer_internal_JniNativeProducerBridge_nativeCreate(
    JNIEnv* env, jclass clazz, jobject snapshot, jobject callback) {
  // Build ve_tls_android_config_view -> ve_tls_config -> ve_tls_producer_create().
}
```

Implementation rules:

- use `ve_tls_android_binding_build_config()`
- call `ve_tls_android_binding_after_create()` after producer creation
- use `ve_tls_producer_update_endpoint()` for runtime endpoint changes
- use `ve_tls_producer_update_static_credentials()` for token reset
- run destroy on a background Java thread and call `ve_tls_android_binding_before_destroy()`

**Step 4: Run test and build to verify it passes**

Run:

```bash
cd ve-tls-android-sdk/tls-android-modules
./gradlew :producer-native:testDebugUnitTest --tests "*NativeApiContractTest"
./gradlew :producer-native:assembleRelease
```

Expected: PASS for the contract test and successful JNI compilation.

**Step 5: Commit**

```bash
git -C ve-tls-android-sdk add tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/internal/JniNativeProducerBridge.java tls-android-modules/producer-native/src/main/cpp/tls_producer_jni.cpp tls-android-modules/producer-native/src/main/cpp/CMakeLists.txt tls-android-modules/producer-native/src/test/java/com/volcengine/tls/android/producer/NativeApiContractTest.java
git -C ve-tls-android-sdk commit -m "feat: add producer-native jni lifecycle bridge"
```

### Task 7: Implement the Internal Java HTTP Bridge and TLS Mapping

**Files:**
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/internal/NativeHttpBridge.java`
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/internal/NativeHttpResponse.java`
- Modify: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/cpp/tls_producer_jni.cpp`
- Modify: `ve-tls-c-sdk/bindings/android/include/ve_tls_android_binding.h`
- Modify: `ve-tls-c-sdk/bindings/android/src/ve_tls_android_binding.c`
- Create: `ve-tls-android-sdk/tls-android-modules/producer-native/src/test/java/com/volcengine/tls/android/producer/NativeHttpBridgeTest.java`
- Test: `ve-tls-android-sdk/tls-android-modules/producer-native/src/test/java/com/volcengine/tls/android/producer/NativeHttpBridgeTest.java`

**Step 1: Write the failing test**

```java
@Test
public void httpBridge_mapsTimeoutsHeadersAndTlsFlags() throws Exception {
  FakeConnection connection = new FakeConnection();
  NativeHttpBridge bridge = new NativeHttpBridge(url -> connection);

  NativeHttpResponse response = bridge.execute(new NativeHttpBridge.Request(
      "POST",
      "https://tls-cn-beijing.volces.com/PutLogs?TopicId=topic-id",
      "User-Agent: tls-producer\r\nx-tls-bodyrawsize: 10",
      new byte[] {1, 2, 3},
      1234,
      5678,
      1,
      1,
      null,
      null,
      "tls-producer"));

  assertEquals(1234, connection.connectTimeout);
  assertEquals(5678, connection.readTimeout);
  assertEquals("tls-producer", connection.requestHeaders.get("User-Agent"));
}
```

**Step 2: Run test to verify it fails**

Run:

```bash
cd ve-tls-android-sdk/tls-android-modules
./gradlew :producer-native:testDebugUnitTest --tests "*NativeHttpBridgeTest"
```

Expected: FAIL because `NativeHttpBridge` does not exist.

**Step 3: Write minimal implementation**

```java
final class NativeHttpBridge {
  interface ConnectionFactory {
    HttpURLConnection open(URL url) throws IOException;
  }

  NativeHttpResponse execute(Request request) throws IOException {
    HttpURLConnection connection = connectionFactory.open(new URL(request.url));
    connection.setConnectTimeout(request.connectTimeoutMs);
    connection.setReadTimeout(request.requestTimeoutMs);
    // apply headers, proxy, TLS options, write body, read response.
  }
}
```

JNI implementation rules:

- store one global `JavaVM*` in `JNI_OnLoad`
- cache `JNIEnv*` per native thread with thread-local storage
- attach on first use and detach on thread exit
- never share `HttpURLConnection` instances across sender threads
- map `tls_verify_peer`, `tls_verify_host`, and `ca_cert_path` explicitly for HTTPS

**Step 4: Run tests and build**

Run:

```bash
cd ve-tls-android-sdk/tls-android-modules
./gradlew :producer-native:testDebugUnitTest --tests "*NativeHttpBridgeTest"
./gradlew :producer-native:assembleRelease
```

Expected: PASS for the unit test and successful native compilation.

**Step 5: Commit**

```bash
git -C ve-tls-android-sdk add tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/internal/NativeHttpBridge.java tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/internal/NativeHttpResponse.java tls-android-modules/producer-native/src/test/java/com/volcengine/tls/android/producer/NativeHttpBridgeTest.java tls-android-modules/producer-native/src/main/cpp/tls_producer_jni.cpp
git -C ve-tls-android-sdk commit -m "feat: add native http bridge"
```

### Task 8: Implement `addLog`, Callback Mapping, and Producer Smoke Tests

**Files:**
- Modify: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/Log.java`
- Modify: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/LogProducerClient.java`
- Modify: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/java/com/volcengine/tls/android/producer/internal/JniNativeProducerBridge.java`
- Modify: `ve-tls-android-sdk/tls-android-modules/producer-native/src/main/cpp/tls_producer_jni.cpp`
- Create: `ve-tls-android-sdk/tls-android-modules/integration-tests/src/test/java/com/volcengine/integration/ProducerNativeApiContractTest.java`
- Create: `ve-tls-android-sdk/tls-android-modules/integration-tests/src/test/java/com/volcengine/integration/ProducerNativeClasspathTest.java`
- Modify: `ve-tls-android-sdk/tls-android-modules/app/src/main/java/com/volcengine/tls/android/demo/MainActivity.java`
- Modify: `ve-tls-android-sdk/tls-android-modules/app/src/main/java/com/volcengine/tls/android/demo/BenchmarkActivity.java`
- Test: `ve-tls-android-sdk/tls-android-modules/integration-tests/src/test/java/com/volcengine/integration/ProducerNativeApiContractTest.java`

**Step 1: Write the failing tests**

```java
@Test
public void producerNativeArtifact_exposesTlsStyleApi() throws Exception {
  Class<?> client = Class.forName("com.volcengine.tls.android.producer.LogProducerClient");
  client.getDeclaredMethod("addLog", Class.forName("com.volcengine.tls.android.producer.Log"));
  client.getDeclaredMethod("destroyLogProducer");
}
```

Add one Java unit test for callback mapping:

```java
@Test
public void dropErrorWith403MapsToAuthError() {
  LogProducerResult result = ResultMapper.fromNative(
      /* result = */ 2,
      /* httpCode = */ 403,
      /* transportKind = */ 0,
      /* transportCode = */ 0,
      "AccessDenied",
      "forbidden",
      "rid-1",
      100,
      80);
  assertEquals(LogProducerResult.Code.AUTH_ERROR, result.getCode());
}
```

**Step 2: Run tests to verify they fail**

Run:

```bash
cd ve-tls-android-sdk/tls-android-modules
./gradlew :producer-native:testDebugUnitTest --tests "*ResultMapperTest"
./gradlew :integration-tests:test --tests "com.volcengine.integration.ProducerNativeApiContractTest"
```

Expected: FAIL because add-log JNI path and result mapping are incomplete.

**Step 3: Write minimal implementation**

```cpp
JNIEXPORT jint JNICALL
Java_com_volcengine_tls_android_producer_internal_JniNativeProducerBridge_nativeAddLog(
    JNIEnv* env, jclass clazz, jlong handle, jlong timeMs,
    jobjectArray keys, jobjectArray values, jint flush) {
  return ve_tls_producer_add_log_with_len_time_parts_hashkey(
      producer, timeMs, hasTimeNs, timeNs, NULL, key_ptrs, key_lens, value_ptrs, value_lens, pair_count, flush);
}
```

Implementation rules:

- phase 1 only passes config-level `hashKey`
- use `ve_tls_producer_set_send_done_v2()` and build `LogProducerResult` in JNI
- support sender-thread callback or main-thread callback via Android `Handler`
- keep `raw_buffer/start_id/end_id` internal only

**Step 4: Run tests and smoke build**

Run:

```bash
cd ve-tls-android-sdk/tls-android-modules
./gradlew :producer-native:testDebugUnitTest
./gradlew :integration-tests:test --tests "com.volcengine.integration.ProducerNativeApiContractTest"
./gradlew :app:assembleDebug
```

Expected: PASS for unit tests, integration tests, and demo app compilation.

**Step 5: Commit**

```bash
git -C ve-tls-android-sdk add tls-android-modules/producer-native/src/main/java tls-android-modules/producer-native/src/main/cpp/tls_producer_jni.cpp tls-android-modules/integration-tests/src/test/java/com/volcengine/integration/ProducerNativeApiContractTest.java tls-android-modules/integration-tests/src/test/java/com/volcengine/integration/ProducerNativeClasspathTest.java tls-android-modules/app/src/main/java/com/volcengine/tls/android/demo/MainActivity.java tls-android-modules/app/src/main/java/com/volcengine/tls/android/demo/BenchmarkActivity.java
git -C ve-tls-android-sdk commit -m "feat: add producer-native add-log path"
```

### Task 9: Retire Legacy Producer Code and Finish Documentation/Publishing

**Files:**
- Delete: `ve-tls-android-sdk/tls-android-modules/producer-lite`
- Modify: `ve-tls-android-sdk/tls-android-modules/settings.gradle`
- Modify: `ve-tls-android-sdk/tls-android-modules/app/build.gradle`
- Modify: `ve-tls-android-sdk/tls-android-modules/full/build.gradle`
- Delete: `ve-tls-android-sdk/tls-android-modules/full/src/main/java/com/volcengine/service/tls/BatchHandler.java`
- Delete: `ve-tls-android-sdk/tls-android-modules/full/src/main/java/com/volcengine/service/tls/LogDispatcher.java`
- Delete: `ve-tls-android-sdk/tls-android-modules/full/src/main/java/com/volcengine/service/tls/Mover.java`
- Delete: `ve-tls-android-sdk/tls-android-modules/full/src/main/java/com/volcengine/service/tls/Producer.java`
- Delete: `ve-tls-android-sdk/tls-android-modules/full/src/main/java/com/volcengine/service/tls/ProducerImpl.java`
- Delete: `ve-tls-android-sdk/tls-android-modules/full/src/main/java/com/volcengine/service/tls/RetryManager.java`
- Delete: `ve-tls-android-sdk/tls-android-modules/full/src/main/java/com/volcengine/service/tls/SendBatchTask.java`
- Delete: `ve-tls-android-sdk/tls-android-modules/full/src/main/java/com/volcengine/model/tls/producer/Attempt.java`
- Delete: `ve-tls-android-sdk/tls-android-modules/full/src/main/java/com/volcengine/model/tls/producer/BatchLog.java`
- Delete: `ve-tls-android-sdk/tls-android-modules/full/src/main/java/com/volcengine/model/tls/producer/CallBack.java`
- Delete: `ve-tls-android-sdk/tls-android-modules/full/src/main/java/com/volcengine/model/tls/producer/ProducerConfig.java`
- Delete: `ve-tls-android-sdk/tls-android-modules/full/src/main/java/com/volcengine/model/tls/producer/Result.java`
- Modify: `ve-tls-android-sdk/tls-android-modules/integration-tests/build.gradle`
- Modify: `ve-tls-android-sdk/README.md`
- Modify: `ve-tls-android-sdk/SDK_USAGE_GUIDE.md`
- Modify: `ve-tls-android-sdk/RELEASE.md`
- Modify: `ve-tls-android-sdk/CHANGELOG.md`
- Test: `ve-tls-android-sdk/tls-android-modules/integration-tests/src/test/java/com/volcengine/integration/ClasspathDiagnosticsTest.java`

**Step 1: Write the failing regression check**

Add one integration test assertion that the legacy producer artifact is gone and the native producer artifact is present in classpath/build outputs.

```java
@Test
public void producerLiteClasses_areAbsent_afterCutover() {
  assertThrows(ClassNotFoundException.class, () ->
      Class.forName("com.volcengine.service.tls.ProducerImpl"));
}
```

**Step 2: Run tests to verify they fail**

Run:

```bash
cd ve-tls-android-sdk/tls-android-modules
./gradlew :integration-tests:test --tests "com.volcengine.integration.ClasspathDiagnosticsTest"
```

Expected: FAIL because legacy classes are still on classpath.

**Step 3: Remove legacy producer code and update docs**

Update docs to reflect:

- `producer-native` is the only formal producer module
- public API is TLS-style and no longer uses `start()/close()/sendLog(Map)`
- `CompressType` default public support is `NONE/LZ4`
- migration requires dependency and API changes

Also update release/publish scripts to publish `producer-native` instead of `producer-lite`.

Concrete cleanup:

- remove `':producer'` from `tls-android-modules/settings.gradle`
- point demo app dependencies at `project(':producer-native')`
- drop `producer-lite` classpath jars from `integration-tests/build.gradle`

**Step 4: Run full verification**

Run:

```bash
cmake -S ve-tls-c-sdk -B /tmp/ve_tls_android_plan_build -DVE_TLS_BUILD_TESTS=ON -DVE_TLS_BUILD_TOOLS=OFF
cmake --build /tmp/ve_tls_android_plan_build --target ve_tls_test_android_binding
ctest --test-dir /tmp/ve_tls_android_plan_build --output-on-failure
cd ve-tls-android-sdk/tls-android-modules
./gradlew :producer-native:assembleRelease :core:assembleRelease :full:assembleRelease :integration-tests:test
```

Expected: PASS for both the C SDK binding tests and the Android module builds/tests.

**Step 5: Commit**

```bash
git -C ve-tls-android-sdk add -A
git -C ve-tls-android-sdk commit -m "refactor: retire java producer and cut over to producer-native"
```

## Manual Verification Checklist

1. Build `:app:assembleDebug`, install the demo APK, and verify `LogProducerClient.addLog()` can send logs successfully with `CompressType.LZ4`.
2. Enable persistent mode, kill the demo process, restart it, and verify recover sends buffered logs from the same persistent directory.
3. Repeat the persistent test after changing the process name suffix and verify the secondary process uses a separate persistent path.
4. Verify that `destroyLogProducer()` returns quickly on the UI thread while pending logs continue graceful shutdown in the background.
5. Verify that changing `endpoint/region/topicId` after restart uses a new persistent directory, not the old one.

## Rollback Notes

- If JNI transport is unstable, stop after Task 5 and keep `producer-native` unpublished.
- If HTTP bridge correctness is unstable, keep `producer-native` as internal-only and do not delete `producer-lite` yet.
- Do not remove `producer-lite` or `full` producer duplicates until Task 8 verification is green.
