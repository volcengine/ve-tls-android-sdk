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

# required env (producer demo)
require_env endPoint
require_env region
require_env ak
require_env sk
require_env topicId

# Clean previous build
rm -rf android-example/target
mkdir -p android-example/target

echo "[INFO] Building runtime classpath"
# Use pom.xml to get dependencies.
mvn -q -Dmdep.outputFile=android-example/.classpath.txt -DincludeScope=runtime dependency:build-classpath

# Read classpath
CP="$(cat android-example/.classpath.txt)"

# Compile SDK sources (core + full)
SDK_CLASSES="android-example/target/sdk_classes"
mkdir -p "$SDK_CLASSES"

SDK_SRC_CORE="tls-android-modules/core/src/main/java"
SDK_SRC_PRODUCER="tls-android-modules/producer-lite/src/main/java"

echo "[INFO] Compiling SDK sources from $SDK_SRC_CORE and $SDK_SRC_PRODUCER"
# Find all java files
find "$SDK_SRC_CORE" "$SDK_SRC_PRODUCER" -name "*.java" > android-example/target/sources_list.txt

# Compile SDK
javac -encoding UTF-8 -cp "$CP" -d "$SDK_CLASSES" @android-example/target/sources_list.txt

# Create version file
mkdir -p "$SDK_CLASSES/com/volcengine"
echo -e "version=1.1.6\nmodule=producer" > "$SDK_CLASSES/com/volcengine/version"

# Compile ProducerDemo
DEMO_CLASSES="android-example/target/demo_classes"
mkdir -p "$DEMO_CLASSES"

echo "[INFO] Compiling ProducerDemo"
javac -encoding UTF-8 -cp "$CP:$SDK_CLASSES" -d "$DEMO_CLASSES" android-example/src/main/java/com/volcengine/example/tls/ProducerDemo.java

echo "[INFO] Running ProducerDemo"
java -cp "$CP:$SDK_CLASSES:$DEMO_CLASSES" com.volcengine.example.tls.ProducerDemo
