#!/usr/bin/env python3
"""Unit tests for the offline Android release AAR checker."""

from __future__ import annotations

import io
import os
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


TOOLS = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS))

from check_android_release import (  # noqa: E402
    ABI_SPECS,
    EXPECTED_ABIS,
    LICENSE_ENTRY,
    NOTICE_ENTRY,
    verify_aar,
)


MANIFEST_TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-sdk android:minSdkVersion="{min_sdk}" />
</manifest>
"""
APACHE_LICENSE = "Apache License\nVersion 2.0, January 2004\n"
LZ4_NOTICE = "LZ4\nBSD 2-Clause License\n"


def make_elf(
    bits: int,
    alignment: int = 16 * 1024,
    residue: int = 0,
    machine: int | None = None,
) -> bytes:
    """Build a small ELF with two PT_LOAD entries for alignment coverage."""

    if machine is None:
        machine = next(expected_machine for expected_bits, expected_machine in ABI_SPECS.values() if expected_bits == bits)
    if bits == 64:
        ident = b"\x7fELF" + bytes((2, 1, 1, 0)) + b"\x00" * 8
        header_size = 64
        entry_size = 56
        header = struct.pack(
            "<16sHHIQQQIHHHHHH",
            ident,
            3,
            machine,
            1,
            0,
            header_size,
            0,
            0,
            header_size,
            entry_size,
            2,
            0,
            0,
            0,
        )
        data = bytearray(header + b"\x00" * (entry_size * 2))
        segments = (
            (0x0000, 0x4000 + residue),
            (0x0040, 0x4040 + residue),
        )
        for index, (file_offset, virtual_address) in enumerate(segments):
            entry = struct.pack(
                "<IIQQQQQQ",
                1,
                5,
                file_offset,
                virtual_address,
                0,
                0,
                0,
                alignment,
            )
            start = header_size + index * entry_size
            data[start : start + entry_size] = entry
        return bytes(data)

    ident = b"\x7fELF" + bytes((1, 1, 1, 0)) + b"\x00" * 8
    header_size = 52
    entry_size = 32
    header = struct.pack(
        "<16sHHIIIIIHHHHHH",
        ident,
        3,
        machine,
        1,
        0,
        header_size,
        0,
        0,
        header_size,
        entry_size,
        1,
        0,
        0,
        0,
    )
    entry = struct.pack("<IIIIIIII", 1, 0, 0x1000, 0, 0, 0, 5, 4096)
    return header + entry


def make_classes_jar(include_license: bool = True, include_notice: bool = True) -> bytes:
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("com/volcengine/tls/BuildConfig.class", b"BuildConfig placeholder")
        if include_license:
            archive.writestr(LICENSE_ENTRY, APACHE_LICENSE)
        if include_notice:
            archive.writestr(NOTICE_ENTRY, LZ4_NOTICE)
    return stream.getvalue()


def make_aar(
    directory: Path,
    *,
    min_sdk: int = 19,
    abis: tuple[str, ...] = EXPECTED_ABIS,
    elf_data: dict[str, bytes] | None = None,
    include_license: bool = True,
    include_notice: bool = True,
    extra_entries: dict[str, bytes] | None = None,
) -> Path:
    path = directory / "fixture.aar"
    elf_data = elf_data or {}
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("AndroidManifest.xml", MANIFEST_TEMPLATE.format(min_sdk=min_sdk))
        archive.writestr(
            "classes.jar",
            make_classes_jar(include_license=include_license, include_notice=include_notice),
        )
        for abi in abis:
            bits = ABI_SPECS[abi][0]
            archive.writestr(
                f"jni/{abi}/libtls.so",
                elf_data.get(abi, make_elf(bits, machine=ABI_SPECS[abi][1])),
            )
        for name, data in (extra_entries or {}).items():
            archive.writestr(name, data)
    return path


class CheckAndroidReleaseTest(unittest.TestCase):
    def test_valid_16k_64_bit_and_present_32_bit_abis(self) -> None:
        with tempfile.TemporaryDirectory(prefix="check-aar-") as temp:
            result = verify_aar(make_aar(Path(temp)))
        self.assertFalse(result.errors, result.errors)
        self.assertTrue(any("arm64-v8a" in check for check in result.checks))
        self.assertTrue(any("x86_64" in check for check in result.checks))
        self.assertTrue(any("ELF32" in check for check in result.checks))
        self.assertTrue(
            any("BuildConfig version requires independent verification" in check for check in result.checks)
        )

    def test_old_4k_64_bit_elf_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory(prefix="check-aar-") as temp:
            path = make_aar(
                Path(temp),
                elf_data={"arm64-v8a": make_elf(64, alignment=4096)},
            )
            result = verify_aar(path)
        self.assertTrue(any("16 KiB" in error for error in result.errors), result.errors)

    def test_all_64_bit_pt_load_segments_require_matching_residue(self) -> None:
        with tempfile.TemporaryDirectory(prefix="check-aar-") as temp:
            path = make_aar(
                Path(temp),
                elf_data={
                    "x86_64": make_elf(64, residue=1, machine=ABI_SPECS["x86_64"][1])
                },
            )
            result = verify_aar(path)
        self.assertTrue(any("residue" in error for error in result.errors), result.errors)

    def test_64_bit_p_align_must_be_power_of_two_and_congruent(self) -> None:
        with tempfile.TemporaryDirectory(prefix="check-aar-") as temp:
            for elf, expected in (
                (
                    make_elf(64, alignment=24576, machine=ABI_SPECS["x86_64"][1]),
                    "power of two",
                ),
                (
                    make_elf(64, alignment=32768, machine=ABI_SPECS["x86_64"][1]),
                    "p_align",
                ),
            ):
                path = make_aar(Path(temp), elf_data={"x86_64": elf})
                result = verify_aar(path)
                self.assertTrue(any(expected in error for error in result.errors), result.errors)

    def test_bad_and_truncated_elf_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory(prefix="check-aar-") as temp:
            for data in (b"not-elf", make_elf(64)[:20]):
                path = make_aar(Path(temp), elf_data={"arm64-v8a": data})
                result = verify_aar(path)
                self.assertTrue(
                    any("ELF" in error or "elf" in error for error in result.errors),
                    result.errors,
                )

    def test_missing_abi_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory(prefix="check-aar-") as temp:
            path = make_aar(Path(temp), abis=tuple(abi for abi in EXPECTED_ABIS if abi != "x86"))
            result = verify_aar(path)
        self.assertTrue(any("JNI ABI x86 is missing" in error for error in result.errors))

    def test_min_sdk_match_mismatch_and_special_16_value(self) -> None:
        with tempfile.TemporaryDirectory(prefix="check-aar-") as temp:
            path_19 = make_aar(Path(temp), min_sdk=19)
            self.assertFalse(verify_aar(path_19, 19).errors)
            mismatch = verify_aar(path_19, 16)
            self.assertTrue(any("minSdkVersion=19" in error for error in mismatch.errors))

            path_16 = make_aar(Path(temp), min_sdk=16)
            self.assertFalse(verify_aar(path_16, 16).errors)

            path_17 = make_aar(Path(temp), min_sdk=17)
            self.assertFalse(verify_aar(path_17, 17).errors)

            path_18 = make_aar(Path(temp), min_sdk=18)
            self.assertFalse(verify_aar(path_18, 18).errors)

            with self.assertRaises(ValueError):
                verify_aar(path_19, 15)

    def test_missing_notice_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory(prefix="check-aar-") as temp:
            path = make_aar(Path(temp), include_notice=False)
            result = verify_aar(path)
        self.assertTrue(any("THIRD_PARTY_NOTICES.md" in error for error in result.errors))

    def test_debug_test_and_credential_config_files_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory(prefix="check-aar-") as temp:
            path = make_aar(
                Path(temp),
                extra_entries={
                    "debug.properties": b"debug=true",
                    "res/raw/test-config.json": b"{}",
                    "credentials.properties": b"accessKey=redacted",
                    "real_tls.properties": b"fixture",
                    "tls_config.properties": b"fixture",
                    "release.keystore": b"fixture",
                    "signing.jks": b"fixture",
                    "ca.pem": b"fixture",
                    "private.key": b"fixture",
                },
            )
            result = verify_aar(path)
        self.assertEqual(9, sum("forbidden" in error for error in result.errors), result.errors)

    def test_generic_secret_scan_is_not_enabled(self) -> None:
        with tempfile.TemporaryDirectory(prefix="check-aar-") as temp:
            path = make_aar(Path(temp), extra_entries={"secret-value.json": b"fixture"})
            result = verify_aar(path)
        self.assertFalse(result.errors, result.errors)

    def test_cli_returns_nonzero_without_leaking_payload(self) -> None:
        with tempfile.TemporaryDirectory(prefix="check-aar-") as temp:
            path = make_aar(
                Path(temp),
                abis=tuple(abi for abi in EXPECTED_ABIS if abi != "x86"),
                extra_entries={"credentials.properties": b"secret-value-must-not-be-printed"},
            )
            environment = os.environ.copy()
            environment["PYTHONDONTWRITEBYTECODE"] = "1"
            process = subprocess.run(
                [sys.executable, str(TOOLS / "check_android_release.py"), "--aar", str(path)],
                env=environment,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )
        self.assertNotEqual(0, process.returncode)
        self.assertNotIn("secret-value-must-not-be-printed", process.stdout + process.stderr)
        self.assertIn("JNI ABI x86 is missing", process.stderr)


if __name__ == "__main__":
    unittest.main()
