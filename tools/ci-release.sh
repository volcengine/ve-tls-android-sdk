#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

python3 tools/check-release-docs.py
python3 -m unittest discover -s tools -p 'test_*.py'
python3 tools/test_publish_credentials.py
python3 -m unittest discover -s android-example -p 'test_run_producer_native_real_validation.py'

tls-android-modules/gradlew -p tls-android-modules \
  :producer-native:testDebugUnitTest :producer-native:lintRelease \
  :producer-native:assembleRelease :producer-native:publishReleasePublicationToMavenLocal \
  --console=plain
python3 tools/check_android_release.py \
  --aar tls-android-modules/producer-native/build/outputs/aar/producer-native-release.aar
python3 tools/check_release_provenance.py \
  --aar tls-android-modules/producer-native/build/outputs/aar/producer-native-release.aar
tls-android-modules/gradlew -p producer-integration-sample \
  :app:testDebugUnitTest :app:assembleRelease --console=plain

if [ "${RUN_DEVICE_TESTS:-0}" = "1" ]; then
  : "${ANDROID_SERIAL:?Select one dedicated test device}"
  : "${EXPECTED_PAGE_SIZE:?Set expected page size}"
  ADB="${ANDROID_HOME:?Set Android SDK path}/platform-tools/adb"
  if [ "$EXPECTED_PAGE_SIZE" != "16384" ] && [ "$EXPECTED_PAGE_SIZE" != "4096" ]; then
    echo 'Unsupported expected page size' >&2
    exit 1
  fi
  actual=$("$ADB" -s "$ANDROID_SERIAL" shell getconf PAGE_SIZE 2>/dev/null | tr -d '\r\n') || actual=''
  if ! [[ "$actual" =~ ^[0-9]+$ ]]; then
    actual=$("$ADB" -s "$ANDROID_SERIAL" shell cat /proc/self/smaps |
      awk '/KernelPageSize/ && !printed {print $2 * 1024; printed=1}' | tr -d '\r\n')
  fi
  if [ "$actual" != "$EXPECTED_PAGE_SIZE" ]; then
    echo 'Device page size does not match the verification target' >&2
    exit 1
  fi
  TEST_PACKAGE=com.volcengine.tls.android.producer
  TEST_CLASSES="$TEST_PACKAGE.ProducerNativeLoadInstrumentedTest,$TEST_PACKAGE.ProducerRecoveryInstrumentedTest,$TEST_PACKAGE.ProducerLifecycleInstrumentedTest,$TEST_PACKAGE.ProducerRealBenchmarkDeliveryInstrumentedTest"
  tls-android-modules/gradlew -p tls-android-modules \
    :producer-native:connectedReleaseAndroidTest -PTEST_BUILD_TYPE=release \
    -Pandroid.testInstrumentationRunnerArguments.class="$TEST_CLASSES" --console=plain
  "$ANDROID_HOME/build-tools/35.0.0/zipalign" -c -P 16 4 \
    tls-android-modules/producer-native/build/outputs/apk/androidTest/release/producer-native-release-androidTest.apk
fi
