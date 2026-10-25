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

BASE = "https://watchly.elfhosted.com/example_private_id/"


class SetupTests(unittest.TestCase):
    def test_enhanced_profile_is_opt_in_and_only_known_fields_survive(self):
        season = {"season": 1, "total": 8, "aired": 8, "watched": 4, "status": "unfinished", "secret": "discard"}
        value = {"addon_base": BASE, "recommendation_options": {"enhanced": True, "secret": "discard"},
                 "taste_profile": {"schema": 1, "viewing": [{"id": "tt1", "type": "series", "seasons": [season], "cast": ["Actor"]}],
                                   "positives": [{"id": "tt2", "type": "movie", "directors": ["Director"]}]}}
        normalized = setup.normalize_config(value)
        self.assertNotIn("secret", json.dumps(normalized))
        self.assertEqual({"enhanced": True}, normalized["recommendation_options"])
        self.assertEqual("unfinished", normalized["taste_profile"]["viewing"][0]["seasons"][0]["status"])
        self.assertEqual(["Actor"], normalized["taste_profile"]["viewing"][0]["cast"])
        self.assertNotIn("recommendation_options", setup.normalize_config({"addon_base": BASE}))

    def test_malformed_seasons_and_options_are_rejected(self):
        good = {"season": 1, "total": 8, "aired": 8, "watched": 4}
        for bad in ({**good, "watched": 9}, {**good, "aired": 7}, {**good, "season": True}, {**good, "status": "maybe"}):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                setup.normalize_seasons([bad])
        with self.assertRaises(ValueError):
            setup.normalize_seasons([good, good])
        with self.assertRaises(ValueError):
            setup.normalize_config({"addon_base": BASE, "recommendation_options": {"enhanced": "true"}})
        with self.assertRaises(ValueError):
            setup.metadata_fields({"cast": ["Actor\nInjected"]})

    def test_review_timestamp_survives_provisioning_and_must_be_a_valid_integer(self):
        record = {"id": "tt1", "type": "series", "seasons": [], "reviewed_at": 1792850000000}
        profile = {"schema": 1, "viewing": [record]}
        self.assertEqual(1792850000000, setup.normalize_taste_profile(profile)["viewing"][0]["reviewed_at"])
        for bad in (True, -1, "1792850000000", 40000000000000):
            record["reviewed_at"] = bad
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                setup.normalize_taste_profile(profile)

    def test_secondary_trakt_connection_is_discarded_and_existing_catalog_is_preserved(self):
        auth = {"client_id": "a" * 64, "access_token": "b" * 64,
                "client_secret": "c" * 64, "refresh_token": "d" * 64, "email": "discard"}
        config = setup.normalize_config({"addon_base": BASE, "trakt_history": auth})
        self.assertEqual(config, {"addon_base": BASE, "watched_movie_ids": [], "watched_series_ids": []})
        self.assertNotIn("access_token", json.dumps(config))

    def test_taste_import_keeps_only_positive_completed_records_and_discards_unknown_fields(self):
        profile = {"schema": 1, "completed": [{"id": "tt123", "type": "series", "completed": True,
                   "genres": ["Drama"], "access_token": "never-forward"},
                   {"id": "tt456", "type": "series", "completed": False, "genres": ["Comedy"]}]}
        result = setup.normalize_config({"addon_base": BASE, "taste_profile": profile})
        self.assertEqual(result["taste_profile"], {"schema": 1, "completed": [{"id": "tt123", "type": "series", "completed": True, "genres": ["Drama"]}]})

    def test_tmdb_read_token_is_explicit_and_cannot_inject_request_headers(self):
        token = "example.TMDB.read-token-only"
        self.assertEqual(setup.normalize_config({"addon_base": BASE, "tmdb_read_token": token})["tmdb_read_token"], token)
        for bad in ("bad\r\nHeader: value", "", "a" * 5000):
            with self.assertRaises(ValueError):
                setup.normalize_config({"addon_base": BASE, "tmdb_read_token": bad})

    def test_accepts_official_manifest_and_preserves_only_known_fields(self):
        config = setup.normalize_config({"addon_base": BASE + "manifest.json",
                                        "watched_movie_ids": ["tt123", "tt123"],
                                        "access_token": "must-never-be-forwarded"})
        self.assertEqual(config, {"addon_base": BASE, "watched_movie_ids": ["tt123"], "watched_series_ids": []})

    def test_rejects_lookalike_hosts_credentials_query_and_path_traversal(self):
        for url in [BASE.replace("https:", "http:"), BASE.replace("watchly.elfhosted.com", "watchly.elfhosted.com.evil.example"),
                    BASE.replace("watchly.elfhosted.com", "user@watchly.elfhosted.com"), BASE + "?token=x", BASE + "#fragment",
                    BASE.replace("watchly.elfhosted.com", "watchly.elfhosted.com:443"), BASE.replace("example_private_id", "../other"),
                    BASE.replace("example_private_id", "%2Fother"), BASE.replace("elfhosted.com", "elfhosted.\ncom")]:
            with self.subTest(url=url), self.assertRaises(ValueError):
                setup.normalize_config({"addon_base": url})

    def test_rejects_malformed_watched_ids(self):
        for ids in ["tt123", [123], ["tt123;echo bad"], ["tvdb:123"]]:
            with self.subTest(ids=ids), self.assertRaises(ValueError):
                setup.normalize_config({"addon_base": BASE, "watched_series_ids": ids})

    def test_export_discards_account_tokens_and_library(self):
        value = {"addons": {"addons": [{"manifest": {"id": "org.watchly.personal"}, "transportUrl": BASE + "manifest.json"}]},
                 "user": {"access_token": "secret"}, "library": [{"name": "private history"}]}
        result = setup.config_from_export(value)
        self.assertEqual(result, {"addon_base": BASE, "watched_movie_ids": [], "watched_series_ids": []})

    def test_ambiguous_or_nonofficial_export_is_rejected(self):
        addon = {"manifest": {"id": "org.watchly.personal"}, "transportUrl": BASE}
        for addons in [[], [addon, addon], [{"manifest": {"id": "community.other"}, "transportUrl": BASE}]]:
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
        self.assertIn("files/recent-cache.json", script)
        self.assertIn("files/google-tv-cache.json", script)
        self.assertNotIn("shared_prefs", script)
        self.assertNotIn("pm clear", script)


if __name__ == "__main__":
    unittest.main()
