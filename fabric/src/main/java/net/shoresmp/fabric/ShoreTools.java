package net.shoresmp.fabric;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.shoresmp.core.PunishmentMatcher;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Zoom, durability warnings, death locations and reconnecting. All purely client-side. */
public final class ShoreTools {
    private ShoreTools(){}
    private static final EquipmentSlot[] WATCHED={EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private static final DateTimeFormatter WHEN=DateTimeFormatter.ofPattern("MMM d, HH:mm",Locale.ROOT);

    // ------------------------------------------------------------------ zoom
    public static KeyBinding zoomKey;
    private static double zoom=1,zoomTarget=1,heldLevel;
    private static boolean wasZooming,toggled,zoomSetSmooth;
    private static long lastZoomFrame;

    private static boolean zooming(){
        var client=MinecraftClient.getInstance();Config c=ShoreClient.config;
        if(zoomKey==null||!c.zoomEnabled||client.currentScreen!=null||client.player==null)return false;
        return c.zoomToggle?toggled:zoomKey.isPressed();
    }
    /** Divisor applied to the rendered field of view. */
    public static double fovDivisor(){
        zoomTarget=wasZooming?heldLevel:1;
        long now=System.nanoTime();
        double dt=lastZoomFrame==0?1:Math.min(0.1,(now-lastZoomFrame)/1e9);lastZoomFrame=now;
        if(!ShoreClient.config.zoomSmooth)zoom=zoomTarget;
        else{zoom+=(zoomTarget-zoom)*Math.min(1,dt*14);if(Math.abs(zoom-zoomTarget)<0.01)zoom=zoomTarget;}
        return zoom;
    }
    /** @return true when the scroll was consumed to adjust zoom. */
    public static boolean scroll(double vertical){
        if(!zooming()||!ShoreClient.config.zoomScroll||vertical==0)return false;
        double step=1+ShoreClient.config.zoomScrollSpeed/100.0;
        heldLevel=Math.clamp(heldLevel*(vertical>0?step:1/step),1.5,50);
        return true;
    }
    /** Runs every client tick: toggle handling and the cinematic camera while zoomed. */
    private static void zoomTick(MinecraftClient client){
        Config c=ShoreClient.config;
        while(zoomKey!=null&&zoomKey.wasPressed())if(c.zoomToggle)toggled=!toggled;
        if(!c.zoomToggle||client.player==null)toggled=c.zoomToggle&&toggled&&client.player!=null;
        boolean active=zooming();
        if(active&&!wasZooming){
            heldLevel=c.zoomLevel;
            // smoothCameraEnabled is a runtime flag (never written to options.txt); only undo it if zoom set it.
            zoomSetSmooth=c.zoomCinematic&&!client.options.smoothCameraEnabled;
            if(zoomSetSmooth)client.options.smoothCameraEnabled=true;
            wasZooming=true;
        }else if(!active&&wasZooming){
            wasZooming=false;restoreZoomInput(client);
        }
    }
    /**
     * Multiplier for camera turning while zoomed: a fixed percentage (static) or in proportion to the
     * zoom (dynamic). Applied to the look movement itself, so the sensitivity setting is never changed.
     */
    public static double lookScale(){
        if(!wasZooming)return 1;
        Config c=ShoreClient.config;
        return c.zoomSensMode==0?c.zoomSensitivity/100.0:1/Math.max(1,heldLevel);
    }
    public static void restoreZoomInput(MinecraftClient client){
        if(zoomSetSmooth){client.options.smoothCameraEnabled=false;zoomSetSmooth=false;}
    }

    // ------------------------------------------------------------------ module toggle keys
    private static final Map<String,Boolean> keyWasDown=new HashMap<>();
    /** Toggle keys set on module pages: a fresh press while playing switches that module on or off. */
    private static void moduleKeysTick(MinecraftClient client){
        var keys=ShoreClient.config.moduleKeys;
        if(keys.isEmpty())return;
        long window=client.getWindow().getHandle();
        for(var e:keys.entrySet()){
            var key=net.minecraft.client.util.InputUtil.fromTranslationKey(e.getValue());
            boolean down=key.getCategory()==net.minecraft.client.util.InputUtil.Type.MOUSE
                ?org.lwjgl.glfw.GLFW.glfwGetMouseButton(window,key.getCode())==org.lwjgl.glfw.GLFW.GLFW_PRESS
                :net.minecraft.client.util.InputUtil.isKeyPressed(client.getWindow(),key.getCode());
            boolean was=keyWasDown.getOrDefault(e.getKey(),false);
            keyWasDown.put(e.getKey(),down);
            if(down&&!was&&client.currentScreen==null&&client.player!=null){
                String status=ShoreScreen.toggleById(e.getKey());
                if(status!=null)client.inGameHud.setOverlayMessage(Text.literal(status),false);
            }
        }
    }

    // ------------------------------------------------------------------ CPS
    private static final java.util.ArrayDeque<Long> leftClicks=new java.util.ArrayDeque<>(),rightClicks=new java.util.ArrayDeque<>();
    public static void click(int button){
        long now=System.currentTimeMillis();
        if(button==0)leftClicks.addLast(now);else if(button==1)rightClicks.addLast(now);
    }
    public static int cps(boolean left){
        var q=left?leftClicks:rightClicks;long cutoff=System.currentTimeMillis()-1000;
        while(!q.isEmpty()&&q.peekFirst()<cutoff)q.pollFirst();
        return q.size();
    }

    // ------------------------------------------------------------------ per-tick checks
    private static final Map<EquipmentSlot,Item> lastItem=new EnumMap<>(EquipmentSlot.class);
    private static final Map<EquipmentSlot,Integer> lastPct=new EnumMap<>(EquipmentSlot.class);
    private static boolean wasDead;

    public static void tick(MinecraftClient client){
        zoomTick(client);
        moduleKeysTick(client);
        var player=client.player;
        if(player==null){lastItem.clear();lastPct.clear();wasDead=false;return;}
        Config c=ShoreClient.config;
        if(c.durabilityAlert)for(EquipmentSlot slot:WATCHED){
            ItemStack s=player.getEquippedStack(slot);
            if(s.isEmpty()||!s.isDamageable()||s.getMaxDamage()<=0){lastItem.remove(slot);lastPct.remove(slot);continue;}
            int left=s.getMaxDamage()-s.getDamage(),pct=left*100/s.getMaxDamage();
            Integer before=lastPct.get(slot);
            if(lastItem.get(slot)==s.getItem()&&before!=null&&before>c.durabilityThreshold&&pct<=c.durabilityThreshold){
                if(c.durabilitySound)client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_PLING,0.6f));
                if(c.durabilityChat)ShoreClient.tell(s.getName().getString()+" has "+left+" durability left");
                else client.inGameHud.setOverlayMessage(Text.literal("⚠ ").withColor(0xFFB45A)
                    .append(s.getName().copy().withColor(0xFFFFFF)).append(Text.literal(" has "+left+" durability left").withColor(0xFFB45A)),false);
            }
            lastItem.put(slot,s.getItem());lastPct.put(slot,pct);
        }
        boolean dead=player.isDead();
        if(dead&&!wasDead&&c.deathLog&&client.world!=null){
            String dim=ShoreHud.title(client.world.getRegistryKey().getValue().getPath());
            String where=player.getBlockX()+" "+player.getBlockY()+" "+player.getBlockZ();
            String server=lastServer==null?"Singleplayer":lastServer.name;
            c.deaths.addFirst(LocalDateTime.now().format(WHEN)+"  ·  "+dim+"  ·  "+where+"  ·  "+server);
            c.save();
            if(c.deathAnnounce)ShoreClient.tell("You died at "+where+" in "+dim+". Use /shore deaths for recent deaths.");
        }
        wasDead=dead;
    }

    // ------------------------------------------------------------------ reconnect
    private static ServerInfo lastServer;
    public static volatile String lastDisconnectReason="";
    private static int autoAttempts;

    public static void joined(MinecraftClient client){
        ServerInfo info=client.getCurrentServerEntry();
        if(info!=null&&!info.isRealm())lastServer=info;
        autoAttempts=0;lastDisconnectReason="";
    }
    private static void reconnect(MinecraftClient client){
        if(lastServer==null)return;
        ConnectScreen.connect(new MultiplayerScreen(new TitleScreen()),client,ServerAddress.parse(lastServer.address),lastServer,false,null);
    }
    public static void register(){
        ScreenEvents.AFTER_INIT.register((client,screen,w,h)->{
            if(!(screen instanceof DisconnectedScreen)||lastServer==null||!ShoreClient.config.reconnectButton)return;
            List<ClickableWidget> buttons=Screens.getButtons(screen);
            // Stack below the lowest existing button ("Back to server list").
            int bx=w/2-100,by=-1;
            for(ClickableWidget b:buttons)if(b.getY()+b.getHeight()+4>by){bx=b.getX()+(b.getWidth()-200)/2;by=b.getY()+b.getHeight()+4;}
            if(by<0)by=h/2+40;
            Config c=ShoreClient.config;
            String reason=lastDisconnectReason==null?"":lastDisconnectReason;
            boolean auto=c.autoReconnect&&autoAttempts<5&&!PunishmentMatcher.matches(reason,c.punishmentPhrases);
            final int[] ticks={auto?c.autoReconnectDelay*20:-1};
            ButtonWidget button=ButtonWidget.builder(label(lastServer,ticks[0]),b->{ticks[0]=-1;reconnect(client);}).dimensions(bx,by,200,20).build();
            buttons.add(button);
            final ButtonWidget[] cancel={null};
            if(auto){
                cancel[0]=ButtonWidget.builder(Text.literal("Cancel auto-reconnect"),b->{ticks[0]=-1;button.setMessage(label(lastServer,-1));b.visible=false;}).dimensions(bx,by+24,200,20).build();
                buttons.add(cancel[0]);
            }
            ScreenEvents.afterTick(screen).register(s->{
                if(ticks[0]<0)return;
                if(--ticks[0]<=0){ticks[0]=-1;autoAttempts++;reconnect(client);return;}
                if(ticks[0]%20==0)button.setMessage(label(lastServer,ticks[0]));
            });
        });
    }
    private static Text label(ServerInfo info,int ticks){
        String name=info.name==null||info.name.isBlank()?info.address:info.name;
        if(name.length()>22)name=name.substring(0,21)+"...";
        if(ticks<0)return Text.literal("Reconnect to "+name);
        return Text.literal("Reconnecting in "+Math.max(1,(ticks+19)/20)+"s...");
    }
}
