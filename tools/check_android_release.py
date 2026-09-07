#!/usr/bin/env python3
"""Offline checks for the contents of an Android Producer AAR.

The checker intentionally uses only the Python standard library.  It reads the
AAR and its nested classes.jar in place; it never extracts, signs, uploads, or
contacts a network service.
"""

from __future__ import annotations

import argparse
import io
import re
import struct
import sys
import zipfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path


ANDROID_XMLNS = "http://schemas.android.com/apk/res/android"
EXPECTED_ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
ABI_SPECS = {
    "arm64-v8a": (64, 183),  # EM_AARCH64
    "armeabi-v7a": (32, 40),  # EM_ARM
    "x86": (32, 3),  # EM_386
    "x86_64": (64, 62),  # EM_X86_64
}
LICENSE_ENTRY = "META-INF/tls-android-producer/LICENSE.txt"
NOTICE_ENTRY = "META-INF/tls-android-producer/THIRD_PARTY_NOTICES.md"
PAGE_SIZE_16K = 16 * 1024
PT_LOAD = 1

CONFIG_SUFFIXES = frozenset(
    {
        ".cfg",
        ".conf",
        ".env",
        ".ini",
        ".json",
        ".properties",
        ".toml",
        ".xml",
        ".yaml",
        ".yml",
    }
)
CONFIG_MARKERS = frozenset(
    {
        "credential",
        "credentials",
        "debug",
        "debuggable",
        "test",
        "tests",
    }
)
MARKER_PREFIXES = tuple(sorted(CONFIG_MARKERS, key=len, reverse=True))
FORBIDDEN_EXACT_NAMES = frozenset({"real_tls.properties", "tls_config.properties"})
FORBIDDEN_SUFFIXES = (".keystore", ".jks", ".pem", ".key")


class AARInputError(ValueError):
    """The input cannot be treated as a readable AAR archive."""


class ElfError(ValueError):
    """The bytes do not contain a structurally readable ELF image."""


@dataclass(frozen=True)
class LoadSegment:
    offset: int
    virtual_address: int
    alignment: int


@dataclass(frozen=True)
class ElfInfo:
    bits: int
    machine: int
    load_segments: tuple[LoadSegment, ...]


@dataclass(frozen=True)
class VerificationResult:
    checks: tuple[str, ...]
    errors: tuple[str, ...]


def _display_entry(name: str) -> str:
    """Keep diagnostics bounded and free of archive payload data."""

    normalized = name.replace("\\", "/")
    if len(normalized) <= 160:
        return normalized
    return normalized[:157] + "..."


def _is_forbidden_config(name: str) -> bool:
    if name.endswith("/"):
        return False

    normalized = name.replace("\\", "/").lower()
    basename = normalized.rsplit("/", 1)[-1]
    if basename in FORBIDDEN_EXACT_NAMES or basename.endswith(FORBIDDEN_SUFFIXES):
        return True
    suffix = "." + basename.rsplit(".", 1)[-1] if "." in basename else ""
    if suffix not in CONFIG_SUFFIXES:
        return False
    if basename in CONFIG_MARKERS:
        return True
    if basename.startswith(MARKER_PREFIXES):
        return True
    components = [component for component in re.split(r"[/._-]+", normalized) if component]
    return any(component in CONFIG_MARKERS for component in components)


def _forbidden_configs(names: list[str] | tuple[str, ...]) -> list[str]:
    return sorted(name for name in names if _is_forbidden_config(name))


def parse_elf(data: bytes) -> ElfInfo:
    """Parse the ELF header and program headers without external binutils."""

    if len(data) < 16:
        raise ElfError("ELF header is truncated")
    if data[:4] != b"\x7fELF":
        raise ElfError("file is not an ELF image")
    if data[5] != 1:
        raise ElfError("ELF is not little-endian")
    if data[6] != 1:
        raise ElfError("ELF identification version is invalid")

    elf_class = data[4]
    if elf_class == 2:
        bits = 64
        header_format = "<16sHHIQQQIHHHHHH"
        header_size = 64
        program_format = "<IIQQQQQQ"
        program_size = 56
    elif elf_class == 1:
        bits = 32
        header_format = "<16sHHIIIIIHHHHHH"
        header_size = 52
        program_format = "<IIIIIIII"
        program_size = 32
    else:
        raise ElfError(f"unsupported ELF class {elf_class}")

    if len(data) < header_size:
        raise ElfError("ELF header is truncated")
    try:
        header = struct.unpack_from(header_format, data, 0)
    except struct.error:
        raise ElfError("ELF header is malformed") from None

    machine = header[2]
    header_version = header[3]
    program_offset = header[5]
    entry_size = header[9]
    entry_count = header[10]
    if header_version != 1:
        raise ElfError("ELF header version is invalid")
    if entry_size < program_size:
        raise ElfError("ELF program-header entry size is invalid")
    if program_offset > len(data):
        raise ElfError("ELF program-header table is truncated")
    table_size = entry_size * entry_count
    if table_size > len(data) - program_offset:
        raise ElfError("ELF program-header table is truncated")

    load_segments: list[LoadSegment] = []
    for index in range(entry_count):
        entry_offset = program_offset + index * entry_size
        try:
            fields = struct.unpack_from(program_format, data, entry_offset)
        except struct.error:
            raise ElfError(f"ELF program header {index} is truncated") from None

        if bits == 64:
            segment_type, _, file_offset, virtual_address, _, file_size, memory_size, alignment = fields
        else:
            segment_type, file_offset, virtual_address, _, file_size, memory_size, _, alignment = fields
        if segment_type != PT_LOAD:
            continue
        if memory_size < file_size:
            raise ElfError(f"ELF PT_LOAD {index} has invalid file/memory size")
        if file_offset > len(data) or file_size > len(data) - file_offset:
            raise ElfError(f"ELF PT_LOAD {index} extends past the file")
        load_segments.append(LoadSegment(file_offset, virtual_address, alignment))

    if not load_segments:
        raise ElfError("ELF has no PT_LOAD program header")
    return ElfInfo(bits, machine, tuple(load_segments))


def _check_elf_for_abi(data: bytes, abi: str, entry_name: str) -> tuple[int, int]:
    try:
        info = parse_elf(data)
    except ElfError as exc:
        raise ElfError(f"{_display_entry(entry_name)}: {exc}") from None

    expected_bits, expected_machine = ABI_SPECS[abi]
    if info.bits != expected_bits:
        raise ElfError(
            f"{_display_entry(entry_name)}: expected ELF{expected_bits}, found ELF{info.bits}"
        )
    if info.machine != expected_machine:
        raise ElfError(
            f"{_display_entry(entry_name)}: ELF machine {info.machine} does not match {abi}"
        )

    if info.bits == 64:
        for index, segment in enumerate(info.load_segments):
            if segment.alignment < PAGE_SIZE_16K:
                raise ElfError(
                    f"{_display_entry(entry_name)}: ELF64 PT_LOAD {index} has "
                    f"p_align={segment.alignment}, requires at least 16 KiB"
                )
            if segment.alignment & (segment.alignment - 1):
                raise ElfError(
                    f"{_display_entry(entry_name)}: ELF64 PT_LOAD {index} has "
                    f"p_align={segment.alignment}, which is not a power of two"
                )
            residue = (segment.virtual_address - segment.offset) % segment.alignment
            if residue != 0:
                raise ElfError(
                    f"{_display_entry(entry_name)}: ELF64 PT_LOAD {index} has "
                    f"vaddr-offset residue {residue}, requires 0 modulo p_align"
                )
    return info.bits, len(info.load_segments)


def _manifest_min_sdk(data: bytes) -> int:
    try:
        root = ET.fromstring(data)
    except ET.ParseError:
        raise ValueError("AndroidManifest.xml is not readable text XML") from None
    if root.tag.rsplit("}", 1)[-1] != "manifest":
        raise ValueError("AndroidManifest.xml root element is not manifest")

    uses_sdk = [element for element in root.iter() if element.tag.rsplit("}", 1)[-1] == "uses-sdk"]
    if not uses_sdk:
        raise ValueError("AndroidManifest.xml has no uses-sdk element")
    values = []
    for element in uses_sdk:
        raw_value = element.attrib.get(f"{{{ANDROID_XMLNS}}}minSdkVersion")
        if raw_value is None:
            raw_value = element.attrib.get("minSdkVersion")
        if raw_value is None:
            raise ValueError("AndroidManifest.xml has no numeric minSdkVersion")
        try:
            values.append(int(raw_value))
        except ValueError:
            raise ValueError("AndroidManifest.xml minSdkVersion is not numeric") from None
    if len(set(values)) != 1:
        raise ValueError("AndroidManifest.xml has conflicting minSdkVersion values")
    return values[0]


def _check_classes_jar(
    classes_data: bytes,
    checks: list[str],
    errors: list[str],
) -> None:
    try:
        with zipfile.ZipFile(io.BytesIO(classes_data), "r") as classes:
            names = classes.namelist()
            duplicate_names = sorted({name for name in names if names.count(name) > 1})
            if duplicate_names:
                errors.append("classes.jar contains duplicate entries")
            corrupt_entry = classes.testzip()
            if corrupt_entry is not None:
                errors.append(
                    "classes.jar contains a corrupt entry: " + _display_entry(corrupt_entry)
                )

            for name in _forbidden_configs(names):
                errors.append(
                    "forbidden release config/key material in classes.jar: "
                    + _display_entry(name)
                )

            if LICENSE_ENTRY not in names:
                errors.append("classes.jar is missing Apache license resource: LICENSE.txt")
            else:
                license_text = classes.read(LICENSE_ENTRY).lower()
                if b"apache license" not in license_text or b"version 2.0" not in license_text:
                    errors.append("classes.jar LICENSE.txt is missing Apache License 2.0 text")
                else:
                    checks.append("classes.jar contains Apache License 2.0 resource")

            if NOTICE_ENTRY not in names:
                errors.append("classes.jar is missing LZ4 notice resource: THIRD_PARTY_NOTICES.md")
            else:
                notice_text = classes.read(NOTICE_ENTRY).lower()
                if b"lz4" not in notice_text or b"bsd 2-clause" not in notice_text:
                    errors.append("classes.jar THIRD_PARTY_NOTICES.md is missing LZ4 notice text")
                else:
                    checks.append("classes.jar contains LZ4 third-party notice resource")
    except (OSError, zipfile.BadZipFile):
        errors.append("classes.jar is not a readable ZIP archive")


def _validate_requested_min_sdk(min_sdk: int) -> None:
    if min_sdk < 16:
        raise ValueError("--min-sdk must be an integer >= 16")


def verify_aar(aar_path: str | Path, min_sdk: int = 19) -> VerificationResult:
    """Return deterministic checks and errors for one AAR."""

    _validate_requested_min_sdk(min_sdk)
    path = Path(aar_path)
    if not path.is_file():
        raise AARInputError("AAR file cannot be read")

    checks: list[str] = []
    errors: list[str] = []
    try:
        archive = zipfile.ZipFile(path, "r")
    except (OSError, zipfile.BadZipFile):
        raise AARInputError("input is not a readable AAR ZIP archive") from None

    with archive:
        names = archive.namelist()
        duplicate_names = sorted({name for name in names if names.count(name) > 1})
        if duplicate_names:
            errors.append("AAR contains duplicate entries")
        try:
            corrupt_entry = archive.testzip()
        except (OSError, zipfile.BadZipFile):
            corrupt_entry = "<unreadable entry>"
        if corrupt_entry is not None:
            errors.append("AAR contains a corrupt entry: " + _display_entry(corrupt_entry))
        checks.append("AAR ZIP is readable")

        for name in _forbidden_configs(names):
            errors.append(
                "forbidden release config/key material in AAR: " + _display_entry(name)
            )

        if "AndroidManifest.xml" not in names:
            errors.append("AAR is missing AndroidManifest.xml")
        else:
            try:
                actual_min_sdk = _manifest_min_sdk(archive.read("AndroidManifest.xml"))
            except (KeyError, OSError, ValueError, zipfile.BadZipFile) as exc:
                errors.append("AndroidManifest.xml check failed: " + str(exc))
            else:
                if actual_min_sdk != min_sdk:
                    errors.append(
                        f"AndroidManifest minSdkVersion={actual_min_sdk} does not match "
                        f"expected {min_sdk}"
                    )
                else:
                    checks.append(f"AndroidManifest minSdkVersion={actual_min_sdk}")

        if "classes.jar" not in names:
            errors.append("AAR is missing classes.jar")
        else:
            try:
                classes_data = archive.read("classes.jar")
            except (KeyError, OSError, zipfile.BadZipFile):
                errors.append("AAR classes.jar cannot be read")
            else:
                checks.append(
                    "classes.jar is present; BuildConfig version requires independent verification"
                )
                _check_classes_jar(classes_data, checks, errors)

        for abi in EXPECTED_ABIS:
            prefix = f"jni/{abi}/"
            shared_objects = sorted(
                name
                for name in names
                if name.startswith(prefix)
                and "/" not in name[len(prefix) :]
                and name.endswith(".so")
            )
            if not shared_objects:
                errors.append(f"JNI ABI {abi} is missing a direct .so entry")
                continue

            abi_ok = True
            segment_count = 0
            for name in shared_objects:
                try:
                    bits, count = _check_elf_for_abi(archive.read(name), abi, name)
                except (KeyError, OSError, ElfError, zipfile.BadZipFile) as exc:
                    errors.append("JNI check failed: " + str(exc))
                    abi_ok = False
                else:
                    segment_count += count
                    if bits != ABI_SPECS[abi][0]:
                        abi_ok = False
            if abi_ok:
                if ABI_SPECS[abi][0] == 64:
                    checks.append(
                        f"JNI ABI {abi}: {len(shared_objects)} ELF64 .so file(s), "
                        f"all {segment_count} PT_LOAD segment(s) satisfy 16 KiB layout"
                    )
                else:
                    checks.append(
                        f"JNI ABI {abi}: {len(shared_objects)} ELF32 .so file(s) present "
                        "(16 KiB alignment not required)"
                    )

    return VerificationResult(tuple(checks), tuple(errors))


def _argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Check an Android Producer AAR offline using only Python's standard library."
    )
    parser.add_argument("--aar", required=True, type=Path, help="AAR file to inspect")
    parser.add_argument(
        "--min-sdk",
        type=int,
        default=19,
        help="expected Android minSdkVersion (default: 19; any integer >= 16 is accepted)",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _argument_parser().parse_args(argv)
    try:
        result = verify_aar(args.aar, args.min_sdk)
    except (AARInputError, ValueError) as exc:
        print("ERROR: " + str(exc), file=sys.stderr)
        return 1

    for check in result.checks:
        print("OK: " + check)
    for error in result.errors:
        print("ERROR: " + error, file=sys.stderr)
    if result.errors:
        print(f"FAIL: {len(result.errors)} check(s) failed", file=sys.stderr)
        return 1
    print("PASS: Android release AAR checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
