# Home catalogs: Trakt, Cinemeta and Android TV apps

The optional shelf consumes the user's **existing official Stremio Trakt Integration add-on**.
It does not reuse a Trakt OAuth token, register an API application, or write to either account.
The home shelf has three movie/show catalog views and a fourth local app-content view:

- **For you**: the official add-on provides personalized recommendations; known watched titles are excluded.
  An optional [Google TV snapshot](google-tv-personalization.md) can be interleaved with these titles,
  with source badges, deduplication and watched exclusions for both. Google is explicitly dated;
  it is refreshed by a separate capture on a computer or in Termux, not the Trakt Refresh button.
- **Watchlist / À regarder**: the official add-on provides your Trakt watchlist. Membership and order are
  retained, including started or previously watched titles still on the list. A valid empty list is
  displayed as empty, with guidance to add titles in Trakt; recommendations are never substituted.
- **Trending / Tendances**: the official Cinemeta **Popular** catalogs, at
  `https://v3-cinemeta.strem.io/catalog/{movie|series}/top.json`. This is Cinemeta popularity,
  not Trakt trending or a live audience count. The provider is named on screen. No private catalog URL,
  token, history or watchlist is sent to Cinemeta.
- **My apps / Mes applis**: app-published channels and resume entries read from Android TV's
  shared `TvProvider`. The Channels chooser and Continue control replace Movies/TV shows in this view.

Up to 40 items are displayed per view. The three catalog views keep provider order and open Stremio
details without starting playback. The app view balances channels and uses each app's published
intent, which may start or resume playback. This launcher does not edit the watchlist or mark titles watched.

## Android TV app channels

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

`--clear` removes the connection, accumulated watched exclusions, all three catalogs and poster cache from
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
mirror. A show is excluded as soon as its series ID is known as watched, even if only one episode
was watched. Personalized ranking comes from the official integration, not a local recommender.

## Refresh and cache

Only the selected view is refreshed. For-you and Watchlist each read movie/series history to check
the private connection, then their two corresponding catalogs. Popular reads two public Cinemeta
catalogs on a separate worker so a slow private connection does not block it. Responses must be valid
JSON catalogs, including legitimately empty arrays. Duplicate and malformed IDs are removed in all
views; watched exclusions apply only to For-you. No write endpoint or playback reporting is used.

Refresh is checked on resume and each visible minute; fresh data is reused for 15 minutes.
Failed automatic attempts are spaced at least a minute apart. The manual Refresh control can
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
- Up from a poster reaches the category controls; Left/Right traverses all seven header buttons.
- The header switches For-you / Watchlist / Trending / My apps, then two view controls and Refresh.
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
