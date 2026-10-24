#!/usr/bin/env python3
"""Import a dated Google TV Top-picks snapshot and blend identified movies/shows with Trakt.

Google TV briefly occupies the screen. Its original enabled/disabled state is restored
after capture along with the custom Home. No playback, account mutation, token extraction,
or private Google API is used. Requires an authorized ADB connection and debug launcher.
"""
import argparse
import hashlib
import html
import importlib.util
import json
from pathlib import Path
import re
import unicodedata
import urllib.request
from urllib.parse import urlsplit
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("recommendation_setup", Path(__file__).with_name("configure-recommendations.py"))
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)
GOOGLE = "com.google.android.apps.tv.launcherx"
HOME = setup.PACKAGE + "/.MainActivity"
ROWS = {"Meilleures recommandations pour vous", "Top picks for you"}


def clean(value):
    return " ".join(unicodedata.normalize("NFC", html.unescape(value or "")).split())


def bounds(node):
    values = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
    return values if len(values) == 4 else [0, 0, 0, 0]


def focused(tree):
    return next((n for n in tree.iter("node") if n.get("focused") == "true"), None)


def personal_row(tree):
    parents = {child: parent for parent in tree.iter() for child in parent}
    node = focused(tree)
    while node is not None:
        if clean(node.get("content-desc")) in ROWS and node.get("class") == "android.widget.GridView":
            return node
        node = parents.get(node)
    return None


def card(tree, rank):
    row = personal_row(tree)
    if row is None:
        raise ValueError("Focus left Google's personalized Top picks row.")
    row_top = bounds(row)[1]
    panel = [n for n in tree.iter("node") if bounds(n)[1] >= row_top * .2 and bounds(n)[3] < row_top]
    logos = {clean(n.get("content-desc")) for n in panel if n.get("class") == "android.widget.ImageView"
             and bounds(n)[3] - bounds(n)[1] >= 50 and clean(n.get("content-desc"))}
    if len(logos) == 1:
        title = next(iter(logos))
    else:
        texts = [n for n in panel if n.get("class") == "android.widget.TextView" and clean(n.get("text"))
                 and bounds(n)[3] - bounds(n)[1] >= 50]
        texts.sort(key=lambda n: bounds(n)[3] - bounds(n)[1], reverse=True)
        if not texts:
            raise ValueError("No unambiguous focused title exposed by Google TV.")
        title = clean(texts[0].get("text"))
    years = set()
    for n in panel:
        match = re.fullmatch(r"((?:18|19|20|21)\d{2})(?:\s*[–-].*)?", clean(n.get("text")))
        if match:
            years.add(match.group(1))
    return {"rank": rank, "title": title, "year": next(iter(years)) if len(years) == 1 else ""}


def title_key(value):
    return "".join(c for c in unicodedata.normalize("NFKD", clean(value)).casefold() if c.isalnum())


def validate_capture(value):
    if not isinstance(value, dict) or value.get("provider") != "google-tv-ui" or value.get("schema") != 1:
        raise ValueError("Invalid capture source.")
    if type(value.get("captured_at")) is not int or value["captured_at"] <= 0:
        raise ValueError("Invalid capture time.")
    items = value.get("items")
    if not isinstance(items, list) or not 1 <= len(items) <= 60:
        raise ValueError("Invalid capture size.")
    for rank, item in enumerate(items, 1):
        if not isinstance(item, dict) or type(item.get("rank")) is not int or item["rank"] != rank:
            raise ValueError("Invalid capture order.")
        if not isinstance(item.get("title"), str) or not clean(item["title"]) or len(item["title"]) > 2000:
            raise ValueError("Invalid captured title.")
        if not isinstance(item.get("year"), str) or (item["year"] and not re.fullmatch(r"(?:18|19|20|21)\d{2}", item["year"])):
            raise ValueError("Invalid captured year.")
    return value


def identify(items, metadata):
    index = {}
    for item in metadata:
        if not isinstance(item, dict) or item.get("type") not in ("movie", "series") or not isinstance(item.get("id"), str) or not re.fullmatch(r"tt\d+", item["id"]):
            continue
        year = re.match(r"(?:18|19|20|21)\d{2}", str(item.get("year", item.get("releaseInfo", ""))))
        if not year or not item.get("name"):
            continue
        key = (title_key(item["name"]), year[0])
        index.setdefault(key, {})[(item["type"], item["id"])] = item
    result = {"movies": [], "shows": []}
    seen = set()
    for item in items:
        if not isinstance(item, dict) or not isinstance(item.get("title"), str) or not re.fullmatch(r"(?:18|19|20|21)\d{2}", str(item.get("year", ""))):
            continue
        matches = index.get((title_key(item["title"]), str(item["year"])), {})
        if len(matches) != 1:
            continue  # Never guess localized titles, remakes or ambiguous movie/show matches.
        identity, meta = next(iter(matches.items()))
        if identity in seen:
            continue
        seen.add(identity)
        value = {k: meta.get(k, "") for k in ("id", "type", "poster", "description")}
        value.update(name=clean(item["title"]), year=str(meta.get("year", meta.get("releaseInfo", item["year"]))), google_rank=item["rank"])
        result["movies" if meta["type"] == "movie" else "shows"].append(value)
    return result


class Capture(setup.Device):
    def key(self, *keys):
        self.run("shell", "input", "keyevent", *keys)

    def ui(self):
        self.run("shell", "uiautomator", "dump", "/data/local/tmp/onn-google-ui.xml")
        return ET.fromstring(self.run("exec-out", "cat", "/data/local/tmp/onn-google-ui.xml"))

    def restore_home(self):
        # Some Google TV firmware needs the original disabled state restored before selecting Home.
        state = "disable-user" if self.google_was_disabled else "enable"
        self.run("shell", "pm", state, "--user", "0", GOOGLE)
        self.run("shell", "cmd", "package", "set-home-activity", "--user", "0", HOME)
        self.key("KEYCODE_HOME")
        actual = self.run("shell", "cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
        if HOME.encode() not in actual:
            raise RuntimeError("Could not restore the custom default Home.")

    def capture(self, limit):
        if HOME.encode() not in self.run("shell", "cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"):
            raise RuntimeError("Start with this launcher as the default Home; no existing launcher choice will be replaced.")
        installed = self.run("shell", "pm", "list", "packages", "--user", "0", GOOGLE).splitlines()
        package_line = ("package:" + GOOGLE).encode()
        if package_line not in installed:
            raise RuntimeError("Google TV is not installed; no package state was changed.")
        self.google_was_disabled = package_line in self.run(
            "shell", "pm", "list", "packages", "-d", "--user", "0", GOOGLE).splitlines()
        try:
            self.run("shell", "pm", "enable", "--user", "0", GOOGLE)
            self.run("shell", "am", "start", "-W", "-n", GOOGLE + "/.home.HomeActivity")
            tree = self.ui()
            # Google remembers the last selected tab. Focus and explicitly activate Home.
            for _ in range(18):
                node = focused(tree)
                if node is not None and node.get("resource-id", "").endswith("/top_nav_tab"):
                    break
                self.key("KEYCODE_DPAD_UP"); tree = self.ui()
            else:
                raise RuntimeError("Google TV navigation unavailable; close dialogs and retry.")
            for _ in range(5):
                node = focused(tree)
                if any(clean(n.get("text")) in {"Accueil", "Home", "For you", "Pour vous"} for n in node.iter("node")):
                    break
                self.key("KEYCODE_DPAD_LEFT"); tree = self.ui()
            else:
                raise RuntimeError("Google TV Home tab not found.")
            self.key("KEYCODE_DPAD_CENTER"); tree = self.ui()
            for _ in range(6):
                if personal_row(tree) is not None:
                    break
                self.key("KEYCODE_DPAD_DOWN"); tree = self.ui()
            else:
                raise RuntimeError("Google TV Top picks not found; app-only mode and other layouts are unsupported.")
            items = []; seen = set()
            for rank in range(1, limit + 1):
                value = card(tree, rank)
                key = (value["title"], value["year"])
                if key in seen:
                    break
                seen.add(key); items.append(value)
                print("Captured Google TV card", rank, flush=True)
                if rank < limit:
                    self.key("KEYCODE_DPAD_RIGHT"); tree = self.ui()
            if not items:
                raise RuntimeError("No Google TV cards captured; previous snapshot retained.")
            return {"items": items, "captured_at": int(self.run("shell", "date", "+%s").strip()) * 1000,
                    "provider": "google-tv-ui", "schema": 1}
        finally:
            try:
                self.restore_home()
            finally:
                try:
                    self.run("shell", "rm", "-f", "/data/local/tmp/onn-google-ui.xml")
                except RuntimeError:
                    pass  # A disconnected device can prevent cleanup; never mask Home restoration errors.

    def metadata(self):
        result = []
        for name in ("recommendations", "trending", "watchlist"):
            try:
                data = json.loads(self.run("exec-out", "run-as", setup.PACKAGE, "cat", "files/" + name + "-cache.json"))
                result.extend(data.get("movies", [])); result.extend(data.get("shows", []))
            except (RuntimeError, ValueError):
                pass
        return result

    def provision_snapshot(self, value):
        payload = json.dumps(value, ensure_ascii=True, separators=(",", ":")).encode()
        if len(payload) > 1_000_000:
            raise ValueError("Snapshot too large.")
        self.run("shell", "am", "start", "-W", "-a", "android.settings.SETTINGS")
        self.run("shell", "am", "force-stop", setup.PACKAGE)
        try:
            self.private("umask 077; mkdir -p files && cat > files/google-tv-cache.json.tmp", data=payload)
            stored = self.run("exec-out", "run-as", setup.PACKAGE, "cat", "files/google-tv-cache.json.tmp")
            if hashlib.sha256(stored).digest() != hashlib.sha256(payload).digest():
                raise RuntimeError("Snapshot verification failed.")
            self.private("mv files/google-tv-cache.json.tmp files/google-tv-cache.json")
        finally:
            self.run("shell", "am", "start", "-W", "-n", HOME)

    def clear_snapshot(self):
        self.run("shell", "am", "start", "-W", "-a", "android.settings.SETTINGS")
        self.run("shell", "am", "force-stop", setup.PACKAGE)
        try:
            self.private("rm -f files/google-tv-cache.json files/google-tv-cache.json.tmp files/google-tv-cache.json.bak files/google-tv-cache.json.new")
        finally:
            self.run("shell", "am", "start", "-W", "-n", HOME)


class PublicRedirect(urllib.request.HTTPRedirectHandler):
    max_redirections = 2

    def redirect_request(self, req, fp, code, msg, headers, address):
        validate_public(address)
        return super().redirect_request(req, fp, code, msg, headers, address)


def validate_public(address):
    uri = urlsplit(address)
    if uri.scheme != "https" or uri.hostname not in {"v3-cinemeta.strem.io", "cinemeta-catalogs.strem.io"} or uri.port is not None or uri.username or uri.password or uri.query or uri.fragment:
        raise ValueError("Untrusted public catalog redirect.")


def public_metadata(pages):
    # General public popularity pages only: Google titles, account identifiers and history are never sent.
    result = []; opener = urllib.request.build_opener(PublicRedirect())
    for kind in ("movie", "series"):
        for page in range(pages):
            extra = "/skip=" + str(page * 50) if page else ""
            address = "https://v3-cinemeta.strem.io/catalog/" + kind + "/top" + extra + ".json"
            validate_public(address)
            try:
                with opener.open(address, timeout=20) as response:
                    data = response.read(2_000_001)
                    if len(data) > 2_000_000:
                        raise ValueError("Catalog too large.")
                    values = json.loads(data)["metas"]
                    if not isinstance(values, list):
                        raise ValueError("Invalid public catalog.")
                    result.extend(values)
            except Exception:
                print("A public metadata page was unavailable; keeping other metadata.", flush=True)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--limit", type=int, default=30, choices=range(1, 61), metavar="1..60")
    parser.add_argument("--public-pages", type=int, default=2, choices=range(0, 7), metavar="0..6")
    parser.add_argument("--save-capture", type=Path, help="Optional PRIVATE local capture JSON; never commit it")
    action = parser.add_mutually_exclusive_group()
    action.add_argument("--input-capture", type=Path, help="Reidentify a previous private capture without opening Google TV")
    action.add_argument("--clear", action="store_true", help="Remove only the Google import, keeping Trakt and dock preferences")
    args = parser.parse_args()
    device = Capture(args.adb, args.serial)
    device.run("shell", "run-as", setup.PACKAGE, "id")
    if args.clear:
        device.clear_snapshot()
        print("Google TV import removed; Trakt, app channels and dock preferences preserved.")
        return
    config = json.loads(device.run("exec-out", "run-as", setup.PACKAGE, "cat", "files/recommendations-config.json"))
    account = hashlib.sha256(setup.normalize_config(config)["addon_base"].encode()).hexdigest()
    if args.input_capture:
        capture = json.loads(args.input_capture.read_text(encoding="utf-8"))
    else:
        capture = device.capture(args.limit)
    validate_capture(capture)
    if args.save_capture:
        args.save_capture.write_text(json.dumps(capture, ensure_ascii=True, indent=2), encoding="utf-8")
    matched = identify(capture["items"], device.metadata() + public_metadata(args.public_pages))
    count = len(matched["movies"]) + len(matched["shows"])
    if count == 0:
        raise RuntimeError("No exact title/year matches. Previous snapshot retained; no fuzzy matches were used.")
    snapshot = {"schema": 1, "provider": "google-tv-ui", "account": account, "captured_at": capture["captured_at"],
                "captured_count": len(capture["items"]), **matched}
    device.provision_snapshot(snapshot)
    print("Imported", len(matched["movies"]), "movies and", len(matched["shows"]), "shows from", len(capture["items"]), "visible Google TV cards.")
    print("Unmatched/ambiguous entries and YouTube clips are omitted. Google is a dated snapshot; Refresh updates Trakt only.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError, ET.ParseError):
        print("Google TV sync failed. Check navigation/connection; the previous imported snapshot is retained. No credentials were printed.", file=__import__("sys").stderr)
        raise SystemExit(1)
