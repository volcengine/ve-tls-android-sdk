#!/usr/bin/env bash
set -euo pipefail
DIR=$(cd "$(dirname "$0")"/.. && pwd)
cd "$DIR"
if [ -x "./gradlew" ]; then
  ./gradlew :producer-native:publishToMavenLocal
else
  gradle :producer-native:publishToMavenLocal
fi
