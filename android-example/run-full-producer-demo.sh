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

# required env
require_env endPoint
require_env region
require_env ak
require_env sk
require_env topicId

# Clean previous build
rm -rf android-example/target
mkdir -p android-example/target

# 1) Build SDK modules with Gradle (handles Lombok, resources, etc.)
echo "[INFO] Building SDK modules with Gradle"
tls-android-modules/gradlew -p tls-android-modules :core:assembleRelease :full:assembleRelease >/dev/null

# 2) Collect runtime classpath from Maven (third-party deps)
echo "[INFO] Building runtime classpath"
mvn -q -Dmdep.outputFile=android-example/.classpath.txt -DincludeScope=runtime dependency:build-classpath
CP="$(cat android-example/.classpath.txt)"

# 3) SDK compiled classes (Gradle outputs)
SDK_CORE_JAR="tls-android-modules/core/build/intermediates/compile_library_classes/release/classes.jar"
SDK_FULL_JAR="tls-android-modules/full/build/intermediates/compile_library_classes/release/classes.jar"

# Fallback to AAR classes.jar if compile_library_classes not present
if [ ! -f "$SDK_CORE_JAR" ]; then
  SDK_CORE_AAR="tls-android-modules/core/build/outputs/aar/core-release.aar"
  if [ -f "$SDK_CORE_AAR" ]; then
    mkdir -p android-example/target/sdk_jars
    unzip -qo "$SDK_CORE_AAR" classes.jar -d android-example/target/sdk_jars
    SDK_CORE_JAR="android-example/target/sdk_jars/classes.jar"
  fi
fi
if [ ! -f "$SDK_FULL_JAR" ]; then
  SDK_FULL_AAR="tls-android-modules/full/build/outputs/aar/full-release.aar"
  if [ -f "$SDK_FULL_AAR" ]; then
    mkdir -p android-example/target/sdk_jars_full
    unzip -qo "$SDK_FULL_AAR" classes.jar -d android-example/target/sdk_jars_full
    SDK_FULL_JAR="android-example/target/sdk_jars_full/classes.jar"
  fi
fi

# 4) Compile demo using compiled SDK classes
DEMO_CLASSES="android-example/target/demo_full_classes"
mkdir -p "$DEMO_CLASSES"

echo "[INFO] Compiling FullProducerDemo"
javac -encoding UTF-8 -cp "$CP:$SDK_CORE_JAR:$SDK_FULL_JAR" -d "$DEMO_CLASSES" android-example/src/main/java/com/volcengine/example/tls/FullProducerDemo.java

# 5) Inject version (on demo classpath)
mkdir -p "$DEMO_CLASSES/com/volcengine"
echo -e "version=1.1.6\nmodule=full" > "$DEMO_CLASSES/com/volcengine/version"

# 6) Run demo
echo "[INFO] Running FullProducerDemo"
java -cp "$CP:$SDK_CORE_JAR:$SDK_FULL_JAR:$DEMO_CLASSES" com.volcengine.example.tls.FullProducerDemo
