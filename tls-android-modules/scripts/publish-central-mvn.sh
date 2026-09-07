#!/usr/bin/env bash
set -euo pipefail

# Do not let bash -x expand a passphrase while normalizing the legacy input.
PUBLISH_XTRACE=0
case $- in
  *x*) PUBLISH_XTRACE=1; set +x ;;
esac

if [ "${PGP_PASSPHRASE+x}" = x ] && [ "${MAVEN_GPG_PASSPHRASE+x}" = x ]; then
  printf '%s\n' 'PGP_PASSPHRASE and MAVEN_GPG_PASSPHRASE cannot both be set' >&2
  exit 2
fi
if [ "${PGP_PASSPHRASE+x}" = x ]; then
  if [ -n "${PGP_PASSPHRASE:-}" ]; then
    export MAVEN_GPG_PASSPHRASE="$PGP_PASSPHRASE"
  else
    unset MAVEN_GPG_PASSPHRASE
  fi
  unset PGP_PASSPHRASE
elif [ "${MAVEN_GPG_PASSPHRASE+x}" = x ] && [ -z "${MAVEN_GPG_PASSPHRASE:-}" ]; then
  unset MAVEN_GPG_PASSPHRASE
fi

if [ "$PUBLISH_XTRACE" -eq 1 ]; then
  set -x
fi
unset PUBLISH_XTRACE

DIR=$(cd "$(dirname "$0")"/.. && pwd)
cd "$DIR"

BASE_VERSION=${BASE_VERSION:-$(grep -E ^POM_VERSION= gradle.properties | cut -d= -f2)}
VERSION_SUFFIX=${VERSION_SUFFIX:-}
VERSION=${VERSION:-${BASE_VERSION}${VERSION_SUFFIX}}
PUBLISH_DIR="$DIR/maven-central-publish/.work-$VERSION"
rm -rf "$PUBLISH_DIR"
mkdir -p "$PUBLISH_DIR"
cp -f "$DIR/maven-central-publish/pom.xml" "$PUBLISH_DIR/pom.xml"
cp -R "$DIR/maven-central-publish/producer" "$PUBLISH_DIR/producer"

python3 - "$PUBLISH_DIR" "$VERSION" <<INNERPY
import pathlib, re, sys
base = pathlib.Path(sys.argv[1])
version = sys.argv[2]
files = ["pom.xml", "producer/pom.xml"]
for rel in files:
    p = base / rel
    s = p.read_text(encoding="utf-8")
    if rel == "pom.xml":
        s = re.sub(r"(<artifactId>tls-android-sdk-central-publish</artifactId>\s*\n\s*<version>)([^<]+)(</version>)", r"\g<1>%s\g<3>" % version, s, count=1)
    if "<properties>" in s and "<revision>" in s:
        s = re.sub(r"(<revision>)([^<]+)(</revision>)", r"\g<1>%s\g<3>" % version, s, count=1)
    p.write_text(s, encoding="utf-8")
INNERPY

GRADLE_ARGS=()
if [ -n "${MIN_SDK_OVERRIDE:-}" ]; then
  GRADLE_ARGS+=("-PMIN_SDK_OVERRIDE=${MIN_SDK_OVERRIDE}")
fi
if [ -n "${VERSION_SUFFIX:-}" ]; then
  GRADLE_ARGS+=("-PVERSION_SUFFIX=${VERSION_SUFFIX}")
fi
PRODUCER_ART_DIR="$PUBLISH_DIR/producer/target/artifacts"
mkdir -p "$PRODUCER_ART_DIR"

if [ ${#GRADLE_ARGS[@]} -gt 0 ]; then
  ./gradlew :producer-native:assembleRelease :producer-native:generateMetadataFileForReleasePublication "${GRADLE_ARGS[@]}" >/dev/null
else
  ./gradlew :producer-native:assembleRelease :producer-native:generateMetadataFileForReleasePublication >/dev/null
fi

cp -f "producer-native/build/outputs/aar/producer-native-release.aar" "$PRODUCER_ART_DIR/tls-android-producer-$VERSION.aar"
cp -f "producer-native/build/publications/release/module.json" "$PRODUCER_ART_DIR/tls-android-producer-$VERSION.module"
jar cf "$PRODUCER_ART_DIR/tls-android-producer-$VERSION-sources.jar" -C producer-native/src/main/java .
EMPTY_DIR="$PUBLISH_DIR/.empty"
mkdir -p "$EMPTY_DIR"
jar cf "$PRODUCER_ART_DIR/tls-android-producer-$VERSION-javadoc.jar" -C "$EMPTY_DIR" .

if [ "${DRY_RUN:-0}" = "1" ]; then
  mvn -q -f "$PUBLISH_DIR/pom.xml" -pl producer -DskipTests=true -Drevision="$VERSION" package
  echo "[DRY RUN] Built producer AAR + sources/javadoc jars and validated Maven packaging"
  exit 0
fi

export GPG_TTY="$(tty || true)"

mvn -q -s ~/.m2/settings.xml -f "$PUBLISH_DIR/pom.xml" -pl producer -DskipTests=true -Drevision="$VERSION" deploy
