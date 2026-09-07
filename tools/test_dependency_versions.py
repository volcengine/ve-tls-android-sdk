#!/usr/bin/env python3
"""Keep the four reviewed dependency advisories out of release manifests."""

from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
MINIMUM = {
    "com.alibaba:fastjson": (1, 2, 84),
    "com.google.protobuf:protobuf-java": (3, 25, 8),
    "com.google.protobuf:protobuf-java-util": (3, 25, 8),
    "com.google.protobuf:protobuf-javalite": (3, 25, 8),
    "at.yawk.lz4:lz4-java": (1, 10, 1),
}
OBSOLETE_LZ4 = {"net.jpountz.lz4:lz4", "org.lz4:lz4-java", "org.lz4:lz4-pure-java"}


def checked_version(value):
    if not re.fullmatch(r"\d+\.\d+\.\d+", value):
        raise ValueError("Dependency version must be an explicit stable version: " + value)
    return tuple(map(int, value.split(".")))


class DependencyVersionsTest(unittest.TestCase):
    def test_root_pom_uses_patched_versions(self):
        root = ET.parse(ROOT / "pom.xml").getroot()
        properties = {
            node.tag.rsplit("}", 1)[-1]: node.text
            for node in root.find("m:properties", NS)
        }
        found = set()
        for dependency in root.findall("m:dependencies/m:dependency", NS):
            coordinate = ":".join(dependency.findtext("m:" + key, namespaces=NS)
                                  for key in ("groupId", "artifactId"))
            self.assertNotIn(coordinate, OBSOLETE_LZ4)
            if coordinate not in MINIMUM:
                continue
            version = dependency.findtext("m:version", namespaces=NS)
            if version.startswith("${") and version.endswith("}"):
                version = properties[version[2:-1]]
            with self.subTest(coordinate=coordinate):
                self.assertGreaterEqual(checked_version(version), MINIMUM[coordinate])
            found.add(coordinate)
        self.assertTrue({"com.alibaba:fastjson", "com.google.protobuf:protobuf-java",
                         "com.google.protobuf:protobuf-java-util", "at.yawk.lz4:lz4-java"} <= found)

    def test_gradle_manifests_do_not_reintroduce_vulnerable_versions(self):
        manifests = list((ROOT / "tls-android-modules").glob("*/build.gradle"))
        manifests.append(ROOT / "producer-integration-sample/app/build.gradle")
        found = set()
        for manifest in manifests:
            text = manifest.read_text()
            for coordinate in OBSOLETE_LZ4:
                self.assertFalse(coordinate + ":" in text,
                                 f"{manifest}: obsolete dependency {coordinate}")
            for coordinate, minimum in MINIMUM.items():
                for version in re.findall(re.escape(coordinate) + r":([^'\"\s]+)", text):
                    with self.subTest(manifest=str(manifest), coordinate=coordinate):
                        self.assertGreaterEqual(checked_version(version), minimum)
                    found.add(coordinate)
        self.assertTrue({"com.alibaba:fastjson", "com.google.protobuf:protobuf-javalite",
                         "at.yawk.lz4:lz4-java"} <= found)

    def test_producer_and_examples_do_not_depend_on_legacy_java_codecs(self):
        for path in ("tls-android-modules/producer-native/build.gradle",
                     "tls-android-modules/app/build.gradle",
                     "tls-android-modules/app-no-sdk/build.gradle",
                     "producer-integration-sample/app/build.gradle",
                     "tls-android-modules/maven-central-publish/producer/pom.xml"):
            text = (ROOT / path).read_text()
            for package in ("com.alibaba", "com.google.protobuf", "net.jpountz.lz4",
                            "org.lz4", "at.yawk.lz4"):
                self.assertFalse(package in text, f"{path}: unexpected dependency {package}")


if __name__ == "__main__":
    unittest.main()
