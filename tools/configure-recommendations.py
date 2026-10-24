#!/usr/bin/env python3
"""Provision the optional official Stremio/Trakt catalog using authorized ADB.

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


def normalize_config(value):
    if not isinstance(value, dict):
        raise ValueError("Configuration must be a JSON object.")
    address = value.get("addon_base", "")
    if not isinstance(address, str):
        raise ValueError("A private official Stremio Trakt manifest URL is required.")
    address = address.strip()
    # Do not allow control characters that urlsplit would otherwise strip.
    if any(ord(c) < 33 or ord(c) > 126 for c in address):
        raise ValueError("The catalog URL contains unsupported characters.")
    url = urlsplit(address)
    path = url.path
    if path.endswith("/manifest.json"):
        path = path[:-len("manifest.json")]
    if (url.scheme != "https" or url.netloc != "www.strem.io"
            or url.query or url.fragment
            or not re.fullmatch(r"/trakt/addon/[A-Za-z0-9_-]+/", path)):
        raise ValueError("Use the HTTPS manifest URL of the official www.strem.io Trakt add-on.")
    result = {"addon_base": "https://www.strem.io" + path}
    for key in ("watched_movie_ids", "watched_series_ids"):
        ids = value.get(key, [])
        if not isinstance(ids, list) or not all(isinstance(x, str) and re.fullmatch(r"tt[0-9]+", x) for x in ids):
            raise ValueError("Watched baselines must be arrays of IMDb IDs.")
        result[key] = sorted(set(ids))
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
                         "files/trending-cache.json.new files/google-tv-cache.json "
                         "files/google-tv-cache.json.tmp files/google-tv-cache.json.bak "
                         "files/google-tv-cache.json.new && rm -rf cache/posters")
        finally:
            self.run("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")


def config_from_export(value):
    """Extract only the official add-on connection, never user or OAuth fields."""
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
        if isinstance(manifest, dict) and str(manifest.get("id", "")).startswith("org.trakt."):
            matches.append(normalize_config({"addon_base": addon.get("transportUrl")}))
    if len(matches) != 1:
        raise ValueError("Expected exactly one official Trakt Integration add-on.")
    return matches[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb", help="Path to the official adb executable")
    parser.add_argument("--serial", help="Authorized ADB device serial, such as your TV IP:5555")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--config", type=Path, help="Private local JSON configuration; never commit this file")
    group.add_argument("--stremio-export", type=Path, help="Local Stremio export; extracts only the official add-on URL")
    group.add_argument("--clear", action="store_true", help="Remove the local connection, watched baseline and poster cache")
    args = parser.parse_args()
    device = Device(args.adb, args.serial)
    try:
        if args.clear:
            device.clear()
            print("Local Trakt connection and cache removed. Dock preferences kept.")
            return 0
        source = args.config or args.stremio_export
        if source:
            if source.stat().st_size > (10_000_000 if args.stremio_export else 2_000_000):
                raise ValueError("Configuration is too large.")
            value = json.loads(source.read_text(encoding="utf-8-sig"))
            if args.stremio_export:
                value = config_from_export(value)
        else:
            value = {"addon_base": getpass.getpass("Private official Stremio Trakt manifest URL (hidden): ")}
        device.provision(normalize_config(value))
        print("Private connection configured and verified. Press Home, then Menu > Trakt recommendations.")
        return 0
    except (ValueError, RuntimeError, OSError, EOFError):
        # Do not expose parser exception text: it can include the private URL.
        print("Setup failed. Check the official manifest URL, local JSON and authorized debug ADB connection.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
