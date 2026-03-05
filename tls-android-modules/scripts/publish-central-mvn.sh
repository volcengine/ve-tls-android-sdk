#!/usr/bin/env bash
set -euo pipefail

DIR=$(cd "$(dirname "$0")"/.. && pwd)
cd "$DIR"

BASE_VERSION=${BASE_VERSION:-$(grep -E '^POM_VERSION=' gradle.properties | cut -d= -f2)}
VERSION_SUFFIX=${VERSION_SUFFIX:-}
VERSION=${VERSION:-${BASE_VERSION}${VERSION_SUFFIX}}
PUBLISH_DIR="$DIR/maven-central-publish/.work-$VERSION"
rm -rf "$PUBLISH_DIR"
mkdir -p "$PUBLISH_DIR"
cp -f "$DIR/maven-central-publish/pom.xml" "$PUBLISH_DIR/pom.xml"
cp -R "$DIR/maven-central-publish/core" "$PUBLISH_DIR/core"
cp -R "$DIR/maven-central-publish/producer" "$PUBLISH_DIR/producer"
cp -R "$DIR/maven-central-publish/full" "$PUBLISH_DIR/full"

python3 - "$PUBLISH_DIR" "$VERSION" <<'PY'
import pathlib, re, sys

base = pathlib.Path(sys.argv[1])
version = sys.argv[2]
files = [
  "pom.xml",
  "core/pom.xml",
  "producer/pom.xml",
  "full/pom.xml",
]

for rel in files:
  p = base / rel
  s = p.read_text(encoding="utf-8")
  if rel == "pom.xml":
    s = re.sub(
      r"(<artifactId>tls-android-sdk-central-publish</artifactId>\\s*\\n\\s*<version>)([^<]+)(</version>)",
      r"\g<1>%s\g<3>" % version,
      s,
      count=1,
    )
  if "<properties>" in s and "<revision>" in s:
    s = re.sub(r"(<revision>)([^<]+)(</revision>)", r"\g<1>%s\g<3>" % version, s, count=1)
  p.write_text(s, encoding="utf-8")
PY

GRADLE_ARGS=()
if [ -n "${MIN_SDK_OVERRIDE:-}" ]; then
  GRADLE_ARGS+=("-PMIN_SDK_OVERRIDE=${MIN_SDK_OVERRIDE}")
fi
if [ -n "${VERSION_SUFFIX:-}" ]; then
  GRADLE_ARGS+=("-PVERSION_SUFFIX=${VERSION_SUFFIX}")
fi
CORE_ART_DIR="$PUBLISH_DIR/core/target/artifacts"
PRODUCER_ART_DIR="$PUBLISH_DIR/producer/target/artifacts"
FULL_ART_DIR="$PUBLISH_DIR/full/target/artifacts"
mkdir -p "$CORE_ART_DIR" "$PRODUCER_ART_DIR" "$FULL_ART_DIR"

if [ ${#GRADLE_ARGS[@]} -gt 0 ]; then
  ./gradlew :core:assembleRelease :producer:assembleRelease :full:assembleRelease "${GRADLE_ARGS[@]}" >/dev/null
  ./gradlew :core:generateMetadataFileForReleasePublication :producer:generateMetadataFileForReleasePublication :full:generateMetadataFileForReleasePublication "${GRADLE_ARGS[@]}" >/dev/null
else
  ./gradlew :core:assembleRelease :producer:assembleRelease :full:assembleRelease >/dev/null
  ./gradlew :core:generateMetadataFileForReleasePublication :producer:generateMetadataFileForReleasePublication :full:generateMetadataFileForReleasePublication >/dev/null
fi

cp -f "core/build/outputs/aar/core-release.aar" "$CORE_ART_DIR/tls-android-core-$VERSION.aar"
cp -f "producer-lite/build/outputs/aar/producer-release.aar" "$PRODUCER_ART_DIR/tls-android-producer-$VERSION.aar"
cp -f "full/build/outputs/aar/full-release.aar" "$FULL_ART_DIR/tls-android-full-$VERSION.aar"

cp -f "core/build/publications/release/module.json" "$CORE_ART_DIR/tls-android-core-$VERSION.module"
cp -f "producer-lite/build/publications/release/module.json" "$PRODUCER_ART_DIR/tls-android-producer-$VERSION.module"
cp -f "full/build/publications/release/module.json" "$FULL_ART_DIR/tls-android-full-$VERSION.module"

jar cf "$CORE_ART_DIR/tls-android-core-$VERSION-sources.jar" -C core/src/main/java .
jar cf "$PRODUCER_ART_DIR/tls-android-producer-$VERSION-sources.jar" -C producer-lite/src/main/java .
jar cf "$FULL_ART_DIR/tls-android-full-$VERSION-sources.jar" -C full/src/main/java .

EMPTY_DIR="$PUBLISH_DIR/.empty"
mkdir -p "$EMPTY_DIR"
jar cf "$CORE_ART_DIR/tls-android-core-$VERSION-javadoc.jar" -C "$EMPTY_DIR" .
jar cf "$PRODUCER_ART_DIR/tls-android-producer-$VERSION-javadoc.jar" -C "$EMPTY_DIR" .
jar cf "$FULL_ART_DIR/tls-android-full-$VERSION-javadoc.jar" -C "$EMPTY_DIR" .

if [ "${DRY_RUN:-0}" = "1" ]; then
  mvn -q -f "$PUBLISH_DIR/pom.xml" -pl core,producer,full -DskipTests=true -Drevision="$VERSION" package
  echo "[DRY RUN] Built AAR + sources/javadoc jars and validated Maven packaging"
  exit 0
fi

export GPG_TTY="$(tty || true)"

MAVEN_ARGS=()
if [ -n "${PGP_PASSPHRASE:-}" ]; then
  MAVEN_ARGS+=("-Dgpg.passphrase=${PGP_PASSPHRASE}" "-DgpgArguments=--pinentry-mode,loopback")
fi

mvn -q -s ~/.m2/settings.xml -f "$PUBLISH_DIR/pom.xml" -pl core,producer,full -DskipTests=true -Drevision="$VERSION" "${MAVEN_ARGS[@]}" deploy
