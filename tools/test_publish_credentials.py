#!/usr/bin/env python3
"""Exercise Maven publish credential handling in an isolated temporary copy."""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CENTRAL_SCRIPT = Path("tls-android-modules/scripts/publish-central-mvn.sh")
LEGACY_SCRIPT = Path("tls-android-modules/scripts/publish-mvn.sh")
TEST_SECRET = "unit-test-only-passphrase-not-a-credential"
OTHER_TEST_SECRET = "unit-test-only-conflicting-passphrase"


def fail_if(condition: bool, message: str) -> None:
    if condition:
        raise AssertionError(message)


def redact(text: str) -> str:
    return text.replace(TEST_SECRET, "<redacted>").replace(OTHER_TEST_SECRET, "<redacted>")


def copy_file(repo: Path, relative: str) -> None:
    source = ROOT / relative
    target = repo / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)


def write_executable(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    path.chmod(0o755)


def prepare_repo(parent: Path) -> tuple[Path, Path, Path]:
    repo = parent / "repo"
    for relative in (
        str(CENTRAL_SCRIPT),
        str(LEGACY_SCRIPT),
        "tls-android-modules/maven-central-publish/pom.xml",
        "tls-android-modules/maven-central-publish/producer/pom.xml",
        "tls-android-modules/maven-publish/pom-producer.xml",
        "tls-android-modules/maven-publish/release-helper-pom.xml",
    ):
        copy_file(repo, relative)

    modules = repo / "tls-android-modules"
    (modules / "gradle.properties").write_text("POM_VERSION=9.9.9\n", encoding="utf-8")
    (modules / "producer-native/src/main/java").mkdir(parents=True, exist_ok=True)

    write_executable(
        modules / "gradlew",
        """#!/usr/bin/env bash
set -eu
printf '%s\\n' gradle-called >> "${MOCK_LOG:?}"
mkdir -p producer-native/build/outputs/aar producer-native/build/publications/release
printf '%s\\n' mock-aar > producer-native/build/outputs/aar/producer-native-release.aar
printf '%s\\n' '{}' > producer-native/build/publications/release/module.json
if [ "${MOCK_GRADLE_FAIL:-0}" = 1 ]; then
  exit 17
fi
""",
    )

    bin_dir = parent / "bin"
    write_executable(
        bin_dir / "jar",
        """#!/usr/bin/env bash
set -eu
[ "${1:-}" = cf ]
output=${2:?}
mkdir -p "$(dirname "$output")"
printf '%s\\n' mock-jar > "$output"
printf '%s\\n' jar-called >> "${MOCK_LOG:?}"
""",
    )
    write_executable(
        bin_dir / "mvn",
        """#!/usr/bin/env bash
set -eu
log=${MOCK_LOG:?}
{
  printf '%s\\n' mvn-called
  printf 'mvn-argv:'
  printf ' <%s>' "$@"
  printf '\\n'
  if [ "${MAVEN_GPG_PASSPHRASE+x}" = x ]; then
    printf '%s\\n' maven-env-present=1
  else
    printf '%s\\n' maven-env-present=0
  fi
  if [ "${PGP_PASSPHRASE+x}" = x ]; then
    printf '%s\\n' legacy-env-present=1
  else
    printf '%s\\n' legacy-env-present=0
  fi
  if [ -n "${MOCK_EXPECTED_SECRET:-}" ] && [ "${MAVEN_GPG_PASSPHRASE:-}" = "$MOCK_EXPECTED_SECRET" ]; then
    printf '%s\\n' passphrase-match=1
  else
    printf '%s\\n' passphrase-match=0
  fi
  case " $* " in
    *" org.apache.maven.plugins:maven-gpg-plugin:3.2.8:sign-and-deploy-file "*)
      printf '%s\\n' deploy-call=1
      ;;
    *" deploy "*)
      printf '%s\\n' deploy-call=1
      ;;
    *" nexus-staging:release "*)
      printf '%s\\n' release-call=1
      ;;
    *)
      printf '%s\\n' deploy-call=0
      ;;
  esac
} >> "$log"
if [ "${MOCK_MVN_FAIL:-0}" = 1 ]; then
  exit 19
fi
""",
    )
    return repo, bin_dir, parent / "mock.log"


def run_script(
    repo: Path,
    bin_dir: Path,
    log: Path,
    script: Path,
    variables: dict[str, str] | None = None,
    bash_args: tuple[str, ...] = (),
) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    for name in (
        "PGP_PASSPHRASE",
        "MAVEN_GPG_PASSPHRASE",
        "MOCK_EXPECTED_SECRET",
        "MOCK_GRADLE_FAIL",
        "MOCK_MVN_FAIL",
        "DRY_RUN",
        "BASE_VERSION",
        "VERSION",
        "VERSION_SUFFIX",
        "MIN_SDK_OVERRIDE",
        "PGP_KEYID",
        "SERVER_ID",
        "DEPLOY_URL",
        "BASH_ENV",
    ):
        env.pop(name, None)
    if variables:
        env.update(variables)
    env["PATH"] = str(bin_dir) + os.pathsep + env.get("PATH", "")
    env["HOME"] = str(repo / "home")
    env["MOCK_LOG"] = str(log)
    command = ["bash", *bash_args, str(repo / script)]
    return subprocess.run(
        command,
        cwd=repo,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )


def assert_no_secret(process: subprocess.CompletedProcess[str], log: Path) -> None:
    output = process.stdout + process.stderr
    log_text = log.read_text(encoding="utf-8") if log.exists() else ""
    fail_if(TEST_SECRET in output or OTHER_TEST_SECRET in output, "a test passphrase reached stdout/stderr")
    fail_if(TEST_SECRET in log_text or OTHER_TEST_SECRET in log_text, "a test passphrase reached mock argv/log")


def assert_no_settings(repo: Path) -> None:
    settings = list((repo / "home").rglob("settings.xml"))
    fail_if(settings, "the publish script created a settings.xml")


def assert_success(process: subprocess.CompletedProcess[str], context: str) -> None:
    if process.returncode != 0:
        raise AssertionError(f"{context} failed: {redact(process.stdout + process.stderr)}")


def assert_failure(process: subprocess.CompletedProcess[str], context: str) -> None:
    if process.returncode == 0:
        raise AssertionError(f"{context} unexpectedly succeeded")


def check_static_contract() -> None:
    central = (ROOT / CENTRAL_SCRIPT).read_text(encoding="utf-8")
    legacy = (ROOT / LEGACY_SCRIPT).read_text(encoding="utf-8")
    pom = (ROOT / "tls-android-modules/maven-central-publish/producer/pom.xml").read_text(encoding="utf-8")

    for text in (central, legacy):
        fail_if("-Dgpg.passphrase=" in text, "a publish script still places a passphrase in a Maven property")
        fail_if("gpg:sign-and-deploy-file" in text, "the legacy goal is not version-pinned")
        fail_if("settings.xml" not in text, "the existing Maven settings input was removed")
    fail_if(
        "org.apache.maven.plugins:maven-gpg-plugin:3.2.8:sign-and-deploy-file" not in legacy,
        "the legacy publish goal is not pinned to maven-gpg-plugin 3.2.8",
    )
    for required in (
        "-Dgpg.bestPractices=true",
        "-Dgpg.passphraseEnvName=MAVEN_GPG_PASSPHRASE",
        "-Dgpg.useagent=true",
    ):
        fail_if(required not in legacy, f"legacy publish command lacks {required}")
    for required in (
        "<artifactId>maven-gpg-plugin</artifactId>",
        "<version>3.2.8</version>",
        "<bestPractices>true</bestPractices>",
        "<passphraseEnvName>MAVEN_GPG_PASSPHRASE</passphraseEnvName>",
        "<useAgent>true</useAgent>",
    ):
        fail_if(required not in pom, f"producer POM lacks {required}")


def check_old_variable_maps(script: Path) -> None:
    with tempfile.TemporaryDirectory(prefix="publish-credentials-") as directory:
        repo, bin_dir, log = prepare_repo(Path(directory))
        process = run_script(
            repo,
            bin_dir,
            log,
            script,
            {"PGP_PASSPHRASE": TEST_SECRET, "MOCK_EXPECTED_SECRET": TEST_SECRET},
        )
        assert_success(process, f"legacy mapping for {script.name}")
        assert_no_secret(process, log)
        log_text = log.read_text(encoding="utf-8")
        fail_if("passphrase-match=1" not in log_text, "the legacy passphrase was not mapped to Maven's env name")
        fail_if("legacy-env-present=1" in log_text, "the legacy passphrase remained in the Maven environment")
        fail_if("deploy-call=1" not in log_text, "the non-dry-run path did not reach the mock deploy")
        assert_no_settings(repo)


def check_new_variable_and_agent(script: Path) -> None:
    with tempfile.TemporaryDirectory(prefix="publish-credentials-") as directory:
        repo, bin_dir, log = prepare_repo(Path(directory))
        process = run_script(
            repo,
            bin_dir,
            log,
            script,
            {"MAVEN_GPG_PASSPHRASE": TEST_SECRET, "MOCK_EXPECTED_SECRET": TEST_SECRET},
        )
        assert_success(process, f"new env variable for {script.name}")
        assert_no_secret(process, log)
        log_text = log.read_text(encoding="utf-8")
        fail_if("passphrase-match=1" not in log_text, "the new Maven passphrase was not passed to Maven")
        fail_if("legacy-env-present=1" in log_text, "the legacy passphrase unexpectedly reached Maven")
        assert_no_settings(repo)

    with tempfile.TemporaryDirectory(prefix="publish-agent-") as directory:
        repo, bin_dir, log = prepare_repo(Path(directory))
        process = run_script(repo, bin_dir, log, script)
        assert_success(process, f"agent-only path for {script.name}")
        assert_no_secret(process, log)
        log_text = log.read_text(encoding="utf-8")
        fail_if("maven-env-present=1" in log_text, "the agent-only path supplied an empty Maven passphrase")
        assert_no_settings(repo)

    for empty_variable in ("PGP_PASSPHRASE", "MAVEN_GPG_PASSPHRASE"):
        with tempfile.TemporaryDirectory(prefix="publish-agent-empty-") as directory:
            repo, bin_dir, log = prepare_repo(Path(directory))
            process = run_script(repo, bin_dir, log, script, {empty_variable: ""})
            assert_success(process, f"empty {empty_variable} agent path for {script.name}")
            assert_no_secret(process, log)
            log_text = log.read_text(encoding="utf-8")
            fail_if("maven-env-present=1" in log_text, "an empty passphrase reached Maven")
            assert_no_settings(repo)


def check_conflict_fails_closed(script: Path) -> None:
    with tempfile.TemporaryDirectory(prefix="publish-conflict-") as directory:
        repo, bin_dir, log = prepare_repo(Path(directory))
        process = run_script(
            repo,
            bin_dir,
            log,
            script,
            {
                "PGP_PASSPHRASE": TEST_SECRET,
                "MAVEN_GPG_PASSPHRASE": OTHER_TEST_SECRET,
                "MOCK_EXPECTED_SECRET": TEST_SECRET,
            },
        )
        assert_failure(process, f"conflicting variables for {script.name}")
        assert_no_secret(process, log)
        fail_if(log.exists(), "a conflicting credential configuration reached a mock subprocess")
        assert_no_settings(repo)


def check_xtrace_and_dry_run(script: Path) -> None:
    with tempfile.TemporaryDirectory(prefix="publish-xtrace-") as directory:
        repo, bin_dir, log = prepare_repo(Path(directory))
        process = run_script(
            repo,
            bin_dir,
            log,
            script,
            {
                "PGP_PASSPHRASE": TEST_SECRET,
                "MOCK_EXPECTED_SECRET": TEST_SECRET,
                "DRY_RUN": "1",
            },
            bash_args=("-x",),
        )
        assert_success(process, f"bash -x dry run for {script.name}")
        assert_no_secret(process, log)
        log_text = log.read_text(encoding="utf-8") if log.exists() else ""
        fail_if("deploy-call=1" in log_text or "release-call=1" in log_text, "DRY_RUN invoked a deploy/release goal")
        assert_no_settings(repo)


def check_maven_failure_propagates(script: Path) -> None:
    with tempfile.TemporaryDirectory(prefix="publish-failure-") as directory:
        repo, bin_dir, log = prepare_repo(Path(directory))
        process = run_script(
            repo,
            bin_dir,
            log,
            script,
            {
                "MAVEN_GPG_PASSPHRASE": TEST_SECRET,
                "MOCK_EXPECTED_SECRET": TEST_SECRET,
                "MOCK_MVN_FAIL": "1",
            },
        )
        assert_failure(process, f"Maven failure propagation for {script.name}")
        assert_no_secret(process, log)
        fail_if(not log.exists(), "the Maven failure case did not invoke mock Maven")
        assert_no_settings(repo)


def main() -> int:
    check_static_contract()
    for script in (CENTRAL_SCRIPT, LEGACY_SCRIPT):
        check_old_variable_maps(script)
        check_new_variable_and_agent(script)
        check_conflict_fails_closed(script)
        check_xtrace_and_dry_run(script)
        check_maven_failure_propagates(script)
    print(
        "publish credential tests: 11 checks passed "
        "(mapping, agent-only, fail-closed, Maven failure, bash -x, DRY_RUN; both scripts)"
    )
    return 0


def test_publish_credentials() -> None:
    """Pytest collection entry point; all subprocesses still use temporary copies."""
    main()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as error:
        print(f"FAIL: {redact(str(error))}", file=sys.stderr)
        raise SystemExit(1)
