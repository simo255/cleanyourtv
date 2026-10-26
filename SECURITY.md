# Neovia security

Neovia stores optional account connections and viewing information, and includes ADB debloat tools
that can change a TV's setup. Report vulnerabilities privately. Do not put tokens, private catalog
URLs, device identifiers or personal data in a public issue.

## How to report

Use [this repository's Security tab](https://github.com/simo255/neovia/security) to submit a private
vulnerability report if reporting is enabled. If it is unavailable, contact the maintainer privately
through a channel they provide before sharing sensitive details.

Include the affected component and version, reproduction steps, expected behavior and possible impact.
Use synthetic accounts and redacted logs where possible. Upstream-only issues can also be reported
through [CleanYourTV's security channel](https://github.com/Gathin23/cleanyourtv/security).

## What counts

- Account keys, private catalog links, viewing information or local feedback exposed to other apps,
  logs, screenshots or network destinations that should not receive them.
- Catalog data or deep links causing unintended app launches, file access or code execution.
- ADB tools acting on the wrong device, mishandling paths or defeating device safety checks.
- A cleanup list or procedure that prevents recovery with the documented restore tools.
- Repository changes that execute unexpected code on a contributor's machine.

Opt-in requests to Stremio, Watchly and configured catalog providers are intentional features.
The launcher adds no telemetry. See [the account privacy notes](README.md#your-accounts-stay-yours).

Keep reports private while a fix is prepared. Never attach real session files, keys or backups to
a public issue or PR. Close USB/network debugging and revoke ADB authorizations after device work.
