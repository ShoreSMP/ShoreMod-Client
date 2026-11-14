# In-game acceptance checklist

## Build gate

- `common:coreTest` passes; build outputs contain only the expected client/plugin resources.
- Include the original logo asset. Confirm it loads as a colored small icon rather than the S fallback.

## Paper and Folia (test both)

- Start with two modded players and one vanilla player in different Folia regions.
- Badges appear only on modded players, including after teleport, respawn, leaving/rejoining and backend transfer.
- No Folia thread-check errors during joins, roster broadcasts, clip notifications or shutdown.
- Ordinary players cannot execute `/modlist`; an explicitly configured developer can; the first UUID pin survives restart and name changes.
- Malformed plugin payloads cannot register someone else's identity, overwrite a roster or trigger other players' clips.
- Restarting/removing the plugin expires client presence and cancels payment queues.

## Inventory

- Empty, sparse, full, renamed and colored shulkers display the correct slots and quantities.
- Container GUI, player inventory and creative inventory behavior is checked separately.
- Escape/click dismisses preview without moving items; Shift-right-click behaves normally.
- RMB revisit deposits exactly one per entry, not one per mouse movement within a slot.
- Incompatible/full/output slots, release outside the GUI, empty cursor, Shift-click and disabled tweaks are tested.
- No ghost stacks or desync under latency; the server remains authoritative.

## Privacy and chat

- New and upgraded installs default to no timestamps or highlight markers, with consecutive identical messages stacked as `[x2]`, `[x3]` rather than silently discarded.
- Chat options can enable timestamps/highlights, disable stacking and reset to plain defaults; choices persist after restart.
- Stacking preserves styled text, click/hover actions and the latest message signature; wrapped lines and scrolling remain usable.
- The original logo aligns with name text and appears for mod users in tab, including the local user, while retaining rank formatting.

- Mention markers/custom highlights preserve click/hover actions and signed-message reporting.
- Repeated-message filter does not remove later legitimate repeated messages.
- F8 hides selected vanilla chat/sidebar/tab/debug elements from screenshots and clips.
- Privacy toggles clear the buffer; changing appearance persists; vanilla opacity is restored on shutdown.

## Clips

- Warm buffer for over 30 seconds; compare actual video duration, colors, orientation and playback with a clock visible in-game.
- Trigger Blacklisted Modifications / Bug Abusing in a hub server system message, on disconnect, and using console integration.
- Ordinary signed player chat does not trigger a clip; repeated triggers within cooldown do not duplicate exports.
- Test short warm-up, resize, privacy changes, 4K framebuffer, minimized window, disconnect/reconnect, disabled recording, low disk space and a failed encoder.
- Validate both native AVI playback and configured FFmpeg MP4 conversion. Video-only is clearly communicated.

## Payments

- Use a test economy and tiny amounts. Verify every plan preview and total.
- `/payall` pays each eligible UUID once; `/payrandom` has no repeated winners.
- Split/rain shares add up exactly, including leftover cents and small budgets.
- Measure >=2 seconds between actual outgoing pay commands, including low FPS/stalls.
- `/paycancel`, disconnect, backend transfer, stale roster and a departing recipient behave correctly.
- Rejected/uncertain payments never automatically retry.

## Version release gate

Use the exact supported targets in COMPATIBILITY.md. Do not widen Minecraft metadata without porting and validating rendering, input, networking and mixin targets.
