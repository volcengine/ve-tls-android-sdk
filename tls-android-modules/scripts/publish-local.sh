#!/usr/bin/env bash
set -euo pipefail
DIR=$(cd "$(dirname "$0")"/.. && pwd)
cd "$DIR"
if [ -x "./gradlew" ]; then
  ./gradlew :core:publishToMavenLocal :producer:publishToMavenLocal :full:publishToMavenLocal
else
  gradle :core:publishToMavenLocal :producer:publishToMavenLocal :full:publishToMavenLocal
fi
