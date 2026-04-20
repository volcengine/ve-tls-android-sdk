# Producer Native HTTP Bridge: HttpURLConnection vs OkHttp Cost Inventory

## Conclusion

Keep `HttpURLConnection` for `producer-native`.

This is not just a size choice. It is the cleaner lifecycle boundary for the current producer-only feature set.

`OkHttp` still belongs in the heavier Java SDK path (`core/full`) where shared transport, interceptors, and service-style request plumbing actually exist.

## Scope

This note evaluates the Android producer-only transport choice for:

- `tls-android-modules/producer-native`
- `tls-android-modules/app` `noProvider` release path

It does **not** recommend rewriting `full/core` away from `OkHttp`.

## Current Code Reality

### Producer-only path

- `producer-native` uses `HttpURLConnection` / `HttpsURLConnection` through `NativeHttpBridge`.
- The bridge implements only what producer runtime needs today:
  - HTTP method
  - headers
  - body upload
  - connect/read timeout
  - optional proxy
  - optional permissive TLS flags
  - optional custom CA path internally
- There is a focused JVM test for this bridge:
  - `tls-android-modules/producer-native/src/test/java/com/volcengine/tls/android/producer/NativeHttpBridgeTest.java`

### Heavy Java SDK path

- `core` still brings:
  - `com.squareup.okhttp3:okhttp:3.12.13`
  - `com.squareup.okio:okio:1.17.5`
- `full` builds service-style TLS clients on top of `BaseServiceImpl` / `TLSHttpUtil` and therefore naturally sits on the heavier `OkHttp` transport stack.
- In this repo, there is no `src/test/java` coverage under `tls-android-modules/core` or `tls-android-modules/full` today.

## Fresh Evidence

### Runtime dependency evidence

Command:

```bash
cd tls-android-modules
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon :app:dependencies --configuration noProviderReleaseRuntimeClasspath
```

Result:

```text
noProviderReleaseRuntimeClasspath
+--- net.jpountz.lz4:lz4:1.3.0
\--- project :producer-native
```

This is the correct producer-only runtime shape. Reintroducing `core` here would also reintroduce `OkHttp/Okio` and the wider Java transport/protobuf stack.

### APK evidence

Commands:

```bash
cd tls-android-modules
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon :app-no-sdk:assembleNoProviderRelease :app:assembleNoProviderRelease
stat -c "%s %n" \
  app-no-sdk/build/outputs/apk/noProvider/release/app-no-sdk-noProvider-release.apk \
  app/build/outputs/apk/noProvider/release/app-noProvider-release.apk
```

Result:

- baseline `app-no-sdk-noProvider-release.apk`: `45105 B`
- producer app `app-noProvider-release.apk`: `266149 B`
- current release APK delta: `221044 B`

This is already in the same order of magnitude as the SLS documentation screenshot the user provided earlier.

### AAR evidence

Command:

```bash
cd ve-tls-android-sdk
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/size/measure_producer_native_vs_sls_maven.sh
```

Result:

```text
metric,current,benchmark
aar_compressed_bytes,241646,305697
arm64_so_bytes,94472,176008
zip_total_bytes,415888,669581
jni_total_bytes,378576,633500
exported_symbol_count,6,320
```

This matters because it proves the producer-native artifact is already smaller than the published SLS Maven benchmark **without** moving producer transport onto `OkHttp`.

## Lifecycle Cost Comparison

### 1. Size and dependency surface

**HttpURLConnection**
- Keeps producer-only path independent from `core`.
- Avoids dragging `OkHttp/Okio` and the heavier Java SDK runtime into producer-only apps.
- Matches the current successful APK and AAR size profile.

**OkHttp**
- Convenient in the heavy Java SDK path where shared client configuration, request signing, and service abstractions already exist.
- Wrong default for producer-native because it couples producer size to unrelated Java SDK runtime concerns.

Verdict: `HttpURLConnection` wins for `producer-native`.

### 2. Development complexity

**HttpURLConnection**
- The bridge is a single small transport shim.
- The feature set is bounded and explicit.
- Custom logic exists for headers/body/TLS/proxy handling, so some handwritten code must be maintained.

**OkHttp**
- Less handwritten transport plumbing.
- But the abstraction surface is much broader than producer runtime actually needs.
- Moving producer onto `OkHttp` would re-couple it to `core`-style transport concerns.

Verdict: `HttpURLConnection` is cheaper as long as the bridge remains small and scoped.

### 3. Testing burden

**HttpURLConnection**
- Current repo state already has a focused bridge unit test.
- Transport behavior can be verified with fake `HttpsURLConnection` objects and does not require the rest of the Java SDK stack.

**OkHttp**
- More mature ecosystem support exists in general.
- But in this repo the heavier `core/full` transport path currently has no matching JVM test tree, so switching producer back onto `OkHttp` would not automatically buy better repo-local confidence.

Verdict: current repo-local test economics still favor the small dedicated bridge.

### 4. Security and audit burden

**HttpURLConnection**
- Uses the platform HTTP/TLS stack by default.
- Fewer vendored transport dependencies in the producer-only artifact.
- Audit burden moves to the custom bridge code for:
  - permissive TLS branches
  - custom CA handling
  - header/body handling
  - timeout/proxy wiring

**OkHttp**
- Adds a separately versioned third-party transport dependency to maintain.
- Can centralize some TLS and proxy behavior if the whole SDK shares it.
- But for producer-native it would import more code and more upgrade surface than the current use case needs.

Verdict: neither option is free; for producer-native the smaller custom bridge is the lower-total-cost choice today.

### 5. Feature fit

Producer-native currently needs a narrow transport contract. It does not need:

- general REST client abstraction
- interceptors
- broad request composition APIs
- shared service runtime
- protobuf-carrying Java transport objects

That is exactly why `HttpURLConnection` fits here and `OkHttp` fits `core/full`.

## Decision

- Keep `HttpURLConnection` in `producer-native`.
- Keep `OkHttp` in the heavier Java SDK path.
- Do **not** treat transport unification as a goal by itself.

## Follow-ups

- Keep `NativeHttpBridgeTest` healthy and extend it when bridge capabilities grow.
- Document that custom CA / TLS verify controls are internal-only today for producer public API.
- Reopen the transport choice only if producer-native begins to require features that genuinely exceed the current bridge scope.
