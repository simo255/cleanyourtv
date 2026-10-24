"""Completion must be established from aired episodes; unfinished viewing is neutral."""
import base64
from datetime import date
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
import zlib

spec = importlib.util.spec_from_file_location("taste_profile", Path(__file__).resolve().parents[1] / "tools/build-taste-profile.py")
taste = importlib.util.module_from_spec(spec)
spec.loader.exec_module(taste)


class CompletionTests(unittest.TestCase):
    def series(self, watched, dates=("2025-01-01", "2025-01-02", "2027-01-01")):
        ids = [f"tt123:1:{i + 1}" for i in range(len(dates))]
        raw = bytes([sum(1 << i for i in watched)])
        field = ids[-1] + f":{len(ids)}:" + base64.b64encode(zlib.compress(raw)).decode()
        item = {"_id": "tt123", "type": "series", "state": {"watched": field}}
        meta = {"id": "tt123", "genres": ["Drama"], "videos": [{"id": identity, "season": 1, "episode": i + 1, "released": dates[i]} for i, identity in enumerate(ids)]}
        return item, meta

    def test_all_aired_episodes_are_positive_and_future_episodes_do_not_count(self):
        item, meta = self.series([0, 1])
        self.assertTrue(taste.completed_series(item, meta, date(2026, 10, 24)))
        self.assertFalse(taste.completed_series(item, meta, date(2027, 1, 2)))

    def test_partial_or_sparse_viewing_is_not_completion(self):
        for watched in ([0], [1], []):
            item, meta = self.series(watched)
            self.assertFalse(taste.completed_series(item, meta, date(2026, 10, 24)))

    def test_unknown_dates_missing_anchors_and_corrupt_bitmaps_are_neutral(self):
        item, meta = self.series([0, 1], ("2025-01-01", "", "2027-01-01"))
        self.assertFalse(taste.completed_series(item, meta, date(2026, 10, 24)))
        item["state"]["watched"] = "missing:3:bad"
        self.assertFalse(taste.completed_series(item, meta, date(2026, 10, 24)))

    def test_no_aired_episodes_cannot_be_positive(self):
        item, meta = self.series([], ("2027-01-01",))
        self.assertFalse(taste.completed_series(item, meta, date(2026, 10, 24)))

    def test_repeated_single_title_export_is_rejected_instead_of_becoming_a_profile(self):
        item, _ = self.series([0, 1])
        with self.assertRaises(ValueError):
            taste.build_profile([{"d": item}] * 20, Path("unused"), date(2026, 10, 24))

    def test_missing_metadata_creates_no_negative_signal(self):
        item, _ = self.series([0])
        with patch.object(Path, "stat", side_effect=FileNotFoundError):
            profile, report = taste.build_profile([item], Path("unused"), date(2026, 10, 24))
        self.assertEqual(profile["completed"], [])
        self.assertEqual(report["neutral_series"], 1)
        self.assertEqual(report["negative_inferred"], 0)
