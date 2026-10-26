# Contributing to Neovia

Neovia is a native Android TV launcher with Stremio resume, Watchly recommendations and a cinema
interface, together with reversible TV debloat tools. It builds on
[CleanYourTV](https://github.com/Gathin23/cleanyourtv), retaining the original authorship and MIT license.

Report Neovia bugs and propose changes in [this repository](https://github.com/simo255/neovia/issues).
For security reports, use [SECURITY.md](SECURITY.md). Coding agents should read [AGENTS.md](AGENTS.md).

## What belongs here

- Launcher fixes and improvements: Home, navigation, Stremio resume, Watchly catalogs, local taste,
  explicit feedback, accessibility and reliability.
- Themes, translations, documentation and optional companion add-ons.
- Debloat tools, backup and restore support, and tested device lists, respecting all device safety rules.

Optional network catalogs and the cinema interface are part of Neovia. Keep account connections
opt-in, request only the permissions a feature needs, and add no telemetry. Discuss new runtime
dependencies and substantial permission changes before implementation. No root, firmware flashing,
uninstalling system apps, or bypassing store protections.

## Find the right place

| Change | Start here |
|---|---|
| Launcher behavior or presentation | [Feature guide](PERSONALIZATION.md), [recommendation internals](docs/recommendations.md), `launcher/` |
| Watchly or Stremio setup | [Watchly guide](docs/watchly.md), [personalization guide](PERSONALIZATION.md) |
| Theme | [Themes](themes/README.md), [theme workflow](docs/workflows/theme.md) |
| Translation | [Translation workflow](docs/workflows/translation.md) |
| Companion app | [Add-ons](addons/README.md), [add-on workflow](docs/workflows/addon.md) |
| Debloat, backup, restore or TV list | [PROMPT.md](PROMPT.md), [TV-list workflow](docs/workflows/tv-list.md), [tools and device reference](UPSTREAM.md) |

The app is named **Neovia**. The Android package `com.gathin.tvhome`, theme provider authorities,
existing file formats and `themes/nocturne/` folder remain stable for existing installations.
Changing them requires an explicit migration plan. Updates need the same signing key to retain app data.

## Validate the change

For launcher changes, use JDK 17, Android SDK platform 34 and build-tools 34.0.0. From `launcher/`:

```sh
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

On Windows, use `gradlew.bat`. Lint must have zero errors. Record the release APK size; Neovia has
no hard APK size limit. For tooling, reference tables and repository documentation, run from the root:

```sh
python -m unittest discover -s tests -v
```

Install the Python dependencies from `tools/requirements.txt` in a virtual environment when needed.
Checks for inherited device and tool tables read [UPSTREAM.md](UPSTREAM.md). Existing upstream README
translations remain reference documentation.

Verify launcher interactions on a real TV and state the model, Android version and firmware tested.
Check D-pad navigation from all four edges, return from apps, and the relevant account state. A build
or unit-test pass alone does not establish device or provider behavior. For UI changes, include a
sanitized TV screenshot. For performance claims, follow [the measurement workflow](docs/workflows/perf-ab.md).

## Debloat safety and private state

Debloat tools remain a supported part of Neovia. Follow [PROMPT.md](PROMPT.md): back up first,
disable rather than uninstall, preserve the Home guard, leave essential packages enabled, and close
ADB afterwards. Every applied cleanup must be reversible with the restore tools. Changes to device
safety rules need a separate maintainer discussion.

Never commit accounts, tokens, private catalog URLs, signing keys, ADB keys, device identifiers,
personal endpoints, backups, runtime exports, generated APKs or other build artifacts. Keep working
captures in the ignored `shots/` or `work/` directories. Remove names, cities, Wi-Fi names and other
private details before sharing screenshots.

## Pull requests

Keep one topic per PR. Use a title such as `Launcher: restore poster focus after returning from Stremio`
or `tools: clarify restore output`. Explain the resulting behavior, relevant checks and device validation,
and fill in [the PR template](.github/pull_request_template.md). Keep copyright and license notices.
Contributions are released under the project's MIT license.
