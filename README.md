# ShoreMod

Fabric client mod for Minecraft **1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1, 26.2, and 26.3**.

[Download ShoreMod 1.2.3](https://github.com/ShoreSMP/ShoreMod-Client/releases/download/v1.2.3/ShoreMod-1.2.3.jar) · [All releases](https://github.com/ShoreSMP/ShoreMod-Client/releases)

## Install

Install Fabric and the Fabric API matching your Minecraft version. Put the downloaded JAR in your mods folder and remove older ShoreMod JARs.

| Minecraft | Fabric Loader | Java |
|---|---|---|
| Listed 1.21.x versions | 0.19.2+ | 21 |
| 26.1, 26.2, 26.3 | 0.20.1+ | 25 |

Press **Right Shift** or use **/shore** to open settings.

## Build

The default source targets Minecraft 1.21.11 with Java 21.

```sh
bash gradlew build
```

On Windows: `.\gradlew.bat build`. Client output is in `fabric/build/libs/`. Source JARs are not installable mods.

The scripts folder contains the tools for building and checking the other supported versions and packaging the universal JAR. The companion server plugin is private and is not included.

## License

Original code: [MIT](LICENSE). Third-party licenses and branding: [THIRD_PARTY.md](THIRD_PARTY.md).
