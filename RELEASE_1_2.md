# ShoreMod 1.2.1

Compatibility patch: remove the unnecessary Loader 0.20.1 floor on 1.21.x. Loader 0.19.2 is tested directly; the seven implementations retain identical application code.

One universal Fabric client supports 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1, 26.2 and 26.3. It selects an exact-version implementation through Fabric Loader; unlisted and future versions are not claimed. Use Loader 0.20.1+, matching Fabric API, Java 21 for 1.21.x or Java 25 for 26.x. Remove older ShoreMod client JARs before installing the universal JAR.

- All ticket screens, commands, links, embedded web engines, dependency downloaders and their related source/licenses are removed.
- Crosshair settings include a local 32x32 pixel painter with colors, erasing, continuous strokes, undo/redo, preview, Cancel and Save and use. Painted colors and transparency are preserved.
- Image import supports PNG/JPEG and dropping a file onto the crosshair settings page. Large images are subsampled safely, normalized to transparent-capable PNG, loaded before activation, and enabled at a visible size with tint off. Import work runs off the render thread; errors leave the old image active.
- Money commands accept k/m/b/t, case-insensitively. `/payall 5b` means 5 billion per recipient. Previews and totals use compact exact amounts; the server still receives ordinary decimal pay commands. Values are positive whole cents, bounded to 1000t per input; no rounding can silently change a payment.
- GUI size accepts 50-150%; font size independently accepts 75-125%. The new default GUI scale is 85%, accent is #50A7FF, and HUD boxes use dark charcoal at 85% opacity. Previous default values migrate once; other customized values remain.
- Recording is off on new installs, with 640px/8 FPS defaults when enabled. Existing recording choices remain. Protect FPS caps readbacks adaptively, pauses on very slow rendering and while menus are open, and keeps one capture in flight. Encoder/image buffers are reused. Settings allow 5-60 FPS; choosing above 30 explicitly disables protection. Actual captured FPS depends on hardware and skipped frames preserve duration. No hardware FPS benchmark is claimed.
- UI text caching is bounded, crosshair files are checked at most once a second, HUD strings and item scans refresh at 10 Hz, and unchanged server rosters are reused with periodic heartbeats.
- Creative delete/special slots keep vanilla behavior. Mouse-tweak actions go through the screen's own slot hook; official Mouse Tweaks takes priority if installed.
- The companion plugin proactively bootstraps presence when channel announcements are delayed. `/shore status` reports presence and badge toggles and requests a refresh; the Logos module has a Refresh action. The companion must be installed on the player's actual backend. Nametag/scoreboard mods can still replace rendering hooks and need live testing.
- The private companion receives stricter inbound validation and current-policy developer authorization. See SECURITY_REVIEW.md for fixes and limits. The public source export still excludes that plugin.

Install the updated companion JAR on the server and restart to receive the presence and authorization fixes; a client update alone does not change server code. A source review cannot establish that the live server, proxy, firewall or other plugins are secure.
