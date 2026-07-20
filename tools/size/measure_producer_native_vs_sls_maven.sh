#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
MODULE_DIR="${REPO_ROOT}/tls-android-modules"
CURRENT_AAR="${MODULE_DIR}/producer-native/build/outputs/aar/producer-native-release.aar"
CURRENT_SO_GLOB="${MODULE_DIR}/producer-native/build/intermediates/stripped_native_libs/release"
BENCH_DIR="${TMPDIR:-/tmp}/producer-native-size-benchmark"
BENCH_AAR="${BENCH_DIR}/aliyun-log-android-sdk-2.7.14.aar"
BENCH_UNPACKED="${BENCH_DIR}/unpacked"
BENCH_URL="https://repo.maven.apache.org/maven2/io/github/aliyun-sls/aliyun-log-android-sdk/2.7.14/aliyun-log-android-sdk-2.7.14.aar"

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing required command: $1" >&2
    exit 1
  }
}

need_cmd curl
need_cmd unzip
need_cmd find
need_cmd stat
need_cmd nm
need_cmd awk

measure_zip_total() {
  unzip -l "$1" | awk 'END { print $1 + 0 }'
}

measure_jni_total() {
  unzip -l "$1" | awk '$4 ~ /^jni\// && $4 !~ /\/$/ { sum += $1 } END { print sum + 0 }'
}

measure_exported_symbols() {
  nm -D --defined-only "$1" | wc -l | tr -d ' '
}

current_so_path() {
  find "${CURRENT_SO_GLOB}" -path '*/arm64-v8a/libtls_producer_jni.so' | head -n 1
}

ensure_benchmark() {
  mkdir -p "${BENCH_DIR}"
  if [[ ! -f "${BENCH_AAR}" ]]; then
    curl -fsSL "${BENCH_URL}" -o "${BENCH_AAR}"
  fi
  rm -rf "${BENCH_UNPACKED}"
  mkdir -p "${BENCH_UNPACKED}"
  unzip -qo "${BENCH_AAR}" -d "${BENCH_UNPACKED}"
}

build_current() {
  (
    cd "${MODULE_DIR}"
    ./gradlew --no-daemon :producer-native:assembleRelease
  )
}

build_current
ensure_benchmark

if [[ ! -f "${CURRENT_AAR}" ]]; then
  echo "current AAR missing: ${CURRENT_AAR}" >&2
  exit 1
fi

CURRENT_SO="$(current_so_path)"
if [[ -z "${CURRENT_SO}" || ! -f "${CURRENT_SO}" ]]; then
  echo "current arm64 stripped .so missing under ${CURRENT_SO_GLOB}" >&2
  exit 1
fi

BENCH_SO="${BENCH_UNPACKED}/jni/arm64-v8a/libsls_producer.so"
if [[ ! -f "${BENCH_SO}" ]]; then
  echo "benchmark arm64 .so missing: ${BENCH_SO}" >&2
  exit 1
fi

CURRENT_AAR_SIZE="$(stat -c %s "${CURRENT_AAR}")"
CURRENT_SO_SIZE="$(stat -c %s "${CURRENT_SO}")"
CURRENT_ZIP_TOTAL="$(measure_zip_total "${CURRENT_AAR}")"
CURRENT_JNI_TOTAL="$(measure_jni_total "${CURRENT_AAR}")"
CURRENT_EXPORTED="$(measure_exported_symbols "${CURRENT_SO}")"

BENCH_AAR_SIZE="$(stat -c %s "${BENCH_AAR}")"
BENCH_SO_SIZE="$(stat -c %s "${BENCH_SO}")"
BENCH_ZIP_TOTAL="$(measure_zip_total "${BENCH_AAR}")"
BENCH_JNI_TOTAL="$(measure_jni_total "${BENCH_AAR}")"
BENCH_EXPORTED="$(measure_exported_symbols "${BENCH_SO}")"

cat <<REPORT
benchmark=maven:io.github.aliyun-sls:aliyun-log-android-sdk:2.7.14
current_aar=${CURRENT_AAR}
current_arm64_so=${CURRENT_SO}
benchmark_aar=${BENCH_AAR}
benchmark_arm64_so=${BENCH_SO}

metric,current,benchmark
aar_compressed_bytes,${CURRENT_AAR_SIZE},${BENCH_AAR_SIZE}
arm64_so_bytes,${CURRENT_SO_SIZE},${BENCH_SO_SIZE}
zip_total_bytes,${CURRENT_ZIP_TOTAL},${BENCH_ZIP_TOTAL}
jni_total_bytes,${CURRENT_JNI_TOTAL},${BENCH_JNI_TOTAL}
exported_symbol_count,${CURRENT_EXPORTED},${BENCH_EXPORTED}
REPORT
