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

SERVER_ID=${SERVER_ID:-sonatype-nexus-staging}
DEPLOY_URL=${DEPLOY_URL:-https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/}
BASE_VERSION=${BASE_VERSION:-$(grep -E ^POM_VERSION= gradle.properties | cut -d= -f2)}
VERSION_SUFFIX=${VERSION_SUFFIX:-}
VERSION=${VERSION:-${BASE_VERSION}${VERSION_SUFFIX}}
GPG_KEYID=${PGP_KEYID:-}
TARGET_DIR="$DIR/maven-publish/target"
mkdir -p "$TARGET_DIR"

./gradlew :producer-native:assembleRelease >/dev/null

jar cf "$TARGET_DIR/tls-android-producer-$VERSION-sources.jar" -C producer-native/src/main/java .

PRODUCER_AAR="producer-native/build/outputs/aar/producer-native-release.aar"
POM_TEMPLATE="maven-publish/pom-producer.xml"
POM_FILE="$TARGET_DIR/tls-android-producer-$VERSION.pom"
python3 - "$POM_TEMPLATE" "$POM_FILE" "$VERSION" <<'INNERPY'
import pathlib, re, sys
src = pathlib.Path(sys.argv[1])
dst = pathlib.Path(sys.argv[2])
version = sys.argv[3]
text = src.read_text(encoding="utf-8")
text = re.sub(r"(<version>)([^<]+)(</version>)", r"\g<1>%s\g<3>" % version, text, count=1)
dst.write_text(text, encoding="utf-8")
INNERPY

if [ "${DRY_RUN:-0}" = "1" ]; then
  echo "[DRY RUN] Built producer AAR and sources jar in $TARGET_DIR"
  echo "[DRY RUN] Effective POM: $POM_FILE"
  exit 0
fi

export GPG_TTY="$(tty || true)"

MAVEN_GPG_ARGS=(
  "-Dgpg.bestPractices=true"
  "-Dgpg.passphraseEnvName=MAVEN_GPG_PASSPHRASE"
  "-Dgpg.useagent=true"
)
if [ -n "$GPG_KEYID" ]; then
  MAVEN_GPG_ARGS+=("-Dgpg.keyname=$GPG_KEYID")
fi

mvn -s ~/.m2/settings.xml -q "${MAVEN_GPG_ARGS[@]}" -Dgpg.executable=gpg \
  org.apache.maven.plugins:maven-gpg-plugin:3.2.8:sign-and-deploy-file \
  -Durl="$DEPLOY_URL" \
  -DrepositoryId="$SERVER_ID" \
  -DpomFile="$POM_FILE" \
  -Dfile="$PRODUCER_AAR" \
  -Dfiles="$TARGET_DIR/tls-android-producer-$VERSION-sources.jar" \
  -Dclassifiers=sources \
  -Dtypes=jar

mvn -s ~/.m2/settings.xml -q -f maven-publish/release-helper-pom.xml nexus-staging:release
