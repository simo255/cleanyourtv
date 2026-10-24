#!/usr/bin/env python3
"""Build a private positive-only taste profile from local Stremio data and cached episode metadata.

No network, ADB, account API or write to Stremio/Trakt. Unfinished viewing stays neutral.
"""
import argparse
import base64
from datetime import date
import json
from pathlib import Path
import re
import zlib


def watched_ids(serialized, ids):
    anchor, length, payload = serialized.rsplit(":", 2)
    if len(length) > 8 or not length.isdigit() or anchor not in ids or len(payload) > 100_000:
        raise ValueError("Unusable watched bitmap.")
    decoder = zlib.decompressobj()
    raw = decoder.decompress(base64.b64decode(payload, validate=True), 65_537)
    if len(raw) > 65_536 or not decoder.eof or decoder.unconsumed_tail or decoder.unused_data:
        raise ValueError("Unusable watched bitmap.")
    offset = int(length) - ids.index(anchor) - 1
    result = set()
    for index, identity in enumerate(ids):
        bit = index + offset
        if 0 <= bit < len(raw) * 8 and raw[bit // 8] & (1 << (bit % 8)):
            result.add(identity)
    if len(result) != sum(byte.bit_count() for byte in raw):
        raise ValueError("Metadata does not account for every watched bit.")
    return result


def completed_series(item, metadata, as_of):
    """All aired regular episodes must be known and watched. Specials and future episodes do not count."""
    state = item.get("state", {})
    field = state.get("watched") if isinstance(state, dict) else None
    if not isinstance(field, str) or not field:
        return False
    videos = metadata.get("videos", [])
    if not isinstance(videos, list) or len(videos) > 10_000 or not all(isinstance(v, dict) for v in videos):
        return False
    try:
        videos = sorted(videos, key=lambda v: (v.get("season", -1), v.get("episode", v.get("number", -1)), v.get("released") or ""))
        ids = [v["id"] for v in videos]
        if len(ids) != len(set(ids)):
            return False
        watched = watched_ids(field, ids)
    except (ValueError, KeyError, TypeError, zlib.error):
        return False
    aired = set()
    for video in videos:
        season, episode = video.get("season"), video.get("episode", video.get("number"))
        if not isinstance(season, int) or not isinstance(episode, int) or season < 0 or episode < 0:
            return False
        if season == 0 or episode == 0:
            continue
        try:
            released = date.fromisoformat(str(video.get("released", ""))[:10])
        except ValueError:
            return False  # missing dates cannot justify a completion claim
        if released <= as_of:
            aired.add(video["id"])
    return bool(aired) and aired.issubset(watched)


def build_profile(library, metadata_dir, as_of):
    if not isinstance(library, list) or len(library) > 20_000:
        raise ValueError("Invalid library.")
    items = {}
    for record in library:
        if not isinstance(record, dict):
            continue
        item = record.get("d", record)
        if isinstance(item, dict) and isinstance(item.get("_id"), str) and re.fullmatch(r"tt[0-9]+", item["_id"]):
            items[item["_id"]] = item
    if len(library) > 10 and len(items) <= 1:
        raise ValueError("Export appears to repeat one title; use a complete recovered library.")
    completed, neutral = [], 0
    for identity, item in items.items():
        if item.get("type") != "series":
            continue
        path = metadata_dir / (identity + ".json")
        try:
            if path.stat().st_size > 2_000_000:
                raise ValueError("Metadata is too large.")
            metadata = json.loads(path.read_text(encoding="utf-8-sig"))["meta"]
            if metadata.get("id", metadata.get("imdb_id")) != identity:
                raise ValueError("Metadata identity mismatch.")
            done = completed_series(item, metadata, as_of)
        except (OSError, ValueError, KeyError, TypeError):
            done, metadata = False, {}
        if not done:
            neutral += 1
            continue
        genres = metadata.get("genres", [])
        if not isinstance(genres, list):
            genres = []
        genres = [g.strip() for g in genres if isinstance(g, str) and 0 < len(g.strip()) <= 80][:12]
        completed.append({"id": identity, "type": "series", "completed": True, "genres": list(dict.fromkeys(genres))})
        if len(completed) > 1000:
            raise ValueError("Too many completed series.")
    return {"schema": 1, "completed": completed}, {"library_titles": len(items), "positive_completed_series": len(completed),
                                                  "neutral_series": neutral, "negative_inferred": 0, "as_of": as_of.isoformat()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--library", type=Path, required=True)
    parser.add_argument("--metadata-dir", type=Path, required=True)
    parser.add_argument("--as-of", type=date.fromisoformat, required=True, help="Date of the viewing snapshot, YYYY-MM-DD")
    parser.add_argument("--output", type=Path, required=True, help="Private output JSON; never commit")
    args = parser.parse_args()
    try:
        if args.library.stat().st_size > 10_000_000:
            raise ValueError("Library is too large.")
        source = json.loads(args.library.read_text(encoding="utf-8-sig"))
        library = source.get("library") if isinstance(source, dict) else source
        profile, report = build_profile(library, args.metadata_dir, args.as_of)
        args.output.write_text(json.dumps(profile, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        args.output.with_suffix(".report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(report))
    except (OSError, ValueError, TypeError):
        parser.exit(1, "Profile not created: check the complete library, cached metadata and snapshot date.\n")


if __name__ == "__main__":
    main()
