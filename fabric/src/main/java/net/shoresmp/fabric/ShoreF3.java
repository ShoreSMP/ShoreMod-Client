package net.shoresmp.fabric;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.LightType;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/** Samples only selected fields while the full debug panel is open. */
public final class ShoreF3 {
    private ShoreF3() {}

    public enum Field {
        VERSION("Minecraft version"), FPS("FPS"), SERVER("Server"), ENTITIES("Entity count"),
        SECTION("Section"), CHUNK_SOURCE("Chunk source"), DIMENSION("Dimension"), POSITION("Position"),
        BLOCK("Block position"), CHUNK("Chunk position"), FACING("Facing"), LIGHT("Client light"),
        CHUNK_INFO("Chunk info"), BIOME("Biome"), DIFFICULTY("Difficulty"), SPAWN("Spawn state"),
        SHADER("Shader"), SOUNDS("Sounds"), MEMORY("Memory usage"), ALLOCATION("Allocation"),
        CPU("CPU"), DISPLAY("Display"), RENDERER("Renderer"), GL("Graphics API"),
        BLOCK_HIT("Block hit result"), ENTITY_HIT("Entity hit result"), RECORDING("Recording");

        final String label;
        Field(String label) { this.label = label; }
        boolean reduced() {
            return switch (this) {
                case SECTION, DIMENSION, POSITION, BLOCK, CHUNK, FACING, LIGHT, CHUNK_INFO, BIOME,
                     DIFFICULTY, SPAWN, BLOCK_HIT, ENTITY_HIT -> true;
                default -> false;
            };
        }
        boolean visible(Config c) {
            Boolean override = c.debugFields.get(name());
            if (override != null) return override;
            return switch (this) {
                case VERSION, FPS, RECORDING -> true;
                case POSITION, DIMENSION, FACING, BIOME -> c.debugCoordinates;
                case BLOCK_HIT -> c.debugTarget;
                case MEMORY -> c.debugMemory;
                default -> false;
            };
        }
        int color(Config c) { return c.debugColors.getOrDefault(name(), 0xF3F8FF); }
    }

    private static KeyBinding panelKey, pieKey, graphKey;
    private static long opened, nextSample, lastBytes, lastSample;
    private static Object lastWorld;
    private static List<Line> rows = List.of();
    private static List<DrawLine> layout = List.of();
    private static List<Line> layoutRows;
    private static int layoutWidth;
    private static CompletableFuture<String> spawnRequest;
    private static String spawnSummary = "Waiting for server";
    private static long nextSpawnSample;
    private static String cpu;
    private static final String GAME_VERSION = net.fabricmc.loader.api.FabricLoader.getInstance()
        .getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
    private record Line(String text, int color) {}
    private record DrawLine(String text, int width, int color) {}

    public static void registerKeys(BiFunction<String,Integer,KeyBinding> register) {
        int unbound = net.minecraft.client.util.InputUtil.UNKNOWN_KEY.getCode();
        panelKey = register.apply("custom_f3", unbound);
        pieKey = register.apply("custom_pie", unbound);
        graphKey = register.apply("custom_graph", unbound);
    }

    public static void tick(MinecraftClient mc) {
        Config c = ShoreClient.config;
        while (panelKey.wasPressed()) if (c.betterF3 && mc.player != null && mc.currentScreen == null)
            mc.debugHudEntryList.toggleF3Enabled();
        while (pieKey.wasPressed()) if (c.betterF3 && mc.player != null && mc.currentScreen == null)
            mc.getDebugHud().toggleRenderingChart();
        while (graphKey.wasPressed()) if (c.betterF3 && mc.player != null && mc.currentScreen == null)
            mc.getDebugHud().toggleRenderingAndTickCharts();

        if (lastWorld != mc.world) {
            lastWorld = mc.world;
            spawnRequest = null;
            nextSpawnSample = 0;
            spawnSummary = "Waiting for server";
            opened = 0;
        }
        if (!active()) {
            if (opened != 0) { opened = 0; rows = List.of(); layout = List.of(); layoutRows = null; lastSample = 0; }
            return;
        }
        refresh(mc, System.nanoTime());
    }

    public static boolean active() {
        Config c = ShoreClient.config;
        return c != null && c.betterF3 && !(c.privacy && c.hideDebug) && ShoreDebug.isPanelOpen();
    }

    public static boolean charts() {
        var hud = MinecraftClient.getInstance().getDebugHud();
        return hud.shouldShowRenderingChart() || hud.shouldRenderTickCharts() || hud.shouldShowPacketSizeAndPingCharts();
    }

    private static void refresh(MinecraftClient mc, long now) {
        if (mc.world == null || mc.player == null) return;
        if (opened == 0) { opened = now; nextSample = now; }
        if (now < nextSample) return;
        Config c = ShoreClient.config;
        nextSample = now + c.debugRefreshMs * 1_000_000L;
        boolean reduced = mc.hasReducedDebugInfo();
        var result = new ArrayList<Line>();
        for (Field f : Field.values()) {
            if (!f.visible(c) || reduced && f.reduced()) continue;
            result.add(new Line(f.label + "  " + value(mc, f, now), f.color(c)));
        }
        rows = List.copyOf(result);
    }

    private static String value(MinecraftClient mc, Field f, long now) {
        var p = mc.player;
        var world = mc.world;
        var pos = p.getBlockPos();
        return switch (f) {
            case VERSION -> GAME_VERSION;
            case FPS -> Integer.toString(mc.getCurrentFps());
            case SERVER -> mc.getCurrentServerEntry() == null ? "Singleplayer" : mc.getCurrentServerEntry().address;
            case ENTITIES -> Integer.toString(world.getRegularEntityCount());
            case SECTION -> (pos.getX() & 15) + " / " + (pos.getY() & 15) + " / " + (pos.getZ() & 15);
            case CHUNK_SOURCE -> world.getChunkManager().getDebugString();
            case DIMENSION -> world.getRegistryKey().getValue().toString();
            case POSITION -> String.format(Locale.ROOT, "%.2f / %.2f / %.2f", p.getX(), p.getY(), p.getZ());
            case BLOCK -> pos.getX() + " / " + pos.getY() + " / " + pos.getZ();
            case CHUNK -> (pos.getX() >> 4) + " / " + (pos.getY() >> 4) + " / " + (pos.getZ() >> 4);
            case FACING -> p.getHorizontalFacing().asString();
            case LIGHT -> world.getLightLevel(LightType.BLOCK, pos) + " block / " + world.getLightLevel(LightType.SKY, pos) + " sky";
            case CHUNK_INFO -> mc.worldRenderer.getChunksDebugString();
            case BIOME -> world.getBiome(pos).getKey().map(k -> k.getValue().getPath()).orElse("Unknown");
            case DIFFICULTY -> world.getDifficulty().name().toLowerCase(Locale.ROOT);
            case SPAWN -> spawn(mc, now);
            case SHADER -> Objects.toString(mc.gameRenderer.getPostProcessorId(), "None");
            case SOUNDS -> mc.getSoundManager().getDebugString();
            case MEMORY -> {
                var rt = Runtime.getRuntime();
                yield ((rt.totalMemory() - rt.freeMemory()) >> 20) + " / " + (rt.maxMemory() >> 20) + " MiB";
            }
            case ALLOCATION -> allocation(now);
            case CPU -> {
                if (cpu == null) cpu = new oshi.SystemInfo().getHardware().getProcessor().getProcessorIdentifier().getName().strip();
                yield cpu;
            }
            case DISPLAY -> mc.getWindow().getFramebufferWidth() + " x " + mc.getWindow().getFramebufferHeight();
            case RENDERER -> RenderSystem.getDevice().getRenderer();
            case GL -> RenderSystem.getDevice().getBackendName() + " " + RenderSystem.getDevice().getVersion();
            case BLOCK_HIT -> mc.crosshairTarget instanceof BlockHitResult h && h.getType() == HitResult.Type.BLOCK
                ? world.getBlockState(h.getBlockPos()).getBlock().getName().getString() + "  " + h.getBlockPos().toShortString() : "None";
            case ENTITY_HIT -> mc.targetedEntity == null ? "None" : mc.targetedEntity.getType().getName().getString();
            case RECORDING -> ShoreClient.recorder.status();
        };
    }

    private static String allocation(long now) {
        var bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean extended) || !extended.isThreadAllocatedMemoryEnabled())
            return "Unavailable";
        long bytes = extended.getTotalThreadAllocatedBytes();
        String value = lastSample == 0 ? "Sampling" : String.format(Locale.ROOT, "%.1f MiB/s",
            Math.max(0L, bytes - lastBytes) * (1e9 / 1048576.0) / Math.max(1L, now - lastSample));
        lastBytes = bytes;
        lastSample = now;
        return value;
    }

    private static String spawn(MinecraftClient mc, long now) {
        var server = mc.getServer();
        if (server == null) return "Server only";
        if (spawnRequest != null && spawnRequest.isDone()) {
            spawnSummary = spawnRequest.getNow("Unavailable");
            spawnRequest = null;
        }
        if (spawnRequest == null && now >= nextSpawnSample) {
            nextSpawnSample = now + 1_000_000_000L;
            var dimension = mc.world.getRegistryKey();
            spawnRequest = server.submit(() -> {
                var level = server.getWorld(dimension);
                var info = level == null ? null : level.getChunkManager().getSpawnInfo();
                if (info == null) return "Waiting for spawn cycle";
                int total = 0;
                for (int count : info.getGroupToCount().values()) total += count;
                return total + " mobs / " + info.getSpawningChunkCount() + " spawning chunks";
            }).exceptionally(error -> "Unavailable");
        }
        return spawnSummary;
    }

    public static void draw(DrawContext ctx) {
        var mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
        long now = System.nanoTime();
        refresh(mc, now);
        Config c = ShoreClient.config;
        float t = !c.debugAnimate || c.debugAnimMs == 0 ? 1f :
            Math.clamp((now - opened) / (c.debugAnimMs * 1_000_000f), 0f, 1f);
        float opacity = c.debugFade ? t : 1f;
        int alpha = Math.round(255 * opacity);
        if (alpha < 5) return;
        int slide = c.debugSlide ? Math.round(12 * (1f - t)) : 0;
        int width = ctx.getScaledWindowWidth(), height = ctx.getScaledWindowHeight();
        int capacity = Math.max(1, (height - 10) / 12);
        int columns = Math.min(2, Math.max(c.debugTwoColumns && rows.size() > 1 ? 2 : 1, (rows.size() + capacity - 1) / capacity));
        int perColumn = Math.min(capacity, (rows.size() + columns - 1) / columns);
        int columnWidth = Math.max(8, (width - 10) / columns);
        if (perColumn == 0) return;
        if (layoutRows != rows || layoutWidth != columnWidth) {
            var measured = new ArrayList<DrawLine>(rows.size());
            for (Line row : rows) {
                String text = mc.textRenderer.trimToWidth(row.text(), Math.max(1, columnWidth - 8));
                measured.add(new DrawLine(text, mc.textRenderer.getWidth(text), row.color()));
            }
            layout = measured;
            layoutRows = rows;
            layoutWidth = columnWidth;
        }
        for (int i = 0; i < Math.min(layout.size(), perColumn * columns); i++) {
            DrawLine row = layout.get(i);
            int x = 5 + i / perColumn * columnWidth - slide;
            int y = 5 + i % perColumn * 12;
            if (c.debugPanelOpacity > 0) ctx.fill(x, y, x + row.width() + 6, y + 12,
                Math.round(c.debugPanelOpacity * 2.55f * opacity) << 24 | c.menuBackgroundColor);
            ctx.drawText(mc.textRenderer, row.text(), x + 3, y + 2, alpha << 24 | row.color(), c.debugShadow);
        }
    }

    static void settings(ShoreScreen s) {
        Config c = ShoreClient.config;
        s.section("Fields");
        for (Field f : Field.values())
            s.toggle(f.label, "", () -> f.visible(c), v -> c.debugFields.put(f.name(), v));
        s.section("Field colors");
        for (Field f : Field.values())
            s.color(f.label, "", () -> f.color(c), v -> c.debugColors.put(f.name(), v));
        s.section("Appearance");
        s.toggle("Two columns", "Split the selected fields across the screen", () -> c.debugTwoColumns, v -> c.debugTwoColumns = v);
        s.slider("Panel opacity", "", 0, 100, 1, "%", () -> c.debugPanelOpacity, v -> c.debugPanelOpacity = v);
        s.toggle("Text shadow", "", () -> c.debugShadow, v -> c.debugShadow = v);
        s.slider("Refresh interval", "Time between data samples", 100, 2000, 50, " ms", () -> c.debugRefreshMs, v -> c.debugRefreshMs = v);
        s.section("Animation");
        s.toggle("Animate", "", () -> c.debugAnimate, v -> c.debugAnimate = v);
        s.toggle("Fade in", "", () -> c.debugFade, v -> c.debugFade = v);
        s.toggle("Slide in", "", () -> c.debugSlide, v -> c.debugSlide = v);
        s.slider("Duration", "", 0, 1500, 10, " ms", () -> c.debugAnimMs, v -> c.debugAnimMs = v);
        s.section("Keybinds");
        s.keybind("Alternate F3", "Open or close the debug panel", panelKey);
        s.keybind("Alternate pie chart", "Toggle the profiler", pieKey);
        s.keybind("Alternate graph", "Toggle the FPS and tick graphs", graphKey);
    }
}
