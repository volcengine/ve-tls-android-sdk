#!/usr/bin/env bash
set -euo pipefail

DIR=$(cd "$(dirname "$0")"/.. && pwd)
cd "$DIR"

VERSION=${VERSION:-$(grep -E '^POM_VERSION=' gradle.properties | cut -d= -f2)}
CORE_ART_DIR="$DIR/maven-central-publish/core/target/artifacts"
PRODUCER_ART_DIR="$DIR/maven-central-publish/producer/target/artifacts"
FULL_ART_DIR="$DIR/maven-central-publish/full/target/artifacts"
mkdir -p "$CORE_ART_DIR" "$PRODUCER_ART_DIR" "$FULL_ART_DIR"

./gradlew :core:assembleRelease :producer:assembleRelease :full:assembleRelease >/dev/null
./gradlew :core:generateMetadataFileForReleasePublication :producer:generateMetadataFileForReleasePublication :full:generateMetadataFileForReleasePublication >/dev/null

cp -f "core/build/outputs/aar/core-release.aar" "$CORE_ART_DIR/tls-android-core-$VERSION.aar"
cp -f "producer-lite/build/outputs/aar/producer-release.aar" "$PRODUCER_ART_DIR/tls-android-producer-$VERSION.aar"
cp -f "full/build/outputs/aar/full-release.aar" "$FULL_ART_DIR/tls-android-full-$VERSION.aar"

cp -f "core/build/publications/release/module.json" "$CORE_ART_DIR/tls-android-core-$VERSION.module"
cp -f "producer-lite/build/publications/release/module.json" "$PRODUCER_ART_DIR/tls-android-producer-$VERSION.module"
cp -f "full/build/publications/release/module.json" "$FULL_ART_DIR/tls-android-full-$VERSION.module"

jar cf "$CORE_ART_DIR/tls-android-core-$VERSION-sources.jar" -C core/src/main/java .
jar cf "$PRODUCER_ART_DIR/tls-android-producer-$VERSION-sources.jar" -C producer-lite/src/main/java .
jar cf "$FULL_ART_DIR/tls-android-full-$VERSION-sources.jar" -C full/src/main/java .

EMPTY_DIR="$DIR/maven-central-publish/.empty"
mkdir -p "$EMPTY_DIR"
jar cf "$CORE_ART_DIR/tls-android-core-$VERSION-javadoc.jar" -C "$EMPTY_DIR" .
jar cf "$PRODUCER_ART_DIR/tls-android-producer-$VERSION-javadoc.jar" -C "$EMPTY_DIR" .
jar cf "$FULL_ART_DIR/tls-android-full-$VERSION-javadoc.jar" -C "$EMPTY_DIR" .

if [ "${DRY_RUN:-0}" = "1" ]; then
  mvn -q -f maven-central-publish/pom.xml -pl core,producer,full -DskipTests=true package
  echo "[DRY RUN] Built AAR + sources/javadoc jars and validated Maven packaging"
  exit 0
fi

export GPG_TTY="$(tty || true)"

MAVEN_ARGS=()
if [ -n "${PGP_PASSPHRASE:-}" ]; then
  MAVEN_ARGS+=("-Dgpg.passphrase=${PGP_PASSPHRASE}" "-DgpgArguments=--pinentry-mode,loopback")
fi

mvn -q -s ~/.m2/settings.xml -f maven-central-publish/pom.xml -pl core,producer,full -DskipTests=true "${MAVEN_ARGS[@]}" deploy
