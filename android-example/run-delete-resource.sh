#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root_dir"

require_env() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    echo "[ERROR] Missing env: $name" >&2
    exit 1
  fi
}

require_env endPoint
require_env region
require_env ak
require_env sk

rm -rf android-example/target
mkdir -p android-example/target

echo "[INFO] Building SDK modules with Gradle"
tls-android-modules/gradlew -p tls-android-modules :core:assembleRelease :full:assembleRelease >/dev/null

echo "[INFO] Building runtime classpath"
mvn -q -Dmdep.outputFile=android-example/.classpath.txt -DincludeScope=runtime dependency:build-classpath
CP="$(cat android-example/.classpath.txt)"

SDK_CORE_AAR="tls-android-modules/core/build/outputs/aar/core-release.aar"
SDK_FULL_AAR="tls-android-modules/full/build/outputs/aar/full-release.aar"
mkdir -p android-example/target/sdk_jars
unzip -qo "$SDK_CORE_AAR" classes.jar -d android-example/target/sdk_jars
SDK_CORE_JAR="android-example/target/sdk_jars/classes.jar"
mkdir -p android-example/target/sdk_jars_full
unzip -qo "$SDK_FULL_AAR" classes.jar -d android-example/target/sdk_jars_full
SDK_FULL_JAR="android-example/target/sdk_jars_full/classes.jar"

DEMO_CLASSES="android-example/target/demo_delete_classes"
mkdir -p "$DEMO_CLASSES"
echo "[INFO] Compiling DeleteResource"
javac -encoding UTF-8 -cp "$CP:$SDK_CORE_JAR:$SDK_FULL_JAR" -d "$DEMO_CLASSES" android-example/src/main/java/com/volcengine/example/tls/DeleteResource.java

mkdir -p "$DEMO_CLASSES/com/volcengine"
echo -e "version=1.1.6\nmodule=full" > "$DEMO_CLASSES/com/volcengine/version"

echo "[INFO] Running DeleteResource"
java -cp "$CP:$SDK_CORE_JAR:$SDK_FULL_JAR:$DEMO_CLASSES" com.volcengine.example.tls.DeleteResource
