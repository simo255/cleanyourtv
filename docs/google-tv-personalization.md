# Separate Google TV recommendations

Version 2.9 displays an identified, dated capture of Google TV's visible **Top picks for you** row in
its own **Google TV** tab. The separate **Trakt** tab displays only Trakt recommendations. Their
rankings and membership are never merged: Trakt watched exclusions apply only to Trakt, while
Google keeps its identified order. A title can appear independently in both views.

Existing Google imports remain compatible, and the previously selected combined view becomes
Trakt without changing the stored watchlist, Trending or My apps selections. Google TV has its own
Movies/TV shows preference. The existing private account binding remains in place for privacy;
separate views do not remove the configuration requirement described below.

## Capture and refresh

Use an authorized ADB connection, a debuggable launcher APK and the existing Trakt configuration:

```sh
python tools/sync-google-tv.py --adb /path/to/adb --serial YOUR_TV_IP:5555
```

The TV briefly displays Google TV while the tool selects Home and traverses up to 30 leading cards
in the personalized row. It follows the focused row's accessibility tree, excluding the separate hero
promotion row. It reads titles and release years without clicking Watch, purchasing, changing
accounts, enabling activity tracking or extracting authentication tokens. Traversal stops at a repeated
title/year; this is a bounded snapshot and not a guaranteed complete ranking. The supported row/tab
labels are English and French. A changed Google layout causes a failed import rather than guessing.

The onn Android TV 12 firmware gives Google TV's Home priority 2 and this launcher priority 0.
`set-home-activity` reports success while Google TV still resolves as Home when enabled. Disabling
only Google's Home component is rejected by the firmware. For this tested setup the tool therefore
enables Google TV temporarily and **restores its previous enabled/disabled state and verifies this launcher is Home**
when capture finishes, including on navigation failure. An interrupted ADB connection can prevent
cleanup; reconnect and restore with these commands if Google TV was previously disabled:

```sh
adb -s YOUR_TV_IP:5555 shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb -s YOUR_TV_IP:5555 shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
adb -s YOUR_TV_IP:5555 shell input keyevent KEYCODE_HOME
```

The capture identifies entries locally against existing catalog caches and general public Cinemeta
popularity pages. Google titles, profile information, history and private catalog URLs are **never sent
to Cinemeta**. Only exact normalized title + release-year matches with one IMDb identity are kept.
Localized titles without a match, ambiguous remakes and ordinary YouTube clips are omitted. No fuzzy
matching or decoding of private provider IDs is used. Corresponding metadata/posters come from the
matched catalog, and OK opens details in Stremio.

The imported subset preserves Google's relative order after identification, without Trakt filtering.
Its date is shown in the Google TV status. **Reload/Relire** reads the saved snapshot; it does not
recapture Google TV or refresh Trakt. **Refresh/Actualiser** in Trakt refreshes only the Trakt view.
Repeat the command to update Google's snapshot, from a computer or from Termux as described below.
Capture runs only when explicitly launched; there is no background accessibility service, root
requirement, private Google API or scheduled screen capture.

`--limit 1..60` bounds traversal. `--public-pages 0..6` controls general public metadata pages per
movie/show category (two by default). Unsupported/unavailable pages are skipped. Optional
`--save-capture /private/location/google-tv-capture.json` saves a PRIVATE title/year/rank file;
`--input-capture /private/location/google-tv-capture.json` can reidentify it without opening Google TV.
Store captures outside the public checkout or in an ignored backup directory.
If the computer cannot access Cinemeta, first open Trending on the TV and refresh it; the capture
tool can identify titles against those local catalog caches with `--public-pages 0`.

## Run locally in Termux, without a computer

The importer uses only Python's standard library and an authorized ADB client. It can run on a
32-bit Android TV. Install `python` and `android-tools`
from Termux's official repository. On an older Termux bootstrap, update `libc++` and `openssl` too:
an outdated `libc++` can prevent the newer ADB executable from loading.

Put these three repository files together in Termux's **private** `~/tvhome-google-tv/` directory:
`sync-google-tv.py`, `configure-recommendations.py`, and `refresh-google-tv-termux.sh`.
Never put the launcher's private account configuration or ADB private key in shared storage.

```sh
sh ~/tvhome-google-tv/refresh-google-tv-termux.sh
```

The wrapper connects to `127.0.0.1:5555`, so the TV must already have TCP ADB enabled. The Termux
client generates its own key: authorize that key in the TV's debugging dialog. Persistent trust is
optional and requires the owner's explicit approval. No private key from the computer is copied.
After a reboot, check whether the firmware still exposes TCP ADB before assuming the command works.
To use a different authorized local endpoint, set `TVHOME_ADB_SERIAL` for this command only.

The wrapper defaults to cached metadata (`--public-pages 0`), prevents simultaneous captures and
stores its credential-free log and exit code as `last-refresh.log` and `last-refresh.exit` under
`${XDG_STATE_HOME:-$HOME/.local/state}/tvhome-google-tv/`, outside the source checkout. Google TV temporarily occupies the screen; leave the remote untouched until the
custom Home returns. The Google TV tab's Reload control rereads the stored snapshot only.

No shortcut installation is required: run the shell command above each time you want a fresh capture.
For a different private directory, keep all three scripts together and use that path.

If Android kills Termux or the local ADB connection is lost during capture, the usual cleanup may
not run. Restore the custom Home using the recovery commands above (with the local ADB serial),
then remove the empty `.refresh-lock` directory in the state folder before retrying. This is an
explicit refresh command; no boot service or recurring task is installed.

## Storage and undo

The account-bound `files/google-tv-cache.json` lives in this launcher's private storage. It is written
atomically over private ADB stdin, verified without printing its contents and never embedded in an APK.
A different Trakt connection, malformed schema or empty invalid import cannot silently reuse it.
The existing Trakt recommendation cache stays separate; missing/invalid Google data falls back to
Trakt. Failed capture or zero identified titles retains the prior import.

Remove only the Google import while keeping Trakt, local app channels and the dock:

```sh
python tools/sync-google-tv.py --adb /path/to/adb --serial YOUR_TV_IP:5555 --clear
```

The broader `configure-recommendations.py --clear` also removes this import.

## Evidence and limits

Ordinary ADB/UI inspection on the onn Streaming Device 4K Pro established that the visible personalized
row exposes focused titles and years, while private app storage remains inaccessible. Google TV was
temporarily enabled without granting notification access or changing Google privacy settings.
The captured source is the actual visible Google TV row, rather than TvProvider's app channels.

Termux-driven capture has also completed with the computer's ADB connection disconnected. This
establishes on-box execution on the tested firmware, not support for every TV or persistence of TCP
ADB after a reboot. Private captures, connection keys and test reports are excluded from the repo.

Public references:

- [Google TV recommendation factors](https://support.google.com/googletv/answer/10466129)
- [Android accessibility window-content capability](https://developer.android.com/guide/topics/ui/accessibility/views/service)
- [Android intent-priority policy](https://android.googlesource.com/platform/frameworks/base/+/0c36369480790826dffd7f2e1679a33fa0edb427/services/core/java/com/android/server/pm/resolution/ComponentResolver.java)
