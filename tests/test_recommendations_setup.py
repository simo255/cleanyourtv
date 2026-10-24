"""Check private URL boundaries and the ADB transport without using an account."""
import importlib.util
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("setup_recommendations", Path(__file__).resolve().parents[1] / "tools/configure-recommendations.py")
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)

BASE = "https://www.strem.io/trakt/addon/example_private_id/"


class SetupTests(unittest.TestCase):
    def test_accepts_official_manifest_and_preserves_only_known_fields(self):
        config = setup.normalize_config({"addon_base": BASE + "manifest.json",
                                        "watched_movie_ids": ["tt123", "tt123"],
                                        "access_token": "must-never-be-forwarded"})
        self.assertEqual(config, {"addon_base": BASE, "watched_movie_ids": ["tt123"], "watched_series_ids": []})

    def test_rejects_lookalike_hosts_credentials_query_and_path_traversal(self):
        for url in [BASE.replace("https:", "http:"), BASE.replace("www.strem.io", "www.strem.io.evil.example"),
                    BASE.replace("www.strem.io", "user@www.strem.io"), BASE + "?token=x", BASE + "#fragment",
                    BASE.replace("www.strem.io", "www.strem.io:443"), BASE.replace("example_private_id", "../other"),
                    BASE.replace("example_private_id", "%2Fother"), BASE.replace("strem.io", "strem.\nio")]:
            with self.subTest(url=url), self.assertRaises(ValueError):
                setup.normalize_config({"addon_base": url})

    def test_rejects_malformed_watched_ids(self):
        for ids in ["tt123", [123], ["tt123;echo bad"], ["tvdb:123"]]:
            with self.subTest(ids=ids), self.assertRaises(ValueError):
                setup.normalize_config({"addon_base": BASE, "watched_series_ids": ids})

    def test_export_discards_account_tokens_and_library(self):
        value = {"addons": {"addons": [{"manifest": {"id": "org.trakt.example"}, "transportUrl": BASE + "manifest.json"}]},
                 "user": {"access_token": "secret"}, "library": [{"name": "private history"}]}
        result = setup.config_from_export(value)
        self.assertEqual(result, {"addon_base": BASE, "watched_movie_ids": [], "watched_series_ids": []})

    def test_ambiguous_or_nonofficial_export_is_rejected(self):
        addon = {"manifest": {"id": "org.trakt.example"}, "transportUrl": BASE}
        for addons in [[], [addon, addon], [{"manifest": {"id": "community.trakt"}, "transportUrl": BASE}]]:
            with self.subTest(addons=addons), self.assertRaises(ValueError):
                setup.config_from_export({"addons": addons})

    def test_private_url_travels_only_over_stdin_and_is_verified(self):
        calls = []
        config = setup.normalize_config({"addon_base": BASE})
        payload = json.dumps(config, separators=(",", ":")).encode()

        def run(command, **kwargs):
            calls.append((command, kwargs))
            data = payload if "exec-out" in command else b""
            return subprocess.CompletedProcess(command, 0, data, b"")

        with patch.object(setup.subprocess, "run", side_effect=run):
            setup.Device("adb", "test-tv").provision(config)
        self.assertTrue(any(kwargs.get("input") == payload for _, kwargs in calls))
        self.assertFalse(any(BASE in " ".join(command) for command, _ in calls))
        self.assertIn("mv files/recommendations-config.json.tmp", " ".join(calls[3][0]))
        self.assertIn("am", calls[-1][0])
        self.assertIn(setup.PACKAGE + "/.MainActivity", calls[-1][0])

    def test_clear_targets_private_recommendation_files_only(self):
        calls = []
        def run(command, **kwargs):
            calls.append(command)
            return subprocess.CompletedProcess(command, 0, b"", b"")
        with patch.object(setup.subprocess, "run", side_effect=run):
            setup.Device("adb", None).clear()
        script = " ".join(calls[3])
        self.assertIn("run-as com.gathin.tvhome", script)
        self.assertIn("cache/posters", script)
        self.assertIn("files/watchlist-cache.json", script)
        self.assertIn("files/trending-cache.json", script)
        self.assertIn("files/google-tv-cache.json", script)
        self.assertNotIn("shared_prefs", script)
        self.assertNotIn("pm clear", script)


if __name__ == "__main__":
    unittest.main()
