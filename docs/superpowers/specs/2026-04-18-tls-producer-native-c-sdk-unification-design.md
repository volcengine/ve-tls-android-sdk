# TLS Android Producer Native Re-architecture Design

Date: 2026-04-18
Status: Draft for user review
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

- map platform-facing inputs to `ve_tls_config` and `ve_tls_producer`
- perform platform-specific callback dispatch
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

### 7.3 `LogProducerConfig`

Required constructor coverage:

- no-arg
- `Context`
- `endpoint, region, projectId, topicId`
- `endpoint, region, projectId, topicId, accessKeyId, accessKeySecret`
- `endpoint, region, projectId, topicId, accessKeyId, accessKeySecret, securityToken`
- `Context` variants of the above

Main setters:

- `setEndpoint`
- `setRegion`
- `setProjectId`
- `setTopicId`
- `setAccessKeyId`
- `setAccessKeySecret`
- `setSecurityToken`
- `setHashKey`
- `setLogTopic`
  Note: this is reserved for log metadata only if TLS still needs that concept; target resource identity always uses `topicId`
- `addTag`
- `setSource`
- `setPacketLogBytes`
- `setPacketLogCount`
- `setPacketTimeout`
- `setMaxBufferLimit`
- `setSendThreadCount`
- `setPersistent`
- `setPersistentFilePath`
- `setPersistentForceFlush`
- `setPersistentMaxFileCount`
- `setPersistentMaxFileSize`
- `setPersistentMaxLogCount`
- `setConnectTimeoutSec`
- `setSendTimeoutSec`
- `setDestroyFlusherWaitSec`
- `setDestroySenderWaitSec`
- `setCompressType`
- `setNtpTimeOffset`
- `setMaxLogDelayTime`
- `setDropDelayLog`
- `setDropUnauthorizedLog`
- `setCallbackFromSenderThread`
- `resetSecurityToken`
- `isValid`
- `isEnabled`

### 7.4 `LogProducerClient`

Formal lifecycle:

- `new LogProducerClient(config)`
- `new LogProducerClient(config, callback)`
- `addLog(Log)`
- `addLog(Log, int flush)`
- `addLogRaw(byte[][] keys, byte[][] values)`
- `destroyLogProducer()`

Not part of the new formal API:

- Java-engine style `start()`
- `closeNow()`
- `reconfig()`

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

`LogProducerConfig` maps to `ve_tls_config`.

Core mapping:

- `endpoint -> endpoint`
- `region -> region`
- `projectId -> project_id`
- `topicId -> topic_id`
- `accessKeyId -> access_key_id`
- `accessKeySecret -> access_key_secret`
- `securityToken -> security_token`
- `hashKey -> default hash_key or per-log hash key`
- `packetLogBytes -> log_bytes_per_package`
- `packetLogCount -> log_count_per_package`
- `packetTimeout -> flush_interval_ms`
- `maxBufferLimit -> max_buffer_bytes`
- `sendThreadCount -> send_thread_count`
- `persistent -> use_persistent`
- `persistentFilePath -> persistent_file_path`
- `persistentMaxLogCount -> max_persistent_log_count`
- `persistentMaxFileSize -> max_persistent_file_size`
- `persistentMaxFileCount -> max_persistent_file_count`
- `persistentForceFlush -> force_flush_disk`
- `connectTimeoutSec -> connect_timeout_ms`
- `sendTimeoutSec -> request_timeout_ms`

Android facade only validates inputs and performs type/name conversion. It must not duplicate native producer logic.

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
- recover support
- callback thread mode switching
- persistent mode forcing single sender
- multi-process persistent path isolation
- bounded graceful destroy

## 10. JNI Boundary Design

JNI should stay small and stable.

Three groups of native entry points:

### 10.1 Config and Lifecycle

- create/destroy config
- config setter bridge methods
- create/destroy producer

### 10.2 Log Ingestion

- add structured log
- add raw log
- reserved extended add-log path for future context encoding

### 10.3 Runtime Controls

- reset security token
- validity/enabled checks
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

### 11.2 Multi-process Persistent Path Rewrite

If current process is not main process, persistent path is rewritten to a process-specific subdirectory, matching the SLS practical behavior.

### 11.3 Callback Thread Mode

- sender-thread callback when enabled
- main-thread callback through Android looper/handler when disabled

### 11.4 Library Loading

Android users load one formal producer shared library only.

### 11.5 Destroy Behavior

`destroyLogProducer()`:

- immediately stops accepting new logs
- returns asynchronously from Java to avoid ANR
- triggers native bounded graceful shutdown in background
- native shutdown waits for flusher/sender within configured bounds
- native shutdown then finishes even if timeout is hit

This explicitly follows the practical SLS destroy model rather than a weakened fire-and-forget model.

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

## 15. Error Model and Callback Model

### 15.1 Error Model

Android public model stays stable and simple:

- public result enum/class through `LogProducerResult`
- JNI maps native result + native error details into stable Android result codes and messages
- private native error surface is not leaked directly

### 15.2 Callback Contract

Stable callback shape:

- `onCall(resultCode, reqId, errorMessage, logBytes, compressedBytes)`

Thread mode:

- sender thread direct callback when enabled
- main thread callback when disabled

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
- callback thread mode tests
- multi-process path tests
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
2. Reserve context-encoding extension path
3. Implement Android `producer-native` main flow
4. Align key runtime behaviors with SLS baseline
5. Remove Java producer implementations
6. Repair docs/tests/publish wiring
7. Publish new AAR and migration guide
8. Reuse the same binding boundary for iOS

## 19. Key Risks and Mitigations

### 19.1 Highest Risks

- incorrect JNI-to-C config mapping
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
