# Producer Native Semantic Gap Ledger

## Conclusion

Most of the review comments are no longer open design concerns. They now split into three buckets:

- already fixed in code
- still stale in spec/docs
- genuinely deferred product/API decisions

Mixing those buckets is how teams end up re-litigating already-solved problems.

## Resolved In Code

### 1. Mirror-and-Freeze is no longer silent

Current behavior:

- `LogProducerClient` snapshots `LogProducerConfig` through `ConfigSnapshot`
- the source config is frozen during client construction
- later mutators throw `IllegalStateException` instead of silently doing nothing

This is a runtime DX guard, not a builder-only redesign. That is the correct first fix.

### 2. Destroy wait is now genuinely split

Current behavior:

- public Java config supports:
  - `setDestroyWaitMs()` for legacy compatibility
  - `setDestroyFlusherWaitMs()`
  - `setDestroySenderWaitMs()`
- native path supports real staged close through `ve_tls_producer_close_split(...)`

So the older statement that TLS only has one destroy wait budget is now stale.

### 3. `updateEndpoint()` semantic is explicit in code and tests

Current behavior:

- already-inflight old requests may still use the old target
- subsequent sends converge to the refreshed endpoint/region/topic quickly

This matches the product choice made in this thread: mixed send is allowed, but convergence must be quick.

### 4. JNI thread-attachment concern was for an older design, not current code

Current JNI bridge does **not** cache `JNIEnv*` in thread-local storage as a long-lived dangling pointer scheme.
It attaches/detaches on demand.

So that reviewer concern was valid against the old spec wording, not against the current implementation.

### 5. `setHashKey()` does not depend on any hidden SLS-style mode field

TLS C SDK uses `hash_key` directly.
There is no separate Java-side hidden `mode=KeyShard` gate to unlock it.

The right action here is semantic clarification, not API expansion.

### 6. Invalid `compressType` is no longer silently coerced

Current behavior:

- public producer API still exposes only `NONE/LZ4`
- internal string parsing now rejects unsupported strings
- sample code now also rejects unsupported `compress` values instead of silently falling back to `LZ4`

That closes the what-if-gzip ambiguity for the Android path.

### 7. Callback result now has explicit failure classification helpers

Current behavior:

- `LogProducerResult.FailureKind`
- `getFailureKind()`
- `getFailureSummary()`
- `hasHttpFailure()`
- `hasTransportFailure()`
- `getBestErrorMessage()`

The sample app now uses the result object’s failure summary instead of reverse-engineering raw fields by hand.

## Resolved In Runtime But Still Stale In Docs

### 1. Split destroy wait

The large design/spec docs still contain old single-budget wording in places.
Those references should now be read as historical, not current behavior.

### 2. Old JNI TLS-thread wording

Some review/spec text still talks as if the implementation caches `JNIEnv*` per sender thread.
That is no longer the current model.

### 3. Callback simplicity wording

The public callback shape is simple, but the result object is intentionally richer.
Docs should stop pretending the structured result is simpler than SLS in every dimension.

## Still Open Or Explicitly Deferred

### 1. Custom CA / TLS verify is internal-only today

Reality:

- `NativeHttpBridge` already supports `tlsVerifyPeer`, `tlsVerifyHost`, and `caCertPath`
- but producer public API does not expose them through `LogProducerConfig` or the Android binding create path

Therefore custom CA support is **not** a public phase-1 producer capability yet.
This is a doc correction first, not a silent support claim.

### 2. Persistent path rewrite has two implementations in the codebase

Current runtime truth for producer create is:

- Java `ConfigSnapshot` rewrites the path through `ProcessUtil.rewritePersistentPath(...)` before native create

But the C SDK repo still carries:

- `ve_tls_android_binding_build_persistent_path(...)`
- tested in `ve_tls_test_android_binding`

That helper is not the current producer runtime source of truth.
This is not a functional blocker today, but it is a maintenance smell and should be unified later.

### 3. `HttpURLConnection` vs `OkHttp` is now a decision record, not an open technical mystery

See:

- `docs/plans/2026-04-20-producer-httpurlconnection-vs-okhttp-cost-inventory.md`

The next step is not transport rewrite. The next step is keeping the bridge bounded and audited.

## Recommended Reading Order

1. This ledger for current semantic status
2. `2026-04-20-producer-httpurlconnection-vs-okhttp-cost-inventory.md` for transport rationale
3. `2026-04-20-producer-module-reorg-and-api-alignment.md` for later package evolution decisions
