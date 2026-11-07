#!/usr/bin/env python3
"""Keep the standard API16 build and publication inputs in agreement."""

import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULES = ROOT / "tls-android-modules"
POM_NS = {"m": "http://maven.apache.org/POM/4.0.0"}


class Api16ReleaseContractTest(unittest.TestCase):
    def test_standard_build_defaults_to_api16_without_legacy_coordinate(self):
        build = (MODULES / "producer-native/build.gradle").read_text()
        self.assertRegex(build, r"findProperty\('MIN_SDK_OVERRIDE'\)\s*\?:\s*16")
        self.assertIn("minSdkValue < 16", build)
        self.assertNotIn("tls-android-producer-legacy", build)
        self.assertIn("'tls-android-producer'", build)
        self.assertNotIn("conscrypt", build.lower())

    def test_sample_uses_standard_api16_artifact(self):
        build = (ROOT / "producer-integration-sample/app/build.gradle").read_text()
        self.assertRegex(build, r"findProperty\('MIN_SDK_OVERRIDE'\)\s*\?:\s*'16'")
        self.assertNotIn("tls-android-producer-legacy", build)
        self.assertIn("'tls-android-producer'", build)

    def test_release_version_inputs_agree(self):
        properties = (MODULES / "gradle.properties").read_text()
        version = re.search(r"^POM_VERSION=(.+)$", properties, re.MULTILINE).group(1)
        build = (MODULES / "producer-native/build.gradle").read_text()
        sample = (ROOT / "producer-integration-sample/app/build.gradle").read_text()
        self.assertIn(f"findProperty('POM_VERSION') ?: '{version}'", build)
        self.assertIn(f"findProperty('SDK_VERSION') ?: '{version}'", sample)
        for relative, element in (
            ("maven-central-publish/pom.xml", "m:version"),
            ("maven-central-publish/producer/pom.xml", "m:properties/m:revision"),
            ("maven-publish/pom-producer.xml", "m:version"),
        ):
            pom = ET.parse(MODULES / relative).getroot()
            self.assertEqual(version, pom.findtext(element, namespaces=POM_NS), relative)


if __name__ == "__main__":
    unittest.main()
