# Source security review

This review covers the companion plugin, its incoming/outgoing payloads, command authorization, identity configuration, and the client features changed in 1.2.0. It does not inspect the live server, proxy, firewall, installed plugins or hosting account, and is not a penetration-test guarantee.

## Fixes

- Server input accepts only the exact two-byte hello message on the presence channel. Clients cannot submit a roster, choose another UUID, submit a clip request or make the server parse variable-sized identity data. Update requests also have an exact two-byte shape. Requests are rate-limited per authenticated connection.
- Developer authorization uses only current configured UUIDs and currently configured pinned names. Stale pinned entries no longer retain access. Unpinned names cannot authorize a player. Name bootstrap is disabled by default and must be explicitly enabled with authenticated online-mode or secured proxy forwarding.
- The plugin no longer issues persistent permission attachments for configured developers. The command checks the current identity policy directly, so config reloads do not leave old attachment grants behind. Explicit permissions granted by the server owner remain authoritative.
- Admin commands still require shoremod.admin; developer access does not imply admin access. No public HTTP endpoint, remote command-execution API or player-upload endpoint is introduced. Clip requests are outbound notifications to a selected player, not server-side video uploads.
- Both developer lists ship empty. Source exports exclude the private plugin, Git metadata, local data and credentials. Common secret patterns are checked during export.
- Embedded web-engine code and dependencies have been removed entirely. Crosshair image dimensions are checked before native decoding, and source images are size-limited.

## Verification and limits

Shared tests exercise exact hello validation against arbitrary byte arrays, rejection of forged clip/roster messages, bounded payload decoding, exact money parsing, capture pacing and immutable roster heartbeats. The private plugin has identity-policy regression checks for removed accounts, stale pins and unpinned names. Client startup/mixin/settings tests run for each supported version; the universal archive is checked with Fabric Loader's resolver.

This does not prove that a server cannot be compromised. Player identity still depends on correct Minecraft authentication and proxy configuration. For Velocity, modern authenticated forwarding and a firewall that restricts backend access are required deployment controls; this plugin cannot verify your hosting firewall. See the upstream PaperMC security guidance: https://docs.papermc.io/velocity/security/ and https://docs.papermc.io/velocity/player-information-forwarding/.

Presence and badges are cosmetic client claims bound to a server-authenticated connection. A modified client can announce that it uses the mod; this does not grant permissions, money, admin access or anti-cheat trust. Existing server permission grants and old server config files are not silently overwritten by installing a new JAR.
