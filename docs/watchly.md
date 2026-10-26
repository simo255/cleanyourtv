# Watchly ElfHosted recommendations

The launcher now uses **Watchly on ElfHosted** for **For you / Pour vous**.
The former Trakt Integration connection is no longer accepted or requested by the launcher.
Stored source ID 0 is retained so saved source selections keep working.

## Connect

1. Open [Watchly ElfHosted](https://watchly.elfhosted.com/configure).
2. Connect Stremio as the history source using its official sign-in flow.
3. Enter and validate your own TMDB API key. Pick your metadata language and discovery style.
   French descriptions can accompany English-language films and series; this is not an audio-language filter.
4. Enable **Top Picks for You** for **Both** movies and series. The launcher's For-you feed uses these rows.
   **Because you Watched/Loved** and **Genre & Keyword Catalogs** can also be installed for browsing in Stremio.
5. Save, then install the generated private add-on in Stremio. Keep its manifest URL private.
6. With the existing debuggable launcher and authorized ADB connection, provision that URL:

```sh
python tools/configure-recommendations.py --serial YOUR_TV_IP:5555
```

The hidden prompt accepts `https://watchly.elfhosted.com/<private-token>/manifest.json`
or its base URL. No token is included in this repository. Other origins, credentials in the URL,
ports, query strings, fragments and encoded path separators are rejected. Private requests never
follow redirects. A fresh Stremio export containing exactly one Watchly ElfHosted add-on can also
be supplied using `--stremio-export`; unrelated account fields are discarded.

## What Home shows

- **For you** starts with Watchly's `watchly.rec` movie and series catalogs. If fewer than 20 titles
  survive local date and viewing exclusions, it also reads up to four enabled personalized rows
  (Because you watched, genre/keyword affinities, creators, loved/liked titles). It stops once at least
  20 eligible titles are available or the enabled rows are exhausted. Duplicate titles keep Top Picks
  metadata; rewatch rows are never used. Secondary-row failures preserve successful results. Each media
  type has a 60-second budget for starting supplemental requests; an in-flight request can finish later.
  All candidates retain the current year and two previous years (2024–2026 in 2026); future releases,
  unknown years, watched/completed exclusions and local hidden choices still apply. This can broaden
  a short selection but cannot guarantee 20 results if Watchly's enabled catalogs lack eligible titles.
- **Discover / New releases** and **Trending** keep their existing public Cinemeta behavior.
- **Continue / Reprendre** keeps its separate encrypted Stremio session and one-minute refresh cadence.
- Discovery catalogs keep their existing twelve-hour foreground refresh cadence. This fetches Watchly's
  current rows; it does not force the hosted service to rebuild its recommendation model.

Local hidden/seen choices and completed-viewing exclusions continue to apply. Local positive
preferences may refine ordering. These choices are not uploaded to Watchly, Stremio or any tracker.
The hosted Watchly service has its own history interpretation; the launcher's rule that unfinished
viewing is neutral does not change Watchly's server-side scoring.

## Updates and feedback

Watching in Stremio feeds Watchly after the player has saved and synchronized the viewing history.
Watchly refreshes its profile and catalogs on its own server schedule. The launcher then reads the
current catalogs using its twelve-hour foreground cache; **Home content → Refresh** bypasses that local
cache. This action does not request a Watchly profile rebuild. The separate Continue view reads
Stremio progress on return to Home with a one-minute minimum gap, and every 15 minutes while Home
stays visible. Failed reads use backoff.

Hold OK on a recommendation and choose **Not interested** to hide that title immediately. The
account-bound local choice survives ordinary refreshes and app updates. It is not sent to Watchly
or Stremio, and it does not down-rank every title in the same genre. Use **Clear choice** from the
feedback menu when available, or the Home content menu's feedback reset to remove saved local choices.
Resetting all feedback also removes positive choices and locally recorded completion decisions.

## Replacing an existing connection

Back up private configuration, feedback, APK, session and layout before changing providers.
Changing the private catalog URL changes the account fingerprint. An explicitly authorized migration
for the same person must rebind the feedback file to the new fingerprint while preserving its entries;
ordinary provisioning intentionally does not merge feedback between accounts. Preserve existing watched
baselines and completion profiles when migrating. Old private catalog caches must not be reused as
Watchly results.

Remove **Trakt Integration** from Stremio's installed add-ons and disconnect **Trakt Scrobbling** in
Stremio settings if replacing that service completely. This does not delete the external Trakt account.
The launcher does not write to tracker accounts or report playback.

## Privacy and limits

Watchly ElfHosted reads the history source authorized during setup. Its public service stores account
settings and credentials server-side; the manifest URL is a private bearer link. Treat it and TMDB keys
as credentials. The launcher stores its private configuration and account-bound cache in app-private
storage. Backups and setup files must remain outside commits.

Configuration in Stremio does not itself add Watchly support to older launcher APKs. The launcher must
include `WatchlyCatalog`, the Watchly repository adapter and the corresponding URL validator.

References: [Watchly documentation](https://github.com/TimilsinaBimal/Watchly/blob/1.17.2/README.md),
[Stremio catalog protocol](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md).
