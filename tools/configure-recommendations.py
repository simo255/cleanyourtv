#!/usr/bin/env python3
"""Provision the optional Watchly ElfHosted catalog using authorized ADB.

The private URL is read with getpass, or from a local JSON file. It is never
passed as a process argument, copied to shared TV storage, or printed.
Requires a debuggable launcher build (assembleDebug) and official adb.
"""

import argparse
import getpass
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import urlsplit

PACKAGE = "com.gathin.tvhome"


def metadata_fields(entry):
    result = {}
    for key, limit in (("genres", 12), ("cast", 8), ("directors", 4)):
        if key not in entry and key != "genres":
            continue
        values = entry.get(key, [])
        if (not isinstance(values, list) or len(values) > limit
                or not all(isinstance(v, str) and 0 < len(v.strip()) <= 120
                           and not any(ord(c) < 32 for c in v) for v in values)):
            raise ValueError("Invalid local metadata.")
        result[key] = list(dict.fromkeys(v.strip() for v in values))
    return result


def normalize_seasons(values):
    if not isinstance(values, list) or len(values) > 100:
        raise ValueError("Invalid seasons.")
    result, seen = [], set()
    for value in values:
        if not isinstance(value, dict):
            raise ValueError("Invalid season.")
        fields = {k: value.get(k, 0) for k in ("season", "total", "watched", "aired", "future", "undated")}
        if (any(type(v) is not int or not 0 <= v <= 10000 for v in fields.values())
                or not 1 <= fields["season"] <= 1000 or fields["season"] in seen
                or fields["watched"] > fields["total"]
                or fields["watched"] > fields["aired"] + fields["undated"]
                or fields["aired"] + fields["future"] + fields["undated"] != fields["total"]):
            raise ValueError("Invalid season counts.")
        state = value.get("status", "")
        if state not in ("", "watched", "unfinished", "unwatched"):
            raise ValueError("Invalid season decision.")
        if state:
            fields["status"] = state
        seen.add(fields["season"])
        result.append(fields)
    return sorted(result, key=lambda s: s["season"])


def normalize_taste_profile(profile):
    if not isinstance(profile, dict) or profile.get("schema") != 1:
        raise ValueError("Invalid taste profile.")
    entries = profile.get("completed", [])
    if not isinstance(entries, list) or len(entries) > 1000:
        raise ValueError("Invalid completed-viewing list.")
    result, seen = [], set()
    for entry in entries:
        if not isinstance(entry, dict):
            raise ValueError("Invalid completed-viewing entry.")
        if entry.get("completed") is not True:
            continue  # incomplete and unknown records are neutral, never dislikes
        identity, kind = entry.get("id", ""), entry.get("type")
        genres = entry.get("genres", [])
        if (not isinstance(identity, str) or not re.fullmatch(r"tt[0-9]+", identity)
                or kind not in ("movie", "series") or not isinstance(genres, list) or len(genres) > 12
                or not all(isinstance(g, str) and 0 < len(g.strip()) <= 80
                           and not any(ord(c) < 32 for c in g) for g in genres)):
            raise ValueError("Invalid completed-viewing details.")
        if (kind, identity) not in seen:
            seen.add((kind, identity))
            result.append({"id": identity, "type": kind, "completed": True,
                           **metadata_fields(entry)})
    normalized = {"schema": 1, "completed": result}
    for key, kind in (("viewing", "series"), ("positives", "movie")):
        if key not in profile:
            continue
        values = profile[key]
        if not isinstance(values, list) or len(values) > 1000:
            raise ValueError("Invalid local viewing profile.")
        records, identities = [], set()
        for entry in values:
            if (not isinstance(entry, dict) or entry.get("type") != kind
                    or not isinstance(entry.get("id"), str) or not re.fullmatch(r"tt[0-9]+", entry["id"])):
                raise ValueError("Invalid local viewing record.")
            record = {"id": entry["id"], "type": kind, **metadata_fields(entry)}
            if key == "viewing":
                record["seasons"] = normalize_seasons(entry.get("seasons"))
                if "reviewed_at" in entry:
                    reviewed_at = entry["reviewed_at"]
                    if type(reviewed_at) is not int or not 0 < reviewed_at <= 32503680000000:
                        raise ValueError("Invalid review timestamp.")
                    record["reviewed_at"] = reviewed_at
            if entry["id"] not in identities:
                identities.add(entry["id"])
                records.append(record)
        normalized[key] = records
    return normalized


def normalize_config(value):
    if not isinstance(value, dict):
        raise ValueError("Configuration must be a JSON object.")
    address = value.get("addon_base", "")
    if not isinstance(address, str):
        raise ValueError("A private Watchly ElfHosted manifest URL is required.")
    address = address.strip()
    # Do not allow control characters that urlsplit would otherwise strip.
    if any(ord(c) < 33 or ord(c) > 126 for c in address):
        raise ValueError("The catalog URL contains unsupported characters.")
    url = urlsplit(address)
    path = url.path
    if path.endswith("/manifest.json"):
        path = path[:-len("manifest.json")]
    if (url.scheme != "https" or url.netloc != "watchly.elfhosted.com"
            or url.query or url.fragment
            or not re.fullmatch(r"/[A-Za-z0-9_-]{1,64}/", path)):
        raise ValueError("Use the HTTPS manifest URL of Watchly on watchly.elfhosted.com.")
    result = {"addon_base": "https://watchly.elfhosted.com" + path}
    for key in ("watched_movie_ids", "watched_series_ids"):
        ids = value.get(key, [])
        if not isinstance(ids, list) or not all(isinstance(x, str) and re.fullmatch(r"tt[0-9]+", x) for x in ids):
            raise ValueError("Watched baselines must be arrays of IMDb IDs.")
        result[key] = sorted(set(ids))
    if "taste_profile" in value:
        result["taste_profile"] = normalize_taste_profile(value["taste_profile"])
    if "recommendation_options" in value:
        options = value["recommendation_options"]
        if not isinstance(options, dict) or type(options.get("enhanced", False)) is not bool:
            raise ValueError("Invalid recommendation options.")
        result["recommendation_options"] = {"enhanced": options.get("enhanced", False)}
    if "tmdb_read_token" in value:
        token = value["tmdb_read_token"]
        if not isinstance(token, str) or not 20 <= len(token) <= 4096 or not re.fullmatch(r"[A-Za-z0-9._~-]+", token):
            raise ValueError("Invalid TMDB read token.")
        result["tmdb_read_token"] = token
    return result


class Device:
    def __init__(self, executable, serial):
        self.command = [executable] + (["-s", serial] if serial else [])

    def run(self, *args, data=None):
        try:
            completed = subprocess.run(self.command + list(args), input=data,
                                       capture_output=True, timeout=40)
        except (OSError, subprocess.TimeoutExpired):
            raise RuntimeError("ADB unavailable or timed out. Check the authorized TV connection.") from None
        if completed.returncode:
            # ADB output may contain account data when reading a private file.
            raise RuntimeError("ADB operation failed. Check the selected TV and use an assembleDebug build.")
        return completed.stdout

    def private(self, script, data=None):
        return self.run("shell", "-T", f"run-as {PACKAGE} sh -c '{script}'", data=data)

    def provision(self, config):
        payload = json.dumps(config, separators=(",", ":")).encode("utf-8")
        if len(payload) > 2_000_000:
            raise ValueError("Configuration is too large.")
        # Check run-as before changing which activity is visible.
        self.run("shell", "run-as", PACKAGE, "id")
        # Android can immediately restart a foreground HOME after force-stop.
        # Keep Settings in front while replacing the private configuration.
        self.run("shell", "am", "start", "-W", "-a", "android.settings.SETTINGS")
        self.run("shell", "am", "force-stop", PACKAGE)
        try:
            self.private("umask 077; mkdir -p files && cat > files/recommendations-config.json.tmp && "
                         "mv files/recommendations-config.json.tmp files/recommendations-config.json", data=payload)
            stored = self.run("exec-out", "run-as", PACKAGE, "cat", "files/recommendations-config.json")
            if hashlib.sha256(stored).digest() != hashlib.sha256(payload).digest():
                raise RuntimeError("Private configuration verification failed.")
        finally:
            self.run("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")

    def clear(self):
        self.run("shell", "run-as", PACKAGE, "id")
        self.run("shell", "am", "start", "-W", "-a", "android.settings.SETTINGS")
        self.run("shell", "am", "force-stop", PACKAGE)
        try:
            # Fixed app-private paths only; leave dock/app preferences intact.
            self.private("rm -f files/recommendations-config.json files/recommendations-config.json.tmp "
                         "files/recommendations-cache.json files/recommendations-cache.json.bak "
                         "files/recommendations-cache.json.new files/watchlist-cache.json "
                         "files/watchlist-cache.json.bak files/watchlist-cache.json.new "
                         "files/trending-cache.json files/trending-cache.json.bak "
                         "files/trending-cache.json.new files/recent-cache.json "
                         "files/recent-cache.json.bak files/recent-cache.json.new files/google-tv-cache.json "
                         "files/recommendation-feedback.json files/recommendation-feedback.json.bak "
                         "files/recommendation-feedback.json.new "
                         "files/google-tv-cache.json.tmp files/google-tv-cache.json.bak "
                         "files/google-tv-cache.json.new && rm -rf cache/posters")
        finally:
            self.run("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")


def config_from_export(value):
    """Extract only the Watchly connection, never user or OAuth fields."""
    addons = value.get("addons", {}) if isinstance(value, dict) else {}
    if isinstance(addons, dict):
        addons = addons.get("addons", [])
    if not isinstance(addons, list):
        raise ValueError("No installed add-ons found in the export.")
    matches = []
    for addon in addons:
        if not isinstance(addon, dict):
            continue
        manifest = addon.get("manifest", {})
        if isinstance(manifest, dict) and "watchly" in str(manifest.get("id", "")).lower():
            matches.append(normalize_config({"addon_base": addon.get("transportUrl")}))
    if len(matches) != 1:
        raise ValueError("Expected exactly one Watchly ElfHosted add-on.")
    return matches[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb", help="Path to the official adb executable")
    parser.add_argument("--serial", help="Authorized ADB device serial, such as your TV IP:5555")
    parser.add_argument("--taste-profile", type=Path, help="Private completed-viewing profile generated locally")
    parser.add_argument("--enhanced-recommendations", action="store_true", help="Opt into local season-aware ranking and broader Cinemeta discovery")
    token_group = parser.add_mutually_exclusive_group()
    token_group.add_argument("--tmdb-token-file", type=Path, help="Private UTF-8 file containing your TMDB API read token")
    token_group.add_argument("--prompt-tmdb-token", action="store_true", help="Read your TMDB API read token at a hidden prompt")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--config", type=Path, help="Private local JSON configuration; never commit this file")
    group.add_argument("--stremio-export", type=Path, help="Local Stremio export; extracts only the official add-on URL")
    group.add_argument("--clear", action="store_true", help="Remove the local connection, watched baseline and poster cache")
    args = parser.parse_args()
    device = Device(args.adb, args.serial)
    try:
        if args.clear:
            device.clear()
            print("Local Watchly connection and cache removed. Dock preferences kept.")
            return 0
        source = args.config or args.stremio_export
        if source:
            if source.stat().st_size > (10_000_000 if args.stremio_export else 2_000_000):
                raise ValueError("Configuration is too large.")
            value = json.loads(source.read_text(encoding="utf-8-sig"))
            if args.stremio_export:
                value = config_from_export(value)
        else:
            value = {"addon_base": getpass.getpass("Private Watchly ElfHosted manifest URL (hidden): ")}
        if args.taste_profile:
            if args.taste_profile.stat().st_size > 256_000:
                raise ValueError("Taste profile is too large.")
            value["taste_profile"] = json.loads(args.taste_profile.read_text(encoding="utf-8-sig"))
        if args.enhanced_recommendations:
            value["recommendation_options"] = {"enhanced": True}
        if args.tmdb_token_file:
            if args.tmdb_token_file.stat().st_size > 8192:
                raise ValueError("TMDB token file is too large.")
            value["tmdb_read_token"] = args.tmdb_token_file.read_text(encoding="utf-8-sig").strip()
        elif args.prompt_tmdb_token:
            value["tmdb_read_token"] = getpass.getpass("Personal TMDB API read token (hidden): ").strip()
        device.provision(normalize_config(value))
        print("Private connection configured and verified. Press Home, then Menu > Watchly.")
        return 0
    except (ValueError, RuntimeError, OSError, EOFError):
        # Do not expose parser exception text: it can include the private URL.
        print("Setup failed. Check the official manifest URL, local JSON and authorized debug ADB connection.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
