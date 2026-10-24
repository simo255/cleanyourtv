"""The local wrapper must keep runtime data out of a checkout and serialize TV navigation."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipIf(os.name == "nt", "requires a POSIX shell")
class TermuxRefreshTest(unittest.TestCase):
    def run_wrapper(self, root, status=0, locked=False):
        scripts = root / "checkout" / "tools"
        scripts.mkdir(parents=True)
        wrapper = scripts / "refresh-google-tv-termux.sh"
        shutil.copyfile(ROOT / "tools" / wrapper.name, wrapper)
        for name in ("sync-google-tv.py", "configure-recommendations.py"):
            (scripts / name).touch()
        binaries = root / "bin"
        binaries.mkdir()
        adb = binaries / "adb"
        adb.write_text('#!/bin/sh\nprintf "%s\\n" "$*" >> "$ADB_TEST_CALLS"\n'
                       'case "$*" in *get-state) echo device;; esac\n', encoding="utf-8")
        python = binaries / "python"
        python.write_text(f'#!/bin/sh\necho "Local refresh test"\nexit {status}\n', encoding="utf-8")
        adb.chmod(0o700)
        python.chmod(0o700)
        state = root / "state" / "tvhome-google-tv"
        if locked:
            (state / ".refresh-lock").mkdir(parents=True)
        calls = root / "adb-calls"
        env = dict(os.environ, PATH=str(binaries) + os.pathsep + os.defpath,
                   XDG_STATE_HOME=str(root / "state"), ADB_TEST_CALLS=str(calls))
        result = subprocess.run(["/bin/sh", str(wrapper)], env=env, capture_output=True, text=True, timeout=10)
        return result, scripts, state, calls

    def test_logs_are_private_state_not_files_in_the_checkout(self):
        with tempfile.TemporaryDirectory() as tmp:
            result, scripts, state, calls = self.run_wrapper(Path(tmp))
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual((state / "last-refresh.exit").read_text().strip(), "0")
            self.assertIn("Local refresh test", (state / "last-refresh.log").read_text())
            self.assertEqual(len(list(scripts.iterdir())), 3)
            self.assertFalse((state / ".refresh-lock").exists())
            self.assertIn("127.0.0.1:5555", calls.read_text())

    def test_existing_lock_prevents_any_adb_command(self):
        with tempfile.TemporaryDirectory() as tmp:
            result, _, state, calls = self.run_wrapper(Path(tmp), locked=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(calls.exists())
            self.assertTrue((state / ".refresh-lock").exists())

    def test_failure_propagates_and_releases_its_own_lock(self):
        with tempfile.TemporaryDirectory() as tmp:
            result, _, state, _ = self.run_wrapper(Path(tmp), status=7)
            self.assertEqual(result.returncode, 7)
            self.assertEqual((state / "last-refresh.exit").read_text().strip(), "7")
            self.assertFalse((state / ".refresh-lock").exists())
