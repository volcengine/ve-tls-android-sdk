# Android Artifact Convergence Decision

## Conclusion

The Android SDK should converge to **one** formal Maven artifact:

- `producer-native`

`core`, `full`, `logger-spi`, and any aggregator artifact should not continue as external Android product artifacts.

## Why This Decision Exists

The producer path is now independently viable:

- `producer-native` no longer depends on the heavy `:core`
- the producer-only release APK delta is already in the same order of magnitude as the SLS producer benchmark expectation
- the producer artifact itself is already smaller than the published SLS Maven producer artifact

That means the Android-facing product has become clear: the meaningful Android SDK surface is producer ingestion, not the broader Java management/query client stack.

## Product Boundary

### Keep as formal Android product

- `producer-native`

This is the only Android artifact that still has clear product meaning.

### Do not continue as formal Android product artifacts

- `core`
- `full`
- `logger-spi`
- any convenience aggregator depending on `full/core + producer-native`

Reason:

- Android-side query/management usage is not the primary client-side scenario
- a separate Java SDK already exists for users who truly need the heavier Java API surface
- continuing to publish `core/full` on Android would create duplicate product surfaces without clear usage value

## Internal Module Policy

Stopping external publication does **not** imply immediate source deletion.

For now, treat the remaining modules as internal implementation or legacy material:

- `producer-native`: formal product module
- `logger-spi`: internal boundary module for dependency isolation
- `core`: internal / legacy module
- `full`: internal / legacy module

## Important Non-goals

### Non-goal 1: Do not make `full` depend on `producer-native`

That would re-couple Java-only users to JNI/native delivery and undo the current producer minimization.

### Non-goal 2: Do not create a new Android `java-sdk` artifact just for symmetry

The absence of a clean Android-side use case is exactly why this artifact should not be introduced.

### Non-goal 3: Do not create an Android aggregator artifact

A one-stop Android artifact is not justified today.
If it exists only for structural elegance, it is product noise, not product value.

## What This Means Practically

### External publishing target

Long-term external Android publication should converge to:

```text
producer-native
```

### Internal codebase target

Short-term internal topology may remain:

```text
producer-native
logger-spi
core
full
```

But only `producer-native` is treated as a real external SDK boundary.

### Follow-up question

The next architectural question is no longer “how many Android artifacts should exist”.
That question is now settled.

The next real question is:

- whether `core/full` still have enough internal value to justify maintenance
- and if yes, whether they should be merged internally or just left as legacy until retirement

## Decision Rule For Follow-up Changes

Any follow-up module work must satisfy this rule:

1. Do not increase the Android external artifact count.
2. Do not reintroduce heavy dependencies into `producer-native`.
3. Do not spend major refactor effort on `core/full` unless that refactor serves internal retirement or simplification.

## Recommended Next Step

Use this decision as the baseline and evaluate `core/full` only as internal legacy modules:

- keep them unpublished
- assess whether they still have meaningful internal consumers
- only then decide merge vs archive vs deletion
