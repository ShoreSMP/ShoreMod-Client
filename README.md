# ShoreMod

Fabric client mod for Minecraft **1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1, 26.2, and 26.3**.

[Download ShoreMod 1.5.2](https://github.com/ShoreSMP/ShoreMod-Client/releases/download/v1.5.2/ShoreMod-1.5.2.jar) | [All releases](https://github.com/ShoreSMP/ShoreMod-Client/releases)

## Install

Install Fabric and the Fabric API matching your Minecraft version. Put the JAR in your mods folder and remove older ShoreMod client JARs.

| Minecraft | Fabric Loader | Java |
|---|---|---|
| Listed 1.21.x versions | 0.19.2+ | 21 |
| 26.1, 26.2, 26.3 | 0.20.1+ | 25 |

Open settings with **Right Shift** or **/shore**. The HUD and settings menu use the 1.4.1 appearance, including charcoal HUD boxes at 85% opacity. You can adjust them in General settings.

## Features

- Change local sky time, weather, brightness and FOV effects. Adjust dropped-item rotation, the mob overlay and individual F3 readouts.
- Start, pause and reset a HUD stopwatch. Use a local PNG for the settings background and tune the Shore menu colors.
- On servers running the updated companion, type `/shoremod giveaway` to host or `/shoremod giveaway <code>` to enter. Use `/shoremod tictactoe` to challenge another ShoreMod player. Money prizes and matched wagers use the server’s in-game economy.

- GPU frame-history motion blur with strength, samples, movement-only mode, FPS protection, capture rate and a GPU memory budget. Off by default.
- Custom end crystal colors, rainbow/ghost styles, opacity, visual size, animation speed, bobbing, base and beam controls.
- World day counter with label, offset and in-game time.
- Integrated shulker tooltips with formatted lore, full-slot expansion (Left Alt + Left Shift), and Left Control locking for item inspection.
- Waypoint quick menu on **M**, `/waypoint add <name> [x y z]`, tracking, offscreen indicators, editing/colors, coordinate copying and optional death markers.
- Darker, single-column settings with configurable panel/HUD/inventory colors, padding and spacing. Updates panel removed.

- Low-overhead preset: disables recording and 3D HUD item icons; repeated nameplate text is cached.
- Configurable block outline, compact F3 panel, inventory opacity, and Minecraft GUI size.
- Colored hitboxes for loaded visible entities, with optional custom range and a configurable entity cap. Vanilla hitboxes can stay enabled without hiding the ShoreMod HUD.
- Lightweight held-totem and pop sprites with independent size and opacity.
- Local waypoints with names, colors, coordinates, range, and separate server/dimension scopes.
- Chat heads with size and outer-layer controls, plus existing chat history, stacking, timestamps, and mentions. Heads appear beside recognized sender names, including multiline server headers, and use Minecraft-managed skins.
- Existing HUD customization, fullbright, zoom, crosshair painter/imports, inventory tools, and optional local clip recorder.

Recording is off by default. Enabling it explicitly exits performance mode. Exact FPS gains depend on hardware, recording settings, and other mods.

## Build

Default: Minecraft 1.21.11, Java 21.

```sh
bash gradlew build
```

Windows: `.\gradlew.bat build`. Client output: `fabric/build/libs/`. Source JARs are not installable mods. The scripts folder and build workflow support the other target versions and universal packaging. The server companion plugin is private and not included.

## License

Original code: [MIT](LICENSE). Dependencies, fonts, and branding: [THIRD_PARTY.md](THIRD_PARTY.md).

Clips save as H.264 MP4 automatically, including the selected 60 FPS timeline. No external encoder installation is required. Export uses a low priority background worker. Clips contain video only.
