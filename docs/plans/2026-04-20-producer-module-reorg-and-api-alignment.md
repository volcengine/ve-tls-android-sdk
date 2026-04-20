# Producer Native Module Reorg And API Alignment Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Keep `producer-native` self-contained and size-efficient while aligning externally visible behavior with SLS where that alignment is real, and explicitly documenting or redesigning the places where TLS intentionally differs.

**Architecture:** Treat `producer-native` as an independent Android producer runtime with no transitive dependency on the current heavy `:core`. Keep heavy HTTP/auth/protobuf logic in the `full/core` side. Extract only the truly shared lightweight contracts such as logger SPI. Do not force method-for-method parity with SLS when SLS does not expose the same API surface.

**Tech Stack:** Android Gradle Plugin, JNI, `ve-tls-c-sdk`, `HttpURLConnection/HttpsURLConnection`, Gradle dependency graphs, release APK/AAR size measurements, SLS published Maven artifacts.

---

## Current Verified State

- `producer-native` no longer needs `implementation project(:core)` to build or publish.
- `:app:assembleNoProviderRelease` passes with runtime classpath only:
  - `project :producer-native`
  - `net.jpountz.lz4:lz4:1.3.0`
- `noProvider` release APK delta vs fair baseline (`app-no-sdk`) is now `217,737 B`, already below the SLS doc screenshot delta `254.5 KB`.
- `slf4jProvider` still pulls `:core`, because `TlsLogger`, `TlsLoggerFactory`, and `TlsLoggerProvider` currently live under `core/src/main/java/com/volcengine/util/`.
- Published SLS Maven producer artifact `io.github.aliyun-sls:aliyun-log-android-sdk:2.7.14` does not depend on SLS `core`; its POM only carries `androidx.annotation`.

This means the main producer size problem was not JNI alone. It was the old `producer-native -> :core` dependency direction.

## Re-prioritized Execution Order (2026-04-20)

Before revisiting module/package evolution, prioritize the next wave in this order:

1. **Improve producer failure classification ergonomics**
   - Keep the callback shape stable.
   - Add clearer `LogProducerResult` helpers/grouping so app code no longer needs to manually combine `code`, `httpCode`, `transportCode`, `errorCode`, and `errorMessage`.
   - Treat this as a producer DX fix, not an API redesign.

2. **Audit `HttpURLConnection` vs `OkHttp` with a real lifecycle cost inventory**
   - Measure size/runtime/dependency impact and maintenance/security/testing cost.
   - Do not assume `OkHttp` is better just because it is richer, and do not assume `HttpURLConnection` is free just because it is smaller.
   - This task is evidence-gathering first; no transport migration should happen in the same wave.

3. **Resolve the remaining spec-plus-API semantic gaps**
   - Work only on the items that still need explicit product semantics or public API clarification beyond `updateEndpoint()`.
   - Prefer narrowing docs/specs or adding runtime guardrails before adding new knobs.

Only after those three tracks are materially settled should the repo revisit package evolution.

---

## Alignment Rule

Use this rule for all follow-up work:

1. Align with SLS on externally visible behavior and packaging shape.
2. Keep TLS implementation style when the same behavior can be achieved more cleanly in TLS.
3. Do not force 1:1 API parity when SLS producer does not expose the same method at all.

Two examples already verified:

- SLS producer does not publicly depend on SLS `core`, so TLS producer should not depend on TLS `:core`.
- SLS producer does not publicly expose `updateEndpoint()` or `setHashKey()` in the open-source Java surface, so TLS cannot claim those APIs are “SLS-aligned” without its own explicit semantics.

---

## Recommended Module Direction

### Recommended target graph

```text
producer-native
  -> no dependency on :core
  -> optional dependency on a new tiny logger SPI module only if producer itself truly needs it

full
  -> :core
  -> optional tiny logger SPI module

core
  -> transport/auth/protobuf/http only
  -> must not depend on producer-native

app(noProvider)
  -> producer-native

app(slf4jProvider)
  -> producer-native
  -> tiny logger SPI module
  -> slf4j
  -> no forced :core dependency just for logging
```

### Minimal next reorg

Create a new tiny module for:

- `TlsLogger`
- `TlsLoggerFactory`
- `TlsLoggerProvider`

Move only those types out of `:core`.

This is the highest-value next step because it removes the current `slf4jProvider -> :core -> okhttp/okio/protobuf` drag without changing producer runtime semantics.

### Long-term reorg boundary

Keep `:core` heavy for now. Do not split it further until logger SPI extraction is done and measured. The current evidence says producer size is already fixed by cutting the wrong direction dependency; a broad `core` refactor would add risk before proving extra value.

### Deferred package evolution direction

After the three priority tracks above, reevaluate package evolution with this working hypothesis:

- Do **not** make `full` depend directly on `producer-native`; that would re-couple Java-only users to JNI/native delivery and undo the current producer minimization.
- If the current `core` and `full` remain semantically inseparable after the semantic cleanup work, consider collapsing them into one Java SDK module with a clearer name.
- If a one-stop artifact is still needed for external users, add a new SLS-like aggregator package later that depends on the Java SDK module plus `producer-native`, instead of renaming the current heavy `:core` and pretending it matches SLS `core`.

In other words, the likely end state is closer to:

```text
producer-native   -> standalone minimal producer
java-sdk          -> current heavy core/full Java API runtime
logger-spi        -> tiny shared contracts
aggregator-core   -> optional convenience artifact depending on java-sdk + producer-native
```

This should only proceed after the current semantic/API cleanup wave, because otherwise the package reshuffle will hide unresolved contract problems instead of simplifying them.

---

## Review Of The 9 Concerns

| # | Concern | Verdict | Evidence | Required Action |
| --- | --- | --- | --- | --- |
| 1 | `HttpURLConnection` vs `OkHttp` lifecycle cost | Partially valid | Size evidence strongly favors `HttpURLConnection` for producer-only path; reintroducing `:core`/`OkHttp` was exactly what bloated APK dex. But maintainability/security cost has not been fully documented. | Keep `HttpURLConnection` for `producer-native`; add a focused bridge test/security checklist instead of moving producer back onto OkHttp. |
| 2 | Mirror-and-Freeze is a DX trap | Valid | `LogProducerClient` snapshots `LogProducerConfig` through `ConfigSnapshot`; later mutations are silently ignored. | Add runtime freeze semantics for `LogProducerConfig` mutators after client creation. Builder-only redesign is a larger v2 API choice, not the first fix. |
| 3 | `destroyWaitMs` merges two SLS semantics | Valid | `ve_tls_producer_close(timeout_ms)` is a single C SDK boundary today, so TLS cannot truly express separate flusher/sender budgets. | Stop claiming parity here. Document current semantics honestly, then decide whether C SDK needs a split close API. |
| 4 | JNI thread attachment assumes fixed sender threads | Concern was valid for old spec, not current code | Current `tls_producer_jni.cpp` attaches/detaches on demand through `ThreadEnv`; it no longer caches `JNIEnv*` per thread in TLS. | Update spec; no code redesign needed for this point. |
| 5 | `updateEndpoint()` semantics are undefined | Valid | C sender path acquires current runtime snapshot at send time. Queued-but-unsent logs do not carry endpoint with them. They will follow the refreshed endpoint/topic/region. | Document this explicitly and add contract tests. If that semantic is unacceptable, redesign or remove the API. |
| 6 | `setHashKey()` depends on hidden mode | Invalid as framed for TLS | TLS C SDK normalizes a non-empty `hash_key` directly; there is no SLS-style hidden Java `mode` field gating it. | Document TLS semantics instead of copying SLS terminology. |
| 7 | callback looks simpler but result object is more complex | Valid | `LogProducerResult` carries transport-level fields that SLS callback does not expose directly. | Keep structured result, but add clearer grouping/helpers and tighten docs so users do not need to reverse-engineer field meaning. |
| 8 | multi-process path rewrite is invisible to C SDK | Invalid as framed, but there is an implementation smell | `ConfigSnapshot` rewrites the persistent path before native create, so C SDK sees the rewritten path. However, Java rewrite logic and `ve_tls_android_binding_build_persistent_path()` use different schemes and the binding helper is effectively unused. | Document actual behavior and unify on one rewrite source of truth. |
| 9 | invalid `compressType` behavior is unknown | Public API concern is mostly invalid | Public producer API exposes enum `NONE/LZ4`; binding maps to native strings; C SDK already has unsupported codec error handling. | Public API docs can stay simple, but internal string parsing should reject unknown strings rather than silently coercing to `LZ4`. |

---

## Extra Finding: Custom CA/TLS Verify Is Not Publicly Reachable Yet

`NativeHttpBridge` already supports:

- `tlsVerifyPeer`
- `tlsVerifyHost`
- `caCertPath`

But the Android producer public path does not expose these fields through `LogProducerConfig`, `JniNativeProducerBridge.nativeCreate(...)`, or `ve_tls_android_config_view`.

Therefore:

- custom CA is not a phase-1 public producer capability today
- any spec text that sounds like it is publicly supported should be narrowed or removed

This should be treated as a spec correction first, not as silent support.

---

## Recommended Work Order

### Task 0: Finish the pre-reorg semantic cleanup

- Land the producer failure-classification ergonomics improvements.
- Complete the `HttpURLConnection` vs `OkHttp` cost inventory with explicit size, testing, maintenance, and audit tradeoffs.
- Finish the remaining spec/API semantic decisions that are still open beyond `updateEndpoint()`.

Resolution artifacts from this wave:

- `docs/plans/2026-04-20-producer-httpurlconnection-vs-okhttp-cost-inventory.md`
- `docs/plans/2026-04-20-producer-semantic-gap-ledger.md`

### Task 1: Lock the current dependency direction

- Preserve the already-verified `producer-native` decoupling from `:core`.
- Keep `app-no-sdk` and `producer-native-stub` as long-lived measurement harnesses.
- Treat `app-no-sdk` as the default baseline for future APK delta checks.

### Task 2: Extract logger SPI from `:core`

- Create a tiny module with only logger SPI and no network/protobuf deps.
- Make `core` and `full` depend on that tiny module if they still use logger SPI.
- Make `slf4jProvider` depend on the tiny module instead of `:core`.
- Rebuild `:app:assembleSlf4jProviderRelease` and remeasure APK size.

### Task 3: Correct producer public contract

- Update spec text for current JNI thread attachment model.
- Document real `updateEndpoint()` semantics: unsent queued logs follow the latest send snapshot.
- Document that `destroyWaitMs` is a single drain budget, not split flusher/sender parity.
- Document that path rewrite reaches native, but unify rewrite implementation.
- Document that public `compressType` is enum-bound and not raw string input.

### Task 4: Fix the worst DX traps

- Add `freeze()` / `ensureMutable()` style runtime protection so mutating config after client construction fails loudly.
- Reject unknown internal `setCompressType(String)` inputs instead of silently mapping everything non-`none` to `LZ4`.
- Decide whether `updateEndpoint()` remains public after its semantics are documented.

### Task 5: Evaluate C SDK parity gaps separately

- Decide whether separate close budgets are needed at C SDK layer.
- Decide whether TLS verify / custom CA should become public producer config in phase-2.
- Do not mix those C changes into the logger SPI/module cleanup wave.

---

## Verification Checklist For Every Follow-up Change

- `./gradlew --no-daemon :producer-native:testDebugUnitTest`
- `./gradlew --no-daemon :app:assembleNoProviderRelease`
- `./gradlew --no-daemon :app:assembleSlf4jProviderRelease`
- `./gradlew --no-daemon :app:dependencies --configuration noProviderReleaseRuntimeClasspath`
- `./gradlew --no-daemon :app:dependencies --configuration slf4jProviderReleaseRuntimeClasspath`
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/size/measure_producer_native_vs_sls_maven.sh`

Success criteria:

- `producer-native` remains independent from heavy `:core`
- `noProvider` release APK delta does not regress above the current `217,737 B` baseline without a conscious tradeoff
- `slf4jProvider` no longer needs `:core` once logger SPI extraction lands

---

## Non-goals For The Next Wave

- Do not reintroduce OkHttp into `producer-native` just to reuse old `:core` utilities.
- Do not split all of `:core` immediately; that is bigger than the evidence justifies.
- Do not claim SLS parity for APIs that SLS producer does not publish.
