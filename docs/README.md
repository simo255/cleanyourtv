# Neovia documentation

[Project overview](../README.md) · [Contributing](../CONTRIBUTING.md)

Neovia combines a personal Android TV home screen with reversible TV debloat tools.

| You want to | Read |
|---|---|
| Build, install and connect your accounts | [Setup and personalization](../PERSONALIZATION.md#build-and-try-the-launcher) |
| Set up Watchly recommendations | [Watchly connection and feedback](watchly.md) |
| Understand catalogs, local taste and completion rules | [Recommendation internals](recommendations.md) |
| Customize the dock, layouts and wallpaper | [Customization](../PERSONALIZATION.md#customize), [themes](../themes/README.md) |
| Add weather or another companion app | [Optional add-ons](../addons/README.md) |
| Debloat a TV safely | [Safety procedure](../PROMPT.md), [debloat and restore reference](../UPSTREAM.md#doing-it-by-hand) |
| Back up, disable packages or restore a TV | [Tool reference](../UPSTREAM.md#doing-it-by-hand), [restore](../UPSTREAM.md#restoring-your-tv) |
| Find tested cleanup lists | [Device index](../examples/README.md) |
| Troubleshoot TV controls or tools | [Troubleshooting](TROUBLESHOOTING.md), [remote support](REMOTE.md) |

## Names and compatibility

**Neovia** is the project and application name. Existing package names, provider authorities and
configuration paths are retained so the rename supports updates to existing installations.
The cinema theme's historical name is Nocturne; its configuration remains in
[`themes/nocturne/`](../themes/nocturne/config.json). Stremio and Watchly are independent services.

## Project origins

Neovia builds on [CleanYourTV by Gathin23 and contributors](https://github.com/Gathin23/cleanyourtv).
Its cleanup tools, themes, module cards and community device lists remain available and supported here.
The inherited [reference documentation](../UPSTREAM.md) describes upstream's offline launcher;
Neovia intentionally supports optional online catalogs. Original MIT notices are preserved.
