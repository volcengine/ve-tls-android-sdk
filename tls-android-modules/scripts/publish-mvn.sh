#!/usr/bin/env bash
set -euo pipefail

DIR=$(cd "$(dirname "$0")"/.. && pwd)
cd "$DIR"

SERVER_ID=${SERVER_ID:-sonatype-nexus-staging}
DEPLOY_URL=${DEPLOY_URL:-https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/}
BASE_VERSION=${BASE_VERSION:-$(grep -E '^POM_VERSION=' gradle.properties | cut -d= -f2)}
VERSION_SUFFIX=${VERSION_SUFFIX:-}
API16_VARIANT=${API16_VARIANT:-0}
case "$API16_VARIANT" in
  1|true|TRUE|yes|YES) API16_VARIANT=1 ;;
  *) API16_VARIANT=0 ;;
esac
if [ "$API16_VARIANT" = "1" ] && [ -z "$VERSION_SUFFIX" ]; then
  VERSION_SUFFIX=-api16
fi
VERSION=${VERSION:-${BASE_VERSION}${VERSION_SUFFIX}}
GPG_KEYID=${PGP_KEYID:-}
GPG_PASSPHRASE=${PGP_PASSPHRASE:-}
TARGET_DIR="$DIR/maven-publish/target"
mkdir -p "$TARGET_DIR"

GRADLE_ARGS=("-PAPI16_VARIANT=${API16_VARIANT}" "-PPOM_VERSION=${BASE_VERSION}")
if [ -n "$VERSION_SUFFIX" ]; then
  GRADLE_ARGS+=("-PVERSION_SUFFIX=${VERSION_SUFFIX}")
fi
./gradlew "${GRADLE_ARGS[@]}" :core:assembleRelease :producer:assembleRelease :full:assembleRelease >/dev/null

jar cf "$TARGET_DIR/tls-android-core-$VERSION-sources.jar" -C core/src/main/java .
jar cf "$TARGET_DIR/tls-android-producer-$VERSION-sources.jar" -C producer-lite/src/main/java .
jar cf "$TARGET_DIR/tls-android-full-$VERSION-sources.jar" -C full/src/main/java .

CORE_AAR="core/build/outputs/aar/core-release.aar"
PRODUCER_AAR="producer-lite/build/outputs/aar/producer-release.aar"
FULL_AAR="full/build/outputs/aar/full-release.aar"

POM_DIR="$TARGET_DIR/poms"
rm -rf "$POM_DIR"
mkdir -p "$POM_DIR"
cp -f maven-publish/pom-core.xml "$POM_DIR/pom-core.xml"
cp -f maven-publish/pom-producer.xml "$POM_DIR/pom-producer.xml"
cp -f maven-publish/pom-full.xml "$POM_DIR/pom-full.xml"
python3 - "$POM_DIR" "$VERSION" "$API16_VARIANT" <<'PY'
import pathlib
import sys

base = pathlib.Path(sys.argv[1])
version = sys.argv[2]
api16_variant = sys.argv[3] == "1"

for path in base.glob("pom-*.xml"):
    text = path.read_text(encoding="utf-8")
    text = text.replace("<version>2.0.4</version>", f"<version>{version}</version>")
    if api16_variant and path.name == "pom-core.xml":
        dependency = """    <dependency>\n      <groupId>org.conscrypt</groupId>\n      <artifactId>conscrypt-android</artifactId>\n      <version>2.5.3</version>\n    </dependency>\n"""
        text = text.replace("  </dependencies>", dependency + "  </dependencies>", 1)
    path.write_text(text, encoding="utf-8")
PY

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
  -DpomFile="$POM_DIR/pom-core.xml" \
  -Dfile="$CORE_AAR" \
  -Dfiles="$TARGET_DIR/tls-android-core-$VERSION-sources.jar" \
  -Dclassifiers=sources -Dtypes=jar

mvn -s ~/.m2/settings.xml -q "${MAVEN_GPG_ARGS[@]}" -Dgpg.executable=gpg gpg:sign-and-deploy-file \
  -Durl="$DEPLOY_URL" -DrepositoryId="$SERVER_ID" \
  -DpomFile="$POM_DIR/pom-producer.xml" \
  -Dfile="$PRODUCER_AAR" \
  -Dfiles="$TARGET_DIR/tls-android-producer-$VERSION-sources.jar" \
  -Dclassifiers=sources -Dtypes=jar

mvn -s ~/.m2/settings.xml -q "${MAVEN_GPG_ARGS[@]}" -Dgpg.executable=gpg gpg:sign-and-deploy-file \
  -Durl="$DEPLOY_URL" -DrepositoryId="$SERVER_ID" \
  -DpomFile="$POM_DIR/pom-full.xml" \
  -Dfile="$FULL_AAR" \
  -Dfiles="$TARGET_DIR/tls-android-full-$VERSION-sources.jar" \
  -Dclassifiers=sources -Dtypes=jar

mvn -s ~/.m2/settings.xml -q -f maven-publish/release-helper-pom.xml nexus-staging:release
