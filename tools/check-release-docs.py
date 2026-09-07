#!/usr/bin/env python3
"""Check release documentation links and accidentally tracked local artifacts."""

import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[1]


def repository_files():
    output = subprocess.check_output(
        ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"],
        cwd=ROOT,
    )
    return sorted({Path(name.decode()) for name in output.split(b"\0") if name})


def anchors(text):
    counts = {}
    result = set(re.findall(r'<a\s+(?:id|name)="([^"]+)"', text))
    for line in text.splitlines():
        match = re.match(r"^#{1,6}\s+(.+?)\s*#*\s*$", line)
        if not match:
            continue
        slug = re.sub(r"[^\w\- ]", "", match.group(1).lower()).replace(" ", "-")
        count = counts.get(slug, 0)
        counts[slug] = count + 1
        result.add(slug if count == 0 else f"{slug}-{count}")
    return result


def main():
    errors = []
    documents = 0
    for relative in repository_files():
        path = ROOT / relative
        if not path.is_file():
            continue
        name = relative.as_posix()
        if path.suffix in {".class", ".apk", ".aab", ".keystore", ".jks"} or name == "android-example/.classpath.txt":
            errors.append(f"{name}: generated artifact or signing key must not be tracked")
        if name.startswith("docs/verification/"):
            errors.append(f"{name}: local verification output belongs outside release docs")
        if path.suffix != ".md":
            continue
        documents += 1
        text = path.read_text(encoding="utf-8")
        if re.search(r"/Users/[^/\s]+/|/data\d+/home/|\b10\.37\.27\.165\b", text):
            errors.append(f"{name}: personal workspace or development host reference")
        if re.search(r"\b(?:aliyun|sls)\b|友商|阿里云", text, re.IGNORECASE):
            errors.append(f"{name}: competitor comparison does not belong in release docs")
        if re.search(r"\bUnreleased\b|待发布|未发布|预期发布坐标", text, re.IGNORECASE):
            errors.append(f"{name}: release docs must not contain temporary release status")
        for target in re.findall(r"\[[^\]\n]*\]\(([^)\n]+)\)", text):
            target = target.split(' "', 1)[0].strip("<>")
            parsed = urlsplit(target)
            if parsed.scheme or parsed.netloc:
                continue
            destination = (path.parent / unquote(parsed.path)).resolve() if parsed.path else path
            if not destination.is_relative_to(ROOT):
                errors.append(f"{name}: link escapes repository: {target}")
            elif not destination.exists():
                errors.append(f"{name}: missing link: {target}")
            elif parsed.fragment and destination.suffix == ".md":
                if unquote(parsed.fragment) not in anchors(destination.read_text(encoding="utf-8")):
                    errors.append(f"{name}: missing heading: {target}")
    for error in errors:
        print(error, file=sys.stderr)
    print(f"Release docs: {documents} Markdown files, {len(errors)} errors")
    return int(bool(errors))


if __name__ == "__main__":
    sys.exit(main())
