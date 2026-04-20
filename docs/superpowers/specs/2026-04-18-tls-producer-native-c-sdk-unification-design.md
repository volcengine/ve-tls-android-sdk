# TLS Android Producer Native Re-architecture Design

Date: 2026-04-18
Status: Revised after architecture review
Scope: `ve-tls-android-sdk` + `ve-tls-c-sdk`

## 1. Summary

This design replaces the current Java-based Android producer implementation with a C SDK based producer/persistent implementation.

The target state is:

- Android producer uses a single native core from `ve-tls-c-sdk`
- Android only keeps a thin platform facade and JNI bridge
- Producer behavior aligns with the mature SLS Android producer where that behavior has already been validated in production
- Public Android API keeps TLS naming and TLS resource identity semantics such as `projectId` and `topicId`
- The same C producer core becomes the foundation for future iOS producer SDK work

This design does **not** preserve compatibility with the current Volcengine Java producer API. Migration guidance will be provided instead.

## 2. Goals

- Remove Java producer engine code and its third-party dependency burden
- Reuse `ve-tls-c-sdk` persistent branch for producer + persistent + recover capability
- Reduce Android SDK package size and dependency conflict risk
- Keep public Android producer API in TLS style
- Align producer behavior with SLS where the SLS behavior is already a mature result
- Enable Android and iOS producer SDKs to share one native core

## 3. Non-goals

- This phase does not move the full synchronous management/client API into native
- This phase does not keep binary or source compatibility with the old Java producer API
- This phase does not finalize the server protocol for context encoding
- This phase does not promise `armeabi` as a default mainline ABI; it is kept as a legacy reservation point only

## 4. Current State

### 4.1 Android SDK

Current producer implementation in `ve-tls-android-sdk` is still Java based:

- `tls-android-modules/producer-lite` contains a Java producer facade and Java async producer engine
- `tls-android-modules/full` duplicates producer engine classes again
- current producer behavior is memory queue + retry only, without persistent/recover
- producer-related Java dependencies indirectly bring `okhttp`, `okio`, `protobuf-javalite`, and in `full` also `fastjson` and `guava`

The branch already references `producer-native` in docs and Gradle wiring, but the module does not actually exist in the checkout. This is an unfinished transition state that must be corrected.

### 4.2 C SDK

`ve-tls-c-sdk` persistent branch already contains:

- producer lifecycle and ingestion APIs
- aggregation, batching, compression, sending, retry, metrics
- persistent append/recover/ack/reclaim logic
- checkpoint, lease, stale takeover, snapshot, sender pipeline
- platform abstraction and HTTP abstraction

The missing part is not producer capability itself, but mobile delivery shape:

- Android/iOS binding layer
- JNI/ObjC bridge
- mobile build outputs and ABI packaging

### 4.3 SLS Reference Baseline

SLS Android producer establishes a proven shape:

- Java API + JNI + native core
- persistent path rewrite for multi-process apps
- callback thread mode switching
- bounded graceful destroy through destroy wait configuration
- persistent mode forcing single sender thread

These behaviors are treated as the default producer behavior baseline unless TLS resource model or platform reality requires an explicit divergence.

## 5. Chosen Direction

Chosen option: thin platform facade + unified C native core.

Rules:

- public producer API is TLS-style, not SLS naming-style
- producer behavior is SLS-grade where applicable
- one native producer core is shared by Android and future iOS
- Android producer becomes `producer-native` as the only formal producer module
- old Java producer implementation exits instead of coexisting long-term

## 6. Target Architecture

### 6.1 Layering

#### Layer A: `ve-tls-c-sdk/core`

The only producer/persistent engine.

Responsibilities:

- add log
- aggregation and batching
- compression
- send and retry
- persistent, recover, checkpoint, lease, reclaim
- metrics and error details

It remains platform-neutral and exposes only stable public C interfaces plus platform abstraction hooks.

#### Layer B: `ve-tls-c-sdk/bindings`

New binding layer to prevent Android/iOS from depending on private producer internals.

Planned structure:

- `bindings/android`
- `bindings/ios`
- optional `bindings/common`

Responsibilities:

- map Java/ObjC config snapshots to `ve_tls_config`
- own binding-stable create/update/destroy entry points
- own platform-specific callback dispatch
- own platform HTTP adapter glue
- provide extension entry points such as future context encoding support

#### Layer C: `ve-tls-android-sdk/tls-android-modules/producer-native`

The only formal Android producer module after migration.

Responsibilities:

- expose TLS-style Android producer API
- own JNI bridge
- own Android-specific path and callback thread handling
- package final per-ABI shared libraries in one AAR

It does not implement producer logic itself.

#### Layer D: `ve-tls-android-sdk` remaining `core/full`

Kept for non-producer APIs only.

This design intentionally does not migrate all synchronous client/management APIs to native in this phase.

## 7. Public Android API Design

### 7.1 Naming Policy

Public API uses TLS terminology and TLS resource identity.

Examples:

- `projectId`
- `topicId`
- `region`
- `hashKey`

Not exposed as formal public names:

- `project`
- `logstore`

### 7.2 Package and Class Shape

Assumption for this phase:

- public package remains `com.volcengine.tls.android.producer`

Formal public classes:

- `LogProducerConfig`
- `LogProducerClient`
- `Log`
- `LogProducerCallback`
- `LogProducerResult`

Supporting value types may be nested under the main public classes, for example `LogProducerConfig.CompressType` and `LogProducerResult.Code`.

### 7.3 `LogProducerConfig`

`LogProducerConfig` is a Java-side mirror object, not a native config handle.

Design rules:

- it is mutable before `LogProducerClient` creation
- `LogProducerClient` clones and freezes a config snapshot at construction time
- mutating `LogProducerConfig` after client creation does not affect an existing producer
- runtime changes use explicit client methods, not implicit config mutation

Required constructor coverage:

- no-arg
- `Context`
- `endpoint, region, projectId, topicId`
- `endpoint, region, projectId, topicId, accessKeyId, accessKeySecret`
- `endpoint, region, projectId, topicId, accessKeyId, accessKeySecret, securityToken`
- `Context` variants of the above

Core setters for phase 1:

- `setEndpoint`
- `setRegion`
- `setProjectId`
- `setTopicId`
- `setAccessKeyId`
- `setAccessKeySecret`
- `setSecurityToken`
- `setHashKey`
- `addTag`
- `setSource`
- `setPacketLogBytes`
- `setPacketLogCount`
- `setPacketTimeoutMs`
- `setMaxBufferLimit`
- `setSendThreadCount`
- `setRetryCount`
- `setPersistent`
- `setPersistentFilePath`
- `setPersistentForceFlush`
- `setPersistentMaxFileCount`
- `setPersistentMaxFileSize`
- `setPersistentMaxLogCount`
- `setConnectTimeoutMs`
- `setRequestTimeoutMs`
- `setDestroyWaitMs`
- `setCompressType(CompressType)`
- `setEnableTimeNs`
- `setCallbackFromSenderThread`
- `isValid`
- `isEnabled`

Phase-1 `CompressType` formal values:

- `NONE`
- `LZ4`

Phase-1 public `CompressType` only exposes `NONE` and `LZ4`; optional non-default compression remains disabled in the default Android build for package-size reasons.

Not part of the phase-1 public config API:

- SLS-style `setNtpTimeOffset`
- `setMaxLogDelayTime`
- `setDropDelayLog`
- `setDropUnauthorizedLog`

These knobs do not have a clean `ve-tls-c-sdk` equivalent yet. They stay outside the formal phase-1 contract until the native semantics are defined in C SDK.

### 7.4 `LogProducerClient`

Formal lifecycle:

- `new LogProducerClient(config)`
- `new LogProducerClient(config, callback)`
- `addLog(Log)`
- `addLog(Log, int flush)`
- `updateEndpoint(String endpoint, String region, String topicId)`
- `resetSecurityToken(String accessKeyId, String accessKeySecret, String securityToken)`
- `destroyLogProducer()`

Not part of the new formal API:

- Java-engine style `start()`
- `closeNow()`
- `reconfig()`
- public `addLogRaw(...)` in phase 1

Raw-buffer ingestion remains an internal/future capability because the current C SDK raw API is buffer-oriented rather than SLS-style key/value-array oriented.

Per-log `hashKey` override is also not part of the phase-1 public API. `hashKey` is config-level only in phase 1 even though the native core supports per-record hash keys internally.

### 7.5 `Log`

Supported model:

- `putContent(...)`
- `putContents(...)`
- `getContent()`
- `getLogTime()`

### 7.6 Context Encoding Extension Reservation

Context encoding is reserved but not finalized in this phase.

Design rule:

- keep API and JNI extension points
- do not lock Android/iOS into a not-yet-final server protocol
- implement formal protocol support in C SDK after server definition is stable

## 8. Config Mapping Rules

### 8.1 Mirror-and-Freeze Model

`LogProducerConfig` does not map to a long-lived native config object.

Creation flow:

1. Java builds a mutable `LogProducerConfig`
2. `LogProducerClient` copies it into an immutable creation snapshot
3. JNI materializes a stack/local `ve_tls_config`, calls `ve_tls_config_init()`, fills fields, and creates `ve_tls_producer`
4. only explicitly supported runtime updates may change the live producer after creation

This avoids the incorrect setter-style assumption and matches the actual `ve-tls-c-sdk` API shape.

### 8.2 Mapping and Runtime Update Matrix

| Public input | Java unit/type | Create-time mapping | Runtime path | Notes |
| --- | --- | --- | --- | --- |
| `endpoint` | string | `endpoint` | `LogProducerClient.updateEndpoint()` -> `ve_tls_producer_update_endpoint()` | update is explicit, not implied by config mutation |
| `region` | string | `region` | `LogProducerClient.updateEndpoint()` -> `ve_tls_producer_update_endpoint()` | same as above |
| `topicId` | string | `topic_id` | `LogProducerClient.updateEndpoint()` -> `ve_tls_producer_update_endpoint()` | same as above |
| `projectId` | string | `project_id` | recreate only | C SDK has no runtime update API for this field |
| `accessKeyId/accessKeySecret/securityToken` | string | `access_key_id/access_key_secret/security_token` | `LogProducerClient.resetSecurityToken()` -> `ve_tls_producer_update_static_credentials()` | callback/provider mode is reserved for a later phase |
| `compressType` | enum | `compress_type` | recreate only | phase-1 public enum is `NONE/LZ4`, mapping to `"none" / "lz4"` |
| `packetLogBytes` | bytes/int | `log_bytes_per_package` | recreate only | no unit conversion |
| `packetLogCount` | count/int | `log_count_per_package` | recreate only | no unit conversion |
| `packetTimeoutMs` | ms/int | `flush_interval_ms` | recreate only | no unit conversion |
| `maxBufferLimit` | bytes/int | `max_buffer_bytes` | recreate only | no unit conversion |
| `sendThreadCount` | count/int | `send_thread_count` | recreate only | if persistent is enabled, Android binding clamps this to `1` before create |
| `retryCount` | count/int | `retry_max_attempts` | recreate only | fine-grained `retry_policy` stays internal in phase 1 |
| `persistent` | bool/int | `use_persistent` | recreate only | binding calls `ve_tls_producer_recover()` immediately after create when enabled |
| `persistentFilePath` | string | `persistent_file_path` | recreate only | may be rewritten by Android multi-process logic |
| `persistentMaxLogCount` | count/int | `max_persistent_log_count` | recreate only | no unit conversion |
| `persistentMaxFileSize` | bytes/int | `max_persistent_file_size` | recreate only | no unit conversion |
| `persistentMaxFileCount` | count/int | `max_persistent_file_count` | recreate only | no unit conversion |
| `persistentForceFlush` | bool/int | `force_flush_disk` | recreate only | no unit conversion |
| `connectTimeoutMs` | ms/int | `connect_timeout_ms` | recreate only | no unit conversion |
| `requestTimeoutMs` | ms/int | `request_timeout_ms` | recreate only | no unit conversion |
| `destroyWaitMs` | ms/int | Android binding destroy timeout | n/a | not a `ve_tls_config` field; used for `ve_tls_producer_close(timeout_ms)` |
| `source` | string | `source` | recreate only | no unit conversion |
| `hashKey` | string | `hash_key` | recreate only | phase-1 public API treats `hashKey` as config-level only |
| `addTag` | string pairs | `log_tags` + `log_tag_count` | recreate only | JNI duplicates tag arrays into native-owned memory for create |
| `enableTimeNs` | bool/int | `enable_time_ns` | recreate only | no unit conversion |
| `callbackFromSenderThread` | bool | Android facade callback mode | n/a | not a C config field |

Android facade validates inputs and performs type/name conversion. It must not duplicate batching, retry, or persistent logic already present in the C SDK.

### 8.3 Public Config Layering

The native config is much richer than the Android phase-1 public surface. The public contract is split into three layers:

- Core public: target identity, credentials, batching, compression, timeout, persistent, callback mode
- TLS advanced public: optional future knobs such as buffer-full policy, rate limit, breaker, ordered-send, send-queue policy
- Internal only: `platform`, `http_client`, `use_global_env`, `pack_thread_count`, `agg_strategy`, TLS verification internals, metrics sink, native credentials provider, raw-buffer import/export helpers

This keeps the first public API focused while leaving room to expose more `ve-tls-c-sdk` capabilities later without redesigning the core boundary.

## 9. Behavior Baseline

### 9.1 Principle

TLS determines interface language.  
SLS determines mature producer behavior baseline.  
C SDK determines final implementation.

### 9.2 Expected Behavior

- asynchronous write path
- aggregation and compression
- default LZ4 behavior unless explicitly changed
- persistent at-least-once semantics
- automatic recover initiated by the binding immediately after producer creation when persistent is enabled
- callback thread mode switching
- persistent mode forcing single sender by Android binding normalization
- multi-process persistent path isolation
- bounded graceful destroy

### 9.3 Explicit Phase-1 Divergences

- destroy wait uses one TLS-style `destroyWaitMs`, not SLS-style separate flusher/sender wait knobs, because `ve-tls-c-sdk` exposes one `ve_tls_producer_close(timeout_ms)` boundary
- public raw-buffer ingestion is deferred; the C SDK raw API is kept as an internal/future path
- SLS-only NTP/delay-log/drop-unauthorized config knobs are not part of phase 1 until equivalent native semantics exist
- per-log `hashKey` override is deferred; phase-1 public API only exposes config-level `hashKey`

### 9.4 Persistent Target Consistency Rule

Recovered records restore persisted payload and persisted per-record hash key, but they do not restore a historic target endpoint/topic identity snapshot for re-send.

Formal rule:

- auto-recover sends recovered records using the current producer config active at recovery time
- if `endpoint`, `region`, `projectId`, or `topicId` has changed since those records were persisted, recovered data may be delivered to the new target
- applications must keep target identity stable across process restarts for a reused persistent directory
- if target identity must change, applications must use a new persistent directory or explicitly clear/retire the old persistent data before creating the new producer

## 10. JNI Boundary Design

JNI should stay small and stable.

Three groups of native entry points:

### 10.1 Producer Creation and Runtime Update

- create producer from one Java config snapshot
- update endpoint/region/topicId explicitly
- update static credentials explicitly
- destroy producer

### 10.2 Log Ingestion

- add structured log
- reserve raw-buffer import/export path for future/internal use
- reserved extended add-log path for future context encoding

### 10.3 Runtime Controls

- destroy/close coordination
- optional future flush/recover/metrics bridge if promoted to public API

Design rule:

- Java never touches private C producer internals
- JNI only bridges stable binding APIs
- private native implementation details remain hidden behind the binding layer

## 11. Android Platform-specific Adaptation

Responsibilities kept in Android facade/binding only:

### 11.1 Context and Default Directories

- obtain app-private storage path from Android `Context`
- generate persistent base directory under app-private files

### 11.1.1 Android Platform Override Reservation

The Android binding must be allowed to override selected `ve_tls_platform` callbacks instead of assuming the default POSIX adapter is always sufficient.

Phase-1 reservation points:

- `file_fsync`
- `path_mkdirs`
- `path_stat`
- `path_remove`
- `path_rename`

Why this reservation is explicit in the spec:

- low-end Android devices can show very high `fsync` latency, which directly affects persistent mode when `force_flush_disk=1`
- Android storage behavior can differ across API levels and scoped-storage environments, even when app-private directories remain the primary target
- future Android-specific throttling, batching, or fsync mitigation may need to live in the platform adapter rather than in the generic producer core

This does not force a phase-1 custom platform implementation, but it makes Android platform adaptation an explicit part of the architecture boundary rather than an afterthought.

### 11.2 Multi-process Persistent Path Rewrite

If current process is not main process, persistent path is rewritten to a process-specific subdirectory, matching the SLS practical behavior.

### 11.3 Callback Thread Mode

- sender-thread callback when enabled
- main-thread callback through Android looper/handler when disabled

### 11.4 HTTP Adapter

`ve_tls_http_client` is implemented by the Android binding, not by linking `libcurl` or bringing back OkHttp.

Binding design:

- C side provides a `ve_tls_http_client` whose `do_request` forwards through JNI
- Java side provides an internal `NativeHttpBridge` implemented with `HttpURLConnection/HttpsURLConnection`
- request fields forwarded include URL, headers, body, timeout, proxy, TLS verification flags, and user-agent
- response fields copied back include HTTP code, response body, request ID, error code, and error message
- `free_response` only releases native-owned copies created by the JNI bridge

This preserves the "no extra third-party network stack" goal while satisfying the C SDK's HTTP abstraction contract.

#### 11.4.1 JNI Thread Attachment Model

The HTTP bridge must treat native sender threads as long-lived JNI callers.

Required design rules:

- keep one global `JavaVM *` from `JNI_OnLoad`
- cache attached `JNIEnv *` per native thread through thread-local storage
- attach a sender thread on first JNI use, not on every request
- detach automatically when that native thread exits
- cache `jclass`/`jmethodID` lookups as global references instead of resolving them on every HTTP call

This follows the same practical direction as the SLS Android producer and avoids repeated attach/detach overhead on the hot send path.

#### 11.4.2 Connection Isolation and Concurrency Rules

`HttpURLConnection` instances are never shared across requests or sender threads.

Rules:

- each HTTP request creates and owns one fresh `HttpURLConnection` or `HttpsURLConnection`
- no connection object is reused across concurrent native sender threads
- persistent mode still clamps sender count to `1`, but non-persistent mode must remain correct when multiple sender threads are enabled
- request body write, response read, and connection teardown all happen within that one request scope

This avoids relying on thread-safety properties that `HttpURLConnection` does not provide at the object level.

#### 11.4.3 TLS, Certificate, and Timeout Mapping

Android HTTP binding must explicitly map the C SDK transport fields that phase-1 exposes, rather than treating them as advisory only.

Required mappings:

- `connect_timeout_ms -> HttpURLConnection.setConnectTimeout()`
- `request_timeout_ms -> HttpURLConnection.setReadTimeout()`
- `user_agent -> User-Agent` request header
- `proxy -> java.net.Proxy` when configured

HTTPS handling rules for phase-1:

- use the platform default trust manager and hostname verifier
- do not expose `tls_verify_peer`, `tls_verify_host`, or `ca_cert_path` in the Android public API for phase-1
- if a later phase exposes custom CA or permissive verification overrides, keep them request-scoped and explicit rather than making them default transport behavior

Security note:

- if peer or host verification overrides are exposed in a later phase, document them as unsafe compatibility/debug paths rather than default production settings
- permissive TLS behavior must never become the default path just because Android custom CA loading is harder to implement

### 11.5 Library Loading

Android users load one formal producer shared library only.

### 11.6 Behavior Ownership Matrix

| Behavior | Owner | Rule |
| --- | --- | --- |
| callback thread switching | Android facade | implemented with Android looper/handler dispatch |
| selected file/path callbacks | Android platform binding | may override `ve_tls_platform` callbacks where Android storage behavior requires it |
| multi-process persistent path rewrite | Android facade | process-specific subdirectory rewrite before create |
| persistent single-sender rule | Android facade | clamp `sendThreadCount` to `1` when persistent is enabled |
| auto-recover | Android binding + C SDK | binding explicitly calls `ve_tls_producer_recover()` after create when persistent is enabled |
| HTTP transport | Android binding + internal Java HTTP bridge | `HttpURLConnection/HttpsURLConnection`, no third-party transport |
| raw-buffer callback details (`raw_buffer`, `start_id`, `end_id`) | binding internal path | retained internally, not in phase-1 public callback |
| NTP offset / delay-log / drop-unauthorized policies | not public in phase 1 | re-evaluate only after native semantics are added |

### 11.7 Destroy Behavior

`destroyLogProducer()`:

- immediately stops accepting new logs
- returns asynchronously from Java to avoid ANR
- triggers background `ve_tls_producer_close(destroyWaitMs)` first
- always follows with `ve_tls_producer_destroy()`
- native shutdown then finishes even if timeout is hit

This explicitly follows the practical SLS destroy model rather than a weakened fire-and-forget model, while mapping onto the actual two-stage C SDK API.

## 12. Build, ABI, and Packaging

### 12.1 Core Packaging Strategy

- `ve-tls-c-sdk` produces static core library inputs for Android integration
- final Android-facing artifact is one bridge/shared library per ABI
- host app sees one AAR and one final producer shared library name

Decision:

- internal integration = static
- external distribution = single final shared library

### 12.2 Build System

Use AGP + CMake `externalNativeBuild(cmake)`.

Reasons:

- current C SDK is already CMake-based
- easier source reuse across Android and iOS
- better fit than introducing new long-term ndk-build dependence

Concrete integration shape:

- `producer-native/src/main/cpp/CMakeLists.txt` is the Android module entry point
- it adds `ve-tls-c-sdk` as a CMake subdirectory with Android-specific options:
- `VE_TLS_ENABLE_CURL=OFF`
- `VE_TLS_BUILD_TESTS=OFF`
- `VE_TLS_BUILD_TOOLS=OFF`
- `VE_TLS_ENABLE_LZ4=ON`
- optional non-default compression remains disabled in the default Android build
- Android binding sources and JNI sources are compiled into one final shared library target, recommended name `tls_producer_jni`
- the final shared library links `ve_tls_core`
- LZ4 is consumed from `ve-tls-c-sdk`'s existing third-party source, not duplicated in the Android module

Public API consequence:

- the default published Android artifact formally supports `CompressType.NONE` and `CompressType.LZ4`
- the phase-1 default public contract only exposes `CompressType.NONE` and `CompressType.LZ4`; any later expansion must be defined and documented separately

### 12.3 ABI Strategy

Mainline ABI target set:

- `armeabi-v7a`
- `arm64-v8a`
- `x86`
- `x86_64`

Legacy reservation:

- `armeabi`

Rule:

- `armeabi` is not part of default mainline packaging unless explicitly re-enabled after legacy toolchain review
- it remains a reserved compatibility point because the user requested that SLS compatibility surface be respected as much as practical

## 13. Package Size Strategy

- producer-native must not depend on Java `core` networking stack
- only producer-relevant C sources are linked into the final native artifact
- tools, demos, and tests are excluded from mobile artifacts
- symbol visibility is minimized so only JNI-required exports remain
- unnecessary compression variants are not compiled by default
- release builds use strip and standard shrink steps

## 14. Repository-level Changes

### 14.1 `ve-tls-c-sdk`

Keep:

- core producer/persistent code
- platform and HTTP abstraction

Add:

- `bindings/android`
- `bindings/ios`
- optional `bindings/common`

Do not allow:

- mobile platforms depending on `core/src/producer/*` private headers directly

### 14.2 `ve-tls-android-sdk`

Add:

- `tls-android-modules/producer-native`

Delete:

- `tls-android-modules/producer-lite`
- producer engine classes duplicated in `full`
- producer models and producer-only Java request/send helpers in `full`

Keep:

- non-producer `core`
- non-producer parts of `full`

Repair:

- README
- `settings.gradle`
- integration tests
- publish scripts
- samples

Add internal Android binding helpers:

- internal Java HTTP bridge classes under `producer-native`
- JNI HTTP adapter sources under `producer-native/src/main/cpp`

## 15. Error Model and Callback Model

### 15.1 Error Model

Android public model stays stable and simple:

- public immutable result object through `LogProducerResult`
- JNI maps native result + native error details into stable TLS result codes and structured fields
- private native error surface is not leaked directly

Type shape:

- `LogProducerResult` is an immutable class
- `LogProducerResult.Code` is a nested enum for stable result categories

`LogProducerResult` carries at least:

- `code`
- `requestId`
- `errorCode`
- `errorMessage`
- `httpCode`
- `transportKind`
- `transportCode`
- `logBytes`
- `compressedBytes`

Recommended `LogProducerResult.Code` values:

- `OK`
- `INVALID`
- `DROP_ERROR`
- `PERSISTENT_ERROR`
- `CLOSED`
- `TIMEOUT`
- `AUTH_ERROR`
- `NETWORK_ERROR`
- `SERVER_ERROR`
- `UNKNOWN_ERROR`

Mapping rule:

- `VE_TLS_OK -> OK`
- `VE_TLS_INVALID -> INVALID`
- `VE_TLS_PERSISTENT_ERROR -> PERSISTENT_ERROR`
- `VE_TLS_CLOSED -> CLOSED`
- `VE_TLS_TIMEOUT -> TIMEOUT`
- `VE_TLS_DROP_ERROR` first checks detailed native error:
  - HTTP `401/403 -> AUTH_ERROR`
  - transport-level failure or no HTTP status -> `NETWORK_ERROR`
  - other HTTP failure -> `SERVER_ERROR`
  - otherwise -> `DROP_ERROR`

### 15.2 Callback Contract

Stable callback shape:

- `onCompletion(LogProducerResult result)`

Thread mode:

- sender thread direct callback when enabled
- main thread callback when disabled

Internal native callback data such as `raw_buffer`, `start_id`, and `end_id` remains available inside the binding layer for future advanced APIs, but is not part of the phase-1 public callback contract.

### 15.3 Lifecycle Contract

- create config
- create client
- add logs
- destroy client

No long-lived Java producer engine state machine remains outside native core.

## 16. Migration Strategy

- `producer-native` becomes the only evolving producer module
- `producer-lite` stops evolving and is retired after migration notice
- migration guidance covers dependency change, API shape change, and behavior differences
- old users either migrate to the new native producer or stay on the old branch knowingly

This follows the user’s explicit preference: adapt to the new path or keep the old path, but do not drag both forward equally.

## 17. Validation Strategy

### 17.1 C SDK Validation

- persistent/recover/checkpoint/lease/reclaim tests
- sender/retry/timeout/credential update tests
- buffer-full and overflow-policy tests
- hash-key and concurrency tests

### 17.2 Android JNI Validation

- config mapping tests
- runtime update matrix tests
- result-code mapping tests
- callback thread mode tests
- multi-process path tests
- HTTP bridge tests
- bounded destroy tests
- ABI smoke tests

### 17.3 Android Integration Validation

- device/emulator verification across supported ABIs
- persistent on/off behavior
- process restart recovery
- minimal integration without bringing `core/full`

### 17.4 Regression Baseline

- behavior baseline against SLS producer semantics
- public interface baseline against TLS naming/resource semantics

## 18. Implementation Sequence

1. Define mobile binding API in `ve-tls-c-sdk`
2. Implement Android HTTP adapter and JNI transport glue
3. Reserve context-encoding extension path
4. Implement Android `producer-native` main flow
5. Align key runtime behaviors with SLS baseline
6. Remove Java producer implementations
7. Repair docs/tests/publish wiring
8. Publish new AAR and migration guide
9. Reuse the same binding boundary for iOS

## 19. Key Risks and Mitigations

### 19.1 Highest Risks

- incorrect JNI-to-C config mapping
- HTTP bridge correctness and performance
- destroy/persistent behavior drift from SLS baseline
- ABI packaging or library loading issues

Mitigation:

- make mapping explicit and testable
- make lifecycle behavior part of integration test baseline
- validate packaging per ABI early

### 19.2 Medium Risks

- callback thread switching issues
- credential refresh/update edge cases
- legacy `armeabi` maintenance cost

### 19.3 Lower Risks

- sample/doc/publish migration errors

## 20. Final Decision Summary

- one producer core: `ve-tls-c-sdk`
- Android producer facade only, no Java producer engine
- TLS public naming, SLS mature behavior baseline
- internal static integration, external single final shared library in one AAR
- `producer-native` becomes the formal producer module
- context encoding stays reserved for forward-compatible future support

## 21. Review Resolution Matrix

The following architecture review items were resolved into this spec revision.

| Review item | Resolution |
| --- | --- |
| `R-01` config model mismatch | adopted: Java mirror + freeze-at-create + runtime update matrix |
| `R-02` `compressType` string mismatch | adopted: Java enum maps to native strings |
| `R-03` destroy lifecycle mismatch | adopted: background `close(timeout)` then `destroy()` |
| `R-04` missing HTTP plan | adopted: JNI + internal Java `HttpURLConnection` bridge |
| `R-05` `addLogRaw` mismatch | adopted: not public in phase 1 |
| `I-01` TLS result model undefined | adopted: `LogProducerResult` becomes TLS-owned structured result |
| `I-02` callback payload mismatch | partially adopted: internal path retained, public callback stays simple |
| `I-03` Android-specific SLS behaviors unassigned | adopted: ownership matrix added |
| `I-04` config layering missing | adopted: core/advanced/internal layering added |
| `I-05` `setLogTopic` mismatch | adopted: removed from formal API |
| `G-01` Android platform adaptation | adopted: platform override reservation and ownership added |
| `G-02` credentials provider | partially adopted: phase 1 uses `update_static_credentials`, provider reserved |
| `G-03` log template optimization | deferred: not blocking phase-1 architecture |
| `G-04` public flush/recover | partially adopted: binding auto-recover after create, flush/recover not public in phase 1 |
| `G-05` build integration gap | adopted: CMake and final shared-library shape added |
| `G-06` endpoint update mapping | adopted: explicit client API + runtime matrix |
| `G-07` unit conversion risk | adopted: mapping matrix uses explicit units |
