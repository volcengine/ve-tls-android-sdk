import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock


SCRIPT_PATH = Path(__file__).with_name("run-producer-native-real-validation.py")
SPEC = importlib.util.spec_from_file_location("real_validation_runner", SCRIPT_PATH)
RUNNER = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(RUNNER)


FAKE_ENV = {
    "VE_TLS_ENDPOINT": "https://fake.example.test",
    "VE_TLS_REGION": "cn-fake",
    "VE_TLS_TOPIC_ID": "fake-topic",
    "VE_TLS_ACCESS_KEY_ID": "fake-access-key",
    "VE_TLS_ACCESS_KEY_SECRET": "fake-access-secret",
    "VE_TLS_SECURITY_TOKEN": "fake-security-token-测试",
}
FAKE_SECRETS = tuple(
    FAKE_ENV[key]
    for key in ("VE_TLS_ACCESS_KEY_ID", "VE_TLS_ACCESS_KEY_SECRET", "VE_TLS_SECURITY_TOKEN")
)


def completed(output="", returncode=0):
    return subprocess.CompletedProcess(["fake-adb"], returncode, stdout=output)


class RealValidationRunnerTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.config_path = self.root / "fake.env"
        self.output_dir = self.root / "output"
        self.config_path.write_text(
            "\n".join(key + "=" + value for key, value in FAKE_ENV.items()) + "\n"
        )

    def tearDown(self):
        self.temp_dir.cleanup()

    def argv(self, *extra):
        return [
            str(SCRIPT_PATH),
            "--config-env",
            str(self.config_path),
            "--adb",
            "fake-adb",
            "--serial",
            "fake-serial",
            "--output",
            str(self.output_dir),
            *extra,
        ]

    def run_main(self, *extra):
        with mock.patch.object(sys, "argv", self.argv(*extra)):
            RUNNER.main()

    def assert_cleanup_called(self, calls):
        cleanup = [
            call
            for call in calls
            if call.args[0][-6:]
            == ["shell", "run-as", RUNNER.PACKAGE, "rm", "-f", RUNNER.REMOTE_CONFIG]
        ]
        self.assertEqual(1, len(cleanup), calls)

    def is_external_probe(self, command):
        return command[3:6] == ["shell", "sh", "-c"] and any(
            "if test -e " in argument for argument in command
        )

    def is_provision(self, command):
        return any(
            "dd bs=1 count=" in argument
            and ("> " + RUNNER.REMOTE_CONFIG) in argument
            for argument in command
        )

    def assert_no_secret_in_command_args(self, calls):
        for call in calls:
            command = call.args[0]
            self.assertTrue(
                all(secret not in argument for argument in command for secret in FAKE_SECRETS),
                command,
            )

    def test_private_provision_uses_stdin_not_command_args(self):
        calls = []

        def run(command, **kwargs):
            calls.append(mock.call(command, **kwargs))
            if self.is_external_probe(command):
                return completed("ABSENT\n")
            return completed("INSTRUMENTATION_CODE: 1\nOK (6 tests)\n")

        with mock.patch.object(RUNNER.subprocess, "run", side_effect=run), mock.patch(
            "builtins.print"
        ) as printed:
            self.run_main()

        provision = next(call for call in calls if self.is_provision(call.args[0]))
        self.assertIn("run-as " + RUNNER.PACKAGE + " sh -c ", provision.args[0][-1])
        payload = provision.kwargs["input"]
        self.assertIsInstance(payload, str)
        for secret in FAKE_SECRETS:
            self.assertIn(secret, payload)
        provision_script = next(
            argument for argument in provision.args[0] if "dd bs=1 count=" in argument
        )
        self.assertIn("2>/dev/null", provision_script)
        self.assertIn(
            "dd bs=1 count=" + str(len(payload.encode("utf-8"))) + " ",
            provision_script,
        )
        self.assertNotEqual(len(payload), len(payload.encode("utf-8")))
        self.assert_no_secret_in_command_args(calls)
        self.assert_cleanup_called(calls)
        self.assertIn("real-smoke: PASS", [call.args[0] for call in printed.call_args_list])

    def test_external_fixture_present_rejects_before_provision_and_cleanup(self):
        calls = []

        def run(command, **kwargs):
            calls.append(mock.call(command, **kwargs))
            if self.is_external_probe(command):
                return completed("PRESENT\n")
            return completed()

        with mock.patch.object(RUNNER.subprocess, "run", side_effect=run), mock.patch(
            "builtins.print"
        ) as printed:
            with self.assertRaisesRegex(RuntimeError, "legacy external-storage credential fixture"):
                self.run_main()

        self.assertEqual(1, len(calls), calls)
        self.assertTrue(self.is_external_probe(calls[0].args[0]))
        self.assertFalse(any(call.kwargs.get("input") for call in calls))
        self.assertFalse((self.output_dir / "real-smoke.log").exists())
        self.assertNotIn("real-smoke: PASS", [call.args[0] for call in printed.call_args_list])

    def test_instrument_failure_cleans_up_without_pass(self):
        calls = []

        def run(command, **kwargs):
            calls.append(mock.call(command, **kwargs))
            if self.is_external_probe(command):
                return completed("ABSENT\n")
            if "instrument" in command:
                return completed("INSTRUMENTATION_CODE: 0\nFAILURES!!!\n")
            return completed()

        with mock.patch.object(RUNNER.subprocess, "run", side_effect=run), mock.patch(
            "builtins.print"
        ) as printed:
            with self.assertRaisesRegex(RuntimeError, "instrumentation failed"):
                self.run_main()

        self.assert_cleanup_called(calls)
        self.assert_no_secret_in_command_args(calls)
        self.assertNotIn("real-smoke: PASS", [call.args[0] for call in printed.call_args_list])

    def assert_smoke_count_rejected(self, output):
        calls = []

        def run(command, **kwargs):
            calls.append(mock.call(command, **kwargs))
            if self.is_external_probe(command):
                return completed("ABSENT\n")
            if "instrument" in command:
                return completed(output)
            return completed()

        with mock.patch.object(RUNNER.subprocess, "run", side_effect=run), mock.patch(
            "builtins.print"
        ) as printed:
            with self.assertRaisesRegex(RuntimeError, "instrumentation failed"):
                self.run_main()

        self.assert_cleanup_called(calls)
        self.assert_no_secret_in_command_args(calls)
        self.assertNotIn("real-smoke: PASS", [call.args[0] for call in printed.call_args_list])

    def test_smoke_zero_tests_is_rejected(self):
        self.assert_smoke_count_rejected("INSTRUMENTATION_CODE: 1\nOK (0 tests)\n")

    def test_smoke_legacy_two_tests_is_rejected(self):
        self.assert_smoke_count_rejected("INSTRUMENTATION_CODE: 1\nOK (2 tests)\n")

    def test_instrument_timeout_cleans_up_without_pass(self):
        calls = []

        def run(command, **kwargs):
            calls.append(mock.call(command, **kwargs))
            if self.is_external_probe(command):
                return completed("ABSENT\n")
            if "instrument" in command:
                raise subprocess.TimeoutExpired(command, kwargs["timeout"])
            return completed()

        with mock.patch.object(RUNNER.subprocess, "run", side_effect=run), mock.patch(
            "builtins.print"
        ) as printed:
            with self.assertRaises(subprocess.TimeoutExpired):
                self.run_main()

        self.assert_cleanup_called(calls)
        self.assert_no_secret_in_command_args(calls)
        self.assertNotIn("real-smoke: PASS", [call.args[0] for call in printed.call_args_list])

    def test_saved_logs_mask_fake_secrets(self):
        calls = []
        leaked_output = "diagnostic=" + FAKE_SECRETS[1] + "\nOK (6 tests)\n"

        def run(command, **kwargs):
            calls.append(mock.call(command, **kwargs))
            if self.is_external_probe(command):
                return completed("ABSENT\n")
            return completed(leaked_output)

        with mock.patch.object(RUNNER.subprocess, "run", side_effect=run):
            self.run_main()

        log = (self.output_dir / "real-smoke.log").read_text()
        self.assertIn("<redacted>", log)
        for secret in FAKE_SECRETS:
            self.assertNotIn(secret, log)

    def test_benchmark_runs_all_20_cases_with_small_persistent_quota(self):
        calls = []
        leaked_output = "diagnostic=" + FAKE_SECRETS[2] + "\nOK (1 test)\n"
        report_output = "status=OK\nserver_note=" + FAKE_SECRETS[2] + "\n"

        def run(command, **kwargs):
            calls.append(mock.call(command, **kwargs))
            if self.is_external_probe(command):
                return completed("ABSENT\n")
            if "cat" in command:
                return completed(report_output)
            if "instrument" in command:
                if any(
                    "ProducerRealBenchmarkInstrumentedTest" in argument
                    for argument in command
                ):
                    return completed(leaked_output)
                return completed("diagnostic=" + FAKE_SECRETS[2] + "\nOK (6 tests)\n")
            return completed()

        with mock.patch.object(RUNNER.subprocess, "run", side_effect=run), mock.patch(
            "builtins.print"
        ) as printed:
            self.run_main("--benchmark", "--duration", "60")

        provision = next(call for call in calls if self.is_provision(call.args[0]))
        provision_props = dict(
            line.split("=", 1)
            for line in provision.kwargs["input"].splitlines()
            if "=" in line
        )
        self.assertEqual("32", provision_props["persistentMaxFileCount"])
        self.assertEqual("8388608", provision_props["persistentMaxFileSize"])
        self.assertEqual("65536", provision_props["persistentMaxLogCount"])

        benchmark_calls = [
            call
            for call in calls
            if "instrument" in call.args[0]
            and any(
                "ProducerRealBenchmarkInstrumentedTest" in argument
                for argument in call.args[0]
            )
        ]
        self.assertEqual(20, len(benchmark_calls))

        def option(command, name):
            return command[command.index(name) + 1]

        observed = {
            (
                option(call.args[0], "benchmarkPersistent"),
                option(call.args[0], "benchmarkProfile"),
                int(option(call.args[0], "benchmarkRateLps")),
            )
            for call in benchmark_calls
        }
        expected = {
            (str(persistent).lower(), profile, rate)
            for persistent in (False, True)
            for profile in ("tls200", "tls700")
            for rate in (1, 10, 100, 200, 500)
        }
        self.assertEqual(expected, observed)
        self.assertEqual(500, max(rate for _, _, rate in observed))
        self.assertEqual(20, len({option(call.args[0], "benchmarkRunId") for call in benchmark_calls}))

        report = json.loads((self.output_dir / "benchmark.json").read_text())
        self.assertEqual(20, len(report))
        self.assertTrue(all(row.get("status") == "OK" for row in report))
        self.assert_cleanup_called(calls)

        output_files = [path for path in self.output_dir.rglob("*") if path.is_file()]
        self.assertTrue(output_files)
        for path in output_files:
            content = path.read_text()
            for secret in FAKE_SECRETS:
                self.assertNotIn(secret, content, path)
        printed_lines = [call.args[0] for call in printed.call_args_list]
        self.assertIn("real-smoke: PASS", printed_lines)


if __name__ == "__main__":
    unittest.main()
