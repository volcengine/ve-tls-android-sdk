#!/usr/bin/env bash
set -euo pipefail

DIR=$(cd "$(dirname "$0")"/.. && pwd)
cd "$DIR"

SERVER_ID=${SERVER_ID:-sonatype-nexus-staging}
DEPLOY_URL=${DEPLOY_URL:-https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/}
VERSION=2.0.0
GPG_KEYID=${PGP_KEYID:-}
GPG_PASSPHRASE=${PGP_PASSPHRASE:-}
TARGET_DIR="$DIR/maven-publish/target"
mkdir -p "$TARGET_DIR"

./gradlew :core:assembleRelease :producer:assembleRelease :full:assembleRelease >/dev/null

jar cf "$TARGET_DIR/tls-android-core-$VERSION-sources.jar" -C core/src/main/java .
jar cf "$TARGET_DIR/tls-android-producer-$VERSION-sources.jar" -C producer-lite/src/main/java .
jar cf "$TARGET_DIR/tls-android-full-$VERSION-sources.jar" -C full/src/main/java .

CORE_AAR="core/build/outputs/aar/core-release.aar"
PRODUCER_AAR="producer-lite/build/outputs/aar/producer-lite-release.aar"
FULL_AAR="full/build/outputs/aar/full-release.aar"

if [ "${DRY_RUN:-0}" = "1" ]; then
  echo "[DRY RUN] Built AAR and sources jars in $TARGET_DIR";
  exit 0;
fi

export GPG_TTY="$(tty || true)"

MAVEN_GPG_ARGS=()
if [ -n "$GPG_KEYID" ]; then
  MAVEN_GPG_ARGS+=("-Dgpg.keyname=$GPG_KEYID")
fi
if [ -n "$GPG_PASSPHRASE" ]; then
  MAVEN_GPG_ARGS+=("-Dgpg.passphrase=$GPG_PASSPHRASE" "-Dgpg.useagent=true")
fi

mvn -s ~/.m2/settings.xml -q "${MAVEN_GPG_ARGS[@]}" -Dgpg.executable=gpg gpg:sign-and-deploy-file \
  -Durl="$DEPLOY_URL" -DrepositoryId="$SERVER_ID" \
  -DpomFile="maven-publish/pom-core.xml" \
  -Dfile="$CORE_AAR" \
  -Dfiles="$TARGET_DIR/tls-android-core-$VERSION-sources.jar" \
  -Dclassifiers=sources -Dtypes=jar

mvn -s ~/.m2/settings.xml -q "${MAVEN_GPG_ARGS[@]}" -Dgpg.executable=gpg gpg:sign-and-deploy-file \
  -Durl="$DEPLOY_URL" -DrepositoryId="$SERVER_ID" \
  -DpomFile="maven-publish/pom-producer.xml" \
  -Dfile="$PRODUCER_AAR" \
  -Dfiles="$TARGET_DIR/tls-android-producer-$VERSION-sources.jar" \
  -Dclassifiers=sources -Dtypes=jar

mvn -s ~/.m2/settings.xml -q "${MAVEN_GPG_ARGS[@]}" -Dgpg.executable=gpg gpg:sign-and-deploy-file \
  -Durl="$DEPLOY_URL" -DrepositoryId="$SERVER_ID" \
  -DpomFile="maven-publish/pom-full.xml" \
  -Dfile="$FULL_AAR" \
  -Dfiles="$TARGET_DIR/tls-android-full-$VERSION-sources.jar" \
  -Dclassifiers=sources -Dtypes=jar

mvn -s ~/.m2/settings.xml -q -f maven-publish/release-helper-pom.xml nexus-staging:release
