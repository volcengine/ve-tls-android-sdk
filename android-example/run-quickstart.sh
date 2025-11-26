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

echo "[INFO] Building SDK classes"
mvn -q -DskipTests package

echo "[INFO] Building runtime classpath"
mvn -q -Dmdep.outputFile=android-example/.classpath.txt -DincludeScope=runtime dependency:build-classpath
CP="$(cat android-example/.classpath.txt):target/classes:android-example/target/classes"
mkdir -p android-example/target/classes

echo "[INFO] Compiling QuickStart"
javac -cp "$CP" -d android-example/target/classes android-example/src/main/java/com/volcengine/example/tls/QuickStart.java

echo "[INFO] Running QuickStart"
java -cp "$CP" com.volcengine.example.tls.QuickStart
