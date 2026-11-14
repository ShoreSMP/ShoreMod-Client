# ShoreMod 1.2.3

[Release changes and controls](RELEASE_1_2.md) Â· [Security review](SECURITY_REVIEW.md)

Fabric client mod source for ShoreSMP. The companion Paper/Folia plugin remains private and is not included in the public source export.

Supported clients: **1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1, 26.2 and 26.3**. Install **ShoreMod-1.2.3.jar** on any of these seven versions, with the matching Fabric API and Fabric Loader 0.19.2+ for 1.21.x, or 0.20.1+ for 26.x. Java 21 is required for 1.21.x; Java 25 is required for 26.x. Remove older ShoreMod client JARs first. See [COMPATIBILITY.md](COMPATIBILITY.md) for version-specific Java requirements and build commands.

## Download

The public release package includes **ShoreMod-1.2.3.jar** and its SHA-256 checksum. Install the JAR in your Fabric mods folder; do not install the source ZIP. The publishing helper uploads these files to the new repository's Releases page.

## License

Original ShoreMod code is available under the MIT license. Third-party dependencies, fonts, and branding retain their own licenses; see [THIRD_PARTY.md](THIRD_PARTY.md).

## Features

- Native-resolution module menu, search, favorites, per-module keys and movable HUD elements.
- Typed numeric settings within documented bounds, custom RGB colors and adjustable fullbright. See [SETTINGS_1_1.md](SETTINGS_1_1.md).
- Zoom, custom crosshairs, target information, keystrokes/CPS, item counters, potion effects and durability alerts.
- Configurable chat history, stacking, mentions and timestamps.
- Shulker previews and optional inventory mouse tweaks.
- Local gameplay clips, privacy controls, death locations and bounded reconnect attempts.
- Payment previews with confirmation, a paced command queue and cancellation.
- Optional server-provided mod-user badges, update notices and targeted clip requests.


## Build

The default source targets Minecraft 1.21.11 and Java 21:

```sh
bash gradlew build
```

On Windows use `.\gradlew.bat build`. Client output is in `fabric/build/libs/`. Private checkouts that also contain the companion plugin produce its output in `plugin/build/libs/`. Source JARs are not installable binaries.


## Optional companion integration

The private companion plugin supplies backend-local mod-user badges, update notices and targeted clip requests. Its source and server-specific data are excluded from this public package. Client settings, HUD and inventory features can be built without the plugin source. Source-only checkouts automatically skip the missing plugin Gradle project; the compatibility workflows likewise omit plugin uploads.

The client opens a server-announced HTTPS update link only when the player chooses it; it does not install updates automatically. Mod presence sent for badges is visible to modded clients and is not confidential staff data.

## Controls and local data

| Control | Action |
|---|---|
| Right Shift or `/shore` | Settings |
| C | Hold zoom |
| F8 | Recording privacy toggle |
| F9 | Save gameplay buffer |
| Shift + right-click | Bypass shulker preview |

Keys can be rebound. Config is stored in `.minecraft/config/shoremod.json`; recordings and reason metadata are in `.minecraft/shoremod-clips/`. Optional FFmpeg conversion uses the player's configured executable. No local player files belong in the source release.

## Validation

The compatibility workflow checks shared core logic, client startup, settings rendering and mixin targets on all seven supported clients. These include typed values, rejected invalid drafts, custom colors, fullbright restoration and preservation of vanilla brightness during options saves.


## Public source export

Read [PUBLIC_SOURCE.md](PUBLIC_SOURCE.md). From a clean Git checkout:

```sh
python3 scripts/audit_public_source.py
python3 scripts/test_public_source.py
python3 scripts/package_source.py
```

The resulting `build/distributions/ShoreMod-Public-Source.zip` contains reviewed tracked source and assets, without Git history or local runtime data. The source-export workflow produces the same archive and a SHA-256 checksum. Use that archive to start a fresh public repository; changing the visibility of the original development repository would expose its old branches, history and other repository content.


## Universal packaging

One Fabric container embeds seven exact-version implementations. Fabric Loader resolves only the implementation matching the running Minecraft and Java versions; the other implementations are not loaded. No broad compatibility claim is made for unlisted or future releases.

The compatibility workflow compiles and runs the settings/mixin smoke checks for each implementation, packages the universal JAR, then checks the exact artifact with Fabric Loader's real dependency resolver for all seven targets. Version-specific JARs remain available for troubleshooting. Install either the universal JAR or one matching version-specific client, never both.

All support-page screens, commands, post-clip links and embedded web-engine dependencies have been removed. Clips remain local and the saved-clip screen only offers Close and Show clip.
