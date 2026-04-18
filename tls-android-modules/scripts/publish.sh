#!/usr/bin/env bash
set -euo pipefail
DIR=$(cd "$(dirname "$0")"/.. && pwd)
cd "$DIR"
if [ -x "./gradlew" ]; then
  ./gradlew :core:publish :producer-native:publish :full:publish
else
  gradle :core:publish :producer-native:publish :full:publish
fi
