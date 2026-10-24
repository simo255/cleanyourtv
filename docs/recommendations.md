# Home catalogs: Trakt, Cinemeta and Android TV apps

The optional shelf consumes the user's **existing official Stremio Trakt Integration add-on**.
It uses only the existing official add-on connection for Trakt recommendations: no GitHub link,
separate Trakt application, OAuth token import or second account connection is required.
It does not reuse a Trakt OAuth token, register an API application, or write to either account.
The home header exposes four sources: **Continue / Reprendre**, **Trakt / For you**,
**New releases** and **Trending**. Google TV, Watchlist and My apps are removed from both menus. Previously saved selections
of a removed section return to Trakt; existing source IDs and private saved data are preserved.
The legacy Watchlist/app-content notes below describe retained formats, rather than visible controls.

## Nocturne cinema home (2.31)

The opt-in `refined_home` theme uses a typography-only title preview above a strip of complete posters. The preview
follows the selected card, and Up from the app dock restores the last card rather than selecting a
different title based on the app's column. Blue underlines distinguish the selected source from the
white remote focus. Source and category tabs switch immediately on remote focus or pointer hover,
without pressing OK. Focusing a source never opens a connection or permission dialog.
Quick menus open in a side panel; refresh, visibility and account actions live
under **Menu → Home catalogs** instead of duplicating the source tabs.

The background stays solid: no enlarged poster or panoramic image appears behind the selected title.
The existing catalog synopsis appears beneath the title on up to two lines. If the catalog has no
description, that area stays empty; no additional metadata request or synopsis window is added.
Hold OK retains the original classification menu for catalog films/series: More like this, Not interested,
Seen/Finished and Clear choice. The Continue view keeps its original read-only progress behavior.
The preview and side menus are inactive when `refined_home` is off.

Catalog ranking is reused until provider data, local feedback or the date changes. Cached poster
decoding has its own queue, separate from network downloads; obsolete queued requests are dropped
when the displayed list changes. The poster row retains enough reusable cards for a full strip.

## Stremio playback resume (optional)

Open **Continue → Connect Stremio**, scan the QR with your phone and approve the connection on
`link.stremio.com`. The text link is also shown if the QR image cannot load. Pairing ends when the
dialog closes or Home is left, and expires after three minutes or 50 polls. New code starts a new attempt.
The Trakt catalog connection is independent and does not need reconnecting.

The launcher reads the official `api.strem.io/api/datastoreGet` library collection. It does not write
history, mark episodes watched, or change any account. Authentication uses Stremio's official link
protocol; no password is entered or stored by this launcher. The bearer key is encrypted with
AES-GCM using a non-exportable Android Keystore key. The encrypted session and private resume
cache live in `no_backup`, separate from recommendation configuration. Disconnect removes both.
In 2.34, transient Keystore errors retain the session and caches for a later retry. Only an unreadable
or expired session (codes 1 / 401) clears the resume data and asks for reconnection. Network failures retain the
dated last successful cache; an empty successful library replaces it rather than showing stale titles.

**In progress** keeps films/series from any release year, with the episode, remaining minutes and
progress when supplied by Stremio. OK opens that exact title/episode in the Stremio app, which owns
playback and restoring the position. The Stremio 1 ms next-episode marker retains its exact episode;
a 90%-watched episode is never silently skipped. Recently caught-up series can show a newly released
next episode when Cinemeta supplies its date. The default resume view does not infer taste preferences.
The separately enabled season-aware ranking below can reuse these already fetched episode records locally.

**Coming next** lists future, dated episodes of at most eight recently watched/followed IMDb series
checked per refresh, sorted by date. It is a bounded preview, not a complete account-wide calendar.
Unknown dates and specials are omitted. Future episodes cannot be launched from this list.
Missing Cinemeta metadata never discards an in-progress item. Metadata is cached in memory for six hours;
In 2.34, account reads run only while Home is visible: returning Home or selecting a tab reads at most
once a minute; staying on Home reads at most every 15 minutes. Failures back off from 2 to 30 minutes,
with at least 10 minutes after 429 or 503. Refresh requests an immediate read. There are no new services, permissions or dependencies.

References: [Stremio's link model](https://github.com/Stremio/stremio-core/blob/development/src/models/link.rs),
[library state rules](https://github.com/Stremio/stremio-core/blob/development/src/types/library/library_item.rs).
The [quicodomingo fork](https://github.com/quicodomingo/tv-vibehack) supplied the feature reference.

## Recommendation catalogs

- **Trakt**: the official add-on supplies the candidates. Known watched movies and positively confirmed
  completed series are excluded. Local positive viewing and explicit choices can refine this view's order.
  An unfinished series is neutral; merely appearing in episode history never means it was disliked or finished.
- **New releases / Nouveautés**: official Cinemeta `year` catalogs for the current and previous year. Each refresh
  reads at most four bounded public responses (movies/shows × two years). Entries are deduplicated,
  require a parseable release date no later than today, and initially sort newest first. A configured
  local taste profile can refine that order. Optional TMDB candidates broaden this public pool;
  this is not a guarantee of streaming availability in your country.
  A series year is its premiere; a new season of an older series does not make it a recent premiere.

One control row sits above a full-width poster row. There is no side summary or inline year selector.
Previously saved `period_*` choices are ignored. Instead, all recommendation feeds (Trakt,
Popular and New releases) automatically retain the current and previous release year: 2025/2026 in
2026, 2026/2027 in 2027, and so on. This default policy applies to movies and series before display;
raw candidates remain cached so an enabled period switch can work offline. Google TV was removed in 2.20;
its old source ID stays reserved, but its snapshot is no longer read. Unknown release
years and known future release dates do not pass. Trakt keeps its personalized order and watched
exclusions; public titles are not inserted into that candidate list. A series year is its premiere, so an
older series with a new season does not pass solely because its displayed year range ends recently.

Watchlist and app channels/Continue are saved or resume lists, and keep their full membership.
No new private API query parameters are assumed: the recommendation policy filters the catalogs
returned by the existing official integration. It cannot invent recent personalized candidates if
that provider returns none; the empty state reports that outcome.

The New releases cache is separate (`recent-cache.json`), public and account-independent. It uses
the existing 4-hour refresh policy and Cinemeta redirect allowlist. It does not read Trakt history.

## Positive viewing and explicit feedback

An optional private `taste_profile` contains confirmed completed series and their genres. A completed
series contributes positive genre affinity; unfinished or unknown viewing contributes no negative
weight. Trakt and New releases can use these weights, with stable provider order on tied scores and
a small diversity pass to avoid repeated runs of the same leading genre. With no learned genres,
their default ordering remains. Popular keeps its own order.

Hold OK on a poster for **More like this**, **Not interested**, **Finished the series** (or Already
watched for a movie), and **Undo my choice**. An explicit rejection hides that title, without treating
other incomplete viewing or its whole genre as disliked. Finished the series adds a positive signal
and excludes that title from discovery. More like this needs genre information; the app explains when
it is absent instead of pretending to learn. These actions are local and never mark anything in Trakt.
Manual hidden/seen choices apply to recommendation views, including Google, but do not alter Watchlist
or app-channel/Continue membership. Imported Trakt completion does not filter Google's membership.

**Home catalogs → Reset my recommendation choices** clears manual choices, preserving imported
completed-viewing positives. Feedback is stored atomically in the account-bound private
`recommendation-feedback.json`. Ranked/hidden results are derived from raw cached candidates, so a
reset works offline; it does not require a fresh catalog download.

To prepare the completion baseline without accessing the TV or an account API:

```sh
python tools/build-taste-profile.py --library /private/stremio-library.json --metadata-dir /private/cinemeta --as-of 2026-10-01 --output /private/taste-profile.json
```

Use the date of that viewing snapshot. The tool decodes Stremio's watched bitmap against the full
cached episode ordering. Every dated, already-aired regular episode must be watched; specials and
future episodes are not required. Missing metadata/dates, malformed bitmaps or missing anchors stay
neutral. The known broken export that repeats one title is rejected. The output copies no account
fields or titles. Its accompanying report records counts and the snapshot date. This is an imported
baseline, not continuous synchronization of full Trakt episode progress: refresh it with newer data
or use the explicit Finished action for new completions.

Then, only when an installation/configuration update is authorized:

```sh
python tools/configure-recommendations.py --serial YOUR_TV_IP:5555 --config /private/recommendations-config.json --taste-profile /private/taste-profile.json
```

## Optional season-aware discovery (2.32)

Set `recommendation_options.enhanced` to `true` in the private configuration, or use
`--enhanced-recommendations` with `tools/configure-recommendations.py` during an authorized configuration
update. It is off by default. No AI model, new permission, dependency or account write is involved.

- Films and series learn separate positive profiles. Available genres, cast and directors contribute;
  repeated completion imports/manual choices count once per title. Unfinished/unwatched records do not
  infer dislikes. Ranking combines normalized content overlap (65%) with provider position (35%), then
  applies a small diversity pass. These weights are heuristics, not a measured quality guarantee.
- **Discover** replaces the New releases label. Per type, a refresh tries the current/previous year,
  page two of the current year (`skip=100`), one Popular genre and Featured (`imdbRating`): at most ten
  public catalog requests in total. The genre rotates by calendar day, independently of private tastes.
  A 60-second start budget per type bounds slow providers (an in-flight request may finish afterwards).
  Successful pages are interleaved and deduplicated to at most 300 candidates per type. Failed pages
  are skipped; if every page fails, the previous feed remains available. The four-hour cache still applies.
- The catalog menu offers **Show all years / Show recent years**, initially recent. This changes discovery
  filtering only; Watchlist and Continue retain their membership. Future dated releases remain excluded.
  Older raw caches that already lost titles need one refresh. Trakt candidates still come exclusively
  from the official Trakt add-on; public Cinemeta results are never inserted into that feed.
- Watched movies and completed series are excluded across Trakt, Discover and Trending. Trending retains
  provider order; Google/app channels and saved lists are not personalized by this option.
- No extra metadata request sends a private history ID to Cinemeta. Ranking uses metadata already present
  in the imported profile or catalog responses. Missing cast/director fields simply contribute no signal.
  Enhanced discovery uses Cinemeta only; the older optional TMDB path is not invoked in this mode.

The optional schema-1 profile accepts `viewing` series records with `id`, `type`, optional `genres`, `cast`,
`directors`, and `seasons`. Each season has integer `season`, `total`, `watched`, `aired`, `future`, `undated`
counts and an optional `status`: `watched`, `unfinished` or `unwatched`. Counts must agree. Explicit review
decisions take precedence over inferred counts. At least half the known season episodes marks that season
viewed and implies earlier known seasons unless explicitly vetoed or containing future episodes. A gap
of one or two aired episodes is tolerated across the whole title, not separately in every season.
An unseen later aired season keeps the series eligible while earlier viewed seasons remain positive.
Wholly future seasons are never inferred viewed and do not erase completion of currently aired seasons.
Unknown dates prevent inferred whole-series completion. A fresh **Finished** choice overrides the reviewed
states of currently known aired seasons; a subsequently discovered season remains eligible.

Starting with **2.33**, when the enhanced option and Stremio connection are both enabled, returning to
Home or visiting a recommendation tab also refreshes watched evidence. This is independent of the
four-hour catalog cache: returning Home or selecting a tab reads at most once a minute; the visible Home timer reads
at most every 15 minutes. Failures back off 2�30 minutes (at least 10 on 429/503); Refresh is immediate.
No account read starts while Home is in the background. The selected recommendation list is
re-filtered as soon as the read finishes, even when its catalog remains cached.

Films use Stremio's `timesWatched` mark (or `flaggedWatched` if the count is absent), including watched films outside Continue.
Playback position alone does not mark a movie complete. Every valid returned movie is considered,
with a local cap of 5,000 movie records. Series use the existing budget of eight episode-metadata
lookups, with up to four slots prioritizing changed viewing and the remainder available for Continue.
Only mapped watched bits and the exact episode playback pointer at at least 75% count; the 1 ms
next-episode marker never does. A large backlog is processed across visits, not as an unbounded
account-wide scan. If metadata is unavailable, a prior valid series summary is retained.

Successful summaries persist in the account-bound, app-private `no_backup/stremio-viewing-cache.json`
(at most 1,000 series and 2 MB). They survive app restarts/offline use and are deleted on disconnect or
account replacement. Removing a library item does not erase its locally known viewing history;
an explicit movie unwatch can remove automatic evidence, but imported Trakt baselines and local
manual choices remain independent. No library/account tokens are copied into the viewing summaries.

An increase in watched-episode counts can supersede an older imported `unfinished`/`unwatched` season
snapshot. If the optional `reviewed_at` Unix-millisecond timestamp is provided on a series record,
the account update must also be newer than that review. Unchanged or lower counts do not override it, and positive manual confirmations
remain intact. The original baseline is never overwritten. A series with a later unseen aired season
remains eligible. Stremio must actually record and synchronize the playback; neither this reader nor
the catalog add-on can reconstruct episodes that never reached the account.

The optional `positives` array holds local metadata for movies; a record contributes only when its ID is
also known watched from the baseline or live Stremio evidence. Available metadata in new Stremio records
can contribute too; missing genres/cast/directors do not prevent the watched-title exclusion.
The provisioning normalizer strips unrelated fields and rejects invalid counts.
Keep these files private. The original `build-taste-profile.py` retains its stricter complete-episode policy;
it does not generate the optional reviewed season records automatically.

## Optional TMDB enrichment

The default works with Cinemeta and the official Trakt integration. TMDB is opt-in and needs the
user's own **API Read Access Token**, read from a private file with `--tmdb-token-file` or a hidden
prompt with `--prompt-tmdb-token` on the configuration tool. It is never embedded in the APK or
printed. The normalizer accepts only this explicit field, not account/export tokens.

TMDB is requested only for the selected New releases view: public Discover movie/TV results for
the current/previous year, sorted by popularity, with at least ten votes. At most ten details per
type are mapped to IMDb for Stremio links, within a bounded fetch window. Public mappings are cached
for a week with a 300-file cap. The resulting candidates join Cinemeta before deduplication/date
checks and local ranking. The view identifies both sources when it contains TMDB entries.

The Bearer token is sent only to fixed HTTPS `api.themoviedb.org` Discover/detail endpoints; redirects
are disabled and responses are bounded. Viewing history, private addon URLs and the local taste
profile are not sent to TMDB. A missing token or failed TMDB request leaves Cinemeta usable. A local
build/test without a token does not validate live TMDB authentication or results. This product uses
the TMDB API but is not endorsed or certified by TMDB.

The other sources keep their own membership:
- **Google TV (removed in 2.20)**: [historical snapshot documentation](google-tv-personalization.md).
  The current launcher does not load or refresh it.
- **Watchlist / À regarder**: the official add-on provides your Trakt watchlist. Membership and order are
  retained, including started or previously watched titles still on the list. A valid empty list is
  displayed as empty, with guidance to add titles in Trakt; recommendations are never substituted.
- **Trending / Tendances**: the official Cinemeta **Popular** catalogs, at
  `https://v3-cinemeta.strem.io/catalog/{movie|series}/top.json`. This is Cinemeta popularity,
  not Trakt trending or a live audience count. The provider is named on screen. No private catalog URL,
  token, history or watchlist is sent to Cinemeta.
- **My apps / Mes applis**: app-published channels and resume entries read from Android TV's
  shared `TvProvider`. The Channels chooser and Continue control replace Movies/TV shows in this view.

Up to 100 items are displayed per view, so recent titles beyond the former 40-item cutoff remain reachable.
The movie/show catalog views use their documented provider/local preference order and open Stremio
details without starting playback. The app view balances channels and uses each app's published
intent, which may start or resume playback. This launcher does not edit the watchlist or mark titles watched.

## Legacy Android TV app channels (not exposed in the current interface)

Select My apps and grant Android's standard `READ_TV_LISTINGS` runtime permission. Denying it keeps
the other catalogs usable and shows an explanation; Refresh can request access again. This does not
grant privileged access to every TV row or to another app's private files. ADB queries without this
permission can return zero rows even when searchable programs exist.

The reader queries channels, preview programs and Watch Next on a worker, without writing to them.
Hidden, disabled or unavailable apps are excluded when loading. A populated channel can be selected
even if it was never approved for the stock launcher (`channel.browsable=0`); individual hidden
programs (`program.browsable=0`) are excluded. All-channel mode interleaves channels so a large
provider cannot occupy every displayed slot. Identical links from the same app are deduplicated.

Continue combines Watch Next with preview entries that report positive, incomplete playback progress.
Watch Next uses its published engagement ordering; preview entries without timestamps retain their
provider ordering. A title known as watched in Trakt can still legitimately be present here. TV rows
are not matched to IMDb by decoding private provider IDs, and are not uploaded to Trakt or Google.

TV data stays in memory and is reread from the provider after restart. Changes invalidate the read
timestamp; refresh runs on selecting/resuming the view, on the visible minute tick, or manually.
Nothing polls as a separate background service. Cached app data can be stale until the owning app
updates it; reading the local database is not evidence that suggestions were refreshed from a server.
The selected channel and Continue choice persist separately from the Trakt Movies/TV shows choice.

Published intents are pinned to the owning app, reject components in other packages and discard
selectors, URI-grant flags and clip data. The app must remain installed and handle its own link.
App posters may be HTTPS images or permission-checked content-provider images, with finite byte
and bitmap-size limits. No private recommendation configuration is passed to these apps.

Google TV Home can stay disabled and our launcher can stay the default Home. These app-published
channels are useful alongside Trakt, but do not reproduce Google's private For-you ranking or
guarantee Netflix/other services will publish content. The private Google TV cache remains unreadable
with an ordinary ADB/app identity on the tested device.

## Private configuration

Build and install `assembleDebug`, install the official Android SDK platform-tools on your
computer, then authorize its ADB connection on the TV. This setup tool uses `run-as`, so a
non-debuggable release APK is not supported by this provisioning method.

First connect Trakt in your Stremio account and enable the official **Trakt Integration** add-on.
For previous viewing activity, import the history into Trakt separately: connecting an account
does not reconstruct missing episode history or watch dates.

The easiest setup is to download a fresh account export from Stremio's account page, keep it
outside the repository, and run this from the repository root:

```sh
adb connect YOUR_TV_IP:5555
python tools/configure-recommendations.py --serial YOUR_TV_IP:5555 --stremio-export /path/to/stremioExport.json
```

The tool extracts **only** the official Trakt add-on's `transportUrl`. It does not transfer
the export's user data, OAuth tokens, library or watch dates. The export itself can contain
credentials: never upload it to GitHub or share it. The known export filenames and private
configuration filenames are ignored by Git as an extra precaution.

Alternatively, omit `--stremio-export` and paste your official add-on manifest URL at the hidden
prompt. Both the full `manifest.json` URL and its base URL are accepted. Use `--adb /path/to/adb`
if the executable is not on PATH. The TV must already be connected and authorized; the tool does
not enable debugging, pair a device, or change its default launcher.

```sh
python tools/configure-recommendations.py --serial YOUR_TV_IP:5555
python tools/configure-recommendations.py --serial YOUR_TV_IP:5555 --clear
```

`--clear` removes the connection, accumulated watched exclusions, catalog caches and poster cache from
this launcher only, including any imported Google TV snapshot. It does not revoke the independent TV-listings permission or erase app-published
TV data. Dock preferences and Stremio/Trakt accounts are preserved. A connection change
is a deliberate setup action; ordinary refreshes do not need ADB or a computer.

### Advanced: seed older watched titles

Provision `files/recommendations-config.json` in the launcher's private app storage. This must
contain the user's own official add-on base URL (the manifest URL without `manifest.json`), plus
optional `watched_movie_ids` and `watched_series_ids` arrays of IMDb IDs. Those arrays provide an
initial watched baseline from an authorized history conversion. The launcher merges the latest
100 history events per category from the official add-on into its saved watched IDs on refresh.
Older known watched IDs are retained; removing a watched entry later in Trakt does not currently
remove that ID from this launcher's local exclusions.

You can supply these arrays in a private local JSON file with
`python tools/configure-recommendations.py --serial YOUR_TV_IP:5555 --config /private/path/recommendations-config.json`.
The script validates the official URL and IMDb IDs, writes atomically into private app storage,
verifies the bytes without printing them, then restarts Home. Starting Settings briefly avoids
Android automatically restarting a foreground Home while its configuration is being replaced.

The supported base is HTTPS on `www.strem.io`, under `/trakt/addon/<private-id>/`. The configuration
is neither embedded in the APK nor accepted through an exported activity. Do not put private
URLs or account data into tracked repository files. Android app backup is disabled. Current
on-device provisioning uses the already authorized ADB connection and the launcher's private
storage. No configuration or credential is printed in logs or displayed on the TV.

Without a seed, only the latest 100 history events per category are fetched initially. Repeated
episode plays may therefore cover fewer than 100 distinct shows. This is not a full Trakt-history
mirror. A series ID alone does not establish completion. Series completion exclusions and positive
genre learning require the imported completion profile or an explicit Finished choice; merely having
started a show never adds negative affinity. Trakt supplies the base candidates, and local positive
preferences refine them without claiming to reproduce Trakt's own recommendation engine.

## Refresh and cache

Only the selected view is refreshed. Trakt and Watchlist each read movie/series history to check
the private connection, then their two corresponding catalogs. Popular reads two public Cinemeta
catalogs on a separate worker so a slow private connection does not block it. Responses must be valid
JSON catalogs, including legitimately empty arrays. Duplicate and malformed IDs are removed in all
views; automatic history-based movie exclusions apply only to For-you. Confirmed completed series
are excluded from local personal discovery, and explicit hidden/seen choices apply as described above.
No write endpoint or playback reporting is used.

Refresh is checked on resume and each visible minute; discovery catalog data is reused for 4 hours.
Continue uses the foreground account cadence described above.
Failed automatic catalog attempts are spaced at least a minute apart; Stremio uses its separate backoff. The manual Refresh control can
retry immediately. Nothing runs as a background service when the launcher is paused.

Each view has its own atomic cache and sync/error/loading state. Private caches are bound to a SHA-256
fingerprint of the configured catalog URL and an explicit source ID. The public cache has its own
provider identity. An existing pre-2.6 recommendations cache migrates as For-you only. A failed refresh
leaves that view's last successful catalog available and labels it as saved data; it cannot replace
another view's data. Posters use an approximately 6 MiB memory cache and a 12 MB disk cache, with two
download workers and sampled bitmap decoding. All network requests have finite time and size
limits and use HTTPS. The VPN badge continues to use Android network state without a remote IP
lookup.

Public Cinemeta catalog requests may follow at most two HTTPS redirects, restricted to
`v3-cinemeta.strem.io` and its official `cinemeta-catalogs.strem.io` service. MetaHub artwork may
likewise follow at most two HTTPS redirects between `images.metahub.space` and `live.metahub.space`.
Private Trakt catalogs and other poster hosts do not follow redirects. The two public allowlists
are separate; neither permits account URLs or arbitrary hosts.

The official integration can also return HTTP 200 with empty catalogs for an invalid private ID.
If a previously nonempty known history suddenly yields no movie or series history at all, the
launcher preserves the prior cache and asks the user to check the Trakt connection. A legitimately
empty recommendations category has its own empty-state message.

## TV controls

- Up from a dock app enters the poster row; Left/Right navigates posters.
- Up from a poster reaches the active source in Nocturne (category controls in other themes); Left/Right traverses the header.
- The header switches Trakt / Continue / New releases / Trending, then the source's category controls.
- Refresh is in Menu → Home catalogs, and also on the header in themes other than Nocturne.
- My apps uses Channels… / Continue; the other sources use Movies / TV shows.
- Down from an empty header returns to the dock, avoiding a focus trap; Up reaches the main menu.
- Down from a poster returns to the dock; Down from the dock opens the app grid.
- OK on a poster opens `stremio:///detail/{movie|series}/{imdb-id}` in `com.stremio.one`.
- Menu → Home catalogs can select a source, category, refresh, or show/hide the shelf.

The last source/category selection survives process restarts. Without private configuration the
For-you shelf stays hidden and makes no private calls. Selecting Trending or My apps explicitly from
the menu can enable public discovery or local app content without linking Trakt.

The existing dock order, app visibility editor, VPN status and device power settings are
independent of the shelf. If the official integration expires or is revoked, reconnect Trakt
inside Stremio and provision the resulting catalog configuration again. Recommendation quality
and upstream processing delays are controlled by Trakt and the official integration.

## References

- [Stremio catalog protocol](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md)
- [Stremio detail links](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/deep-links.md)
- [Official Stremio Trakt integration](https://blog.stremio.com/stremio-tech-update-28-trakt-scrobbling-2-way-sync-more/)
- [Official Cinemeta manifest: Popular movie/series catalogs](https://v3-cinemeta.strem.io/manifest.json)
- [Android TV preview programs](https://developer.android.com/reference/android/media/tv/TvContract.PreviewPrograms)
- [Android TV Watch Next programs](https://developer.android.com/reference/android/media/tv/TvContract.WatchNextPrograms)
- [TvProvider access filtering](https://android.googlesource.com/platform/packages/providers/TvProvider/+/refs/heads/master/src/com/android/providers/tv/TvProvider.java)
