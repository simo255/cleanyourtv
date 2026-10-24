import importlib.util
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("google_sync", Path(__file__).resolve().parents[1] / "tools/sync-google-tv.py")
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)


def frame(title="Example &amp; Friends", year="2026", row="Top picks for you", logo=True):
    widget = 'ImageView' if logo else 'TextView'
    field = 'content-desc' if logo else 'text'
    return ET.fromstring(f'''<hierarchy><node class="android.widget.{widget}" {field}="{title}" bounds="[100,190][800,370]" />
    <node class="android.widget.TextView" text="{year}" bounds="[600,560][700,600]" />
    <node class="android.widget.GridView" content-desc="{row}" bounds="[0,700][1920,900]">
    <node class="android.view.View" focused="true" bounds="[100,710][420,890]" /></node></hierarchy>''')


class GoogleSyncTests(unittest.TestCase):
    def test_focused_movie_title_and_year_come_from_personalized_row(self):
        self.assertEqual(sync.card(frame(), 3), {"rank": 3, "title": "Example & Friends", "year": "2026"})
        with self.assertRaises(ValueError):
            sync.card(frame(row="Sponsored"), 1)

    def test_video_without_a_release_year_cannot_be_guessed_as_a_movie(self):
        value = sync.card(frame(title="Example video", year="4 years ago", logo=False), 1)
        self.assertEqual(value["year"], "")
        self.assertEqual(sync.identify([value], []), {"movies": [], "shows": []})

    def test_exact_title_year_order_and_duplicate_handling(self):
        metadata = [{"id": "tt1", "type": "movie", "name": "Example & Friends", "year": "2026"},
                    {"id": "tt2", "type": "series", "name": "Other", "year": "2025–2026"}]
        values = [{"rank": 1, "title": "Other", "year": "2025"},
                  {"rank": 2, "title": "Example & Friends", "year": "2026"},
                  {"rank": 3, "title": "Example & Friends", "year": "2026"}]
        result = sync.identify(values, metadata)
        self.assertEqual(result["movies"][0]["id"], "tt1")
        self.assertEqual(result["movies"][0]["google_rank"], 2)
        self.assertEqual(len(result["movies"]), 1)
        self.assertEqual(result["shows"][0]["id"], "tt2")

    def test_remakes_and_ambiguous_matches_are_not_fuzzily_selected(self):
        value = [{"rank": 1, "title": "Example", "year": "2026"}]
        metadata = [{"id": "tt1", "type": "movie", "name": "Example", "year": "1990"}]
        self.assertFalse(sync.identify(value, metadata)["movies"])
        metadata += [{"id": "tt2", "type": "movie", "name": "Example", "year": "2026"},
                     {"id": "tt3", "type": "movie", "name": "Example", "year": "2026"}]
        self.assertFalse(sync.identify(value, metadata)["movies"])

    def test_public_redirects_cannot_reach_account_urls_or_arbitrary_hosts(self):
        sync.validate_public("https://cinemeta-catalogs.strem.io/top/catalog/movie/top.json")
        for address in ["http://v3-cinemeta.strem.io/", "https://www.strem.io/trakt/addon/private/",
                        "https://v3-cinemeta.strem.io.evil.test/", "https://user@v3-cinemeta.strem.io/",
                        "https://v3-cinemeta.strem.io/?token=secret", "https://v3-cinemeta.strem.io:443/"]:
            with self.subTest(address=address), self.assertRaises(ValueError):
                sync.validate_public(address)

    def test_capture_navigation_failure_still_restores_custom_home(self):
        class Fake(sync.Capture):
            def __init__(self): self.calls = []
            def run(self, *args, data=None):
                self.calls.append(args)
                if "packages" in args: return ("package:" + sync.GOOGLE).encode()
                return sync.HOME.encode() if "resolve-activity" in args else b"Success"
            def ui(self): return ET.fromstring('<hierarchy><node focused="true" /></hierarchy>')
        device = Fake()
        with self.assertRaises(RuntimeError):
            device.capture(1)
        self.assertIn(("shell", "pm", "disable-user", "--user", "0", sync.GOOGLE), device.calls)
        self.assertIn(("shell", "cmd", "package", "set-home-activity", "--user", "0", sync.HOME), device.calls)

    def test_capture_keeps_an_originally_enabled_google_launcher_enabled(self):
        class Fake(sync.Capture):
            def __init__(self): self.calls = []
            def run(self, *args, data=None):
                self.calls.append(args)
                if "packages" in args:
                    return b"" if "-d" in args else ("package:" + sync.GOOGLE).encode()
                return sync.HOME.encode() if "resolve-activity" in args else b"Success"
            def ui(self): return ET.fromstring('<hierarchy><node focused="true" /></hierarchy>')
        device = Fake()
        with self.assertRaises(RuntimeError): device.capture(1)
        self.assertNotIn(("shell", "pm", "disable-user", "--user", "0", sync.GOOGLE), device.calls)
        self.assertEqual(device.calls.count(("shell", "pm", "enable", "--user", "0", sync.GOOGLE)), 2)

    def test_missing_google_tv_does_not_mutate_packages_or_home(self):
        class Fake(sync.Capture):
            def __init__(self): self.calls = []
            def run(self, *args, data=None):
                self.calls.append(args)
                return sync.HOME.encode() if "resolve-activity" in args else b""
        device = Fake()
        with self.assertRaises(RuntimeError): device.capture(1)
        self.assertFalse(any("enable" in call or "disable-user" in call or "set-home-activity" in call
                             for call in device.calls))

    def test_removing_google_import_does_not_clear_trakt_or_dock(self):
        class Fake(sync.Capture):
            def __init__(self): self.calls = []
            def run(self, *args, data=None): self.calls.append(args); return b""
        device = Fake(); device.clear_snapshot()
        script = " ".join(" ".join(call) for call in device.calls)
        self.assertIn("files/google-tv-cache.json", script)
        self.assertNotIn("recommendations-config", script)
        self.assertNotIn("recommendations-cache", script)
        self.assertNotIn("shared_prefs", script)
        self.assertNotIn("pm clear", script)

    def test_capture_validation_rejects_missing_fields_and_invalid_rank_or_year(self):
        valid = {"schema": 1, "provider": "google-tv-ui", "captured_at": 123,
                 "items": [{"rank": 1, "title": "Example", "year": "2026"}]}
        self.assertIs(sync.validate_capture(valid), valid)
        for bad in [{}, {**valid, "captured_at": True}, {**valid, "items": []},
                    {**valid, "items": [{"rank": 2, "title": "Example", "year": "2026"}]},
                    {**valid, "items": [{"rank": 1, "title": "Example", "year": "unknown"}]}]:
            with self.assertRaises(ValueError): sync.validate_capture(bad)

    def test_malformed_metadata_does_not_poison_other_valid_matches(self):
        values = [{"rank": 1, "title": "Example", "year": "2026"}]
        result = sync.identify(values, [None, "wrong", {"id": 3, "type": "movie"},
                                      {"id": "tt1", "type": "movie", "name": "Example", "year": "2026"}])
        self.assertEqual(result["movies"][0]["id"], "tt1")


if __name__ == "__main__": unittest.main()
