#!/usr/bin/env python3
"""Keep the Android CI matrix and shared release script on the agreed contract."""

import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/android-ci.yml"
RELEASE_SCRIPT = ROOT / "tools/ci-release.sh"


class CiReleaseContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")
        cls.release_script = RELEASE_SCRIPT.read_text(encoding="utf-8")

    def test_matrix_has_required_api_images_and_explicit_api_validation(self):
        expected_images = {
            "API16": ("16", "system-images;android-16;default;x86"),
            "API19": ("19", "system-images;android-19;default;x86"),
            "API35-16KB": ("35", "system-images;android-35;google_apis_ps16k;x86_64"),
        }
        for name, (api, image) in expected_images.items():
            self.assertRegex(
                self.workflow,
                rf"- name: {re.escape(name)}\n\s+api: '{api}'\n\s+image: {re.escape(image)}",
            )

        self.assertIn("MATRIX_API: ${{ matrix.api }}", self.workflow)
        self.assertIn("SYSTEM_IMAGE: ${{ matrix.image }}", self.workflow)
        self.assertIn("EXPECTED_API: ${{ matrix.api }}", self.workflow)
        self.assertIn('expected_prefix="system-images;android-${MATRIX_API};"', self.workflow)

    def test_only_existing_pinned_actions_are_used(self):
        uses = re.findall(r"^\s+uses:\s+(\S+)$", self.workflow, re.MULTILINE)
        self.assertEqual(
            [
                "actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5",
                "actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5",
                "actions/setup-java@c1e323688fd81a25caa38c78aa6df2d33d3e20d9",
            ],
            uses,
        )
        self.assertNotIn("secrets.", self.workflow)
        self.assertNotIn("VE_TLS_ACCESS_KEY", self.workflow)

    def test_release_script_uses_min16_and_checks_device_api(self):
        self.assertIn("readonly MIN_SDK_OVERRIDE=16", self.release_script)
        self.assertIn('-PMIN_SDK_OVERRIDE="$MIN_SDK_OVERRIDE"', self.release_script)
        self.assertEqual(3, self.release_script.count('-PMIN_SDK_OVERRIDE="$MIN_SDK_OVERRIDE"'))
        self.assertIn('--min-sdk "$MIN_SDK_OVERRIDE"', self.release_script)
        self.assertIn(':app:lintRelease', self.release_script)
        self.assertIn(': "${EXPECTED_API:?Set expected Android API}"', self.release_script)
        self.assertIn('getprop ro.build.version.sdk', self.release_script)
        self.assertIn('"$actual_api" != "$EXPECTED_API"', self.release_script)
        for document in ("RELEASE.md", "CONTRIBUTING.md"):
            self.assertIn("EXPECTED_API", (ROOT / document).read_text(encoding="utf-8"))

    def test_device_suite_is_offline_and_covers_existing_bridge_and_benchmark_classes(self):
        expected_classes = (
            "ProducerNativeLoadInstrumentedTest",
            "ProducerRecoveryInstrumentedTest",
            "ProducerLifecycleInstrumentedTest",
            "ProducerRealBenchmarkDeliveryInstrumentedTest",
            "ProducerRealBenchmarkConfigInstrumentedTest",
            "ProducerRealBenchmarkMathInstrumentedTest",
        )
        for class_name in expected_classes:
            self.assertIn(class_name, self.release_script)

        self.assertNotIn("ProducerRealSendInstrumentedTest", self.release_script)
        self.assertNotIn("ProducerRealBenchmarkInstrumentedTest", self.release_script)


if __name__ == "__main__":
    unittest.main()
