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
    // Optional visual tools. Bounded settings; no server changes.
    public boolean lowOverhead=true, performanceDefaultsApplied=false, hudItemIcons=false;
    public boolean chatHeadHat=true;public int chatHeadSize=8;
    public boolean blockOverlay=false, betterF3=false, guiTweaks=false, guiNoDim=true;
    public boolean coloredHitboxes=false, hitboxPlayersOnly=true, totemTweaks=false, waypoints=false, chatHeads=false;
    public boolean debugCoordinates=true, debugTarget=true, debugMemory=true, blockInfo=true;
    public int blockColor=0x50A7FF, blockOpacity=90, outlineWidth=2, hitboxColor=0x50A7FF, hitboxRange=24;
    public int inventoryOpacity=75, heldTotemSize=75, heldTotemOpacity=85, popTotemSize=75, popTotemOpacity=85;
    public int waypointColor=0x50A7FF, waypointRange=10000, waypointScale=100;
    public String waypointName="Home", waypointCoordinates="";
    public List<Waypoint> savedWaypoints=new ArrayList<>();
    public static final class Waypoint {
        public String name="Waypoint",server="",dimension=""; public int x,y,z,color=0x50A7FF; public boolean enabled=true,tracked=false;
    }
    // Visual customization (all optional; no server state changes).
    public boolean motionBlur=false, blurMovingOnly=true, blurAutoProtect=true;
    public int blurStrength=35, blurSamples=2, blurFps=60, blurMinFps=45, blurMemoryMb=96;
    public boolean customCrystals=false, crystalRainbow=false, crystalBase=true, crystalBeam=true;
    public int crystalColor=0xB56CFF, crystalOpacity=100, crystalSize=100, crystalSpeed=100, crystalBob=100, crystalStyle=0;
    public boolean dayCounter=false, dayOneBased=true, dayShowTime=false;
    public int dayOffset=0;
    public String dayLabel="Day";
    public int hitboxWidth=1, hitboxOpacity=100, hitboxLimit=32, hitboxStyle=0, hitboxEyeColor=0xFF5555, hitboxLookColor=0x55CCFF;
    public boolean hitboxEyes=false, hitboxLook=false, hitboxTargetColor=true, hitboxDistanceFade=false;
    public int hitboxTargetRgb=0xFF5555;
    public int guiTintColor=0xFFFFFF, menuBackgroundColor=0x0D0F12, hudBackgroundColor=0x101217, hudBorderColor=0x50A7FF;
    public boolean hudBorder=false,menuTwoColumns=false,menuDefaults140=false;
    public int hudPadding=5, hudGap=2, hudMargin=4, menuShade=33;
    public boolean waypointDistances=true, waypointCoordinatesShown=false, waypointBackground=true, waypointOffscreen=true;
    public boolean waypointCompass=true, waypointAutoDeath=false, waypointOnlyCurrentServer=true;
    public int waypointOpacity=75, waypointMaxVisible=16;
    public int shulkerGridColor=0x704A78, shulkerBorderColor=0x500078, shulkerBackgroundColor=0x100010;
    public boolean shulkerHints=true, shulkerLock=true, shulkerItemTooltip=true;
    public boolean tooltipDefaults140=false;
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
        if(!menuDefaults140){panelOpacity=100;menuBackgroundColor=0x07090C;menuShade=45;menuDefaults140=true;}
        if(!tooltipDefaults140){shulkerMode=1;tooltipDefaults140=true;}
        blurStrength=Math.clamp(blurStrength,0,85);blurSamples=Math.clamp(blurSamples,1,4);
        blurFps=Math.clamp(blurFps,15,120);blurMinFps=Math.clamp(blurMinFps,15,120);blurMemoryMb=Math.clamp(blurMemoryMb,16,256);
        crystalColor&=0xFFFFFF;crystalOpacity=Math.clamp(crystalOpacity,5,100);crystalSize=Math.clamp(crystalSize,25,200);
        crystalSpeed=Math.clamp(crystalSpeed,0,300);crystalBob=Math.clamp(crystalBob,0,200);crystalStyle=Math.clamp(crystalStyle,0,2);
        dayOffset=Math.clamp(dayOffset,-1000000,1000000);if(dayLabel==null)dayLabel="Day";dayLabel=dayLabel.substring(0,Math.min(24,dayLabel.length()));
        hitboxWidth=Math.clamp(hitboxWidth,1,4);hitboxOpacity=Math.clamp(hitboxOpacity,5,100);hitboxLimit=Math.clamp(hitboxLimit,1,64);hitboxStyle=Math.clamp(hitboxStyle,0,2);
        hitboxEyeColor&=0xFFFFFF;hitboxLookColor&=0xFFFFFF;hitboxTargetRgb&=0xFFFFFF;
        guiTintColor&=0xFFFFFF;menuBackgroundColor&=0xFFFFFF;hudBackgroundColor&=0xFFFFFF;hudBorderColor&=0xFFFFFF;
        hudPadding=Math.clamp(hudPadding,2,12);hudGap=Math.clamp(hudGap,0,12);hudMargin=Math.clamp(hudMargin,0,40);menuShade=Math.clamp(menuShade,0,90);
        waypointOpacity=Math.clamp(waypointOpacity,0,100);waypointMaxVisible=Math.clamp(waypointMaxVisible,1,32);
        shulkerGridColor&=0xFFFFFF;shulkerBorderColor&=0xFFFFFF;shulkerBackgroundColor&=0xFFFFFF;

        if(!blueDefaultApplied){if(accent==0x35DBD2)accent=0x50A7FF;blueDefaultApplied=true;}
        if(!darkHudDefaultApplied){if(hudBgOpacity==50)hudBgOpacity=85;darkHudDefaultApplied=true;}
        if(!compactGuiDefaultApplied){if(menuScale==100)menuScale=85;compactGuiDefaultApplied=true;}
        if(!performanceDefaultsApplied){recording=false;lowOverhead=true;performanceDefaultsApplied=true;}
        chatHeadSize=Math.clamp(chatHeadSize,4,8);
        blockOpacity=Math.clamp(blockOpacity,0,100);outlineWidth=Math.clamp(outlineWidth,1,4);hitboxRange=Math.clamp(hitboxRange,4,48);
        inventoryOpacity=Math.clamp(inventoryOpacity,10,100);
        heldTotemSize=Math.clamp(heldTotemSize,10,200);popTotemSize=Math.clamp(popTotemSize,10,200);
        heldTotemOpacity=Math.clamp(heldTotemOpacity,0,100);popTotemOpacity=Math.clamp(popTotemOpacity,0,100);
        waypointRange=Math.clamp(waypointRange,16,30000000);waypointScale=Math.clamp(waypointScale,50,150);
        blockColor&=0xFFFFFF;hitboxColor&=0xFFFFFF;waypointColor&=0xFFFFFF;
        if(waypointName==null)waypointName="Home";if(waypointCoordinates==null)waypointCoordinates="";
        if(savedWaypoints==null)savedWaypoints=new ArrayList<>();
        savedWaypoints.removeIf(w->w==null||w.name==null||w.server==null||w.dimension==null||Math.abs((long)w.x)>30000000||Math.abs((long)w.z)>30000000||w.y< -2048||w.y>2048);
        while(savedWaypoints.size()>64)savedWaypoints.removeLast();
        for(var w:savedWaypoints){w.name=w.name.substring(0,Math.min(32,w.name.length()));w.color&=0xFFFFFF;}
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
    public int panelColor() { return ((int)(panelOpacity * 2.55) << 24) | menuBackgroundColor; }
}

