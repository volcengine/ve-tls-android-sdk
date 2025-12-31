#!/usr/bin/env bash
set -euo pipefail
DIR=$(cd "$(dirname "$0")"/.. && pwd)
cd "$DIR"
if [ -x "./gradlew" ]; then
  ./gradlew :producer:publish :core:publish
else
  gradle :producer:publish :core:publish
fi
