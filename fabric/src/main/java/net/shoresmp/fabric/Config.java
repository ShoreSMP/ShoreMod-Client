package net.shoresmp.fabric;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.*;
import java.io.*;

public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("shoremod.json");
    public boolean timestamps = false, mentionHighlight = true, filterDuplicates = true;
    public int chatDefaultsVersion = 2;
    public int chatHistoryLimit = 5000;
    public boolean shulkerPreview = true, mouseTweaks = true, badges = true;
    public boolean badgeNametags = true, badgeTab = true;
    public boolean privacy = false, hideChat = true, hideScoreboard = true, hideTab = true, hideDebug = true;
    public volatile boolean antiLeakTextures = false;
    public boolean recording = false, saveEveryDisconnect = false, recordingProtectFps = true;
    public boolean blueDefaultApplied, darkHudDefaultApplied, compactGuiDefaultApplied;
    public int accent = 0x50A7FF, panelOpacity = 90, clipFps = 8, clipWidth = 640, maxClipStorageMb = 1024;
    public double chatOpacity = 0.5;
    public String ffmpegPath = "";
    public List<String> punishmentPhrases = new ArrayList<>(List.of("blacklisted modifications", "bug abusing", "temporarily banned", "permanently banned", "you are banned", "you have been banned", "unfair advantage", "cheating", "exploiting"));
    public List<String> highlightWords = new ArrayList<>();
    public boolean mentionSound = false;
    // HUD overlay
    public boolean hud = true, hudFps = true, hudPing = true, hudCoords = false, hudBiome = false, hudClock = false, hudArmor = true;
    public int hudCorner = 0, hudScale = 100;
    /** Per-element HUD size in percent, keyed by module id (fps, ping, coords, biome, clock, armor). */
    public Map<String,Integer> hudSizes = new HashMap<>();
    public int hudSize(String id){Integer v=hudSizes.get(id);return v==null?100:v;}
    public boolean fullbright = false;
    public int fullbrightStrength = 60;
    public int hudCustomColor = 0xFFFFFF, crosshairCustomColor = 0xFFFFFF, mentionCustomColor = 0x35DBD2;
    // Tools
    public boolean zoomScroll = true, zoomSmooth = true, durabilityAlert = true, deathLog = true, reconnectButton = true, autoReconnect = false;
    public int zoomLevel = 4, durabilityThreshold = 10, autoReconnectDelay = 10;
    public List<String> deaths = new ArrayList<>();
    // Mod menu
    public boolean zoomEnabled = true, extendedChat = true;
    public boolean tweakRmb = true, tweakLmb = true, tweakShift = true, tweakWheel = true;
    public int menuScale = 85, menuFontScale = 100, paymentScale = 70;
    // Zoom (Lunar-style options)
    public boolean zoomToggle = false, zoomCinematic = false;
    public int zoomSensMode = 1, zoomSensitivity = 60, zoomScrollSpeed = 20;
    // CPS, toggle sprint/sneak, crosshair
    public boolean cps = false, cpsRight = false, toggleSprint = false, toggleSneak = false, toggleSprintHud = true;
    public boolean crosshair = false, crosshairDot = false, crosshairOutline = true;
    public int crosshairColor = 0, crosshairLength = 5, crosshairGap = 2, crosshairThickness = 1;
    // More options
    public boolean shulkerColorBorder = true, hudShadow = true, timestamps24 = true, timestampSeconds = false;
    public boolean durabilitySound = true, durabilityChat = false, deathAnnounce = true;
    public int shulkerMode = 0, hudBgOpacity = 85, hudTextColor = 0, stackStyle = 0;
    public int stackLookback = 8, mentionColor = 0;
    // PvP
    public boolean targetHud = false, potionHud = false, keystrokes = false, itemCounter = false;
    public boolean totemPops = false, totemPopsSelf = false, keystrokesMouse = true, keystrokesSpace = true;
    public boolean itemsTotem = true, itemsPearl = true, itemsGapple = true, itemsCrystal = true, itemsArrow = false, itemsXp = false;
    public List<String> itemsCustom = new ArrayList<>();
    public boolean itemsShowZero = false, keystrokesAccent = false, targetDistance = false;
    // Shulker preview
    public boolean shulkerTitle = true, shulkerEmptySlots = true;
    public int shulkerScale = 100, shulkerBgOpacity = 96, shulkerBorderMode = 0;
    // Crosshair image
    public int crosshairMode = 0, crosshairImageSize = 15, crosshairOpacity = 100;
    public boolean crosshairTint = false;
    public String crosshairImage = "";
    public int[] crosshairPaint = new int[net.shoresmp.core.PixelCanvas.COUNT];
    public List<String> favorites = new ArrayList<>();
    /** Module id -> key translation key (e.g. "key.keyboard.h") that toggles it in game. */
    public Map<String,String> moduleKeys = new HashMap<>();
    public static Config load() {
        try {
            if (Files.exists(PATH)) {
                JsonObject data=JsonParser.parseString(Files.readString(PATH)).getAsJsonObject();
                Config result = GSON.fromJson(data, Config.class);
                if (result != null) {
                    if(!data.has("chatDefaultsVersion")) {
                        // Apply the new plain-chat default once to pre-existing installations.
                        result.timestamps=false; result.mentionHighlight=false; result.filterDuplicates=true;
                        result.chatDefaultsVersion=1; result.save();
                    }
                    if(result.chatDefaultsVersion<2) {
                        // Mentions now highlight the whole line; switch them on for existing installs once.
                        result.mentionHighlight=true; result.chatDefaultsVersion=2; result.save();
                    }
                    result.validate(); return result;
                }
            }
        } catch (IOException | RuntimeException e) { ShoreClient.LOG.warn("Could not read ShoreMod config; using defaults", e); }
        Config result = new Config(); result.save(); return result;
    }
    private void validate() {
        if(!blueDefaultApplied){if(accent==0x35DBD2)accent=0x50A7FF;blueDefaultApplied=true;}
        if(!darkHudDefaultApplied){if(hudBgOpacity==50)hudBgOpacity=85;darkHudDefaultApplied=true;}
        if(!compactGuiDefaultApplied){if(menuScale==100)menuScale=85;compactGuiDefaultApplied=true;}
        chatHistoryLimit = Math.clamp(chatHistoryLimit,100,20000);
        clipFps = Math.clamp(clipFps, 5, 60); clipWidth = Math.clamp(clipWidth, 320, 1920);
        panelOpacity = Math.clamp(panelOpacity, 20, 100); maxClipStorageMb = Math.clamp(maxClipStorageMb, 128, 16384);
        chatOpacity = Double.isFinite(chatOpacity) ? Math.clamp(chatOpacity, 0, 1) : 0.5;
        if (punishmentPhrases == null) punishmentPhrases = new ArrayList<>();
        if (highlightWords == null) highlightWords = new ArrayList<>();
        if (ffmpegPath == null) ffmpegPath = "";
        privacy = false; // privacy mode was removed
        if (deaths == null) deaths = new ArrayList<>();
        if (favorites == null) favorites = new ArrayList<>();
        if (moduleKeys == null) moduleKeys = new HashMap<>();
        if (itemsCustom == null) itemsCustom = new ArrayList<>();
        if (crosshairImage == null) crosshairImage = "";
        if (crosshairPaint == null || crosshairPaint.length != net.shoresmp.core.PixelCanvas.COUNT) crosshairPaint = new int[net.shoresmp.core.PixelCanvas.COUNT];
        shulkerScale = Math.clamp(shulkerScale, 50, 200);
        shulkerBgOpacity = Math.clamp(shulkerBgOpacity, 0, 100); shulkerBorderMode = Math.clamp(shulkerBorderMode, 0, 2);
        crosshairMode = Math.clamp(crosshairMode, 0, 1); crosshairImageSize = Math.clamp(crosshairImageSize, 4, 64); crosshairOpacity = Math.clamp(crosshairOpacity, 10, 100);
        menuScale = Math.clamp(menuScale, 50, 150);
        menuFontScale = Math.clamp(menuFontScale, 75, 125);
        paymentScale = Math.clamp(paymentScale, 50, 100);
        zoomSensMode = Math.clamp(zoomSensMode, 0, 1); zoomSensitivity = Math.clamp(zoomSensitivity, 5, 100); zoomScrollSpeed = Math.clamp(zoomScrollSpeed, 5, 60);
        crosshairColor = Math.clamp(crosshairColor, 0, 6); crosshairLength = Math.clamp(crosshairLength, 1, 15);
        shulkerMode = Math.clamp(shulkerMode, 0, 1); hudBgOpacity = Math.clamp(hudBgOpacity, 0, 100); hudTextColor = Math.clamp(hudTextColor, 0, 3); stackStyle = Math.clamp(stackStyle, 0, 2); stackLookback = Math.clamp(stackLookback, 1, 20); mentionColor = Math.clamp(mentionColor, 0, 5);
        crosshairGap = Math.clamp(crosshairGap, 0, 10); crosshairThickness = Math.clamp(crosshairThickness, 1, 4);
        while (deaths.size() > 10) deaths.removeLast();
        hudCorner = Math.clamp(hudCorner, 0, 3); hudScale = Math.clamp(hudScale, 50, 250);
        if (hudSizes == null) hudSizes = new HashMap<>();
        hudSizes.replaceAll((k, v) -> v == null ? 100 : Math.clamp(v, 50, 250));
        zoomLevel = Math.clamp(zoomLevel, 2, 20); durabilityThreshold = Math.clamp(durabilityThreshold, 1, 50);
        autoReconnectDelay = Math.clamp(autoReconnectDelay, 3, 120);
        fullbrightStrength = Math.clamp(fullbrightStrength, 0, 100);
        hudCustomColor &= 0xFFFFFF; crosshairCustomColor &= 0xFFFFFF; mentionCustomColor &= 0xFFFFFF;
        favorites.removeIf(id -> id == null || id.equals("reach") || id.equals("combo"));
        moduleKeys.remove("reach"); moduleKeys.remove("combo");
        hudSizes.remove("reach"); hudSizes.remove("combo");
        accent &= 0xFFFFFF;
    }
    public void save() {
        validate();
        try {
            Files.createDirectories(PATH.getParent());
            Path temp = PATH.resolveSibling("shoremod.json.tmp");
            Files.writeString(temp, GSON.toJson(this));
            Files.move(temp, PATH, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) { ShoreClient.LOG.error("Could not save ShoreMod settings", e); }
    }
    public int panelColor() { return ((int)(panelOpacity * 2.55) << 24) | 0x0D1016; }
}

