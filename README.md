# ShoreMod

Fabric client mod for Minecraft **1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1, 26.2, and 26.3**.

[Download ShoreMod 1.3.3](https://github.com/ShoreSMP/ShoreMod-Client/releases/download/v1.3.3/ShoreMod-1.3.3.jar) | [All releases](https://github.com/ShoreSMP/ShoreMod-Client/releases)

## Install

Install Fabric and the Fabric API matching your Minecraft version. Put the JAR in your mods folder and remove older ShoreMod client JARs.

| Minecraft | Fabric Loader | Java |
|---|---|---|
| Listed 1.21.x versions | 0.19.2+ | 21 |
| 26.1, 26.2, 26.3 | 0.20.1+ | 25 |

Open settings with **Right Shift** or **/shore**. New visual modules are optional.

## Features

- Low-overhead preset: disables recording and 3D HUD item icons; repeated nameplate text is cached.
- Configurable block outline, compact F3 panel, inventory opacity, and Minecraft GUI size.
- Colored hitboxes for nearby visible entities, capped at 32.
- Lightweight held-totem and pop sprites with independent size and opacity.
- Local waypoints with names, colors, coordinates, range, and separate server/dimension scopes.
- Chat heads with size and outer-layer controls, plus existing chat history, stacking, timestamps, and mentions. Heads appear on new messages with recognized player-name prefixes and use Minecraft-managed skins.
- Existing HUD customization, fullbright, zoom, crosshair painter/imports, inventory tools, and optional local clip recorder.

Recording is disabled on the first upgrade to 1.3.3. Enabling it explicitly exits performance mode. Exact FPS gains depend on hardware, recording settings, and other mods.

## Build

Default: Minecraft 1.21.11, Java 21.

```sh
bash gradlew build
```

Windows: `.\gradlew.bat build`. Client output: `fabric/build/libs/`. Source JARs are not installable mods. The scripts folder and build workflow support the other target versions and universal packaging. The server companion plugin is private and not included.

## License

Original code: [MIT](LICENSE). Dependencies, fonts, and branding: [THIRD_PARTY.md](THIRD_PARTY.md).

Clips save as H.264 MP4 automatically, including the selected 60 FPS timeline. No external encoder installation is required. Export uses a low priority background worker. Clips contain video only.
