# ShoreMod 1.2.0 settings

Numeric settings accept your own whole-number values. Click the value beside a slider, type a number, then press Enter or click away. Values apply live when valid. The tooltip shows the allowed range. Invalid drafts turn red and leave the last valid setting in effect; Escape restores it.

This includes individual and overall HUD sizes, opacity, chat history, reconnect delay, zoom, sensitivity, crosshair dimensions, shulker previews, and clip FPS, resolution and storage. Typed values do not have to match the slider's preset increments: a 173% HUD size or 17 FPS recording is valid. Zoom is bounded to 2–20x; fullbright strength is bounded to 0–100%.

HUD text, mention highlights and crosshairs support six-digit RGB hex colors, with an optional leading #. Entering a valid custom color selects Custom automatically. Image crosshairs use the color when Tint with color is enabled.

Fullbright is an optional Utility module with a live brightness boost and its own toggle-key binding. A strength of zero leaves normal lighting; 100 is the strongest supported boost. The original Minecraft brightness is returned immediately when the module is disabled, and options saves read the original value so boosted brightness is not written into options.txt. Custom shader packs that replace Minecraft lighting are outside the tested rendering path.

Reach Display and Combo Counter have been removed, including their HUD tracking and obsolete favorites/key bindings. Practical readouts, target information, inventory tools and alerts remain. Anti-Leak now has a Shuffle action so its pattern can be changed immediately without toggling the module twice.


Validation includes numeric/color boundary checks, the real gamma getter, on/off restoration, saving options while fullbright is active, 173% typed HUD sizing, rejected invalid drafts, and rendering the Fullbright, Crosshair and General settings pages on every supported client version. Live multiplayer and shader-pack combinations still require gameplay testing.
