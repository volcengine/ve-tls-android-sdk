#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MODULE_DIR="$ROOT_DIR/tls-android-modules"
WORKSPACE_DIR="$(cd "$ROOT_DIR/.." && pwd)"
DEFAULT_CONFIG_ENV="$WORKSPACE_DIR/ve-tls-c-sdk/.local/config/real_demo.env"
CONFIG_ENV="${CONFIG_ENV:-}"
CONFIG_PROPS="${CONFIG_PROPS:-$ROOT_DIR/tls_config.properties}"
RESULT_ROOT="${RESULT_ROOT:-$ROOT_DIR/android-example/target/producer-native-benchmark}"
STAMP="$(date '+%Y%m%d-%H%M%S')"
RESULT_DIR="$RESULT_ROOT/$STAMP"
REPORT_MD="$RESULT_DIR/summary.md"
TEST_PACKAGE="com.volcengine.tls.android.producer.test"
TEST_CLASS="com.volcengine.tls.android.producer.ProducerRealBenchmarkInstrumentedTest#testRunRealBenchmarkScenario"
INSTRUMENTATION_COMPONENT="$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner"
REMOTE_BASE="/sdcard/Android/data/$TEST_PACKAGE/files"
REMOTE_CONFIG="$REMOTE_BASE/real_tls.properties"
REMOTE_BENCH_DIR="$REMOTE_BASE/benchmark"
PROFILES="${PROFILES:-tls200 tls700}"
MODE_LIST="${MODE_LIST:-memory persistent}"
RATE_LIST="${RATE_LIST:-1 10 100 200 500}"
DURATION_S="${DURATION_S:-120}"
SEND_THREAD_COUNT="${SEND_THREAD_COUNT:-1}"
RETRY_MAX_ATTEMPTS="${RETRY_MAX_ATTEMPTS:-${VE_TLS_RETRY_MAX_ATTEMPTS:-0}}"
RETRY_TOTAL_TIMEOUT_MS="${RETRY_TOTAL_TIMEOUT_MS:-90000}"
RETRY_INITIAL_INTERVAL_MS="${RETRY_INITIAL_INTERVAL_MS:-500}"
RETRY_MAX_INTERVAL_MS="${RETRY_MAX_INTERVAL_MS:-10000}"
PACKET_TIMEOUT_MS="${PACKET_TIMEOUT_MS:-3000}"
PACKET_LOG_BYTES="${PACKET_LOG_BYTES:-1048576}"
PACKET_LOG_COUNT="${PACKET_LOG_COUNT:-1024}"
MAX_BUFFER_LIMIT="${MAX_BUFFER_LIMIT:-67108864}"
DESTROY_AWAIT_MS="${DESTROY_AWAIT_MS:-20000}"
CALLBACK_FROM_SENDER_THREAD="${CALLBACK_FROM_SENDER_THREAD:-false}"
FAIL_ON_DEGRADED="${FAIL_ON_DEGRADED:-false}"
PERSISTENT_MAX_FILE_COUNT="${PERSISTENT_MAX_FILE_COUNT:-32}"
PERSISTENT_MAX_FILE_SIZE="${PERSISTENT_MAX_FILE_SIZE:-8388608}"
PERSISTENT_MAX_LOG_COUNT="${PERSISTENT_MAX_LOG_COUNT:-65536}"

require_env() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    echo "[ERROR] Missing env: $name" >&2
    exit 1
  fi
}

prop_get() {
  local file="$1"
  local key="$2"
  awk -F= -v k="$key" '$1==k{print substr($0, index($0, "=")+1); exit}' "$file"
}

ADB_BIN="${ADB_BIN:-$(command -v adb || true)}"
if [ -z "$ADB_BIN" ]; then
  echo "[ERROR] adb not found" >&2
  exit 1
fi

ADB_SERIAL="${ADB_SERIAL:-}"
adb_cmd() {
  if [ -n "$ADB_SERIAL" ]; then
    "$ADB_BIN" -s "$ADB_SERIAL" "$@"
  else
    "$ADB_BIN" "$@"
  fi
}

if [ -n "$CONFIG_ENV" ]; then
  if [ ! -f "$CONFIG_ENV" ]; then
    echo "[ERROR] Missing real config env: $CONFIG_ENV" >&2
    exit 1
  fi
  set -a
  # shellcheck disable=SC1090
  source "$CONFIG_ENV"
  set +a
elif [ -f "$CONFIG_PROPS" ]; then
  VE_TLS_ENDPOINT="$(prop_get "$CONFIG_PROPS" endPoint)"
  VE_TLS_REGION="$(prop_get "$CONFIG_PROPS" region)"
  VE_TLS_TOPIC_ID="$(prop_get "$CONFIG_PROPS" topicId)"
  VE_TLS_ACCESS_KEY_ID="$(prop_get "$CONFIG_PROPS" ak)"
  VE_TLS_ACCESS_KEY_SECRET="$(prop_get "$CONFIG_PROPS" sk)"
  VE_TLS_SECURITY_TOKEN="$(prop_get "$CONFIG_PROPS" token)"
  VE_TLS_COMPRESS_TYPE="$(prop_get "$CONFIG_PROPS" compress)"
elif [ -f "$DEFAULT_CONFIG_ENV" ]; then
  set -a
  # shellcheck disable=SC1090
  source "$DEFAULT_CONFIG_ENV"
  set +a
else
  echo "[ERROR] Missing config: set CONFIG_PROPS or CONFIG_ENV" >&2
  exit 1
fi

require_env VE_TLS_ENDPOINT
require_env VE_TLS_REGION
require_env VE_TLS_TOPIC_ID
require_env VE_TLS_ACCESS_KEY_ID
require_env VE_TLS_ACCESS_KEY_SECRET

mkdir -p "$RESULT_DIR"
TMP_PROPS="$(mktemp)"
trap 'rm -f "$TMP_PROPS"' EXIT

COMPRESS_VALUE="${BENCHMARK_COMPRESS:-${VE_TLS_COMPRESS_TYPE:-lz4}}"
CONNECT_TIMEOUT_MS="${CONNECT_TIMEOUT_MS:-${VE_TLS_CONNECT_TIMEOUT_MS:-5000}}"
REQUEST_TIMEOUT_MS="${REQUEST_TIMEOUT_MS:-${VE_TLS_REQUEST_TIMEOUT_MS:-5000}}"

cat > "$TMP_PROPS" <<EOF
endpoint=$VE_TLS_ENDPOINT
region=$VE_TLS_REGION
topicId=$VE_TLS_TOPIC_ID
accessKeyId=$VE_TLS_ACCESS_KEY_ID
accessKeySecret=$VE_TLS_ACCESS_KEY_SECRET
securityToken=${VE_TLS_SECURITY_TOKEN:-}
compress=$COMPRESS_VALUE
sendThreadCount=$SEND_THREAD_COUNT
retryMaxAttempts=$RETRY_MAX_ATTEMPTS
retryTotalTimeoutMs=$RETRY_TOTAL_TIMEOUT_MS
retryInitialIntervalMs=$RETRY_INITIAL_INTERVAL_MS
retryMaxIntervalMs=$RETRY_MAX_INTERVAL_MS
connectTimeoutMs=$CONNECT_TIMEOUT_MS
requestTimeoutMs=$REQUEST_TIMEOUT_MS
packetTimeoutMs=$PACKET_TIMEOUT_MS
packetLogBytes=$PACKET_LOG_BYTES
packetLogCount=$PACKET_LOG_COUNT
maxBufferLimit=$MAX_BUFFER_LIMIT
destroyAwaitMs=$DESTROY_AWAIT_MS
callbackFromSenderThread=$CALLBACK_FROM_SENDER_THREAD
failOnDegraded=$FAIL_ON_DEGRADED
persistentMaxFileCount=$PERSISTENT_MAX_FILE_COUNT
persistentMaxFileSize=$PERSISTENT_MAX_FILE_SIZE
persistentMaxLogCount=$PERSISTENT_MAX_LOG_COUNT
EOF

DEVICE_STATE="$(adb_cmd get-state 2>/dev/null || true)"
if [ "$DEVICE_STATE" != "device" ]; then
  echo "[ERROR] adb device not ready" >&2
  exit 1
fi

echo "[INFO] Building and installing producer-native androidTest"
(cd "$MODULE_DIR" && ./gradlew :producer-native:assembleDebugAndroidTest :producer-native:installDebugAndroidTest >/dev/null)

adb_cmd shell mkdir -p "$REMOTE_BASE" "$REMOTE_BENCH_DIR" >/dev/null
adb_cmd push "$TMP_PROPS" "$REMOTE_CONFIG" >/dev/null

DEVICE_MODEL="$(adb_cmd shell getprop ro.product.model | tr -d '\r')"
DEVICE_RELEASE="$(adb_cmd shell getprop ro.build.version.release | tr -d '\r')"
DEVICE_SDK="$(adb_cmd shell getprop ro.build.version.sdk | tr -d '\r')"
DEVICE_ABI="$(adb_cmd shell getprop ro.product.cpu.abi | tr -d '\r')"

{
  echo "# Producer Native Real Benchmark"
  echo
  echo "- generated_at: $STAMP"
  echo "- device_model: $DEVICE_MODEL"
  echo "- device_release: $DEVICE_RELEASE"
  echo "- device_sdk: $DEVICE_SDK"
  echo "- device_abi: $DEVICE_ABI"
  echo "- modes: $MODE_LIST"
  echo "- profiles: $PROFILES"
  echo "- rates: $RATE_LIST"
  echo "- duration_s: $DURATION_S"
  echo "- send_thread_count: $SEND_THREAD_COUNT"
  echo "- packet_log_bytes: $PACKET_LOG_BYTES"
  echo "- packet_log_count: $PACKET_LOG_COUNT"
  echo "- packet_timeout_ms: $PACKET_TIMEOUT_MS"
  echo "- max_buffer_limit: $MAX_BUFFER_LIMIT"
  echo "- retry_max_attempts: $RETRY_MAX_ATTEMPTS"
  echo "- retry_total_timeout_ms: $RETRY_TOTAL_TIMEOUT_MS"
  echo "- retry_initial_interval_ms: $RETRY_INITIAL_INTERVAL_MS"
  echo "- retry_max_interval_ms: $RETRY_MAX_INTERVAL_MS"
  echo "- connect_timeout_ms: $CONNECT_TIMEOUT_MS"
  echo "- request_timeout_ms: $REQUEST_TIMEOUT_MS"
  echo "- compress: $COMPRESS_VALUE"
  echo "- callback_from_sender_thread: $CALLBACK_FROM_SENDER_THREAD"
  echo "- fail_on_degraded: $FAIL_ON_DEGRADED"
  echo "- persistent_max_file_count: $PERSISTENT_MAX_FILE_COUNT"
  echo "- persistent_max_file_size: $PERSISTENT_MAX_FILE_SIZE"
  echo "- persistent_max_log_count: $PERSISTENT_MAX_LOG_COUNT"
  echo "- cpu_metric: process_cpu_minus_sampler_thread_cpu"
  echo
  echo "| mode | profile | target_lps | accepted_lps | callback_ok | callback_fail | steady_cpu_ms | steady_wall_ms | steady_cpu_pct_total | steady_pss_peak_kb | steady_rss_peak_kb | steady_threads_peak | cpu_ms | wall_total_ms | available_processors | cpu_pct_total | pss_peak_kb | rss_peak_kb | threads_peak | raw_kb_s | compressed_kb_s | compression_ratio | status |"
  echo "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|"
} > "$REPORT_MD"

overall_status=0

for mode in $MODE_LIST; do
  persistent_flag=false
  case "$mode" in
    persistent)
      persistent_flag=true
      ;;
    memory|non_persistent|non-persistent)
      persistent_flag=false
      ;;
    *)
      echo "[ERROR] unsupported mode: $mode" >&2
      exit 1
      ;;
  esac
  for profile in $PROFILES; do
    for rate in $RATE_LIST; do
      run_id="${mode}_${profile}_${rate}lps_${STAMP}"
      local_report="$RESULT_DIR/${run_id}.properties"
      echo "[INFO] Running mode=$mode profile=$profile rate_lps=$rate"
      adb_cmd shell rm -f "$REMOTE_BENCH_DIR/$run_id.properties" >/dev/null 2>&1 || true
      adb_cmd shell am force-stop "$TEST_PACKAGE" >/dev/null 2>&1 || true
      set +e
      adb_cmd shell am instrument -w \
        -e class "$TEST_CLASS" \
        -e benchmarkRunId "$run_id" \
        -e benchmarkProfile "$profile" \
        -e benchmarkRateLps "$rate" \
        -e benchmarkDurationS "$DURATION_S" \
        -e benchmarkSendThreadCount "$SEND_THREAD_COUNT" \
        -e benchmarkRetryMaxAttempts "$RETRY_MAX_ATTEMPTS" \
        -e benchmarkRetryTotalTimeoutMs "$RETRY_TOTAL_TIMEOUT_MS" \
        -e benchmarkRetryInitialIntervalMs "$RETRY_INITIAL_INTERVAL_MS" \
        -e benchmarkRetryMaxIntervalMs "$RETRY_MAX_INTERVAL_MS" \
        -e benchmarkConnectTimeoutMs "$CONNECT_TIMEOUT_MS" \
        -e benchmarkRequestTimeoutMs "$REQUEST_TIMEOUT_MS" \
        -e benchmarkPacketTimeoutMs "$PACKET_TIMEOUT_MS" \
        -e benchmarkPacketLogBytes "$PACKET_LOG_BYTES" \
        -e benchmarkPacketLogCount "$PACKET_LOG_COUNT" \
        -e benchmarkMaxBufferLimit "$MAX_BUFFER_LIMIT" \
        -e benchmarkDestroyAwaitMs "$DESTROY_AWAIT_MS" \
        -e benchmarkCallbackFromSenderThread "$CALLBACK_FROM_SENDER_THREAD" \
        -e benchmarkCompress "$COMPRESS_VALUE" \
        -e benchmarkFailOnDegraded "$FAIL_ON_DEGRADED" \
        -e benchmarkPersistent "$persistent_flag" \
        -e benchmarkPersistentMaxFileCount "$PERSISTENT_MAX_FILE_COUNT" \
        -e benchmarkPersistentMaxFileSize "$PERSISTENT_MAX_FILE_SIZE" \
        -e benchmarkPersistentMaxLogCount "$PERSISTENT_MAX_LOG_COUNT" \
        "$INSTRUMENTATION_COMPONENT" >/dev/null
      instrument_rc=$?
      set -e

      if ! adb_cmd pull "$REMOTE_BENCH_DIR/$run_id.properties" "$local_report" >/dev/null; then
        printf "| %s | %s | %s | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | HARNESS_FAIL |\n" \
          "$mode" "$profile" "$rate" >> "$REPORT_MD"
        overall_status=1
        continue
      fi

      status="$(prop_get "$local_report" status)"
      accepted_lps="$(prop_get "$local_report" acceptedLps)"
      callback_ok="$(prop_get "$local_report" callbackSuccess)"
      callback_fail="$(prop_get "$local_report" callbackFailure)"
      steady_cpu_ms="$(prop_get "$local_report" steadyCpuMs)"
      steady_wall_ms="$(prop_get "$local_report" steadyWallMs)"
      steady_cpu_pct_total="$(prop_get "$local_report" steadyCpuPctTotal)"
      steady_pss_peak_kb="$(prop_get "$local_report" steadyPssPeakKb)"
      steady_rss_peak_kb="$(prop_get "$local_report" steadyRssPeakKb)"
      steady_threads_peak="$(prop_get "$local_report" steadyThreadsPeak)"
      cpu_ms="$(prop_get "$local_report" cpuMs)"
      wall_total_ms="$(prop_get "$local_report" wallTotalMs)"
      available_processors="$(prop_get "$local_report" availableProcessors)"
      cpu_pct_total="$(prop_get "$local_report" cpuPctTotal)"
      pss_peak_kb="$(prop_get "$local_report" pssPeakKb)"
      rss_peak_kb="$(prop_get "$local_report" rssPeakKb)"
      threads_peak="$(prop_get "$local_report" threadsPeak)"
      raw_kb_s="$(prop_get "$local_report" callbackRawKbPerSec)"
      compressed_kb_s="$(prop_get "$local_report" callbackCompressedKbPerSec)"
      compression_ratio="$(prop_get "$local_report" compressionRatio)"

      printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n" \
        "$mode" "$profile" "$rate" "${accepted_lps:-0}" "${callback_ok:-0}" "${callback_fail:-0}" \
        "${steady_cpu_ms:-0}" "${steady_wall_ms:-0}" "${steady_cpu_pct_total:-0}" \
        "${steady_pss_peak_kb:-0}" "${steady_rss_peak_kb:-0}" "${steady_threads_peak:-0}" \
        "${cpu_ms:-0}" "${wall_total_ms:-0}" "${available_processors:-0}" "${cpu_pct_total:-0}" \
        "${pss_peak_kb:-0}" "${rss_peak_kb:-0}" "${threads_peak:-0}" \
        "${raw_kb_s:-0}" "${compressed_kb_s:-0}" "${compression_ratio:-0}" "${status:-UNKNOWN}" >> "$REPORT_MD"

      if [ "$instrument_rc" -ne 0 ] || [ "${status:-UNKNOWN}" != "OK" ]; then
        overall_status=1
      fi
    done
  done
done

echo "[INFO] Summary written to $REPORT_MD"
if [ "$overall_status" -ne 0 ]; then
  echo "[WARN] At least one benchmark run reported non-OK status" >&2
fi
exit "$overall_status"
