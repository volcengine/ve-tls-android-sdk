#!/usr/bin/env python3
"""Verify the shipped BuildConfig, not just the source version manifest."""

import argparse
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--aar", required=True, type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    properties = (root / "tls-android-modules/gradle.properties").read_text()
    version = re.search(r"^POM_VERSION=(.+)$", properties, re.MULTILINE).group(1).strip()
    commit = (root / "tls-android-modules/producer-native/ve-tls-c-sdk.version").read_text().strip()
    with tempfile.TemporaryDirectory(prefix="tls-provenance-") as temporary:
        classes = Path(temporary) / "classes.jar"
        with zipfile.ZipFile(args.aar) as archive:
            classes.write_bytes(archive.read("classes.jar"))
        output = subprocess.check_output(
            ["javap", "-constants", "-classpath", str(classes),
             "com.volcengine.tls.android.producer.BuildConfig"], text=True, timeout=30)
    expected = {"SDK_VERSION": version, "VE_TLS_C_SDK_COMMIT": commit,
                "SDK_USER_AGENT": "volc-tls-android/producer/v" + version,
                "BUILD_TYPE": "release"}
    for name, value in expected.items():
        if f'{name} = "{value}";' not in output:
            raise SystemExit(f"Release BuildConfig mismatch: {name}")
    if "boolean DEBUG = false;" not in output:
        raise SystemExit("Release BuildConfig must not enable DEBUG")
    print("PASS: shipped release BuildConfig matches SDK version, User-Agent and pinned Core")


if __name__ == "__main__":
    main()
