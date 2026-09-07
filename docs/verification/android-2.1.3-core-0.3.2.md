# Android 2.1.3 / C Core 0.3.2 verification

Status: runtime compatibility verified; resource matrix pending; not published.

## Inputs

- Android public branch: `feat/producer-native-v2.1.3`, based on `github/master-2.0@c4c71ae829e0bd67901c17465046d819ab454545`.
- Android internal branch: `feat/android-core-v0.3.2`, based on `f5360e840953595f760454b4ecca0ed3fa449507`.
- C Core: `v0.3.2`, immutable commit `1d41ec4edb850ee7dd0b7f63c49738d6a9669c21`.
- Separate clean Core worktree: `../ve-tls-c-sdk-v0.3.2`; original Core checkout preserved.
- Android version: `2.1.3` (unreleased), minSdk 19, NDK 21.4.7075529, JDK 17.
- Defaults preserved: 1 MiB / 1024 logs / 3000 ms / 64 MiB / 1 sender / LZ4.

## Behavior

- Transient transport failures and retryable HTTP failures retain WAL and automatically enter another bounded retry cycle. Memory-mode exhaustion still produces a failure callback.
- JNI calls a cached Java retry classifier. Certificate, peer validation, TLS handshake/protocol, malformed URL, local file, runtime and explicit interruption failures are terminal. Temporary IO errors remain retryable.
- Java validates hashKey before native creation: null/empty or 32 lowercase hex digits, excluding all `f`.
- Authentication retain has no terminal failure callback. Callers must refresh STS before expiry via `resetSecurityToken`; this update does not add a new notification API or automatic STS provider.
- Recovery uses the existing WAL format and Java API. Java `Log` currently carries millisecond timestamps only.
- Loopback cleartext permission is confined to the instrumentation APK and is not packaged into the release AAR.

## Results

| Evidence | Result |
| --- | --- |
| C v0.3.2 macOS Debug + ASan/UBSan | 5/5: basic, Android binding, C++ headers, LZ4 symbols and host consumer |
| Public Android release build | Passed, arm64-v8a / armeabi-v7a / x86 / x86_64 |
| Public Java unit tests | 86 passed, 0 failed, 0 skipped (final follow-up build) |
| Public release lint | Passed |
| Internal release build, lint and Java tests | Passed; 86 unit tests (final follow-up build) |
| API 36.1 emulator instrumentation | 16 passed (final follow-up APK) |
| Public/internal producer-native sources | main, test and androidTest source files byte-identical |
| AAR BuildConfig | SDK 2.1.3 and exact Core commit confirmed using javap |
| AAR manifest | minSdk 19, INTERNET permission; no test network policy |
| API 19 device runtime | Follow-up: 16/16 passed on x86 software emulator, including the URL compatibility fix |
| Real TLS service | Follow-up: API 19 6/6 passed after TLS 1.2 fix; initial API 36.1 6/6 passed |
| New resource benchmark | Incomplete: initial samples discarded; new run blocked by a legacy external credential fixture |

The seven new JNI recovery tests cover cross-cycle retry after a one-attempt budget, buffered/sync authentication retain and credential update, close after three failed requests followed by WAL replay in a new client, memory-mode terminal failure, a malformed URL, and a Java socket read timeout. Both Java error tests require `JavaHttpBridgeError`; the positive timeout assertion (`retryable=true`) proves the cached JNI classifier executes rather than taking its false fallback. Two existing native load/durability tests also pass. Network tests use only a loopback service and fake credentials.

Independent review found and prompted fixes for permanent empty-CA/network-policy errors entering retry, the missing positive JNI classifier test, and pending exceptions escaping JNI diagnostics. Empty-CA integration and wrapped security/network-policy unit regressions pass. The diagnostic error path now clears failures from Java class/method lookup and UTF extraction before returning to the sender.

Release AAR SHA-256 (public build):

```text
99f1900d44f11ad6e60ac2aafe82984b664461d9931f1960729e7272cddf8987
```

## Reproduce

From the Android repository root, use the clean Core v0.3.2 worktree and configure your local JDK/SDK:

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
export VE_TLS_C_SDK_DIR="$(cd ../ve-tls-c-sdk-v0.3.2 && pwd)"
cd tls-android-modules
./gradlew :producer-native:assembleRelease :producer-native:lintRelease :producer-native:testDebugUnitTest
./gradlew :producer-native:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.volcengine.tls.android.producer.ProducerRecoveryInstrumentedTest,com.volcengine.tls.android.producer.ProducerNativeLoadInstrumentedTest
```

The Core worktree must be clean and at the exact pinned commit; the build rejects a drifting adjacent checkout. The emulator must already be running. Device tests do not need `real_tls.properties`.

Local raw logs: `/tmp/android-213-reviewed.log`, `/tmp/android-213-internal-reviewed.log`; XML/HTML reports are under `producer-native/build/test-results`, `build/reports`, and `build/outputs/androidTest-results` within `tls-android-modules`.

Independent re-review found no remaining blocker in the three fixes. The owner confirmed the final reruns above completed successfully after that review scope was defined.

SDK package.xml / Gradle deprecation warnings remain environmental/toolchain warnings. No tag was created and no Maven upload was performed.

## Follow-up Runtime Validation (2026-09-07)

Runtime compatibility and final build/review reconciliation completed. Resource matrix incomplete; no release approval is implied.

- API 36.1: six real HTTPS smoke cases passed, covering memory / buffered WAL / sync WAL with immediate and timer flush. No TLS verification bypass was used.
- The first resource run was deliberately interrupted after review identified incomplete-delivery detection in the old benchmark. Those samples are not the release baseline. The new coverage gate merges successful log-id ranges and rejects missing or invalid coverage.
- Remote API 19: the x86 Android 4.4.2 r06 image boots on the development VM without KVM using one software-emulated CPU and GPU off. The two-CPU SwiftShader attempt exposed adb but did not provide a usable shell.
- Initial API 19 JNI run: 8/9 passed. Malformed IPv6 URL syntax was raised as a plain `IOException` on API 19, causing incorrect retry classification. URI syntax is now checked before connection creation. The new Java regression failed before the fix; final API 19 and API 36.1 JNI/coverage reruns passed 16/16 each.
- Initial API 19 real HTTPS run: 0/6 passed, all failing with `SSLHandshakeException` / `unsupported protocol`. API 19 supports TLS 1.2 but does not enable it by default. The adapter now enables only TLS 1.2 on API 16-19 using the existing socket factory, retaining trust and hostname validation; API 20+ is unchanged. Final API 19 real HTTPS smoke passed 6/6 with no certificate bypass. See the [Android SSLSocket protocol table](https://developer.android.com/reference/javax/net/ssl/SSLSocket).
- Credential provisioning now uses adb stdin into the instrumentation app's private directory, with fixed-byte reads for old adb PTYs and cleanup in `finally`. Legacy external-storage fixtures are rejected by the new runner rather than silently left in use. Timeout/interruption cleanup was confirmed on the test devices.
- The local API 36.1 emulator contains a legacy external fixture from an earlier run. The strict new runner stopped before provisioning; explicit approval to remove that one test file is pending. No complete 20-case performance result exists for this update.

Safe real-validation entry point (run from the repository root after installing the current instrumentation APK):

```bash
python3 android-example/run-producer-native-real-validation.py \
  --config-env /private/path/real_demo.env \
  --adb "$ANDROID_HOME/platform-tools/adb" \
  --serial emulator-5554 \
  --output /tmp/android-213-real-validation \
  --benchmark
```

The optional benchmark uses 120 seconds per case, the last 60 seconds as the steady window, memory / buffered WAL, `tls200` / `tls700` fixed data, and 1/10/100/200/500 logs/s (20 cases). It preserves 1 MiB packets, 1024 logs, 3000 ms, 64 MiB memory budget, one sender, LZ4, and the historical benchmark WAL quota of 32 x 8 MiB / 65536 records. CPU excludes the sampler's measured thread CPU but still includes the test process, Java/JNI transport and workload generation. PSS/RSS are process measurements, not SDK-only heap usage. These are emulator observations, not a physical-device performance guarantee.

Current local evidence: `/tmp/android-213-api19-instrumentation.log`, `/tmp/android-213-api19-url-diagnostic.log`, `/tmp/android-213-api19-real-diagnostic.log`, `/tmp/android-213-real-api36/real-smoke.log`. Remote setup evidence is under `/tmp/tls-api19-validation.RhmXI6`; the test AVD and extracted non-system libraries are temporary validation artifacts, not a production service installation.

Final compatibility evidence: `/tmp/android-213-api19-final.log`, `/tmp/android-213-api36-final.log`, `/tmp/android-213-real-api19-final/real-smoke.log`, `/tmp/android-213-followup-final-build.log`. The remote environment is Android 4.4.2 / API 19 / x86, emulator 37.1.11, `-accel off -cores 1 -gpu off`; absence of `/dev/kvm` remains unchanged. This is actual Android runtime evidence but not physical-device or accelerated performance evidence.

Final owner builds: `/tmp/android-213-final-owner-build.log` and `/tmp/android-213-public-followup-build.log`; both include release AAR, release lint and 86 Java tests. The private runner's eight mock tests also passed. Independent review found no remaining blocker after removing the legacy `addSuppressed` call; the close-IOException regression preserves the original TLS failure. The final release AAR still contains all four ABIs, minSdk 19, SDK 2.1.3 and exact Core SHA, without instrumentation network policy. Changes are prepared on the Android feature branches; no merge, tag creation or Maven publication is part of this delivery.
