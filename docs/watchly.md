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
preferences may refine ordering. Choices stay local by default. The optional account sync below
sends explicit positive and watched choices to Stremio, which Watchly reads.
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

### Sync explicit choices to Stremio

In **Home content**, choose **Sync choices to Stremio**. This option is off by default;
enabling it queues existing saved choices and sends future explicit choices:

- **More like this** sends a Stremio **Liked** rating, preserving an existing **Loved** rating.
- **Watched and liked / Finished and liked the series** combines watched history and the Liked rating
  in one choice. The title is hidden locally with the same positive preference weight as More like this,
  counted once even if completion was already imported. Its two account writes retry independently;
  one succeeding does not discard the other. Existing saved choices are not converted automatically.
- **Already watched / Finished the series** updates watched history. Series updates mark dated,
  aired regular episodes; saved season decisions restrict backfilled completion. Future episodes,
  specials and unknown release dates are not invented as watched. Resume positions and watch dates
  are preserved. New history-only titles do not become saved library favorites.
- **Not interested** stays local. There is no matching dislike field in this integration.

The private outbox survives restarts and retries while Home is active, in batches of at most
20 titles. It is bound to the connected Stremio session and Watchly URL; changing either requires
enabling sync again. It never exports the Stremio session key. Each history write is checked against
a fresh record and read back. Existing ratings already marked Liked or Loved are left alone.
After successful writes the launcher requests a Watchly refresh. That request is not proof that
Watchly has finished rebuilding; provider caches, including its likes cache, can delay changes.
Sync runs quietly without a success or waiting popup after each choice. **Home content** shows a
discreet status distinguishing choices still waiting to send from choices already sent to Stremio whose
Watchly refresh is pending. A connection-needed status appears only when no usable local Stremio session
is available; local hide/clear actions and an empty queue do not require that session. Choices are
saved to the consented account's outbox before session loading, so a temporary session failure can retry.

Stopping sync cancels pending changes and keeps local choices. Clearing/hiding a title cancels
unsent changes; resetting all choices cancels the pending queue. An account request already in
flight can finish. None of these actions undo watched history or ratings already sent to Stremio.

## Replacing an existing connection

Back up private configuration, feedback, APK, session and layout before changing providers.
Changing the private catalog URL changes the account fingerprint. An explicitly authorized migration
for the same person must rebind the feedback file to the new fingerprint while preserving its entries;
ordinary provisioning intentionally does not merge feedback between accounts. Preserve existing watched
baselines and completion profiles when migrating. Old private catalog caches must not be reused as
Watchly results.

Remove **Trakt Integration** from Stremio's installed add-ons and disconnect **Trakt Scrobbling** in
Stremio settings if replacing that service completely. This does not delete the external Trakt account.
The launcher does not write to Trakt or report playback. The optional sync writes only explicit
positive and watched choices to Stremio.

## Privacy and limits

Watchly ElfHosted reads the history source authorized during setup. Its public service stores account
settings and credentials server-side; the manifest URL is a private bearer link. Treat it and TMDB keys
as credentials. The launcher stores its private configuration and account-bound cache in app-private
storage. Backups and setup files must remain outside commits.

Configuration in Stremio does not itself add Watchly support to older launcher APKs. The launcher must
include `WatchlyCatalog`, the Watchly repository adapter and the corresponding URL validator.

References: [Watchly documentation](https://github.com/TimilsinaBimal/Watchly/blob/1.17.2/README.md),
[Stremio catalog protocol](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md).
