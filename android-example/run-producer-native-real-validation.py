#!/usr/bin/env python3
"""Run installed instrumentation with private, short-lived test credentials."""
import argparse
import json
from pathlib import Path
import re
import shlex
import subprocess
import time


PACKAGE = "com.volcengine.tls.android.producer.test"
COMPONENT = PACKAGE + "/androidx.test.runner.AndroidJUnitRunner"
CLASS_PREFIX = "com.volcengine.tls.android.producer."
REMOTE_CONFIG = "files/real_tls.properties"


def load_env(path):
    values = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:]
        key, separator, raw = line.partition("=")
        if not separator or not re.fullmatch(r"[A-Za-z_][A-Za-z_0-9]*", key.strip()):
            raise ValueError("config must contain simple environment assignments")
        parsed = shlex.split(raw, comments=True)
        if len(parsed) > 1:
            raise ValueError("config values with spaces must be quoted")
        values[key.strip()] = parsed[0] if parsed else ""
    return values


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config-env", type=Path, required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--benchmark", action="store_true")
    parser.add_argument("--duration", type=int, default=120)
    args = parser.parse_args()
    if args.duration < 60:
        parser.error("benchmark duration must cover at least the 60-second steady window")
    env = load_env(args.config_env)
    mappings = {"endpoint": "VE_TLS_ENDPOINT", "region": "VE_TLS_REGION",
                "topicId": "VE_TLS_TOPIC_ID", "accessKeyId": "VE_TLS_ACCESS_KEY_ID",
                "accessKeySecret": "VE_TLS_ACCESS_KEY_SECRET", "securityToken": "VE_TLS_SECURITY_TOKEN"}
    props = {key: env.get(value, "") for key, value in mappings.items()}
    if any(not props[k] for k in mappings if k != "securityToken"):
        raise ValueError("missing required endpoint/region/topic/credential fields")
    if not props["endpoint"].startswith("https://"):
        raise ValueError("real validation requires HTTPS")
    props.update({"compress": "lz4", "sendThreadCount": "1", "packetLogBytes": "1048576",
                  "packetLogCount": "1024", "packetTimeoutMs": "3000", "maxBufferLimit": "67108864",
                  "persistentMaxFileCount": "32", "persistentMaxFileSize": "8388608",
                  "persistentMaxLogCount": "65536"})
    secrets = [props[k] for k in ("accessKeyId", "accessKeySecret", "securityToken") if props[k]]
    args.output.mkdir(parents=True, exist_ok=True)

    def redact(text):
        for secret in secrets:
            text = text.replace(secret, "<redacted>")
        return text

    def adb(*command, payload=None, timeout=60):
        result = subprocess.run([args.adb, "-s", args.serial, *command], input=payload,
                                text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
        output = redact(result.stdout)
        if result.returncode:
            raise RuntimeError("adb failed: " + output)
        return output

    def instrument(name, extra=(), timeout=180, expected_count=6):
        adb("shell", "am", "force-stop", PACKAGE)
        output = adb("shell", "am", "instrument", "-w", "-e", "class", CLASS_PREFIX + name,
                     *extra, COMPONENT, timeout=timeout)
        counts = re.findall(r"OK \((\d+) tests?\)", output)
        if counts != [str(expected_count)] or "FAILURES!!!" in output:
            raise RuntimeError("instrumentation failed: " + output)
        return output

    def escape(value):
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")

    # Provision through stdin, never through adb arguments or shared device storage.
    payload = "".join(key + "=" + escape(value) + "\n" for key, value in props.items())
    # Legacy adb shell uses a PTY and may not deliver EOF to cat. Read a fixed byte count.
    provision = "run-as " + PACKAGE + " sh -c " + shlex.quote(
        f"umask 077; mkdir -p files; dd bs=1 count={len(payload.encode('utf-8'))} "
        f"> {REMOTE_CONFIG} 2>/dev/null")
    started = time.strftime("%Y%m%d-%H%M%S")
    rows = []
    external_config = f"/sdcard/Android/data/{PACKAGE}/files/real_tls.properties"
    external_state = adb("shell", "sh", "-c", shlex.quote(
        f"if test -e {external_config}; then echo PRESENT; else echo ABSENT; fi"))
    if external_state.strip() != "ABSENT":
        raise RuntimeError("remove the legacy external-storage credential fixture before validation")
    try:
        adb("shell", provision, payload=payload)
        smoke = instrument("ProducerRealSendInstrumentedTest")
        (args.output / "real-smoke.log").write_text(smoke)
        print("real-smoke: PASS", flush=True)
        if not args.benchmark:
            return
        for mode in ("memory", "persistent"):
            for profile in ("tls200", "tls700"):
                for rate in (1, 10, 100, 200, 500):
                    run_id = f"{mode}_{profile}_{rate}_{started}"
                    extra = ("-e", "benchmarkRunId", run_id,
                             "-e", "benchmarkProfile", profile,
                             "-e", "benchmarkRateLps", str(rate),
                             "-e", "benchmarkDurationS", str(args.duration),
                             "-e", "benchmarkPersistent", str(mode == "persistent").lower())
                    print(f"benchmark start: {mode} {profile} {rate} lps", flush=True)
                    log = instrument("ProducerRealBenchmarkInstrumentedTest#testRunRealBenchmarkScenario",
                                     extra, timeout=args.duration + 150, expected_count=1)
                    (args.output / (run_id + ".log")).write_text(log)
                    remote = f"/sdcard/Android/data/{PACKAGE}/files/benchmark/{run_id}.properties"
                    report = adb("shell", "cat", remote)
                    (args.output / (run_id + ".properties")).write_text(report)
                    row = dict(line.split("=", 1) for line in report.splitlines()
                               if "=" in line and not line.startswith("#"))
                    rows.append(row)
                    (args.output / "benchmark.json").write_text(json.dumps(rows, indent=2))
                    print("benchmark result: " + str(row.get("status", "UNKNOWN")), flush=True)
                    if row.get("status") != "OK":
                        raise RuntimeError("benchmark degraded; inspect saved non-secret report")
    finally:
        adb("shell", "run-as", PACKAGE, "rm", "-f", REMOTE_CONFIG)


if __name__ == "__main__":
    main()
