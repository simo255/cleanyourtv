# Android TV Home · Stremio + Trakt

A customizable fork of [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv), previously
**tv-vibehack**, with personalized recommendations on a clean Android TV home screen.
The recommendation integration has been tested on an **onn Streaming Device 4K Pro, Android TV 12**.
Other firmware needs its own compatibility check.

Cette version ajoute les recommandations Stremio/Trakt, un dock de sept applications modifiable
avec la télécommande, un menu rapide et un indicateur VPN. Chaque utilisateur connecte son
propre catalogue : aucun compte, historique personnel ou lien privé n'est inclus dans le code.

## What this fork adds

- **Trakt**: Movies and TV shows from your existing official Stremio **Trakt Integration**
  add-on, with known watched titles and duplicates excluded. OK opens the title in Stremio.
- **Google TV**: an independent, dated capture of Google's visible Top picks. Its tab keeps Google's
  identified order and does not use Trakt's watched exclusions or recommendation ranking.
- **Watchlist / À regarder**: your Trakt watchlist with its own empty state and cache.
- **Trending / Tendances**: public **Popular** movie/show catalogs from official Cinemeta,
  identified on screen and independent of your account history.
- **My apps / Mes applis**: searchable Android TV channels published locally by installed apps,
  plus **Continue / Reprendre** from Watch Next and entries with playback progress. Choose a
  channel or mix all channels; cards identify and open their owning app.
- **Seven dock slots on one row**, with an editor controlled by the remote. Hold OK on an app
  to reorder, pin, remove or hide it. Hidden apps can be shown again from the menu.
- **Quick menu** for recommendations, dock editing, network, Bluetooth pairing, sound,
  applications, an optional VPN app, inputs and system settings.
- **VPN indicator** from Android's VPN transport state, without a remote IP lookup.
- **French and English**, cached catalogs/posters, finite network limits and no background service.

The shelf is optional: without private configuration it stays hidden unless you explicitly select
public Trending or My apps from the menu. Personalized Trakt ranking comes from the official integration. This launcher reads
catalogs; it does not upload history, report playback, or write to your Stremio/Trakt account.

This branch integrates upstream through `cc4c7c59`, including themes, adaptive tile sizes, module
cards, improved focus animation, remote navigation fixes and additional translations.
The VPN capsule now uses upstream's network-pill display, including an inactive indicator when
a VPN app is configured. Upstream keeps its reference launcher free of content feeds;
this fork deliberately takes a different direction. Original authorship and MIT licensing remain.

## Build and try the launcher

Requires JDK 17, Android SDK platform 34/build-tools 34.0.0, Python 3.9+, and official Android
platform-tools (`adb`). The launcher requires Android 8.0 or later; other devices are unverified.

```sh
git clone --branch main https://github.com/simo255/cleanyourtv.git
cd cleanyourtv/launcher
./gradlew assembleDebug
```

On Windows, use `gradlew.bat assembleDebug`. Set `JAVA_HOME` and `ANDROID_HOME` to your installed
JDK and SDK. A debug build is required for the current private-configuration tool.

Enable developer options and USB/network debugging on your own TV, then authorize this computer
when prompted on the TV. From the repository root:

```sh
adb connect YOUR_TV_IP:5555
adb -s YOUR_TV_IP:5555 install -r launcher/app/build/outputs/apk/debug/app-debug.apk
adb -s YOUR_TV_IP:5555 shell am start -n com.gathin.tvhome/.MainActivity
```

These commands open the launcher for a trial. To select it as Home afterwards:

```sh
adb -s YOUR_TV_IP:5555 shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
```

Firmware behavior varies. Record your original Home component before changing it so you can
restore it with the same command. Package disabling and idle/power changes are separate device
operations; they are not performed by this launcher or its recommendation setup tool. The legacy
debloat/install scripts and model examples are inherited from upstream and need review for your TV.

Updating an existing install requires the same signing key. Build from your original development
environment if you want to preserve its app data; an APK signed by a different key will not update it.

## Connect Stremio + Trakt

1. Link your Trakt account in Stremio and enable the official **Trakt Integration** add-on.
2. Download a fresh Stremio account export and keep it private, outside this repository.
3. From the repository root, run:

```sh
python tools/configure-recommendations.py --serial YOUR_TV_IP:5555 --stremio-export /path/to/stremioExport.json
```

Only the official add-on's private catalog URL is copied into the launcher's private storage.
The account export itself is not sent to the TV. You can instead omit `--stremio-export` and paste
your manifest URL at a hidden prompt. Use `--adb /path/to/adb` when needed.

Press Home, then **Menu → Home catalogs**. Select **Trakt**, **Google TV**, **Watchlist** or **Trending** on the
home header; Movies and TV shows work within each view. Only the selected source refreshes.
Refresh happens on returning to Home or while Home is visible once the 15-minute cache interval
expires; manual Refresh is also available.
Up from the dock enters posters, Left/Right navigates, Up reaches category buttons, and Down
returns to the dock. Down from the dock opens the app grid. OK opens details without starting playback.

For local app content, select **My apps** and allow the TV-listings permission. **Channels…**
selects all channels or one app/channel; **Continue** shows available resume entries. These links
follow the publishing app's behavior and can resume playback. Google TV Home can remain disabled;
this reads Android TV's shared data, not Google's private personalized home ranking. Apps control
the freshness and completeness of their published suggestions.

To populate the separate Google TV view, run the
capture while your TV is available for temporary navigation:

```sh
python tools/sync-google-tv.py --adb /path/to/adb --serial YOUR_TV_IP:5555
```

On the tested onn firmware Google TV must be disabled again after capture to preserve Home.
The tool restores it automatically. Only unambiguous title/year movie/show matches are imported;
the shelf shows Google's capture date. Reload in Google TV reads the saved import, while another
capture is needed for new Google suggestions. Trakt Refresh is independent. See
[capture, privacy, limitations and undo](docs/google-tv-personalization.md).

To remove the local connection and recommendation cache while keeping dock preferences:

```sh
python tools/configure-recommendations.py --serial YOUR_TV_IP:5555 --clear
```

**Limits:** initial watched filtering covers the latest 100 history events per category, plus
optional locally seeded IMDb IDs. Previously learned watched IDs are retained. A watched episode
excludes its show, and removing an event in Trakt does not automatically remove a local exclusion.
Historical imports and future playback scrobbling must be handled by Stremio/Trakt separately.
See [configuration, privacy and cache details](docs/recommendations.md).

## Customize

The starter dock lists Stremio, YouTube, Prime Video and Netflix, with capacity for seven apps.
Choose any installed apps and their order from the TV; no device owner's favorites are bundled.
Missing applications are skipped. Use **Menu → Edit dock**, or hold OK on an app tile, to choose
your own order. Removing an app from the dock leaves it in the grid; hiding affects this launcher
only. Preferences survive restarts and updates.

Defaults live in
[`config.xml`](launcher/app/src/main/res/values/config.xml). Upstream's [themes](themes/README.md)
can overlay those defaults without rebuilding; saved dock edits take precedence over theme dock
defaults. Tiles automatically fit the configured column count. The quick menu's VPN shortcut uses
`vpn_app` (empty by default); the VPN status badge works independently of the VPN provider. Bluetooth pairing on
Android 12+ requests Nearby devices permission before opening the system pairing screen.

The catalog shelf shrinks to the space above the dock when a theme leaves grid rows visible.
If there is insufficient room, it stays hidden so the grid remains usable. Module cards and
the upstream input/Home accessibility helpers remain optional; pulling this code does not enable
services, change the device's default Home or install an APK.

## Compatibility and validation

The dock editor, VPN indicator, Stremio/Trakt catalogs, app channels and Termux-driven capture were
exercised on Android TV 12. The latest upstream merge also builds and passes its JVM and Python
tests. Other devices, firmware versions and streaming-app playback paths are not universally verified.
Google TV import supports the documented English/French UI and only exact title/year matches; it
does not provide continuous synchronization or guarantee every recommended title can be identified.

For on-box refresh, follow the [Termux setup](docs/google-tv-personalization.md#run-locally-in-termux-without-a-computer).
Each user supplies their own private account connection. No viewing history, device backup, ADB key,
capture, account export or private URL is bundled. See [upstream integration](docs/upstream-sync.md)
for build details and limits.

## License and credits

MIT, see [LICENSE](LICENSE). Based on the launcher and tooling by
[Gathin23 and contributors](https://github.com/Gathin23/cleanyourtv).
The Outfit typeface uses the [SIL Open Font License](launcher/FONT-LICENSE-OFL.txt).
Stremio and Trakt remain independent services; this fork is not an official client of either.
