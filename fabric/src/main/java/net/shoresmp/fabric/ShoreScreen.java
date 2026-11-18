package net.shoresmp.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.*;

/**
 * The ShoreMod menu, drawn at native resolution through {@link ShoreUi}: a top bar (logo, clock, page
 * tabs, theme, close) above a panel showing either the module grid (filters, search, favorites) or a
 * settings page. Layout and hit-testing happen every frame in logical units (immediate mode).
 */
public final class ShoreScreen extends Screen {
    // ------------------------------------------------------------------ palette
    private static final int TEXT=0xFFF4F5F7, MUTED=0xFF9BA1AA, FAINT=0xFF60666F, HEART=0xFFFF5C7A;
    private int TOP, TOP_HOVER, CARD, CARD_HOVER, EDGE, EDGE_HOVER, CHIP, CHIP_HOVER, BTN, BTN_HOVER;
    private int ON, ON_HOVER, OFF, OFF_HOVER, ROW_HOVER, INPUT, INPUT_EDGE, DIVIDER, TRACK;
    private void updatePalette(Config c) {
        int surface=0xFF000000|c.menuSurface, edge=0xFF000000|c.menuEdge, enabled=0xFF000000|c.menuEnabledColor;
        if(CARD==surface && EDGE==edge && ON==enabled)return;
        CARD=surface; EDGE=edge; ON=enabled;
        ON_HOVER=ON==0xFF7CC62F?0xFF8BD33E:ShoreUi.mix(ON,TEXT,0.16f);
        if(CARD==0xFF090B0E && EDGE==0xFF23272D){
            TOP=0xEB121417; TOP_HOVER=0xF51C1F24;
            CARD_HOVER=0xFF11151B; EDGE_HOVER=0xFF343941;
            CHIP=0xFF1C1F24; CHIP_HOVER=0xFF262A30; BTN=0xFF1E2227; BTN_HOVER=0xFF2A2F35;
            OFF=0xFF1E2227; OFF_HOVER=0xFF272B31; ROW_HOVER=0xFF1B1E23;
            INPUT=0xFF0B0D0F; INPUT_EDGE=0xFF2A2E34; DIVIDER=0xFF1E2126; TRACK=0xFF353A41;
            return;
        }
        CARD_HOVER=ShoreUi.mix(CARD,EDGE,0.32f); EDGE_HOVER=ShoreUi.mix(EDGE,TEXT,0.24f);
        TOP=CARD; TOP_HOVER=CARD_HOVER;
        CHIP=ShoreUi.mix(CARD,EDGE,0.48f); CHIP_HOVER=EDGE;
        BTN=CHIP; BTN_HOVER=EDGE; OFF=CHIP; OFF_HOVER=EDGE;
        ROW_HOVER=CARD_HOVER; INPUT=ShoreUi.mix(CARD,0xFF000000,0.32f); INPUT_EDGE=EDGE;
        DIVIDER=ShoreUi.mix(CARD,EDGE,0.65f); TRACK=ShoreUi.mix(EDGE,TEXT,0.16f);
    }
    private static int categoryColor(String category) {
        return switch(category) {
            case "HUD" -> 0xFF75C9FA; case "PvP" -> 0xFFFF877D; case "Chat" -> 0xFF68DCC8;
            case "Inventory" -> 0xFFEEC373; case "Utility" -> 0xFFB6A5FB; case "Clips" -> 0xFFF49BC5;
            default -> accent();
        };
    }
    private static final String[] CATEGORIES={"All","New","HUD","PvP","Chat","Inventory","Utility","Clips"};
    private static final String[] CORNER_NAMES={"Top left","Top right","Bottom left","Bottom right"};
    private static final int[] ACCENTS={0x35DBD2,0x50A7FF,0xAA83FF,0xFF759D,0xFF8A3D,0xF4D35E,0x7CC62F,0xEEEEEE};
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyy/MM/dd",Locale.ROOT),TIME=DateTimeFormatter.ofPattern("hh:mm a",Locale.ROOT);
    private static final int TOGGLE=0,CHOICE=1,ACTION=2,INFO=3,FIELD=4,ACCENT=5,SLIDER=6,SEGMENT=7,KEY=8;
    /** One stable click action per key row, so a click on the box being edited can be recognised. */
    private final Map<Row,Runnable> keyClicks=new HashMap<>();
    private Runnable keyClick(Row r){return keyClicks.computeIfAbsent(r,x->()->listening=x);}

    private enum Page {GRID,MODULE,GENERAL,CONTROLS}
    private record Module(String id,String name,String desc,String category,String icon,boolean isNew,
                          BooleanSupplier get,Consumer<Boolean> set,Consumer<ShoreScreen> settings){}
    /** One settings row (data only; drawn every frame). */
    static final class Row {
        int kind;String title,help,section;BooleanSupplier get,available=()->true;Consumer<Boolean> set;
        Supplier<String> value;Runnable action;Input input;boolean keycap;int indent;float anim=-1;
        IntSupplier iget;IntConsumer iset;int min,max,step;String suffix="";float trackX,trackW;String[] options;
        Supplier<net.minecraft.client.util.InputUtil.Key> keyGet;Consumer<net.minecraft.client.util.InputUtil.Key> keySet;
        Row dependsOn(Row master){available=master.get;indent=14;return this;}
    }
    /** Minimal single-line text input. */
    static final class Input {
        String text="",placeholder;int cursor,max;boolean selectAll;Consumer<String> change;
        Function<String,String> validation;Supplier<String> currentValue;boolean commitOnBlur;
        Input(String placeholder,String value,int max,Consumer<String> change){this.placeholder=placeholder;this.max=max;this.change=change;set(value,false);}
        void set(String v,boolean notify){text=v.length()>max?v.substring(0,max):v;cursor=text.length();selectAll=false;if(notify&&!commitOnBlur&&change!=null)change.accept(text);}
        void insert(String s){
            if(selectAll){text="";cursor=0;selectAll=false;}
            s=s.replace("\n","").replace("\r","");
            int room=max-text.length();if(room<=0)return;if(s.length()>room)s=s.substring(0,room);
            text=text.substring(0,cursor)+s+text.substring(cursor);cursor+=s.length();if(!commitOnBlur&&change!=null)change.accept(text);
        }
        void backspace(boolean word){
            if(selectAll){set("",true);return;}
            if(cursor==0)return;int from=word?wordStart():cursor-1;
            text=text.substring(0,from)+text.substring(cursor);cursor=from;if(!commitOnBlur&&change!=null)change.accept(text);
        }
        void delete(){if(selectAll){set("",true);return;}if(cursor<text.length()){text=text.substring(0,cursor)+text.substring(cursor+1);if(!commitOnBlur&&change!=null)change.accept(text);}}
        private int wordStart(){int i=cursor;while(i>0&&text.charAt(i-1)==' ')i--;while(i>0&&text.charAt(i-1)!=' ')i--;return i;}
    }
    private record Hit(float x,float y,float w,float h,float[] clip,Runnable click,Input input){
        boolean contains(float px,float py){
            if(px<x||py<y||px>=x+w||py>=y+h)return false;
            return clip==null||(px>=clip[0]&&py>=clip[1]&&px<clip[0]+clip[2]&&py<clip[1]+clip[3]);
        }
    }

    private final ShoreUi ui=new ShoreUi();
    private final List<Module> modules=new ArrayList<>();
    private final List<Row> rows=new ArrayList<>();
    private final List<Hit> hits=new ArrayList<>();
    private final Screen parent;
    private Page page=Page.GRID;
    private Module current;
    private String category="All",pendingSection,tooltip;
    private boolean favoritesOnly;
    private final Input search=new Input("Search","",40,v->{scroll=scrollTarget=0;});
    private Input hexInput,focused;
    private float mx,my,scroll,scrollTarget,maxScroll,frameDt;
    private float[] clip;
    private long lastFrame,resetArmedUntil,caretEpoch;
    private Config.Waypoint deleteWaypoint;private long deleteWaypointUntil;

    public ShoreScreen(Screen parent){super(Text.literal("ShoreMod"));this.parent=parent;defineModules();for(var marker:ShoreClient.config.savedWaypoints)collapsed.add("waypoints/Marker: "+marker.name);collapsed.add("GENERAL/Palette");collapsed.add("betterf3/Field colors");collapsed.add("betterf3/Animation");collapsed.add("hitboxes/Advanced");collapsed.add("motionblur/Advanced");}

    private static int accent(){return 0xFF000000|ShoreClient.config.accent;}
    private static boolean light(int c){return ((c>>16&255)*299+(c>>8&255)*587+(c&255)*114)/1000>150;}
    private static int onAccent(){return light(accent())?0xFF0D0F12:0xFFFFFFFF;}

    // ------------------------------------------------------------------ modules
    private void defineModules(){
        Config c=ShoreClient.config;
        Function<String,Consumer<ShoreScreen>> hudSettings=id->s->{
            s.section("Size");
            s.slider("Element size","Only this HUD element",50,250,5,"%",()->c.hudSize(id),v->c.hudSizes.put(id,v));
            s.section("All HUD elements");
            s.slider("Overall size","Scales every HUD element together",50,250,5,"%",()->c.hudScale,v->c.hudScale=v);
            s.choice("Position","Corner of the screen the HUD sits in",()->CORNER_NAMES[c.hudCorner],()->{c.hudCorner=(c.hudCorner+1)%4;c.save();});
            s.section("Style");
            s.slider("Background opacity","Darkness of the box behind each element",0,100,5,"%",()->c.hudBgOpacity,v->c.hudBgOpacity=v);
            s.segment("Text color","",new String[]{"White","Accent","Gray","Custom"},()->c.hudTextColor,v->c.hudTextColor=v);
            s.color("Custom text color","Type a hex color; applies to the HUD",()->c.hudCustomColor,v->{c.hudCustomColor=v;c.hudTextColor=3;});
            s.toggle("Text shadow","Minecraft's drop shadow behind the text",()->c.hudShadow,v->c.hudShadow=v);
        };
        mod("motionblur","Motion Blur","GPU frame-history blending with a sharp HUD.","Utility","camera",true,()->c.motionBlur,v->{c.motionBlur=v;ShoreMotionBlur.clear();},s->{
            s.section("Appearance");
            s.slider("Strength","0 is clear; higher values leave longer trails",0,85,1,"%",()->c.blurStrength,v->c.blurStrength=v);
            s.slider("History samples","More samples use more GPU memory",1,4,1,"",()->c.blurSamples,v->c.blurSamples=v);
            s.toggle("Only while moving","Avoid trails when you stand still",()->c.blurMovingOnly,v->c.blurMovingOnly=v);
            s.slider("Movement tail","How long blur lingers after a turn or movement stops",0,350,5," ms",()->c.blurMotionTail,v->c.blurMotionTail=v);
            s.section("Advanced");
            s.slider("Capture rate","GPU texture copies per second",15,120,1," FPS",()->c.blurFps,v->c.blurFps=v);
            s.toggle("FPS protection","Pause the effect below the FPS threshold",()->c.blurAutoProtect,v->c.blurAutoProtect=v);
            s.slider("Minimum FPS","",15,120,1," FPS",()->c.blurMinFps,v->c.blurMinFps=v);
            s.slider("GPU memory budget","History automatically shrinks to fit this budget",16,256,8," MB",()->c.blurMemoryMb,v->c.blurMemoryMb=v);
            s.info("GPU history","No CPU framebuffer readbacks",ShoreMotionBlur::status);
        });
        mod("crystals","End Crystals","Customize the crystal model appearance without changing hit detection.","PvP","cube",true,()->c.customCrystals,v->c.customCrystals=v,s->{
            s.section("Color");
            s.color("Crystal color","Recolors the active resource pack texture",()->c.crystalColor,v->c.crystalColor=v);
            s.toggle("Rainbow","Cycle hue at a bounded update rate",()->c.crystalRainbow,v->c.crystalRainbow=v);
            s.segment("Style","",new String[]{"Textured","Flat tint","Ghost"},()->c.crystalStyle,v->c.crystalStyle=v);
            s.slider("Opacity","",5,100,1,"%",()->c.crystalOpacity,v->c.crystalOpacity=v);
            s.section("Model");
            s.slider("Visual size","Does not change the server hitbox",25,200,1,"%",()->c.crystalSize,v->c.crystalSize=v);
            s.slider("Animation speed","Spin and bob timing; zero freezes animation",0,300,1,"%",()->c.crystalSpeed,v->c.crystalSpeed=v);
            s.slider("Bobbing amount","",0,200,1,"%",()->c.crystalBob,v->c.crystalBob=v);
            s.toggle("Base plate","",()->c.crystalBase,v->c.crystalBase=v);
            s.toggle("Beam","",()->c.crystalBeam,v->c.crystalBeam=v);
        });
        mod("stopwatch","Stopwatch","Time runs and fights with a pausable HUD timer.","HUD","clock",true,
            ()->c.stopwatch,v->{c.stopwatch=v;if(!v)ShoreClient.stopwatch.reset();},s->{
                s.section("Stopwatch");
                s.row(WATCH_PREVIEW,"Preview","Current session");
                s.action("Start or stop","Starting a new run clears the previous time","Start / stop",ShoreClient.stopwatch::startStop).available=()->c.stopwatch;
                s.action("Pause or resume","Continue from your previous time","Pause / resume",ShoreClient.stopwatch::pauseResume).available=()->c.stopwatch;
                s.action("Reset","Clear the current time","Clear",ShoreClient.stopwatch::reset);
                s.toggle("Milliseconds","Display three fractional digits",()->c.watchMillis,v->c.watchMillis=v);
                s.section("Appearance");
                s.segment("Display mode","",new String[]{"Background","Brackets","Text"},()->c.watchStyle,v->c.watchStyle=v);
                s.segment("Background width","Dynamic follows the timer. Fixed fits at least the chosen width.",new String[]{"Dynamic","Fixed"},()->c.watchAutoWidth?0:1,v->c.watchAutoWidth=v==0).available=()->c.watchStyle==0;
                s.slider("Fixed width","",30,180,1," px",()->c.watchWidth,v->c.watchWidth=v).available=()->c.watchStyle==0&&!c.watchAutoWidth;
                s.slider("Background height","",10,48,1," px",()->c.watchHeight,v->c.watchHeight=v).available=()->c.watchStyle==0;
                s.color("Background","",()->c.watchBackground,v->c.watchBackground=v).available=()->c.watchStyle==0;
                s.slider("Background opacity","",0,100,1,"%",()->c.watchOpacity,v->c.watchOpacity=v).available=()->c.watchStyle==0;
                s.toggle("Border","",()->c.watchBorder,v->c.watchBorder=v).available=()->c.watchStyle==0;
                s.color("Border color","",()->c.watchEdge,v->c.watchEdge=v).available=()->c.watchStyle==0&&c.watchBorder;
                s.color("Timer text","",()->c.watchText,v->c.watchText=v);
                s.toggle("Text shadow","",()->c.watchShadow,v->c.watchShadow=v);
                s.slider("Element size","",50,250,5,"%",()->c.hudSize("stopwatch"),v->c.hudSizes.put("stopwatch",v));
                s.section("Keys");
                s.keybind("Start / stop","",ShoreClient.watchStart);
                s.keybind("Pause / resume","",ShoreClient.watchPause);
                s.keybind("Reset timer","",ShoreClient.watchReset);
            });
        mod("days","Day Counter","Display the world's day count in your HUD.","HUD","clock",true,()->c.dayCounter,v->c.dayCounter=v,s->{
            s.section("Counter");
            s.field("Label","","Day",c.dayLabel,v->{c.dayLabel=v.substring(0,Math.min(24,v.length()));c.save();});
            s.toggle("Start at day one","Off uses Minecraft's zero-based day count",()->c.dayOneBased,v->c.dayOneBased=v);
            s.slider("Day offset","Adjust for a season reset", -1000000,1000000,1,"",()->c.dayOffset,v->c.dayOffset=v);
            s.toggle("Show in-game time","Use server-supplied time, not your local clock",()->c.dayShowTime,v->c.dayShowTime=v);
            hudSettings.apply("days").accept(s);
        });
        mod("performance","Performance","Reduce recurring rendering and disable framebuffer recording.","Utility","gauge",true,()->c.lowOverhead,v->{c.lowOverhead=v;ShoreClient.recorder.clear();},s->{
            s.info("Recording", "Performance mode skips framebuffer capture entirely",ShoreClient.recorder::status);
            s.toggle("HUD item icons","3D item models cost more than plain text labels",()->c.hudItemIcons,v->c.hudItemIcons=v);
            s.action("Low-overhead preset","Turns off recording and 3D HUD icons","Apply",()->{c.lowOverhead=true;c.recording=false;c.hudItemIcons=false;c.save();ShoreClient.recorder.clear();});
        });
        mod("blockoverlay","Block Overlay","Colored outline and details for the block under your crosshair.","Utility","cube",true,()->c.blockOverlay,v->c.blockOverlay=v,s->{
            s.color("Outline color","RGB hex",()->c.blockColor,v->c.blockColor=v);
            s.slider("Opacity","",0,100,1,"%",()->c.blockOpacity,v->c.blockOpacity=v);
            s.slider("Line width","Screen pixels",1,4,1," px",()->c.outlineWidth,v->c.outlineWidth=v);
            s.toggle("Block information","Name and coordinates near the crosshair",()->c.blockInfo,v->c.blockInfo=v);
        });
        mod("betterf3","Custom F3","Choose each debug field and its color.","Utility","gauge",true,()->c.betterF3,v->c.betterF3=v,ShoreF3::settings);
        mod("moboverlay","Mob Overlay","Outline or tint mob models.","PvP","target",true,()->c.mobOverlay,v->c.mobOverlay=v,ShoreMobOverlay::settings);
        mod("guitweaks","GUI Tweaks","Transparent inventory backgrounds and adjustable Minecraft GUI size.","Inventory","gear",true,()->c.guiTweaks,v->c.guiTweaks=v,s->{
            s.toggle("Clear world background","Remove blur and dimming behind inventory screens",()->c.guiNoDim,v->c.guiNoDim=v);
            s.color("Inventory tint","Color applied only to inventory background textures",()->c.guiTintColor,v->c.guiTintColor=v);
            s.slider("Inventory opacity","Background textures only; items and text stay readable",10,100,1,"%",()->c.inventoryOpacity,v->c.inventoryOpacity=v);
            s.slider("Minecraft GUI scale","Changes vanilla menus, inventory and HUD size",1,6,1,"x",()->MinecraftClient.getInstance().options.getGuiScale().getValue(),v->{var mc=MinecraftClient.getInstance();mc.options.getGuiScale().setValue(v);mc.options.write();mc.onResolutionChanged();});
        });
        mod("hitboxes","Hitboxes","Colored wireframes for nearby visible entities.","PvP","box",true,()->c.coloredHitboxes,v->c.coloredHitboxes=v,s->{
            s.color("Color","RGB hex",()->c.hitboxColor,v->c.hitboxColor=v);
            s.toggle("Players only","Skip mobs and other entities",()->c.hitboxPlayersOnly,v->c.hitboxPlayersOnly=v);
            s.segment("Range mode","Loaded follows entities supplied by the server, without an extra distance cutoff",new String[]{"Loaded entities","Custom range"},()->c.hitboxLoadedRange?0:1,v->c.hitboxLoadedRange=v==0);
            s.slider("Custom range","Used only in Custom range mode",4,256,1,"m",()->c.hitboxRange,v->c.hitboxRange=v);
            s.section("Style");
            s.segment("Shape","Visual only; actual hit detection stays unchanged",new String[]{"Full box","Corners","Ground"},()->c.hitboxStyle,v->c.hitboxStyle=v);
            s.slider("Line thickness","",1,4,1," px",()->c.hitboxWidth,v->c.hitboxWidth=v);
            s.slider("Opacity","",5,100,1,"%",()->c.hitboxOpacity,v->c.hitboxOpacity=v);
            s.toggle("Highlight target","Different color for the entity under your crosshair",()->c.hitboxTargetColor,v->c.hitboxTargetColor=v);
            s.color("Target color","",()->c.hitboxTargetRgb,v->c.hitboxTargetRgb=v);
            s.section("Advanced");
            s.toggle("Eye level","Draw an eye-height reference line",()->c.hitboxEyes,v->c.hitboxEyes=v);
            s.color("Eye-line color","",()->c.hitboxEyeColor,v->c.hitboxEyeColor=v);
            s.toggle("Look direction","Show the direction the entity faces",()->c.hitboxLook,v->c.hitboxLook=v);
            s.color("Look-line color","",()->c.hitboxLookColor,v->c.hitboxLookColor=v);
            s.toggle("Distance fade","Fade distant hitboxes",()->c.hitboxDistanceFade,v->c.hitboxDistanceFade=v);
            s.slider("Entity limit","Visible entities only; keeps work bounded",1,256,1,"",()->c.hitboxLimit,v->c.hitboxLimit=v);
        });
        mod("totemtweaks","Totem Tweaks","Use a lightweight, customizable totem sprite in your hand and on pop.","PvP","totem",true,()->c.totemTweaks,v->c.totemTweaks=v,s->{
            s.slider("Held size","First-person totem sprite",10,200,1,"%",()->c.heldTotemSize,v->c.heldTotemSize=v);
            s.slider("Held opacity","0 hides the held sprite",0,100,1,"%",()->c.heldTotemOpacity,v->c.heldTotemOpacity=v);
            s.slider("Pop size","Size of the pop animation",10,200,1,"%",()->c.popTotemSize,v->c.popTotemSize=v);
            s.slider("Pop opacity","0 hides the pop animation",0,100,1,"%",()->c.popTotemOpacity,v->c.popTotemOpacity=v);
        });
        mod("waypoints","Waypoints","Save here, enter coordinates, and manage local markers.","Utility","pin",true,()->c.waypoints,v->c.waypoints=v,s->{
            s.section("Create a marker");
            s.field("Name","Up to 32 characters","Home",c.waypointName,v->{c.waypointName=v.substring(0,Math.min(32,v.length()));c.save();});
            s.field("Coordinates","Blank uses where you stand; spaces or commas","X Y Z",c.waypointCoordinates,v->{c.waypointCoordinates=v;c.save();});
            s.color("New marker color","",()->c.waypointColor,v->c.waypointColor=v);
            s.action("Save waypoint","Maximum 64 local markers","Save",()->{ShoreVisuals.saveWaypoint();s.build();});
            s.action("Save current position","Ignores typed coordinates","Save here",()->{String old=c.waypointCoordinates;c.waypointCoordinates="";ShoreVisuals.saveWaypoint();c.waypointCoordinates=old;s.build();});
            s.section("Display");
            s.toggle("Show distance","",()->c.waypointDistances,v->c.waypointDistances=v);
            s.toggle("Show coordinates","",()->c.waypointCoordinatesShown,v->c.waypointCoordinatesShown=v);
            s.toggle("Offscreen indicators","Keep an arrow at the screen edge for markers behind you",()->c.waypointOffscreen,v->c.waypointOffscreen=v);
            s.toggle("Tracked marker at top","Pin the selected marker above the crosshair",()->c.waypointCompass,v->c.waypointCompass=v);
            s.toggle("Label background","",()->c.waypointBackground,v->c.waypointBackground=v);
            s.slider("Background opacity","",0,100,1,"%",()->c.waypointOpacity,v->c.waypointOpacity=v);
            s.slider("Visible range","Distance in blocks",16,30000000,16,"m",()->c.waypointRange,v->c.waypointRange=v);
            s.slider("Label size","",50,150,1,"%",()->c.waypointScale,v->c.waypointScale=v);
            s.slider("Maximum labels","Nearest markers first; tracked marker gets priority",1,32,1,"",()->c.waypointMaxVisible,v->c.waypointMaxVisible=v);
            s.toggle("Death waypoint","Save a marker automatically when you die",()->c.waypointAutoDeath,v->c.waypointAutoDeath=v);
            s.section("Saved markers");
            s.toggle("Only this server","Filter the manager list; rendering always respects server/dimension",()->c.waypointOnlyCurrentServer,v->{c.waypointOnlyCurrentServer=v;s.build();});
            for(var w:new ArrayList<>(c.savedWaypoints)){
                if(c.waypointOnlyCurrentServer&&!w.server.equals(ShoreVisuals.scope(MinecraftClient.getInstance())))continue;
                s.section("Marker: "+w.name);
                s.toggle("Visible",w.x+", "+w.y+", "+w.z+" | "+w.dimension,()->w.enabled,v->w.enabled=v);
                s.field("Rename","","Marker",w.name,v->{w.name=v.substring(0,Math.min(32,v.length()));c.save();});
                s.color("Marker color","",()->w.color,v->w.color=v);
                s.action("Track marker","Bring this marker to the top of the screen",w.tracked?"Tracked":"Track",()->{for(var other:c.savedWaypoints)other.tracked=false;w.tracked=true;w.enabled=true;c.waypoints=true;c.save();s.build();});
                s.action("Copy coordinates","Copies X Y Z to your clipboard","Copy",()->MinecraftClient.getInstance().keyboard.setClipboard(w.x+" "+w.y+" "+w.z));
                s.action("Delete marker","Click twice to permanently remove this marker","Delete",()->{if(s.deleteWaypoint==w&&System.currentTimeMillis()<s.deleteWaypointUntil){c.savedWaypoints.remove(w);s.deleteWaypoint=null;c.save();s.build();}else{s.deleteWaypoint=w;s.deleteWaypointUntil=System.currentTimeMillis()+3000;ShoreClient.tell("Click Delete again to remove "+w.name);}});
            }
        });
        mod("fps","FPS","Your frames per second.","HUD","gauge",true,()->c.hudFps,v->c.hudFps=v,hudSettings.apply("fps"));
        mod("ping","Ping","Your latency to the server in milliseconds.","HUD","signal",true,()->c.hudPing,v->c.hudPing=v,hudSettings.apply("ping"));
        mod("coords","Coordinates","Your position and the direction you are facing.","HUD","pin",true,()->c.hudCoords,v->c.hudCoords=v,s->{
            s.section("Readout");
            s.toggle("Facing direction","Add a second line for the compass direction",()->c.coordsFacing,v->c.coordsFacing=v);
            s.toggle("Facing axis","Add the X or Z direction",()->c.coordsAxis,v->c.coordsAxis=v).available=()->c.coordsFacing;
            hudSettings.apply("coords").accept(s);
        });
        mod("biome","Biome","The biome you are standing in.","HUD","tree",true,()->c.hudBiome,v->c.hudBiome=v,hudSettings.apply("biome"));
        mod("clock","Clock","Your local time on screen.","HUD","clock",true,()->c.hudClock,v->c.hudClock=v,s->{
            s.section("Clock");
            s.segment("Time format","Local time",new String[]{"24 hour","12 hour"},()->c.clockTwelveHour?1:0,v->c.clockTwelveHour=v==1);
            s.toggle("Show seconds","",()->c.clockSeconds,v->c.clockSeconds=v);
            hudSettings.apply("clock").accept(s);
        });
        mod("armor","Armor Status","Durability of everything you wear and hold.","HUD","armor",true,()->c.hudArmor,v->c.hudArmor=v,s->{
            s.section("Equipment");
            s.segment("Durability","",new String[]{"Percent","Remaining","Remaining / total"},()->c.armorReadout,v->c.armorReadout=v);
            s.color("Worn equipment","Durability at or below 50%",()->c.armorMidColor,v->c.armorMidColor=v);
            s.color("Low durability","Use for items near breaking",()->c.armorLowColor,v->c.armorLowColor=v);
            hudSettings.apply("armor").accept(s);
        });

        mod("stacking","Message Stacking","Merge repeated chat messages into one line with a count.","Chat","stack",false,()->c.filterDuplicates,v->c.filterDuplicates=v,s->{
            s.section("Counter");
            s.segment("Counter style","How the repeat count is shown",new String[]{"[x2]","(2)","x2"},()->c.stackStyle,v->c.stackStyle=v);
            s.slider("Look back","Merge with a repeat this many messages back",1,20,1,"",()->c.stackLookback,v->c.stackLookback=v);
        });
        mod("timestamps","Timestamps","Show the time beside incoming messages.","Chat","history",false,()->c.timestamps,v->c.timestamps=v,s->{
            s.section("Format");
            s.segment("Clock","",new String[]{"24-hour","12-hour"},()->c.timestamps24?0:1,v->c.timestamps24=v==0);
            s.toggle("Show seconds","Add seconds to each timestamp",()->c.timestampSeconds,v->c.timestampSeconds=v);
        });
        mod("mentions","Mentions","Highlight chat lines that mention your name or words you choose.","Chat","bell",false,()->c.mentionHighlight,v->c.mentionHighlight=v,s->{
            s.section("Alerts");
            s.segment("Highlight color","",new String[]{"Yellow","Accent","Red","Green","Blue","Custom"},()->c.mentionColor,v->c.mentionColor=v);
            s.color("Custom highlight color","Type a hex color for your mention alerts",()->c.mentionCustomColor,v->{c.mentionCustomColor=v;c.mentionColor=5;});
            s.toggle("Mention sound","Play a soft ping when you are mentioned",()->c.mentionSound,v->c.mentionSound=v);
            s.section("Words");
            s.field("Highlight words","Separate words with commas","e.g. diamonds, base, trade",String.join(", ",c.highlightWords),v->{c.highlightWords=parse(v);c.save();});
        });
        mod("chat","Chat Tweaks","Longer chat history and an adjustable background.","Chat","chat",false,()->c.extendedChat,v->c.extendedChat=v,s->{
            s.section("Appearance");
            s.toggle("Chat heads","Show a sender skin beside player messages",()->c.chatHeads,v->c.chatHeads=v);
            s.slider("Head size","Fits inside a normal chat line",4,8,1," px",()->c.chatHeadSize,v->c.chatHeadSize=v);
            s.toggle("Head outer layer","Show hats and the skin outer layer",()->c.chatHeadHat,v->c.chatHeadHat=v);
            s.section("History");
            s.slider("History length","Messages kept when you scroll back",100,20000,100,"",()->c.chatHistoryLimit,v->c.chatHistoryLimit=v);
            s.section("Look");
            s.slider("Background","Darkness of the backdrop behind chat",0,100,1,"%",()->(int)Math.round(c.chatOpacity*100),v->c.chatOpacity=v/100.0);
        });

        mod("mousetweaks","Mouse Tweaks","Drag to spread, collect and move items. Scroll to move one at a time.","Inventory","mouse",false,()->c.mouseTweaks,v->c.mouseTweaks=v,s->{
            s.section("Tweaks");
            s.toggle("Right-click drag","Hold right-click with a stack and sweep to drop one item per slot",()->c.tweakRmb,v->c.tweakRmb=v);
            s.toggle("Left-click collect","Hold left-click on a matching stack and sweep to gather more",()->c.tweakLmb,v->c.tweakLmb=v);
            s.toggle("Shift-drag to move","Hold shift + left-click and sweep to move every stack you touch",()->c.tweakShift,v->c.tweakShift=v);
            s.toggle("Scroll wheel","Scroll down on an item to send one, up to pull one back",()->c.tweakWheel,v->c.tweakWheel=v);
        });
        mod("shulker","Shulker Preview","Integrated contents and lore; Alt+Shift expands, Control locks.","Inventory","box",false,()->c.shulkerPreview,v->c.shulkerPreview=v,s->{
            s.section("Preview");
            s.segment("Show contents","When hovering a shulker box",new String[]{"Hold Shift","Always"},()->c.shulkerMode,v->c.shulkerMode=v);
            s.slider("Size","Scale of the preview",50,200,5,"%",()->c.shulkerScale,v->c.shulkerScale=v);
            s.section("Look");
            s.segment("Border","",new String[]{"Dye color","Accent","None"},()->c.shulkerBorderMode,v->c.shulkerBorderMode=v);
            s.slider("Background opacity","Darkness behind the items",0,100,5,"%",()->c.shulkerBgOpacity,v->c.shulkerBgOpacity=v);
            s.toggle("Show name","The shulker box's name above the items",()->c.shulkerTitle,v->c.shulkerTitle=v);
            s.toggle("Show empty slots","Draw a slot background for empty slots",()->c.shulkerEmptySlots,v->c.shulkerEmptySlots=v);
            s.toggle("Compact item counts","For example, 1.7k instead of 1728",()->c.shulkerCompactCount,v->c.shulkerCompactCount=v);
            s.toggle("Modifier hints","Show Alt+Shift and Control instructions",()->c.shulkerHints,v->c.shulkerHints=v);
            s.toggle("Lock with Left Control","Freeze the tooltip so you can inspect contained items",()->c.shulkerLock,v->c.shulkerLock=v);
            s.toggle("Contained item tooltips","Hover items while the preview is locked",()->c.shulkerItemTooltip,v->c.shulkerItemTooltip=v);
            s.section("Colors");
            s.color("Tooltip background","",()->c.shulkerBackgroundColor,v->c.shulkerBackgroundColor=v);
            s.color("Tooltip border","Used with the Dye color option",()->c.shulkerBorderColor,v->c.shulkerBorderColor=v);
            s.color("Grid tint","Undyed boxes; dyed boxes follow their dye",()->c.shulkerGridColor,v->c.shulkerGridColor=v);
        });

        mod("antileak","Anti-Leak","Scramble texture rotation patterns used to locate bases.","Utility","cube",false,()->c.antiLeakTextures,v->{
            c.antiLeakTextures=v;if(v)AntiLeakTextures.reshuffle();ShoreClient.recorder.clear();
            if(v)ShoreClient.tell("Texture-pattern scrambling enabled. Terrain, structures and bedrock placement can still reveal your location.");
            var mc=net.minecraft.client.MinecraftClient.getInstance();if(mc.world!=null)mc.worldRenderer.reload();
        },s->{
            s.section("Good to know");
            s.action("New texture pattern","Generate a different pattern right now","Shuffle",()->{
                AntiLeakTextures.reshuffle();var mc=net.minecraft.client.MinecraftClient.getInstance();
                if(c.antiLeakTextures&&mc.world!=null)mc.worldRenderer.reload();
            });
        });

        mod("timechanger","Time Changer","Choose the local sky and lighting while keeping the server clock.","Utility","clock",true,()->c.timeChanger,v->c.timeChanger=v,s->{
            String[] skyTimes={"Morning","Noon","Sunset","Midnight","Custom"};
            s.choice("Time of day","Cycle between sky presets and a custom time",()->skyTimes[c.timePreset],()->{c.timePreset=(c.timePreset+1)%skyTimes.length;c.save();});
            s.slider("Custom time","0 is sunrise, 6000 is noon and 18000 is midnight",0,23999,1," ticks",()->c.timeCustom,v->{c.timeCustom=v;c.timePreset=4;});
        });
        mod("weather","Weather Changer","Choose local precipitation, intensity and color.","Utility","cube",true,()->c.weatherChanger,v->c.weatherChanger=v,s->{
            s.segment("Weather","Default follows the server",new String[]{"Default","Clear","Rain","Thunder"},()->c.weatherMode,v->c.weatherMode=v);
            s.slider("Intensity","Applies to rain and thunder presets",0,100,1,"%",()->c.weatherIntensity,v->c.weatherIntensity=v);
            s.color("Rain and snow color","Tints precipitation in wet and snowy biomes",()->c.weatherTint,v->c.weatherTint=v);
        });
        mod("fov","FOV Changer","Tune your field of view while moving or drawing a bow.","PvP","zoom",true,()->c.fovChanger,v->c.fovChanger=v,s->{
            s.toggle("Custom base FOV","Use a ShoreMod base angle",()->c.customBaseFov,v->c.customBaseFov=v);
            s.slider("Base angle","Applies when Custom base FOV is on",30,110,1," deg",()->c.baseFov,v->c.baseFov=v);
            s.section("Dynamic FOV");
            String widen="Positive widens your view, zero keeps the usual effect";
            String narrow="Positive narrows your view, zero keeps the usual effect";
            s.slider("Bow zoom",narrow,-50,50,1,"%",()->c.bowFov,v->c.bowFov=v);
            s.slider("Sprinting",widen,-50,50,1,"%",()->c.sprintFov,v->c.sprintFov=v);
            s.slider("Flying",widen,-50,50,1,"%",()->c.flyingFov,v->c.flyingFov=v);
            s.slider("Slowness",narrow,-50,50,1,"%",()->c.slownessFov,v->c.slownessFov=v);
            s.slider("Swiftness",widen,-50,50,1,"%",()->c.swiftnessFov,v->c.swiftnessFov=v);
        });
        mod("itemphysics","Item Physics","Let dropped items tumble through the air and settle on the ground.","Inventory","cube",true,()->c.itemPhysics,v->c.itemPhysics=v,s->{
            s.slider("Rotation speed","Zero stops the spin",0,300,1,"%",()->c.dropSpin,v->c.dropSpin=v);
            s.toggle("Airborne tumble","Tilt items as they fall",()->c.dropTumble,v->c.dropTumble=v);
            s.toggle("Settle on landing","Items rest on the ground and blocks stay upright",()->c.dropSettle,v->c.dropSettle=v);
        });
        mod("fullbright","Brightness / Fullbright","Tune brightness independently of your Minecraft options.","Utility","gauge",true,()->c.fullbright,v->c.fullbright=v,s->{
            s.section("Brightness");
            s.segment("Mode","Boost adds to your game setting. Fixed uses a chosen brightness.",new String[]{"Boost","Fixed"},()->c.brightnessMode,v->c.brightnessMode=v);
            s.slider("Brightness boost","0 keeps normal lighting; 100 gives the strongest boost",0,100,1,"%",()->c.fullbrightStrength,v->c.fullbrightStrength=v);
            s.slider("Fixed brightness","100% matches Minecraft's brightest regular setting",0,1600,1,"%",()->c.brightnessFixed,v->c.brightnessFixed=v);
        });
        mod("zoom","Zoom","Zoom in and out of objects. Scroll to zoom further.","Utility","zoom",true,()->c.zoomEnabled,v->c.zoomEnabled=v,s->{
            s.section("General");
            s.segment("Zoom mode","Hold the key, or press once to zoom and again to stop",new String[]{"Hold","Toggle"},()->c.zoomToggle?1:0,v->c.zoomToggle=v==1);
            s.keybind("Zoom key","Change it in Controls",ShoreTools.zoomKey);
            s.slider("Start zoom level","How far a press zooms in",2,20,1,"x",()->c.zoomLevel,v->c.zoomLevel=v);
            s.section("Sensitivity");
            Row mode=s.segment("Sensitivity mode","Dynamic slows the mouse more the further you zoom",new String[]{"Static","Dynamic"},()->c.zoomSensMode,v->c.zoomSensMode=v);
            s.slider("Static sensitivity","Mouse speed while zoomed, in Static mode",5,100,5,"%",()->c.zoomSensitivity,v->c.zoomSensitivity=v).available=()->c.zoomSensMode==0;
            s.toggle("Smooth movement","Cinematic camera while zoomed",()->c.zoomCinematic,v->c.zoomCinematic=v);
            s.section("Scroll");
            Row scroll=s.toggle("Scroll zoom","Use the mouse wheel while zooming",()->c.zoomScroll,v->c.zoomScroll=v);
            s.slider("Scroll sensitivity","How much each wheel step zooms",5,60,5,"%",()->c.zoomScrollSpeed,v->c.zoomScrollSpeed=v).dependsOn(scroll);
            s.section("Smooth");
            s.toggle("Smooth zoom","Ease in and out instead of snapping",()->c.zoomSmooth,v->c.zoomSmooth=v);
        });
        mod("cps","CPS","Clicks per second, shown in the HUD.","PvP","click",true,()->c.cps,v->c.cps=v,s->{
            s.section("Display");
            s.toggle("Show right clicks","Display left | right clicks per second",()->c.cpsRight,v->c.cpsRight=v);
            hudSettings.apply("cps").accept(s);
        });
        mod("togglesprint","Toggle Sprint","Press sprint or sneak once instead of holding it.","Utility","sprint",true,()->c.toggleSprint,v->{
            c.toggleSprint=v;var o=net.minecraft.client.MinecraftClient.getInstance().options;o.getSprintToggled().setValue(v);o.write();
        },s->{
            s.section("Toggle");
            s.toggle("Toggle sneak","Press sneak once to keep sneaking",()->c.toggleSneak,v->{c.toggleSneak=v;var o=net.minecraft.client.MinecraftClient.getInstance().options;o.getSneakToggled().setValue(v);o.write();});
            s.toggle("Show in HUD","Shows \"Sprinting (Toggled)\" while active",()->c.toggleSprintHud,v->c.toggleSprintHud=v);
            hudSettings.apply("togglesprint").accept(s);
        });
        mod("crosshair","Custom Crosshair","Your own crosshair: a shape you design or a picture you choose.","Utility","crosshair",true,()->c.crosshair,v->c.crosshair=v,s->{
            s.action("Paint your crosshair","Draw a 32x32 crosshair with colors, erasing and undo","Paint",()->client.setScreen(new CrosshairEditorScreen(s)));
            s.section("Style");
            s.segment("Type","",new String[]{"Shape","Image"},()->c.crosshairMode,v->{c.crosshairMode=v;c.save();s.build();});
            s.slider("Opacity","How see-through the crosshair is",10,100,5,"%",()->c.crosshairOpacity,v->c.crosshairOpacity=v);
            if(c.crosshairMode==1){
                s.section("Image");
                s.info("Picture",c.crosshairImage.isBlank()?"PNG or JPEG; large images shrink automatically. Drop files here too.":"Saved in config/shoremod/crosshairs",
                    ()->c.crosshairImage.isBlank()?"None chosen":java.nio.file.Path.of(c.crosshairImage).getFileName().toString());
                s.action("Choose image","Import a PNG or JPEG; also supports dropping files onto this page","Browse",s::chooseCrosshairImage);
                s.slider("Image size","Width and height on screen",4,64,1," px",()->c.crosshairImageSize,v->c.crosshairImageSize=v);
                s.toggle("Tint with color","Color the picture with the color below",()->c.crosshairTint,v->c.crosshairTint=v);
                s.action("Crosshairs folder","Open the folder your pictures are copied to","Open",()->{
                    try{var dir=ShoreHud.CrosshairImage.folder();java.nio.file.Files.createDirectories(dir);Util.getOperatingSystem().open(dir);}catch(Exception e){ShoreClient.tellError(e.getMessage());}
                });
            }else{
                s.section("Shape");
                s.slider("Length","Length of each arm",1,15,1," px",()->c.crosshairLength,v->c.crosshairLength=v);
                s.slider("Gap","Space between the arms and the center",0,10,1," px",()->c.crosshairGap,v->c.crosshairGap=v);
                s.slider("Thickness","Width of each arm",1,4,1," px",()->c.crosshairThickness,v->c.crosshairThickness=v);
                s.toggle("Center dot","A dot in the middle",()->c.crosshairDot,v->c.crosshairDot=v);
                s.toggle("Outline","Draw an edge around the shape",()->c.crosshairOutline,v->c.crosshairOutline=v);
                s.color("Outline color","",()->c.crosshairOutlineColor,v->c.crosshairOutlineColor=v).available=()->c.crosshairOutline;
                s.slider("Outline opacity","Relative to crosshair opacity",0,100,1,"%",()->c.crosshairOutlineOpacity,v->c.crosshairOutlineOpacity=v).available=()->c.crosshairOutline;
            }
            s.section("Color");
            s.segment("Color","",ShoreHud.CROSSHAIR_NAMES,()->c.crosshairColor,v->c.crosshairColor=v);
            s.color("Custom crosshair color","Preview your own RGB color while playing",()->c.crosshairCustomColor,v->{c.crosshairCustomColor=v;c.crosshairColor=6;});
        });
        mod("durability","Durability Alert","A sound and message before your tools or armor break.","Utility","pickaxe",true,()->c.durabilityAlert,v->c.durabilityAlert=v,s->{
            s.section("Alert");
            s.slider("Warn at","Remaining durability that triggers the alert",1,50,1,"%",()->c.durabilityThreshold,v->c.durabilityThreshold=v);
            s.toggle("Sound","Play a warning sound",()->c.durabilitySound,v->c.durabilitySound=v);
            s.segment("Show in","",new String[]{"Action bar","Chat"},()->c.durabilityChat?1:0,v->c.durabilityChat=v==1);
        });
        mod("deaths","Death Log","Remember where you died. Type /shore deaths to list them.","Utility","skull",true,()->c.deathLog,v->c.deathLog=v,s->{
            s.section("Messages");
            s.toggle("Announce in chat","Tell you where you died right after it happens",()->c.deathAnnounce,v->c.deathAnnounce=v);
            s.section("History");
            s.info("Last death",c.deaths.isEmpty()?"Nothing recorded yet":c.deaths.getFirst().split("  ·  ")[0],()->{
                if(c.deaths.isEmpty())return "None";
                String[] p=c.deaths.getFirst().split("  ·  ");return p.length>2?p[2]+"  "+p[1]:c.deaths.getFirst();});
            s.info("Saved deaths","The last 10 are kept",()->String.valueOf(c.deaths.size()));
            if(!c.deaths.isEmpty())s.action("Clear history","Forget every saved death location","Clear",()->{c.deaths.clear();c.save();s.build();});
        });
        mod("reconnect","Reconnect","Adds a reconnect button to the disconnect screen.","Utility","reconnect",true,()->c.reconnectButton,v->c.reconnectButton=v,s->{
            s.section("Automatic");
            Row auto=s.toggle("Auto-reconnect","Skipped after bans or punishment messages",()->c.autoReconnect,v->c.autoReconnect=v);
            s.slider("Delay","Wait before reconnecting",3,120,1,"s",()->c.autoReconnectDelay,v->c.autoReconnectDelay=v).dependsOn(auto);
        });
        mod("logos","Shore Logos","Show the ShoreSMP mark beside other ShoreMod players.","Utility","@logo",false,()->c.badges,v->c.badges=v,s->{
            s.info("Server presence","Badges require the companion plugin on this backend",ShoreClient::presenceStatus);
            s.action("Refresh badges","Retry the presence handshake without reconnecting","Refresh",ShoreClient::resyncPresence);
            s.section("Show on");
            s.toggle("Nametags","Beside player names in the world",()->c.badgeNametags,v->c.badgeNametags=v);
            s.toggle("Tab list","Beside names in the player list",()->c.badgeTab,v->c.badgeTab=v);
        });

        mod("clips","Clip Recorder","Keep the last 30 seconds so you can save punishments and moments.","Clips","record",false,()->c.recording,v->{c.recording=v;if(v)c.lowOverhead=false;ShoreClient.recorder.clear();},s->{
            s.info("Recorder status","Recording is opt-in; menus are never captured",ShoreClient.recorder::status);
            s.section("Video");
            s.toggle("Protect FPS","Adaptive capture rate; pauses on very slow frames and menus",()->c.recordingProtectFps,v->{c.recordingProtectFps=v;ShoreClient.recorder.clear();});
            s.slider("Frame rate","Up to 60 FPS. Above 30 disables FPS protection and uses more GPU/CPU",5,60,1," FPS",()->c.clipFps,v->{c.clipFps=v;if(v>30)c.recordingProtectFps=false;ShoreClient.recorder.clear();});
            s.slider("Resolution","Video width, bounded to 320–1920 pixels",320,1920,20," px",()->c.clipWidth,v->{c.clipWidth=v;ShoreClient.recorder.clear();});
            s.slider("Clip length","Maximum gameplay history to keep",5,30,1," s",()->c.clipSeconds,v->c.clipSeconds=v);
            s.slider("Frame quality","Sharper frames use more memory in the rolling buffer",40,95,1,"%",()->c.clipJpegQuality,v->c.clipJpegQuality=v);
            s.keybind("Save a clip","Save your buffered gameplay now",ShoreClient.clipKey);
            s.toggle("Open saved clip menu","Open the clip actions after an export finishes",()->c.clipPopup,v->c.clipPopup=v);
            s.section("Auto-save");
            s.toggle("Save on every disconnect","When off, only punishment disconnects are saved",()->c.saveEveryDisconnect,v->c.saveEveryDisconnect=v);
            s.field("Punishment phrases","Messages containing these save a clip. Separate with commas.","e.g. you have been banned",String.join(", ",c.punishmentPhrases),v->{c.punishmentPhrases=parse(v);c.save();});
            s.section("Storage");
            s.slider("Storage limit","Maximum clip storage in megabytes",128,16384,128," MB",()->c.maxClipStorageMb,v->c.maxClipStorageMb=v);
            s.action("Clips folder","Browse the videos saved on this computer","Open",()->{try{java.nio.file.Files.createDirectories(ShoreClient.recorder.directory());Util.getOperatingSystem().open(ShoreClient.recorder.directory());}catch(Exception e){ShoreClient.tellError(e.getMessage());}});
        });
        // ---- PvP
        mod("keystrokes","Keystrokes","Shows WASD, your mouse buttons with CPS, and the space bar.","PvP","keyboard",true,()->c.keystrokes,v->c.keystrokes=v,s->{
            s.section("Keys");
            s.toggle("Mouse buttons","Left and right click with clicks per second",()->c.keystrokesMouse,v->c.keystrokesMouse=v);
            s.toggle("Space bar","Show the jump key",()->c.keystrokesSpace,v->c.keystrokesSpace=v);
            s.segment("Pressed color","",new String[]{"White","Accent"},()->c.keystrokesAccent?1:0,v->c.keystrokesAccent=v==1);
            hudSettings.apply("keystrokes").accept(s);
        });
        mod("target","Target HUD","Name and health of the player or mob you are fighting.","PvP","target",true,()->c.targetHud,v->c.targetHud=v,s->{
            s.section("Display");
            s.toggle("Show distance","How far away your target is",()->c.targetDistance,v->c.targetDistance=v);
            hudSettings.apply("target").accept(s);
        });
        mod("potions","Potion Effects","Active effects with their level and time left.","PvP","potion",true,()->c.potionHud,v->c.potionHud=v,hudSettings.apply("potions"));
        mod("items","Item Counter","How many totems, pearls, gapples and crystals you are carrying.","PvP","items",true,()->c.itemCounter,v->c.itemCounter=v,s->{
            s.section("Count");
            s.toggle("Totems","",()->c.itemsTotem,v->c.itemsTotem=v);
            s.toggle("Ender pearls","",()->c.itemsPearl,v->c.itemsPearl=v);
            s.toggle("Golden apples","",()->c.itemsGapple,v->c.itemsGapple=v);
            s.toggle("End crystals","",()->c.itemsCrystal,v->c.itemsCrystal=v);
            s.toggle("Arrows","",()->c.itemsArrow,v->c.itemsArrow=v);
            s.toggle("Bottles o' enchanting","",()->c.itemsXp,v->c.itemsXp=v);
            s.toggle("Show when zero","Keep showing items you have none of",()->c.itemsShowZero,v->c.itemsShowZero=v);
            s.section("Your own items");
            s.field("Extra items","Item ids separated by commas","e.g. cobweb, obsidian, water_bucket",String.join(", ",c.itemsCustom),v->{c.itemsCustom=parse(v);c.save();});
            hudSettings.apply("items").accept(s);
        });
        mod("totempops","Totem Pop Counter","Tells you in chat when nearby players pop a totem, and how many.","PvP","totem",true,()->c.totemPops,v->c.totemPops=v,s->{
            s.section("Messages");
            s.toggle("Include yourself","Also count your own totem pops",()->c.totemPopsSelf,v->c.totemPopsSelf=v);
        });
        modules.sort(Comparator.comparing(Module::name,String.CASE_INSENSITIVE_ORDER));
    }
    private void mod(String id,String name,String desc,String cat,String icon,boolean isNew,BooleanSupplier get,Consumer<Boolean> set,Consumer<ShoreScreen> settings){
        modules.add(new Module(id,name,desc,cat,icon,isNew,get,set,settings));
    }
    private boolean favorite(Module m){return ShoreClient.config.favorites.contains(m.id());}
    private void toggleFavorite(Module m){var f=ShoreClient.config.favorites;if(!f.remove(m.id()))f.add(m.id());ShoreClient.config.save();}
    private void toggleModule(Module m){m.set().accept(!m.get().getAsBoolean());ShoreClient.config.save();}
    private List<Module> visibleModules(){
        String q=search.text.strip().toLowerCase(Locale.ROOT);
        return modules.stream().filter(m->category.equals("All")||(category.equals("New")?m.isNew():m.category().equals(category)))
            .filter(m->!favoritesOnly||favorite(m))
            .filter(m->q.isEmpty()||(m.name()+" "+m.desc()+" "+m.category()).toLowerCase(Locale.ROOT).contains(q)).toList();
    }

    // ------------------------------------------------------------------ pages & rows
    void showGrid(String cat,String q){page=Page.GRID;category=cat;search.set(q,false);scroll=scrollTarget=0;build();}
    void showModule(String id){for(Module m:modules)if(m.id().equals(id)){openModule(m);return;}}
    void showPage(String name){openPage(Page.valueOf(name));}
    private void openModule(Module m){current=m;page=Page.MODULE;scroll=scrollTarget=0;focused=null;build();}
    private void openPage(Page p){page=p;current=null;scroll=scrollTarget=0;focused=null;build();}

    @Override protected void init(){build();}
    @Override public void tick(){}

    void build(){
        rows.clear();keyClicks.clear();switchAnim.clear();settingsLayout=null;listening=null;pendingSection=null;hexInput=null;
        Config c=ShoreClient.config;
        switch(page){
            case MODULE -> {
                if(current.settings()!=null)current.settings().accept(this);
                String id=current.id();
                section("Keybind");
                Row tk=row(KEY,"Toggle key","Press it in game to switch "+current.name()+" on or off");
                tk.keyGet=()->{String t=c.moduleKeys.get(id);return t==null?net.minecraft.client.util.InputUtil.UNKNOWN_KEY:net.minecraft.client.util.InputUtil.fromTranslationKey(t);};
                tk.keySet=k->{if(k.equals(net.minecraft.client.util.InputUtil.UNKNOWN_KEY))c.moduleKeys.remove(id);else c.moduleKeys.put(id,k.getTranslationKey());c.save();};
            }
            case GENERAL -> {
                section("Theme");
                Row r=row(ACCENT,"Accent color","Used for tabs, switches and highlights");
                hexInput=new Input("RRGGBB",String.format("%06X",c.accent),7,v->{if(v.matches("#?[0-9a-fA-F]{6}")){c.accent=Integer.parseInt(v.replace("#",""),16);c.save();}});
                r.input=hexInput;
                slider("Panel opacity","How much of the world shows through this menu",20,100,1,"%",()->c.panelOpacity,v->c.panelOpacity=v);
                slider("GUI size","Shrink the entire panel while keeping it centered",50,150,1,"%",()->c.menuScale,v->c.menuScale=v);
                slider("Font size","Text size independent of the panel size",75,125,1,"%",()->c.menuFontScale,v->c.menuFontScale=v);
                slider("Payment dialog size","Size of pay-all and other payment confirmations",50,100,1,"%",()->c.paymentScale,v->c.paymentScale=v);
                section("Menu appearance");
                color("Panel color","Dark background of the settings window",()->c.menuBackgroundColor,v->c.menuBackgroundColor=v);
                slider("World dimming","Darken the world while this menu is open",0,90,1,"%",()->c.menuShade,v->c.menuShade=v);
                toggle("Two-column settings","Use a denser layout; off gives each setting its own row",()->c.menuTwoColumns,v->c.menuTwoColumns=v);
                slider("Card width","Preferred width of each module on the grid",150,260,5," px",()->c.menuCardWidth,v->c.menuCardWidth=v);
                slider("Scroll distance","Distance per mouse wheel step",12,96,1," px",()->c.menuScrollStep,v->c.menuScrollStep=v);
                toggle("Category icon colors","Give each module category a different color",()->c.menuCategoryColors,v->c.menuCategoryColors=v);
                section("Palette");
                color("Card surface","Cards, tabs and controls",()->c.menuSurface,v->c.menuSurface=v);
                color("Card border","Card outlines and inactive controls",()->c.menuEdge,v->c.menuEdge=v);
                color("Enabled controls","Active toggles and buttons",()->c.menuEnabledColor,v->c.menuEnabledColor=v);
                section("Background image");
                action("Choose a background","Choose a local PNG or drag it onto General settings","Browse",()->chooseImage(true));
                info("Image status","PNG files up to 24 MiB and 24 megapixels",ShoreMenuBackground::status);
                toggle("Show image","Use the imported background in the ShoreMod menu",()->c.menuImage,v->{c.menuImage=v;if(!v)ShoreMenuBackground.clear();});
                segment("Image fit","",new String[]{"Cover","Fit","Stretch"},()->c.menuImageFit,v->c.menuImageFit=v);
                slider("Image opacity","",0,100,1,"%",()->c.menuImageOpacity,v->c.menuImageOpacity=v);
                color("Image tint","White preserves the image colors",()->c.menuImageTint,v->c.menuImageTint=v);
                section("HUD appearance");
                color("HUD box color","Background color of HUD boxes",()->c.hudBackgroundColor,v->c.hudBackgroundColor=v);
                toggle("HUD borders","Thin colored top and bottom edges",()->c.hudBorder,v->c.hudBorder=v);
                color("Border color","RGB hex",()->c.hudBorderColor,v->c.hudBorderColor=v);
                slider("HUD padding","Space inside each readout",2,12,1," px",()->c.hudPadding,v->c.hudPadding=v);
                slider("HUD spacing","Gap between HUD boxes",0,12,1," px",()->c.hudGap,v->c.hudGap=v);
                slider("Screen margin","Distance from the chosen corner",0,40,1," px",()->c.hudMargin,v->c.hudMargin=v);
                section("Reset");
                action("Reset all settings","Restore every ShoreMod option to its default","Reset",this::resetAll);
            }
            case CONTROLS -> {
                section("ShoreMod keys");
                keybind("Open ShoreMod","This menu",ShoreClient.menuKey);
                keybind("Zoom","Hold to zoom in",ShoreTools.zoomKey);
                keybind("Waypoints","Create or manage your markers",ShoreClient.waypointKey);
                keybind("Save a clip","Save the last 30 seconds",ShoreClient.clipKey);
                keybind("Stopwatch start / stop","",ShoreClient.watchStart);
                keybind("Stopwatch pause / resume","",ShoreClient.watchPause);
                keybind("Reset stopwatch","",ShoreClient.watchReset);
                section("Commands");
                info("/shore","Open this menu",()->"");
                info("/shore deaths","List your recent death locations",()->"");
                info("/shoremod giveaway [code]","Host a giveaway or enter with the host's code",()->"");
                info("/shoremod tictactoe","Challenge another ShoreMod player",()->"");
                info("/shoremod clip","Save the gameplay buffered by Clip Recorder",()->"");
            }
            default -> {}
        }
    }
    void section(String title){pendingSection=title;}
    private Row row(int kind,String title,String help){
        Row r=new Row();r.kind=kind;r.title=title;r.help=help;r.section=pendingSection;pendingSection=null;rows.add(r);return r;
    }
    Row toggle(String title,String help,BooleanSupplier get,Consumer<Boolean> set){Row r=row(TOGGLE,title,help);r.get=get;r.set=set;return r;}
    Row choice(String title,String help,Supplier<String> value,Runnable next){Row r=row(CHOICE,title,help);r.value=value;r.action=next;return r;}
    Row action(String title,String help,String button,Runnable act){Row r=row(ACTION,title,help);r.value=()->button;r.action=act;return r;}
    /** A Minecraft key binding, rebound in place: click the box, then press a key. */
    Row keybind(String title,String help,KeyBinding key){
        Row r=row(KEY,title,help);
        r.keyGet=()->key==null?net.minecraft.client.util.InputUtil.UNKNOWN_KEY:net.minecraft.client.util.InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey());
        r.keySet=k->{if(key==null)return;key.setBoundKey(k);KeyBinding.updateKeysByCode();client.options.write();};
        return r;
    }
    private void importCrosshair(java.nio.file.Path chosen){
        var thread=new Thread(()->{
            try{
                String imported=ShoreHud.CrosshairImage.importFile(chosen);
                net.minecraft.client.MinecraftClient.getInstance().execute(()->{
                    ShoreHud.CrosshairImage.invalidate();
                    if(ShoreHud.CrosshairImage.texture(imported)==null){ShoreClient.tellError("Could not load the selected image. Try a PNG or JPEG.");return;}
                    Config c=ShoreClient.config;c.crosshairImage=imported;c.crosshairMode=1;c.crosshair=true;c.crosshairTint=false;c.crosshairImageSize=32;c.save();
                    ShoreClient.tell("Crosshair image imported and enabled. Change its size in /shore > Custom Crosshair.");
                    if(page==Page.MODULE)build();
                });
            }catch(Exception e){ShoreClient.tellError("Image import failed: "+e.getMessage());}
        },"ShoreMod image import");thread.setDaemon(true);thread.start();
    }
    @Override public void onFilesDropped(java.util.List<java.nio.file.Path> paths){
        if(current!=null&&current.id().equals("crosshair")&&!paths.isEmpty())importCrosshair(paths.getFirst());
        else if(page==Page.GENERAL&&!paths.isEmpty())ShoreMenuBackground.importImage(paths.getFirst());
        else super.onFilesDropped(paths);
    }
    void chooseCrosshairImage(){chooseImage(false);}
    void chooseImage(boolean background){
        Thread t=new Thread(()->{
            try(var stack=org.lwjgl.system.MemoryStack.stackPush()){
                var filters=stack.mallocPointer(3);filters.put(stack.UTF8("*.png"));
                if(!background)filters.put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg"));filters.flip();
                String chosen=org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(background?"Choose menu background":"Choose a crosshair image","",filters,background?"PNG images":"PNG or JPEG images",false);
                if(chosen!=null){
                    var file=java.nio.file.Path.of(chosen);
                    if(background)ShoreMenuBackground.importImage(file);else importCrosshair(file);
                }
            }catch(Exception|LinkageError e){ShoreClient.tellError(background?"Drop a PNG onto General settings.":"Drop a PNG or JPEG onto the crosshair settings page.");}
        },"ShoreMod file picker");t.setDaemon(true);t.start();
    }
    private Row listening;
    private static String keyLabel(net.minecraft.client.util.InputUtil.Key k){
        return k==null||k.equals(net.minecraft.client.util.InputUtil.UNKNOWN_KEY)?"None":k.getLocalizedText().getString();
    }
    private static ShoreScreen registry;
    /** Toggles a module by id from a toggle key in game. Returns a status message, or null if unknown. */
    static String toggleById(String id){
        if(registry==null)registry=new ShoreScreen(null);
        for(Module m:registry.modules)if(m.id().equals(id)){
            m.set().accept(!m.get().getAsBoolean());ShoreClient.config.save();
            return m.name()+(m.get().getAsBoolean()?" enabled":" disabled");
        }
        return null;
    }
    Row info(String title,String help,Supplier<String> value){Row r=row(INFO,title,help);r.value=value;return r;}
    Row slider(String title,String help,int min,int max,int step,String suffix,IntSupplier get,IntConsumer set){
        Row r=row(SLIDER,title,help);r.min=min;r.max=max;r.step=step;r.suffix=suffix;r.iget=get;r.iset=set;
        r.input=new Input("Number",Integer.toString(get.getAsInt()),Math.max(6,Integer.toString(max).length()),v->{
            var number=net.shoresmp.core.SettingValues.integer(v,min,max);
            if(number.isPresent()&&number.getAsInt()!=get.getAsInt()){set.accept(number.getAsInt());ShoreClient.config.save();}
        });
        r.input.commitOnBlur=true;
        r.input.validation=v->net.shoresmp.core.SettingValues.integer(v,min,max).isPresent()?"":"Enter "+min+"–"+max+suffix;
        r.input.currentValue=()->Integer.toString(get.getAsInt());
        return r;
    }
    /** A row of mutually exclusive buttons, e.g. Hold / Toggle. */
    Row segment(String title,String help,String[] options,IntSupplier get,IntConsumer set){
        Row r=row(SEGMENT,title,help);r.options=options;r.iget=get;r.iset=set;return r;
    }
    private Row activeSlider;
    private float pressX;
    private int sliderDraft;
    private void finishInput(boolean apply){
        Input in=focused;focused=null;
        if(in==null||!in.commitOnBlur)return;
        if(apply&&(in.validation==null||in.validation.apply(in.text).isEmpty()))in.change.accept(in.text);
        if(in.currentValue!=null)in.set(in.currentValue.get(),false);
    }
    private void setSlider(Row r,float x){
        if(r.trackW<=0)return;
        float t=Math.clamp((x-r.trackX)/r.trackW,0f,1f);
        int v=r.min+Math.round(t*(r.max-r.min)/r.step)*r.step;
        sliderDraft=Math.clamp(v,r.min,r.max);
    }
    @Override public boolean mouseDragged(Click click,double dx,double dy){
        if(activeSlider!=null){setSlider(activeSlider,ui.fromGui(click.x()));return true;}
        return super.mouseDragged(click,dx,dy);
    }
    @Override public boolean mouseReleased(Click click){
        if(activeSlider!=null){
            setSlider(activeSlider,ui.fromGui(click.x()));
            Row r=activeSlider;activeSlider=null;
            if(sliderDraft!=r.iget.getAsInt())r.iset.accept(sliderDraft);
            ShoreClient.config.save();return true;
        }
        return super.mouseReleased(click);
    }
    Row field(String title,String help,String placeholder,String value,Consumer<String> changed){
        Row r=row(FIELD,title,help);r.input=new Input(placeholder,value,3000,changed);return r;
    }
    Row color(String title,String help,IntSupplier get,IntConsumer set){
        Row r=field(title,help,"RRGGBB",String.format("%06X",get.getAsInt()),v->{
            var rgb=net.shoresmp.core.SettingValues.color(v);
            if(rgb.isPresent()){set.accept(rgb.getAsInt());ShoreClient.config.save();}
        });
        r.input.max=7;r.input.validation=v->net.shoresmp.core.SettingValues.color(v).isPresent()?"":"Use six hex digits, e.g. 35DBD2";
        r.input.currentValue=()->String.format("%06X",get.getAsInt());return r;
    }
    private void resetAll(){
        if(System.currentTimeMillis()>resetArmedUntil){resetArmedUntil=System.currentTimeMillis()+3000;return;}
        Config c=ShoreClient.config,d=new Config();
        boolean reloadTextures=c.antiLeakTextures;
        for(var f:Config.class.getFields()){
            if(java.lang.reflect.Modifier.isStatic(f.getModifiers())||f.getName().equals("deaths")||f.getName().equals("chatDefaultsVersion")||f.getName().equals("savedWaypoints"))continue;
            try{Object v=f.get(d);f.set(c,v instanceof List<?> l?new ArrayList<>(l):v);}catch(ReflectiveOperationException ignored){}
        }
        c.save();ShoreClient.recorder.clear();ShoreMenuBackground.clear();ShoreClient.stopwatch.reset();
        if(reloadTextures&&client.world!=null)client.worldRenderer.reload();
        resetArmedUntil=0;build();
    }
    private static int next(int[] values,int current){for(int i=0;i<values.length;i++)if(values[i]==current)return values[(i+1)%values.length];return values[0];}
    private static List<String> parse(String v){return new ArrayList<>(Arrays.stream(v.split(",")).map(String::strip).filter(s->!s.isEmpty()).limit(100).toList());}
    private static String formatSize(int mb){return mb>=1024?(mb/1024)+" GB":mb+" MB";}

    // ------------------------------------------------------------------ interaction helpers
    private boolean over(float x,float y,float w,float h){
        if(mx<x||my<y||mx>=x+w||my>=y+h)return false;
        return clip==null||(mx>=clip[0]&&my>=clip[1]&&mx<clip[0]+clip[2]&&my<clip[1]+clip[3]);
    }
    /** Registers a clickable area and reports whether the mouse is over it. */
    private boolean hit(float x,float y,float w,float h,Runnable click){hits.add(new Hit(x,y,w,h,clip,click,null));return over(x,y,w,h);}
    private boolean hitInput(float x,float y,float w,float h,Input input){hits.add(new Hit(x,y,w,h,clip,null,input));return over(x,y,w,h);}

    @Override public boolean mouseClicked(Click click,boolean doubled){
        float px=ui.fromGui(click.x()),py=ui.fromGui(click.y());
        if(listening!=null&&click.button()>=2){ // middle and side mouse buttons can be bound too
            listening.keySet.accept(net.minecraft.client.util.InputUtil.Type.MOUSE.createFromCode(click.button()));listening=null;return true;
        }
        if(click.button()!=0)return true;
        Row wasListening=listening;listening=null;
        if(wasListening!=null){
            // Clicking anywhere cancels; clicking the same box again just stops listening.
            for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).contains(px,py)&&hits.get(i).input()==null&&hits.get(i).click()==keyClick(wasListening))return true;
        }
        for(int i=hits.size()-1;i>=0;i--){
            Hit h=hits.get(i);
            if(!h.contains(px,py))continue;
            if(h.input()!=null){
                if(focused!=h.input())finishInput(true);
                h.input().selectAll=doubled||(focused!=h.input()&&h.input().currentValue!=null);
                focused=h.input();caretEpoch=System.currentTimeMillis();
                placeCaret(h,px);
                return true;
            }
            finishInput(true);
            client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK,1f));
            pressX=px;h.click().run();
            return true;
        }
        finishInput(true);
        return true;
    }
    private void placeCaret(Hit h,float px){
        Input in=h.input();float tx=h.x()+12,best=Float.MAX_VALUE;int at=in.text.length();
        for(int i=0;i<=in.text.length();i++){float d=Math.abs(tx+ui.width(in.text.substring(0,i),false,13)-px);if(d<best){best=d;at=i;}}
        in.cursor=at;
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(maxScroll>0)scrollTarget=Math.clamp(scrollTarget-(float)vertical*ShoreClient.config.menuScrollStep,0,maxScroll);
        return true;
    }
    @Override public boolean keyPressed(KeyInput input){
        int key=input.key();boolean ctrl=(input.modifiers()&GLFW.GLFW_MOD_CONTROL)!=0;
        if(listening!=null){
            Row r=listening;listening=null;
            if(key==GLFW.GLFW_KEY_ESCAPE)return true;
            r.keySet.accept(key==GLFW.GLFW_KEY_BACKSPACE||key==GLFW.GLFW_KEY_DELETE?net.minecraft.client.util.InputUtil.UNKNOWN_KEY:net.minecraft.client.util.InputUtil.fromKeyCode(input));
            return true;
        }
        if(focused!=null){
            Input in=focused;caretEpoch=System.currentTimeMillis();
            switch(key){
                case GLFW.GLFW_KEY_ESCAPE->{if(in.currentValue!=null)in.set(in.currentValue.get(),false);finishInput(false);}
                case GLFW.GLFW_KEY_ENTER,GLFW.GLFW_KEY_KP_ENTER->{if(in.validation==null||in.validation.apply(in.text).isEmpty())finishInput(true);}
                case GLFW.GLFW_KEY_BACKSPACE->in.backspace(ctrl);
                case GLFW.GLFW_KEY_DELETE->in.delete();
                case GLFW.GLFW_KEY_LEFT->{in.selectAll=false;in.cursor=Math.max(0,in.cursor-1);}
                case GLFW.GLFW_KEY_RIGHT->{in.selectAll=false;in.cursor=Math.min(in.text.length(),in.cursor+1);}
                case GLFW.GLFW_KEY_HOME->{in.selectAll=false;in.cursor=0;}
                case GLFW.GLFW_KEY_END->{in.selectAll=false;in.cursor=in.text.length();}
                case GLFW.GLFW_KEY_A->{if(ctrl)in.selectAll=true;}
                case GLFW.GLFW_KEY_V->{if(ctrl)in.insert(client.keyboard.getClipboard());}
                case GLFW.GLFW_KEY_C->{if(ctrl)client.keyboard.setClipboard(in.text);}
                default->{}
            }
            return true;
        }
        if(ctrl&&key==GLFW.GLFW_KEY_F){if(page!=Page.GRID)openPage(Page.GRID);focused=search;return true;}
        if(key==GLFW.GLFW_KEY_ESCAPE){
            if(page==Page.GRID&&!search.text.isEmpty()){search.set("",true);return true;}
            if(page!=Page.GRID){openPage(Page.GRID);return true;}
        }
        return super.keyPressed(input);
    }
    @Override public boolean charTyped(CharInput input){
        if(focused==null||!input.isValidChar())return false;
        focused.insert(input.asString());caretEpoch=System.currentTimeMillis();return true;
    }

    // ------------------------------------------------------------------ render
    @Override public void render(DrawContext ctx,int guiX,int guiY,float delta){
        long now=System.nanoTime();float dt=lastFrame==0?0:Math.min(0.1f,(now-lastFrame)/1e9f);lastFrame=now;frameDt=dt;
        float menuUnit=Math.min(ShoreUi.unit(client,ShoreClient.config.menuScale),Math.min(client.getWindow().getFramebufferWidth()/560f,client.getWindow().getFramebufferHeight()/360f));
        ui.begin(ctx,Math.max(0.1f,menuUnit));
        var window=client.getWindow();
        mx=ui.fromGui(client.mouse.getScaledX(window));my=ui.fromGui(client.mouse.getScaledY(window));
        hits.clear();tooltip=null;clip=null;
        scroll+=(scrollTarget-scroll)*Math.min(1,dt*14);if(Math.abs(scrollTarget-scroll)<0.3f)scroll=scrollTarget;

        updatePalette(ShoreClient.config);
        ShoreMenuBackground.draw(ui,ShoreClient.config);
        ui.rect(0,0,ui.W,ui.H,(Math.round(ShoreClient.config.menuShade*2.55f)<<24));
        float cw=Math.min(ui.W-36,800),cx=(ui.W-cw)/2,totalH=Math.min(ui.H-24,420);
        float barY=(ui.H-totalH)/2,tabsY=barY+40,panelY=tabsY+46,panelH=totalH-86;
        drawTopBar(cx,cw,barY,tabsY);

        ui.round(cx,panelY,cw,panelH,12,ShoreClient.config.panelColor());
        if(page==Page.GRID)drawGrid(cx,panelY,cw,panelH,dt);else drawSettings(cx,panelY,cw,panelH,dt);

        if(tooltip!=null){
            float tw=ui.width(tooltip,false,12)+20,tx=Math.min(mx+12,ui.W-tw-6),ty=my+18;
            ui.outline(tx,ty,tw,26,6,0xFF2A2E34,0xF5121418);
            ui.text(tooltip,false,12,tx+10,ty+7,TEXT);
        }
        ui.end();
    }

    private void drawTopBar(float cx,float cw,float barY,float tabsY){
        int accent=accent();
        ui.icon("@logo",cx,barY-3,36,0);
        var t=LocalDateTime.now();
        String date=t.format(DATE),time=t.format(TIME);
        float dw=ui.width(date,true,13),pw=dw+ui.width(time,false,13)+54,px=cx+52;
        ui.round(px,barY,pw,30,8,TOP);
        int hour=t.getHour();
        ui.icon(hour>=7&&hour<19?"sun":"moon",px+13,barY+8,14,TEXT);
        ui.text(date,true,13,px+35,barY+8,TEXT);
        ui.text(time,false,13,px+35+dw+9,barY+8,MUTED);

        // MOD MENU pill + page tabs
        float x=cx,h=38;
        float pillW=ui.width("MOD MENU",true,13)+64;
        boolean sel=page==Page.GRID||page==Page.MODULE,hov=hit(x,tabsY,pillW,h,()->openPage(Page.GRID));
        ui.round(x,tabsY,pillW,h,9,sel?(hov?ShoreUi.mix(accent,0xFFFFFFFF,0.12f):accent):(hov?TOP_HOVER:TOP));
        int fg=sel?onAccent():TEXT;
        ui.icon("grid",x+20,tabsY+12,14,fg);
        ui.text("MOD MENU",true,13,x+43,tabsY+12.5f,fg);
        x+=pillW+10;
        Object[][] tabs={{"gear","General settings",Page.GENERAL},{"keyboard","Controls",Page.CONTROLS}};
        for(Object[] tab:tabs){
            Page p=(Page)tab[2];
            topButton(x,tabsY,h,(String)tab[0],(String)tab[1],page==p,false,()->openPage(p));

            x+=h+10;
        }
        float right=cx+cw;
        topButton(right-h,tabsY,h,"close","Close",false,true,this::close);
        topButton(right-h*2-10,tabsY,h,"palette","Theme",page==Page.GENERAL,true,()->openPage(Page.GENERAL));
    }
    private void topButton(float x,float y,float s,String icon,String tip,boolean selected,boolean outlined,Runnable action){
        int accent=accent();boolean hov=hit(x,y,s,s,action);
        int fg;
        if(outlined){ui.ring(x,y,s,s,9,accent,hov?ShoreUi.mix(TOP,accent,0.18f):TOP);fg=accent;}
        else if(selected){ui.round(x,y,s,s,9,accent);fg=onAccent();}
        else{ui.round(x,y,s,s,9,hov?TOP_HOVER:TOP);fg=hov?0xFFFFFFFF:TEXT;}
        ui.icon(icon,x+(s-17)/2,y+(s-17)/2,17,fg);
        if(hov)tooltip=tip;
    }

    // ------------------------------------------------------------------ grid
    private void drawGrid(float px,float py,float pw,float ph,float dt){
        int accent=accent();
        float pad=18,ix=px+pad,iw=pw-pad*2,cy=py+pad,ch=32;
        // search + favorites
        float heartX=ix+iw-ch,searchW=Math.clamp(iw*0.18f,120,240),searchX=heartX-10-searchW;
        boolean hov=hit(heartX,cy,ch,ch,()->{favoritesOnly=!favoritesOnly;scroll=scrollTarget=0;});
        ui.round(heartX,cy,ch,ch,8,favoritesOnly?ShoreUi.mix(CHIP,HEART,0.22f):hov?CHIP_HOVER:CHIP);
        ui.icon(favoritesOnly?"heart_filled":"heart",heartX+(ch-15)/2,cy+(ch-15)/2+0.5f,15,favoritesOnly?HEART:hov?TEXT:MUTED);
        if(hov)tooltip="Favorites only";
        drawInput(search,searchX,cy,searchW,ch,"search");
        // chips
        float x=ix;
        for(String cat:CATEGORIES){
            float w=Math.max(44,ui.width(cat,sel0(cat),13)+24);
            if(x+w>searchX-12)break;
            boolean sel=category.equals(cat);
            boolean h=hit(x,cy,w,ch,()->{category=cat;scroll=scrollTarget=0;});
            ui.round(x,cy,w,ch,8,sel?(h?ShoreUi.mix(accent,0xFFFFFFFF,0.12f):accent):h?CHIP_HOVER:CHIP);
            ui.centered(cat,sel,13,x+w/2,cy+(ch-13)/2,sel?onAccent():h?TEXT:MUTED,w-10);
            x+=w+7;
        }

        // cards
        float top=cy+ch+16,viewH=py+ph-10-top,gap=14;
        int cols=Math.max(2,(int)((iw+gap)/(ShoreClient.config.menuCardWidth+gap)));
        float cardW=(iw-8-gap*(cols-1))/cols,cardH=Math.clamp(cardW*0.8f,150,220);
        List<Module> list=visibleModules();
        int rowsN=(list.size()+cols-1)/cols;
        float contentH=rowsN==0?0:rowsN*(cardH+gap)-gap;
        maxScroll=Math.max(0,contentH-viewH+4);scrollTarget=Math.clamp(scrollTarget,0,maxScroll);scroll=Math.clamp(scroll,0,maxScroll);
        clip=new float[]{px,top,pw,viewH};
        ui.scissor(px,top,pw,viewH);
        // Bold titles only if every title fits in bold; otherwise all regular, so the grid stays consistent.
        boolean titleBold=true;for(Module m:modules)if(ui.width(m.name(),true,15)>cardW-74){titleBold=false;break;}
        for(int i=0;i<list.size();i++){
            float x0=ix+(i%cols)*(cardW+gap),y0=top+(i/cols)*(cardH+gap)-scroll;
            if(y0+cardH<top||y0>top+viewH)continue;
            drawCard(list.get(i),x0,y0,cardW,cardH,titleBold);
        }
        if(list.isEmpty()){
            boolean favs=favoritesOnly&&search.text.isBlank();
            float mid=px+pw/2;
            ui.icon(favs?"heart":"search",mid-18,top+50,36,FAINT);
            ui.centered(favs?"No favorites yet":"No mods match",true,15,mid,top+100,MUTED,iw);
            ui.centered(favs?"Click the heart on a mod to pin it here.":"Try another search or category.",false,13,mid,top+124,FAINT,iw);
        }
        ui.endScissor();
        clip=null;
        scrollbar(px+pw-9,top,viewH,contentH);
    }
    private void drawCard(Module m,float x,float y,float w,float h,boolean titleBold){
        boolean on=m.get().getAsBoolean();
        boolean hover=over(x,y,w,h);
        hit(x,y,w,h,()->openModule(m)); // body (registered first so buttons win)
        ui.outline(x,y,w,h,11,hover?EDGE_HOVER:EDGE,hover?CARD_HOVER:CARD);
        float pad=18;
        ui.text(m.name(),titleBold,15,x+pad,y+pad,on?TEXT:MUTED,w-pad*2-34);
        // favorite
        boolean fav=favorite(m);float hs=30,hx=x+w-pad-hs+4,hy=y+12;
        boolean heartHover=hit(hx,hy,hs,hs,()->toggleFavorite(m));
        ui.round(hx,hy,hs,hs,8,heartHover?BTN_HOVER:BTN);
        ui.icon(fav?"heart_filled":"heart",hx+(hs-14)/2,hy+(hs-14)/2+0.5f,14,fav?HEART:heartHover?TEXT:MUTED);
        // icon
        float by=y+h-pad-34,area=by-(y+46),is=Math.min(52,area*0.72f);
        int iconColor=on?0xFFFFFFFF:0xFF5D636B;
        if(ShoreClient.config.menuCategoryColors){
            int color=categoryColor(m.category());
            iconColor=on?color:ShoreUi.mix(CARD,color,0.62f);
        }
        ui.icon(m.icon(),x+(w-is)/2,y+46+(area-is)/2,is,iconColor);
        // settings + toggle
        float gs=34;
        boolean gearHover=hit(x+pad,by,gs,gs,()->openModule(m));
        ui.round(x+pad,by,gs,gs,8,gearHover?BTN_HOVER:BTN);
        ui.icon("gear",x+pad+(gs-15)/2,by+(gs-15)/2,15,gearHover?TEXT:MUTED);
        if(gearHover)tooltip="Settings";
        float tx=x+pad+gs+10,tw=w-pad*2-gs-10;
        boolean toggleHover=hit(tx,by,tw,gs,()->toggleModule(m));
        toggleButton(tx,by,tw,gs,on,toggleHover);
    }
    private void toggleButton(float x,float y,float w,float h,boolean on,boolean hover){
        ui.round(x,y,w,h,8,on?(hover?ON_HOVER:ON):(hover?OFF_HOVER:OFF));
        int label=ON!=0xFF7CC62F && light(ON)?0xFF081923:0xFFFFFFFF;
        ui.centered(on?"Enabled":"Disabled",true,13.5f,x+w/2,y+(h-13.5f)/2,on?label:MUTED,w-10);
    }
    private void scrollbar(float x,float top,float viewH,float contentH){
        if(maxScroll<=0)return;
        float thumb=Math.max(36,viewH*viewH/contentH),ty=top+(viewH-thumb)*scroll/maxScroll;
        ui.round(x,top,4,viewH,2,0x10FFFFFF);
        ui.round(x,ty,4,thumb,2,0x50FFFFFF);
    }

    // ------------------------------------------------------------------ compact settings pages
    private static final float FS=12.5f,ROW_H=42;
    private final Set<String> collapsed=new HashSet<>();
    private static final Map<String,String[]> RESET_FIELDS=Map.ofEntries(
        Map.entry("stopwatch",new String[]{"watchMillis","watchStyle","watchBorder","watchShadow","watchAutoWidth","watchWidth","watchHeight","watchBackground","watchOpacity","watchText","watchEdge"}),
        Map.entry("coords",new String[]{"coordsFacing","coordsAxis"}),
        Map.entry("armor",new String[]{"armorReadout","armorLowColor","armorMidColor"}),
        Map.entry("clock",new String[]{"clockTwelveHour","clockSeconds"}),
        Map.entry("performance",new String[]{"hudItemIcons"}),
        Map.entry("blockoverlay",new String[]{"blockColor","blockOpacity","outlineWidth","blockInfo"}),
        Map.entry("betterf3",new String[]{"debugCoordinates","debugTarget","debugMemory","debugFields","debugColors","debugAnimate","debugFade","debugSlide","debugShadow","debugTwoColumns","debugAnimMs","debugPanelOpacity","debugRefreshMs"}),
        Map.entry("moboverlay",new String[]{"mobAlways","mobMode","mobOutlineColor","mobFillColor","mobFillStrength","mobRange"}),
        Map.entry("guitweaks",new String[]{"inventoryOpacity","guiNoDim","guiTintColor"}),
        Map.entry("motionblur",new String[]{"blurStrength","blurSamples","blurMovingOnly","blurMotionTail","blurFps","blurAutoProtect","blurMinFps","blurMemoryMb"}),
        Map.entry("crystals",new String[]{"crystalColor","crystalRainbow","crystalStyle","crystalOpacity","crystalSize","crystalSpeed","crystalBob","crystalBase","crystalBeam"}),
        Map.entry("days",new String[]{"dayLabel","dayOneBased","dayOffset","dayShowTime"}),
        Map.entry("hitboxes",new String[]{"hitboxColor","hitboxPlayersOnly","hitboxRange","hitboxLoadedRange","hitboxWidth","hitboxOpacity","hitboxLimit","hitboxStyle","hitboxEyes","hitboxLook","hitboxTargetColor","hitboxDistanceFade","hitboxEyeColor","hitboxLookColor","hitboxTargetRgb"}),
        Map.entry("totemtweaks",new String[]{"heldTotemSize","heldTotemOpacity","popTotemSize","popTotemOpacity"}),
        Map.entry("waypoints",new String[]{"waypointColor","waypointRange","waypointScale"}),
        Map.entry("cps",new String[]{"cpsRight"}),Map.entry("keystrokes",new String[]{"keystrokesMouse","keystrokesSpace","keystrokesAccent"}),
        Map.entry("items",new String[]{"itemsTotem","itemsPearl","itemsGapple","itemsCrystal","itemsArrow","itemsXp","itemsShowZero","itemsCustom"}),Map.entry("target",new String[]{"targetDistance"}),Map.entry("totempops",new String[]{"totemPopsSelf"}),Map.entry("togglesprint",new String[]{"toggleSneak","toggleSprintHud"}),
        Map.entry("stacking",new String[]{"stackStyle","stackLookback"}),Map.entry("timestamps",new String[]{"timestamps24","timestampSeconds"}),
        Map.entry("timechanger",new String[]{"timePreset","timeCustom"}),Map.entry("weather",new String[]{"weatherMode","weatherIntensity","weatherTint"}),Map.entry("itemphysics",new String[]{"dropSpin","dropSettle","dropTumble"}),Map.entry("fov",new String[]{"customBaseFov","baseFov","bowFov","sprintFov","flyingFov","slownessFov","swiftnessFov"}),Map.entry("fullbright",new String[]{"fullbrightStrength","brightnessMode","brightnessFixed"}),Map.entry("mentions",new String[]{"mentionSound","highlightWords","mentionColor","mentionCustomColor"}),Map.entry("chat",new String[]{"chatHistoryLimit","chatOpacity","chatHeads","chatHeadSize","chatHeadHat"}),
        Map.entry("mousetweaks",new String[]{"tweakRmb","tweakLmb","tweakShift","tweakWheel"}),Map.entry("shulker",new String[]{"shulkerMode","shulkerScale","shulkerBorderMode","shulkerBgOpacity","shulkerTitle","shulkerEmptySlots"}),
        Map.entry("zoom",new String[]{"zoomToggle","zoomLevel","zoomSensMode","zoomSensitivity","zoomCinematic","zoomScroll","zoomScrollSpeed","zoomSmooth"}),
        Map.entry("durability",new String[]{"durabilityThreshold","durabilitySound","durabilityChat"}),Map.entry("deaths",new String[]{"deathAnnounce"}),
        Map.entry("reconnect",new String[]{"autoReconnect","autoReconnectDelay"}),Map.entry("logos",new String[]{"badgeNametags","badgeTab"}),
        Map.entry("clips",new String[]{"clipFps","clipWidth","clipSeconds","clipJpegQuality","clipPopup","recordingProtectFps","saveEveryDisconnect","punishmentPhrases","maxClipStorageMb"}),
        Map.entry("crosshair",new String[]{"crosshairLength","crosshairGap","crosshairThickness","crosshairDot","crosshairColor","crosshairOutline","crosshairMode","crosshairImageSize","crosshairOpacity","crosshairTint","crosshairCustomColor","crosshairOutlineColor","crosshairOutlineOpacity"}));
    private static final Set<String> HUD_IDS=Set.of("stopwatch","days","fps","ping","coords","biome","clock","armor","cps","togglesprint","keystrokes","target","potions","items");
    /** Restores this module's own settings (not its on/off state) to their defaults. */
    private void resetModule(Module m){
        Config c=ShoreClient.config,d=new Config();
        if(HUD_IDS.contains(m.id()))c.hudSizes.remove(m.id());
        for(String name:RESET_FIELDS.getOrDefault(m.id(),new String[0])){
            try{var f=Config.class.getField(name);Object v=f.get(d);f.set(c,v instanceof List<?> l?new ArrayList<>(l):v);}
            catch(ReflectiveOperationException e){ShoreClient.LOG.warn("Cannot reset {}",name);}
        }
        c.save();build();
    }

    private void drawSettings(float px,float py,float pw,float ph,float dt){
        float pad=18,ix=px+pad,iw=pw-pad*2,hy=py+14,hh=30;
        if(page==Page.MODULE){
            Module m=current;
            boolean back=hit(ix,hy,hh,hh,()->openPage(Page.GRID));
            ui.round(ix,hy,hh,hh,7,back?BTN_HOVER:BTN);
            ui.icon("back",ix+8,hy+8,14,back?TEXT:MUTED);
            if(back)tooltip="Back to mods";
            String reset="Reset Settings";float rw=ui.width(reset,false,FS)+26,rx=ix+iw-rw;
            boolean rh=hit(rx,hy,rw,hh,()->resetModule(m));
            ui.round(rx,hy,rw,hh,7,rh?BTN_HOVER:BTN);
            ui.text(reset,false,FS,rx+13,hy+(hh-FS)/2,rh?TEXT:MUTED);
            float fx=rx-hh-8;boolean fav=favorite(m);
            boolean fh=hit(fx,hy,hh,hh,()->toggleFavorite(m));
            ui.round(fx,hy,hh,hh,7,fh?BTN_HOVER:BTN);
            ui.icon(fav?"heart_filled":"heart",fx+8,hy+8,14,fav?HEART:fh?TEXT:MUTED);
            float lx=ix+hh+12;
            ui.icon(m.icon(),lx,hy+(hh-20)/2,20,m.get().getAsBoolean()?0xFFFFFFFF:MUTED);
            float nx=lx+30;
            ui.text(m.name(),true,14,nx,hy+(hh-14)/2,TEXT);
            float dx=nx+ui.width(m.name(),true,14)+12,maxDesc=fx-dx-70;
            String desc=ui.fit(m.desc(),false,FS,maxDesc);
            ui.text(desc,false,FS,dx,hy+(hh-FS)/2,MUTED);
            float sx=dx+ui.width(desc,false,FS)+12;
            boolean sh=hit(sx,hy+(hh-18)/2,36,18,()->toggleModule(m));
            drawSwitch(sx,hy+hh/2,m.get().getAsBoolean(),true,sh,m.id());
        }else{
            String title=switch(page){case GENERAL->"General";case CONTROLS->"Controls";default->"";};
            String sub=switch(page){case GENERAL->"Theme and menu settings";case CONTROLS->"Keybinds and commands";default->"";};
            String ic=switch(page){case GENERAL->"gear";case CONTROLS->"keyboard";default->"grid";};
            ui.icon(ic,ix+4,hy+(hh-20)/2,20,0xFFFFFFFF);
            ui.text(title,true,14,ix+34,hy+(hh-14)/2,TEXT);
            ui.text(sub,false,FS,ix+34+ui.width(title,true,14)+12,hy+(hh-FS)/2,MUTED,iw-200);
        }
        float top=hy+hh+12,viewH=py+ph-8-top,w=iw-10;
        float contentH=layoutGrid(ix,top,w,false);
        maxScroll=Math.max(0,contentH-viewH+8);scrollTarget=Math.clamp(scrollTarget,0,maxScroll);scroll=Math.clamp(scroll,0,maxScroll);
        clip=new float[]{px,top,pw,viewH};
        ui.scissor(px,top,pw,viewH);
        layoutGrid(ix,top-scroll,w,true);
        ui.endScissor();
        clip=null;
        scrollbar(px+pw-9,top,viewH,contentH+8);
    }
    private static final int WATCH_PREVIEW=9;
    private static boolean wide(Row r){return r.kind==FIELD||r.kind==ACCENT;}
    /** Single-line rows that need the full width, e.g. a choice of more than three buttons. */
    private static boolean full(Row r){return r.kind==WATCH_PREVIEW||wide(r)||(r.kind==SEGMENT&&r.options.length>3);}
    private static float height(Row r){return wide(r)?72:ROW_H;}
    private record SettingsSection(String title,String key,List<Row[]> lines,float cardHeight){}
    private List<SettingsSection> settingsLayout;
    private boolean layoutTwoColumns;
    private List<SettingsSection> settingsSections(){
        boolean twoColumns=ShoreClient.config.menuTwoColumns;
        if(settingsLayout!=null&&layoutTwoColumns==twoColumns)return settingsLayout;
        layoutTwoColumns=twoColumns;
        String pageKey=page==Page.MODULE?current.id():page.name();
        var sections=new ArrayList<SettingsSection>();
        for(int i=0;i<rows.size();){
            String sec=rows.get(i).section!=null?rows.get(i).section:"Settings";
            int j=i+1;while(j<rows.size()&&rows.get(j).section==null)j++;
            List<Row[]> lines=new ArrayList<>();Row pending=null;
            for(int k=i;k<j;k++){
                Row r=rows.get(k);
                if(!twoColumns||full(r)){
                    if(pending!=null){lines.add(new Row[]{pending,null});pending=null;}
                    lines.add(new Row[]{r});
                }else if(pending==null)pending=r;
                else{lines.add(new Row[]{pending,r});pending=null;}
            }
            if(pending!=null)lines.add(new Row[]{pending,null});
            float cardHeight=32;
            for(Row[] line:lines)cardHeight+=height(line[0]);
            sections.add(new SettingsSection(sec,pageKey+"/"+sec,lines,cardHeight));
            i=j;
        }
        settingsLayout=sections;
        return sections;
    }
    /** Draw only intersecting cards. Row grouping is stable until the page or column setting changes. */
    private float layoutGrid(float x,float y0,float w,boolean draw){
        float y=y0;
        int accent=accent();
        for(SettingsSection section:settingsSections()){
            boolean closed=collapsed.contains(section.key());
            if(draw&&y+24>=clip[1]&&y<=clip[1]+clip[3]){
                boolean hh=hit(x,y,w,24,()->{if(!collapsed.remove(section.key()))collapsed.add(section.key());});
                ui.icon(closed?"chevron":"chevron_down",x+2,y+6,12,accent);
                ui.text(section.title(),true,13,x+22,y+(24-13)/2,hh?0xFFFFFFFF:TEXT);
            }
            y+=28;
            if(!closed){
                float pad=20,cardH=section.cardHeight();
                if(draw&&y+cardH>=clip[1]&&y<=clip[1]+clip[3]){
                    ui.outline(x,y,w,cardH,9,EDGE,CARD);
                    float ly=y+pad-4,colGap=36,colW=(w-pad*2-colGap)/2;
                    for(Row[] l:section.lines()){
                        float lh=height(l[0]);
                        if(l.length==1)drawCompactRow(l[0],x+pad,ly,w-pad*2,lh);
                        else{
                            drawCompactRow(l[0],x+pad,ly,colW,lh);
                            if(l[1]!=null)drawCompactRow(l[1],x+pad+colW+colGap,ly,colW,lh);
                        }
                        ly+=lh;
                    }
                }
                y+=cardH+22;
            }
        }
        return y-y0;
    }
    private final Map<String,Float> switchAnim=new HashMap<>();
    private void drawSwitch(float x,float cy,boolean value,boolean on,boolean hover,String key){
        float target=value?1:0;Float a=switchAnim.get(key);
        float an=a==null?target:a+(target-a)*Math.min(1,frameDt*16);if(Math.abs(target-an)<0.01f)an=target;
        switchAnim.put(key,an);
        int track=ShoreUi.mix(TRACK,ON,an);if(!on)track=ShoreUi.mix(track,0xFF15181C,0.55f);
        if(hover)track=ShoreUi.mix(track,0xFFFFFFFF,0.08f);
        ui.round(x,cy-9,36,18,9,track);
        ui.circle(x+9+an*18,cy,13,on?0xFFFFFFFF:0xFF8B929B);
    }
    private void drawCompactRow(Row r,float x,float y,float w,float h){
        if(y+h<clip[1]||y>clip[1]+clip[3])return;
        int accent=accent();
        boolean on=r.available.getAsBoolean();
        float cy=wide(r)?y+12:y+h/2,tx=x+(r.indent>0?12:0);
        String value=r.value==null?"":r.value.get();
        float cw=0,right=x+w;
        boolean labelHover=false;
        switch(r.kind){
            case TOGGLE->{
                cw=36;
                boolean hov=on&&hit(x,y,w,h,()->{r.set.accept(!r.get.getAsBoolean());ShoreClient.config.save();});
                drawSwitch(right-36,cy,r.get.getAsBoolean(),on,hov,"row:"+System.identityHashCode(r));
                labelHover=hov;
            }
            case CHOICE->{
                cw=Math.max(96,ui.width(value,false,FS)+36);float bx=right-cw;
                boolean hov=on&&hit(bx,cy-12,cw,24,r.action);
                ui.outline(bx,cy-12,cw,24,6,hov?0xFF3A3F46:INPUT_EDGE,INPUT);
                ui.text(value,false,FS,bx+10,cy-FS/2,on?TEXT:FAINT,cw-30);
                ui.icon("chevron_down",right-20,cy-5,10,on?MUTED:FAINT);
            }
            case SEGMENT->{
                float sx=right;
                for(int k=r.options.length-1;k>=0;k--){
                    float bw=ui.width(r.options[k],false,FS)+20;sx-=bw;
                    final int idx=k;boolean sel=r.iget.getAsInt()==k;
                    boolean bh=on&&hit(sx,cy-12,bw,24,()->{r.iset.accept(idx);ShoreClient.config.save();});
                    ui.round(sx,cy-12,bw,24,6,sel?(on?accent:CHIP):bh?CHIP_HOVER:CHIP);
                    ui.centered(r.options[k],false,FS,sx+bw/2,cy-FS/2,sel?(on?onAccent():FAINT):on?(bh?TEXT:MUTED):FAINT,bw-4);
                    sx-=5;
                }
                cw=right-sx;
            }
            case SLIDER->{
                float trackW=Math.min(128,w*0.32f);cw=trackW+106;
                float vx=right-cw,tx0=right-trackW-6;r.trackX=tx0;r.trackW=trackW;
                int v=activeSlider==r?sliderDraft:r.iget.getAsInt();float t=(v-r.min)/(float)(r.max-r.min);
                if(focused!=r.input)r.input.set(Integer.toString(v),false);
                if(on)drawInput(r.input,vx,cy-12,76,24,null);
                else{ui.round(vx,cy-12,76,24,5,INPUT);ui.centered(Integer.toString(v),false,12,vx+38,cy-6,FAINT,72);}
                ui.text(r.suffix.strip(),false,10,vx+79,cy-5,on?MUTED:FAINT,22);
                if(over(vx,cy-12,76,24)&&tooltip==null)tooltip="Type any whole number from "+r.min+" to "+r.max+r.suffix+". Enter applies; Esc cancels.";
                boolean sh=on&&hit(tx0-8,cy-12,trackW+16,24,()->{activeSlider=r;sliderDraft=r.iget.getAsInt();setSlider(r,pressX);});
                ui.round(tx0,cy-2,trackW,4,2,TRACK);
                ui.round(tx0,cy-2,Math.max(4,trackW*t),4,2,on?accent:FAINT);
                ui.circle(tx0+trackW*t,cy,activeSlider==r||sh?15:13,on?accent:0xFF8B929B);
            }
            case KEY->{
                boolean listen=listening==r;
                String label=listen?"Press a key...":keyLabel(r.keyGet.get());
                cw=Math.max(96,ui.width(label,false,FS)+24);float bx=right-cw;
                boolean hov=on&&hit(bx,cy-12,cw,24,keyClick(r));
                ui.outline(bx,cy-12,cw,24,6,listen?accent:hov?0xFF3A3F46:INPUT_EDGE,INPUT);
                ui.text(label,false,FS,bx+10,cy-FS/2,listen?accent:on?TEXT:FAINT,cw-16);
                if(hov&&!listen)tooltip="Click, then press a key. Backspace clears.";
            }
            case ACTION->{
                if(r.keycap){
                    cw=Math.max(96,ui.width(value,false,FS)+24);float bx=right-cw;
                    boolean hov=on&&hit(bx,cy-12,cw,24,r.action);
                    ui.outline(bx,cy-12,cw,24,6,hov?0xFF3A3F46:INPUT_EDGE,INPUT);
                    ui.text(value,false,FS,bx+10,cy-FS/2,on?MUTED:FAINT,cw-16);
                }else{
                    boolean armed=value.equals("Reset")&&System.currentTimeMillis()<resetArmedUntil;
                    String label=armed?"Click to confirm":value;
                    cw=Math.max(70,ui.width(label,false,FS)+26);float bx=right-cw;
                    boolean hov=on&&hit(bx,cy-12,cw,24,r.action);
                    int bg=armed?0xFFE5484D:on?(hov?ShoreUi.mix(accent,0xFFFFFFFF,0.12f):accent):CHIP;
                    ui.round(bx,cy-12,cw,24,6,bg);
                    ui.centered(label,false,FS,bx+cw/2,cy-FS/2,armed?0xFFFFFFFF:on?onAccent():FAINT,cw-6);
                }
            }
            case WATCH_PREVIEW->{cw=w*0.55f;ShoreHud.previewStopwatch(ui,right,cy,cw,h-6);}
            case INFO->{if(!value.isEmpty()){cw=Math.min(w*0.5f,ui.width(value,false,FS));ui.text(value,false,FS,right-cw,cy-FS/2,on?MUTED:FAINT,cw+1);}}
            default->{}
        }
        float labelW=(wide(r)?w:w-cw-12)-(tx-x);
        ui.text(r.title,false,FS,tx,cy-FS/2,on?TEXT:FAINT,labelW);
        if(!r.help.isEmpty()&&(labelHover||over(tx,cy-10,Math.min(labelW,ui.width(r.title,false,FS)),20)))tooltip=r.help;
        if(r.kind==FIELD){
            if(on)drawInput(r.input,x,y+28,w,28,null);
            else {
                ui.round(x,y+28,w,28,5,INPUT);
                ui.text(r.input.text,false,13,x+12,y+35,FAINT,w-24);
            }
        }
        if(r.kind==ACCENT){
            float sx=x,sy=y+28;
            for(int c:ACCENTS){
                boolean sel=ShoreClient.config.accent==c;
                boolean sh=hit(sx,sy,26,26,()->{ShoreClient.config.accent=c;ShoreClient.config.save();if(hexInput!=null)hexInput.set(String.format("%06X",c),false);});
                if(sel){ui.circle(sx+13,sy+13,26,0xFFFFFFFF);ui.circle(sx+13,sy+13,22,CARD);}
                else if(sh)ui.circle(sx+13,sy+13,24,0x40FFFFFF);
                ui.circle(sx+13,sy+13,18,0xFF000000|c);
                sx+=32;
            }
            float hw=110;
            ui.text("#",false,FS,x+w-hw-14,sy+13-FS/2,FAINT);
            drawInput(r.input,x+w-hw,sy,hw,26,null);
        }
    }
    private void drawInput(Input in,float x,float y,float w,float h,String icon){
        int accent=accent();boolean f=focused==in;
        boolean hov=hitInput(x,y,w,h,in);
        String error=in.validation==null?"":in.validation.apply(in.text);
        ui.outline(x,y,w,h,8,!error.isEmpty()?0xFFFF626B:f?accent:hov?0xFF3A3F46:INPUT_EDGE,INPUT);
        if(hov&&!error.isEmpty())tooltip=error;
        float right=icon!=null?30:12,tx=x+12,avail=w-12-right,ty=y+(h-13)/2;
        if(icon!=null)ui.icon(icon,x+w-24,y+(h-13)/2,13,f?TEXT:FAINT);
        ui.scissor(x+8,y,w-8-right+4,h); // intersects with any enclosing scissor
        if(in.text.isEmpty()){
            ui.text(in.placeholder,false,13,tx,ty,FAINT);
            if(f&&caretOn())ui.rect(tx,y+8,1.2f,h-16,TEXT);
        }else{
            float caretX=ui.width(in.text.substring(0,in.cursor),false,13);
            float shift=Math.max(0,caretX-avail+4);
            if(in.selectAll&&f)ui.round(tx-2-shift,y+7,Math.min(avail+4,ui.width(in.text,false,13)+4),h-14,3,ShoreUi.alpha(accent,0.35f));
            ui.text(in.text,false,13,tx-shift,ty,TEXT);
            if(f&&caretOn()&&!in.selectAll)ui.rect(tx+caretX-shift,y+8,1.2f,h-16,TEXT);
        }
        ui.endScissor();
    }
    private boolean sel0(String cat){return category.equals(cat);}
    private boolean caretOn(){return ((System.currentTimeMillis()-caretEpoch)/530)%2==0;}

    @Override public void close(){finishInput(true);activeSlider=null;ShoreClient.config.save();client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}

