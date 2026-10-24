# Android TV Home · Stremio + Trakt

A customizable fork of [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv), previously
**tv-vibehack**, with personalized recommendations on a clean Android TV home screen.
The recommendation integration has been tested on an **onn Streaming Device 4K Pro, Android TV 12**.
Other firmware needs its own compatibility check.

Cette version ajoute les recommandations Stremio/Trakt, un dock de sept applications modifiable
avec la télécommande, un menu rapide et un indicateur VPN. Chaque utilisateur connecte son
propre catalogue : aucun compte, historique personnel ou lien privé n'est inclus dans le code.

## What this fork adds

- **Stremio Continue / Reprendre**: opt-in QR sign-in to your Stremio account, separate from the Trakt
  catalog connection. Resume films and series with progress, remaining minutes and the exact episode
  link. A Coming next view lists dated upcoming episodes for up to eight recently watched/followed
  IMDb series checked per refresh. Session keys are encrypted using Android Keystore; disconnecting
  deletes this launcher's session and resume cache. Nothing is uploaded to your library or taste profile.
  Select **Continue → Connect Stremio**, scan the QR with your phone and approve on Stremio's site.
- **Trakt**: Movies and TV shows from your existing official Stremio **Trakt Integration**
  add-on, with known watched movies, confirmed completed series and duplicates excluded. A local
  positive-viewing profile can refine ordering. OK opens the title in Stremio.
- **Trending / Tendances**: public **Popular** movie/show catalogs from official Cinemeta,
  identified on screen and independent of your account history.
- **New releases / Nouveautés**: official Cinemeta catalogs for the current and previous year,
  deduplicated and ordered by known release date. Upcoming and undated entries are excluded.
  For shows this means their premiere, not a new season of an older show.
- **Simple recommendation shelf**: one control row and a full-width poster row, without a side
  summary or year selector. Trakt and Popular automatically retain only the current
  and previous release year. This applies to fetched and saved catalogs, before display.
  The header and catalog menu show Trakt, Continue, New releases and Trending; older
  saved Google TV/Watchlist/My apps selections return to Trakt without renumbering stored source IDs.
- **Positive-only viewing preferences**: a confirmed completed series is a positive example;
  unfinished viewing stays neutral. Hold OK for More like this, Not interested, Finished or Undo.
  Preferences remain private and refine Trakt/New releases, with a small diversity pass.
  Manual hidden/seen choices are explicit. No feedback is uploaded to accounts.
- **Optional season-aware discovery (2.32)**: separate film/series taste, reviewed season rules,
  shared watched exclusions and a recent/all-years menu switch. Discover broadens Cinemeta to bounded
  year, genre and Featured pages. Available cast/director metadata enriches the local ranking without
  sending private history IDs for extra metadata lookups. Enable `recommendation_options.enhanced`
  in private configuration; see [the schema and limits](docs/recommendations.md#optional-season-aware-discovery-232).
  No AI model is used. This version requires its own on-TV validation before deployment.
- **Automatic watched exclusions (2.33)**: with enhanced recommendations and Stremio connected,
  returning Home refreshes Stremio viewing independently of catalog refreshes. Movies marked watched
  and completed series are removed from Trakt, Discover and Trending. Private account-bound summaries
  survive restarts; changed series share the existing eight metadata-lookup slots with Continue.
- **Optional TMDB enrichment**: public recent candidates with IMDb mappings join New releases when
  the user's own private TMDB API read token is configured. Cinemeta remains usable without it.
- **Optional weather on Home**: city, temperature and conditions from this fork’s separate weather
  add-on, with dated saved readings when offline. Enable the private theme’s `home_weather` option;
  both apps must be signed with the same key. No city or coordinates are included in the launcher.
- **Seven dock slots on one row**, with an editor controlled by the remote. Hold OK on an app
  to reorder, pin, remove or hide it. Hidden apps can be shown again from the menu.
- **Optional Nocturne cinema UI** with a solid background, title details, a larger poster strip, blue source indicators,
  a frameless app dock, restored poster focus and side menus. Available short synopses appear below the
  title; hold OK retains the classification menu. Enable `themes/nocturne/config.json`.
- **Quick menu** for recommendations, dock editing, network, Bluetooth pairing, sound,
  applications, an optional VPN app, inputs and system settings.
- **VPN indicator** from Android's VPN transport state, without a remote IP lookup.
- **French and English**, cached catalogs/posters, finite network limits and no background service.

The shelf is optional: without private configuration it stays hidden unless you explicitly select
public Trending or New releases from the menu. Trakt supplies candidates and local positive preferences can refine their ranking. This launcher reads
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

Press Home, then **Menu → Home catalogs**. Select **Continue**, **For you**, **New releases** or **Trending** on the
home header; Movies and TV shows work within each view. Only the selected source refreshes.
Catalog refresh happens on returning to Home or while Home is visible once the 4-hour cache interval
expires. Continue keeps its one-minute refresh interval; manual Refresh is also available.
Up from the dock enters posters, Left/Right navigates, Up reaches category buttons, and Down
returns to the dock. Down from the dock opens the app grid. OK opens details without starting playback.

Google TV integration was removed in 2.20. Its old saved selection returns to Trakt and its
snapshot is never loaded. [Legacy capture documentation](docs/google-tv-personalization.md)
is retained for older versions; capture tools are not part of the current Home flow.

To remove the local connection and recommendation cache while keeping dock preferences:

```sh
python tools/configure-recommendations.py --serial YOUR_TV_IP:5555 --clear
```

Trakt recommendations use your existing official Stremio/Trakt add-on connection. No GitHub
association or additional Trakt connection is needed. The add-on supplies the personalized
candidates; local saved preferences can refine them without an additional account request.

**Limits:** initial watched filtering covers the latest 100 history events per category, plus
optional locally seeded IMDb IDs. Previously learned watched IDs are retained. A watched episode
alone does not establish that its show was completed or disliked. Completion preferences use the
imported profile or an explicit local Finished choice. Removing movie history in Trakt does not
automatically remove an accumulated local movie exclusion.
Historical imports and future playback scrobbling must be handled by Stremio/Trakt separately.
See [configuration, privacy and cache details](docs/recommendations.md).

## Customize

The optional `themes/nocturne` theme provides a dark, readable Home layout: complete posters,
larger two-line titles, opaque caption panels, visible app names and a white focus outline.
Sources and categories share one navigation row. Refresh and Stremio disconnect remain in
**Menu → Home catalogs**; an unconnected Stremio view shows a large explanation and Connect button.
Up from a poster returns to the active source, Down returns to the dock, and another Down opens
the app grid. The `refined_home` switch is off for other themes.

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
Each user supplies their own private account connection. No viewing history, device backup, ADB key,
capture, account export or private URL is bundled. See [upstream integration](docs/upstream-sync.md)
for build details and limits.

## License and credits

MIT, see [LICENSE](LICENSE). Based on the launcher and tooling by
[Gathin23 and contributors](https://github.com/Gathin23/cleanyourtv).
The Outfit typeface uses the [SIL Open Font License](launcher/FONT-LICENSE-OFL.txt).
Stremio and Trakt remain independent services; this fork is not an official client of either.
