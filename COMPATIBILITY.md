# ShoreMod 1.2.0 compatibility

Install **ShoreMod-1.2.1.jar** on any supported version below. Remove older ShoreMod clients first; do not install the universal container together with a version-specific ShoreMod JAR. Fabric Loader 0.19.2+ for 1.21.x, or 0.20.1+ for 26.x and the Fabric API for your game version are required.

| Minecraft | Java | Fabric API used for verification |
| --- | --- | --- |
| 1.21.11 | 21 | 0.141.6+1.21.11 |
| 26.3 | 25 | 0.166.2+26.3 |
| 26.2 | 25 | 0.161.0+26.2 |
| 26.1 | 25 | 0.145.1+26.1 |
| 1.21.10 | 21 | 0.138.4+1.21.10 |
| 1.21.8 | 21 | 0.136.1+1.21.8 |
| 1.21.5 | 21 | 0.128.2+1.21.5 |

## How one JAR works

The container embeds seven exact-version clients under META-INF/jars. They have a shared mod ID and distinct implementation versions. Fabric Loader chooses exactly one whose Minecraft, Java and Fabric API dependencies are satisfied. The root container requires that client, so it cannot silently start with no implementation. It declares only the seven listed Minecraft versions.

This is one distributable with multiple implementations, not a claim that Minecraft rendering/input APIs are compatible across releases. Unlisted and future releases require porting and verification before they are added.

## Rebuilding

The base source targets 1.21.11. Run `bash gradlew build` with Java 21. The compatibility workflow migrates sources to official mappings for 26.x, applies version-specific API adjustments, compiles each target, audits mixins, starts each client, renders settings pages, and shuts down cleanly. It then packages the seven resulting clients with `scripts/package_universal.py` and checks selection using Fabric Loader's real resolver with `scripts/check_universal.py`.

The public client source builds independently of the private companion plugin. Private checkouts can also build the companion against its existing server API. The universal client does not contain that plugin.

Support screens, support commands, web-engine code, native web runtimes and their dependency/license files are absent. Local gameplay clip recording and Show clip remain.

The automated checks cover startup, settings, version selection, packaging and mixin targets. Live multiplayer, Folia behavior, shader packs and third-party mod combinations require gameplay testing.
