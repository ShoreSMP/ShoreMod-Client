package net.shoresmp.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import net.shoresmp.core.*;
import org.lwjgl.glfw.GLFW;
import org.slf4j.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.client.gui.screen.Screen;

public final class ShoreClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("ShoreMod");
    public static Config config;
    public static final Roster roster = new Roster();
    public static ClipRecorder recorder;
    public static final PaymentQueue payments = new PaymentQueue();
    public static long lastRosterAt;
    public static long sessionEpoch;
    private static final AtomicReference<Supplier<Screen>> pendingScreen=new AtomicReference<>();
    public static void openNextTick(Supplier<Screen> screen){pendingScreen.set(screen);}
    private static long nextHello;
    private static boolean backendConfirmed;
    public static void resyncPresence(){nextHello=0;}
    public static String presenceStatus(){return lastRosterAt!=0?roster.users().size()+" mod users synced":backendConfirmed?"Server confirmed; waiting for roster":"Waiting for server handshake";}
    private static long nextUpdateCheck;
    public static ModUpdates.Notice latestUpdate;
    private static String announcedUpdate="";
    public static String version(){return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("shoremod_universal").or(()->net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("shoremod")).orElseThrow().getMetadata().getVersion().getFriendlyString();}
    public static boolean updateAvailable(){return latestUpdate!=null&&ModUpdates.newer(latestUpdate.version(),version());}
    public static void checkUpdates(){nextUpdateCheck=0;}
    public static void openUpdate(){if(latestUpdate!=null&&!latestUpdate.url().isEmpty())net.minecraft.util.Util.getOperatingSystem().open(java.net.URI.create(latestUpdate.url()));}
    private Double originalChatOpacity;
    public static KeyBinding menuKey, clipKey,waypointKey;
    static KeyBinding watchStart, watchPause, watchReset;
    static final net.shoresmp.core.Stopwatch stopwatch = new net.shoresmp.core.Stopwatch(System::nanoTime);
    /** Localized name of the key currently bound, e.g. "F8", for hints in the UI. */
    public static String keyName(KeyBinding key){return key==null||key.isUnbound()?"Unbound":key.getBoundKeyLocalizedText().getString();}
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("shoremod", "controls"));
    @Override public void onInitializeClient() {
        config = Config.load(); recorder = new ClipRecorder();
        var menu = menuKey = key("menu", GLFW.GLFW_KEY_RIGHT_SHIFT);
        var clip = clipKey = key("clip", GLFW.GLFW_KEY_F9);
        ShoreF3.registerKeys(this::key);
        waypointKey=key("waypoint",GLFW.GLFW_KEY_M);
        int unbound = InputUtil.UNKNOWN_KEY.getCode();
        watchStart = key("watch_start", unbound);
        watchPause = key("watch_pause", unbound);
        watchReset = key("watch_reset", unbound);
        ShoreTools.zoomKey = key("zoom", GLFW.GLFW_KEY_C);
        ShoreHud.register();
        ShoreTools.register();
        ShorePvp.register();
        PayloadTypeRegistry.playC2S().register(UpdatePayload.ID, UpdatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UpdatePayload.ID, UpdatePayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(UpdatePayload.ID,(payload,context)->{
            try{var notice=ModUpdates.decode(payload.bytes());context.client().execute(()->{
                latestUpdate=notice;
                if(updateAvailable()&&!notice.version().equals(announcedUpdate)){
                    announcedUpdate=notice.version();tell("Update "+notice.version()+" is available. You have "+version()+". Open /shore > Updates.");
                }
            });}catch(IOException e){LOG.warn("Invalid update announcement",e);}
        });
        PayloadTypeRegistry.playC2S().register(ShorePayload.ID, ShorePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShorePayload.ID, ShorePayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ShorePayload.ID, (payload, context) -> {
            try {
                Wire.Message message = Wire.decode(payload.bytes());
                if (message.op() == Wire.HELLO) { backendConfirmed=true;nextHello=0;sessionEpoch++; roster.clear(); lastRosterAt = 0; payments.cancel(false); }
                else if (message.op() == Wire.ROSTER && roster.accept(message)) lastRosterAt = System.nanoTime();
                else if (message.op() == Wire.CLIP) recorder.trigger(message.reason(), true);
            } catch (IOException e) { LOG.warn("Invalid ShoreMod server payload", e); }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            sessionEpoch++; latestUpdate=null;announcedUpdate="";nextUpdateCheck=0;
            backendConfirmed=false;roster.clear(); lastRosterAt = 0; nextHello = 0; payments.cancel(false);
            recorder.beginSession();
            ShoreTools.joined(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            stopwatch.reset();
            sessionEpoch++;ShoreMotionBlur.clear();ShoreCrystals.reload();
            pendingScreen.set(null);latestUpdate=null;announcedUpdate="";
            backendConfirmed=false;payments.cancel(false); roster.clear(); lastRosterAt = 0;
            // Keep the ring briefly: disconnect screen/transfer reasons may arrive after this event.
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if(config.recording && !config.lowOverhead)recorder.trigger(message.getString(), false);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var screen=pendingScreen.getAndSet(null);
            if(screen!=null)client.setScreen(screen.get());
            while (watchStart.wasPressed()) if (config.stopwatch && client.player != null) stopwatch.startStop();
            while (watchPause.wasPressed()) if (config.stopwatch && client.player != null) stopwatch.pauseResume();
            while (watchReset.wasPressed()) stopwatch.reset();
            while (menu.wasPressed()) client.setScreen(new ShoreScreen(client.currentScreen));
            while (clip.wasPressed()) recorder.saveManual();
            long now = System.nanoTime();
            if(client.getNetworkHandler()!=null&&now>=nextUpdateCheck&&ClientPlayNetworking.canSend(UpdatePayload.ID)){
                ClientPlayNetworking.send(new UpdatePayload(ModUpdates.request()));nextUpdateCheck=now+60_000_000_000L;
            }
            if (client.getNetworkHandler() != null && now >= nextHello && (backendConfirmed||ClientPlayNetworking.canSend(ShorePayload.ID))) {
                ClientPlayNetworking.send(new ShorePayload(Wire.hello())); nextHello = now + 10_000_000_000L;
            }
            if (lastRosterAt != 0 && now - lastRosterAt > 35_000_000_000L) { roster.clear(); lastRosterAt = 0; }
            payments.tick(client);
            ShoreTools.tick(client);
            ShorePvp.tick(client);
            ShoreVisuals.tick(client);
            while(waypointKey.wasPressed()){var waypointMenu=new ShoreScreen(null);waypointMenu.showModule("waypoints");openNextTick(()->waypointMenu);}
            if (config.extendedChat) client.options.getTextBackgroundOpacity().setValue(config.chatOpacity);
            else if (originalChatOpacity != null) client.options.getTextBackgroundOpacity().setValue(originalChatOpacity);
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> originalChatOpacity = client.options.getTextBackgroundOpacity().getValue());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ShoreMenuBackground.clear();
            ShoreMotionBlur.clear();ShoreCrystals.reload();recorder.close();
            ShoreTools.restoreZoomInput(client);
            if (originalChatOpacity != null) client.options.getTextBackgroundOpacity().setValue(originalChatOpacity);
        });
        ShoreGames.register();
        ShoreCommands.register();
        ShoreSelfTest.register(); // inert unless -Dshoremod.selftest is set
    }
    private KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.shoremod." + name, InputUtil.Type.KEYSYM, code, CATEGORY));
    }
    public static void tell(String value){message(value,0xEAF2F7);}
    public static void tellError(String value){message(value,0xFF9696);}
    private static void message(String value,int color) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> client.inGameHud.getChatHud().addMessage(Text.empty().append(Text.literal("SHOREMOD").withColor(config.accent).formatted(net.minecraft.util.Formatting.BOLD)).append(Text.literal(" \u00BB ").withColor(0x7F929E)).append(Text.literal(value).withColor(color))));
    }
}
