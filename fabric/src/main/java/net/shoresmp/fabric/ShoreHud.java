package net.shoresmp.fabric;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.*;
import net.minecraft.util.Identifier;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Compact info panel (FPS, ping, position, biome, clock) plus an equipment durability column. */
public final class ShoreHud {
    private static final DateTimeFormatter CLOCK=DateTimeFormatter.ofPattern("HH:mm");
    private static final EquipmentSlot[] SLOTS={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND};
    private static final Identifier UI=Identifier.of("shoremod","ui");
    private ShoreHud(){}

    public static void register(){
        HudElementRegistry.addLast(Identifier.of("shoremod","hud"),(ctx,tick)->render(ctx));
        HudElementRegistry.replaceElement(net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CROSSHAIR,vanilla->(ctx,tick)->{
            Config c=ShoreClient.config;var client=MinecraftClient.getInstance();
            if(c==null||!c.crosshair||!client.options.getPerspective().isFirstPerson()||client.options.hudHidden
                ||client.getDebugHud().shouldShowDebugHud()||client.player==null||client.player.isSpectator())vanilla.render(ctx,tick);
            else crosshair(ctx);
        });
    }
    private static Text ui(String s){return Text.literal(s).setStyle(Style.EMPTY.withFont(new StyleSpriteSource.Font(UI)));}

    private record Readout(String id,String text){}
    private static List<Readout> cachedReadouts=List.of();
    private static long nextReadouts,cachedSession;
    private static Object readoutPlayer;
    /** One short readout per box, e.g. "118 FPS" or "XYZ: 3, -60, 10", tagged with its module id for sizing. */
    private static List<Readout> lines(MinecraftClient client){
        long now=System.nanoTime();
        if(now<nextReadouts&&readoutPlayer==client.player&&cachedSession==ShoreClient.sessionEpoch)return cachedReadouts;
        Config c=ShoreClient.config;var player=client.player;var out=new ArrayList<Readout>();
        boolean hidePosition=false;
        if(c.hudFps)out.add(new Readout("fps",client.getCurrentFps()+" FPS"));
        if(c.hudPing&&player!=null&&client.getNetworkHandler()!=null&&!client.isInSingleplayer()){
            var entry=client.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if(entry!=null)out.add(new Readout("ping",entry.getLatency()+" ms"));
        }
        if(c.hudCoords&&player!=null&&!hidePosition){
            out.add(new Readout("coords","XYZ: "+player.getBlockX()+", "+player.getBlockY()+", "+player.getBlockZ()));
            var facing=player.getHorizontalFacing();
            String axis=switch(facing){case NORTH->"-Z";case SOUTH->"+Z";case EAST->"+X";case WEST->"-X";default->"";};
            out.add(new Readout("coords",title(facing.asString())+" ("+axis+")"));
        }
        if(c.hudBiome&&player!=null&&client.world!=null&&!hidePosition)
            out.add(new Readout("biome",client.world.getBiome(player.getBlockPos()).getKey().map(k->title(k.getValue().getPath())).orElse("Unknown")));
        if(c.hudClock)out.add(new Readout("clock",LocalTime.now().format(CLOCK)));
        var target=ShorePvp.target();
        if(c.targetHud&&target!=null)out.add(new Readout("target",target.getName().getString()+"  ❤ "+String.format(Locale.ROOT,"%.1f",target.getHealth()+target.getAbsorptionAmount())+(c.targetDistance?String.format(Locale.ROOT,"  %.1fm",target.distanceTo(player)):"")));
        if(c.potionHud&&player!=null){
            var effects=new ArrayList<>(player.getStatusEffects());
            effects.sort(Comparator.comparingInt(e->-e.getDuration()));
            for(var e:effects){
                String name=e.getEffectType().value().getName().getString();
                int lvl=e.getAmplifier()+1;
                String roman=lvl<=1?"":" "+(lvl<=10?new String[]{"","I","II","III","IV","V","VI","VII","VIII","IX","X"}[lvl]:String.valueOf(lvl));
                int secs=e.getDuration()/20;
                String time=e.isInfinite()?"∞":String.format(Locale.ROOT,"%d:%02d",secs/60,secs%60);
                out.add(new Readout("potions",name+roman+"  "+time));
            }
        }
        if(c.cps)out.add(new Readout("cps",c.cpsRight?ShoreTools.cps(true)+" | "+ShoreTools.cps(false)+" CPS":ShoreTools.cps(true)+" CPS"));
        if(player!=null&&c.toggleSprintHud){
            if(c.toggleSneak&&client.options.sneakKey.isPressed())out.add(new Readout("togglesprint","Sneaking (Toggled)"));
            else if(c.toggleSprint&&client.options.sprintKey.isPressed())out.add(new Readout("togglesprint","Sprinting (Toggled)"));
        }
        nextReadouts=now+100_000_000L;readoutPlayer=client.player;cachedSession=ShoreClient.sessionEpoch;cachedReadouts=List.copyOf(out);
        return cachedReadouts;
    }

    static String title(String id){
        var sb=new StringBuilder();
        for(String part:id.split("_")){if(part.isEmpty())continue;if(!sb.isEmpty())sb.append(' ');sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));}
        return sb.toString();
    }

    private static final int TEXT=0xFFFFFFFF;
    private static int box(){return (Math.round(ShoreClient.config.hudBgOpacity*2.55f)<<24)|0x101217;}
    private static int textColor(){Config c=ShoreClient.config;return switch(c.hudTextColor){case 1->0xFF000000|c.accent;case 2->0xFFC8CDD3;case 3->0xFF000000|c.hudCustomColor;default->TEXT;};}
    /**
     * Classic PvP-client HUD: each readout in its own small dark box, Minecraft's own pixel font with
     * its drop shadow, drawn in GUI units so it matches vanilla text exactly.
     */
    /** One stacked HUD element: unscaled size, its scale, and how to draw it at (0,0). */
    private record Block(int w,int h,float scale,java.util.function.Consumer<DrawContext> draw){}
    private static final int BOX_H=16,GEAR_H=20,PAD_X=5,KEY=20,KEY_GAP=2;

    private static void render(DrawContext ctx){
        ShoreVisuals.render(ctx);
        var client=MinecraftClient.getInstance();Config c=ShoreClient.config;
        if(c==null||client.player==null||client.options.hudHidden||client.getDebugHud().shouldShowDebugHud())return;
        var tr=client.textRenderer;
        float global=net.shoresmp.core.HudScale.guiUnits(client.getWindow().getFramebufferHeight(),client.getWindow().getScaleFactor(),c.hudScale,100);
        List<Block> blocks=new ArrayList<>();
        for(Readout r:lines(client)){
            int w=tr.getWidth(r.text())+PAD_X*2;
            blocks.add(new Block(w,BOX_H,global*c.hudSize(r.id())/100f,g->{
                box(g,0,0,w,BOX_H);
                g.drawText(tr,r.text(),PAD_X,(BOX_H-8)/2+1,textColor(),c.hudShadow);
            }));
        }
        if(c.keystrokes)blocks.add(keystrokes(client,global*c.hudSize("keystrokes")/100f));
        if(c.itemCounter){
            float sc=global*c.hudSize("items")/100f;
            for(var count:itemCounts(client,c))if(count.amount()>0||c.itemsShowZero)blocks.add(itemRow(tr,count.icon(),count.label(),textColor(),sc));
        }
        if(c.hudArmor){
            float sc=global*c.hudSize("armor")/100f;
            for(EquipmentSlot slot:SLOTS){
                ItemStack s=client.player.getEquippedStack(slot);
                if(s.isEmpty()||!s.isDamageable())continue;
                int max=s.getMaxDamage(),left=max-s.getDamage(),pct=max<=0?100:Math.round(left*100f/max);
                int color=pct>50?textColor():pct>Math.max(c.durabilityThreshold,20)?0xFFFFFF55:0xFFFF5555;
                blocks.add(itemRow(tr,s,pct+"%",color,sc));
            }
        }
        if(blocks.isEmpty())return;

        // Stack the blocks down from the chosen corner, each at its own scale. When a column would run
        // into chat (left side) or off the screen, the next blocks start a new column beside it.
        int sw=ctx.getScaledWindowWidth(),sh=ctx.getScaledWindowHeight();
        boolean right=c.hudCorner==1||c.hudCorner==3,bottom=c.hudCorner>=2;
        final float gap=2*global,margin=4*global;
        float topOffset=right&&!bottom&&!client.player.getStatusEffects().isEmpty()?52:0;
        int chatH=net.minecraft.client.gui.hud.ChatHud.getHeight(client.options.getChatHeightUnfocused().getValue())+48;
        float avail=Math.max(60,sh-margin*2-topOffset-(right?0:Math.min(sh*0.6f,chatH)));
        List<List<Block>> columns=new ArrayList<>();List<Block> col=new ArrayList<>();float colH=0;
        for(Block b:blocks){
            float h=b.h()*b.scale();
            if(!col.isEmpty()&&colH+gap+h>avail){columns.add(col);col=new ArrayList<>();colH=0;}
            colH+=(col.isEmpty()?0:gap)+h;col.add(b);
        }
        columns.add(col);
        var m=ctx.getMatrices();
        float colX=margin;
        for(List<Block> column:columns){
            float height=-gap,width=0;
            for(Block b:column){height+=b.h()*b.scale()+gap;width=Math.max(width,b.w()*b.scale());}
            float y=bottom?sh-margin-height-(right?0:60):margin+topOffset;
            for(Block b:column){
                float x=right?sw-colX-b.w()*b.scale():colX;
                m.pushMatrix();m.translate(x,y);m.scale(b.scale(),b.scale());
                b.draw().accept(ctx);
                m.popMatrix();
                y+=b.h()*b.scale()+gap;
            }
            colX+=width+gap*2;
        }
    }
    private static Block itemRow(net.minecraft.client.font.TextRenderer tr,ItemStack stack,String label,int color,float sc){
        int iconWidth=ShoreClient.config.hudItemIcons?20:0;
        if(!ShoreClient.config.hudItemIcons)label=stack.getName().getString()+" "+label;
        final String text=label;
        int w=PAD_X+iconWidth+tr.getWidth(text)+PAD_X;
        return new Block(w,GEAR_H,sc,g->{
            box(g,0,0,w,GEAR_H);
            if(ShoreClient.config.hudItemIcons)g.drawItem(stack,PAD_X-1,2);
            g.drawText(tr,text,PAD_X+iconWidth,(GEAR_H-8)/2+1,color,ShoreClient.config.hudShadow);
        });
    }
    private record ItemCount(ItemStack icon,int amount,String label){}
    private static List<ItemCount> cachedItems=List.of();
    private static long nextItems,itemSession;
    private static Object itemPlayer;
    private static List<ItemCount> itemCounts(MinecraftClient client,Config config){
        long now=System.nanoTime();if(now<nextItems&&itemPlayer==client.player&&itemSession==ShoreClient.sessionEpoch)return cachedItems;
        var result=new ArrayList<ItemCount>();var inventory=client.player.getInventory();
        for(var group:counted(config)){
            int count=0;for(int i=0;i<inventory.size();i++)for(var item:group)if(inventory.getStack(i).isOf(item))count+=inventory.getStack(i).getCount();
            result.add(new ItemCount(new ItemStack(group[0]),count,String.valueOf(count)));
        }
        nextItems=now+100_000_000L;itemPlayer=client.player;itemSession=ShoreClient.sessionEpoch;return cachedItems=List.copyOf(result);
    }
    /** Items the counter shows; each group is counted together (e.g. both kinds of golden apple). */
    private static List<net.minecraft.item.Item[]> counted(Config c){
        var out=new ArrayList<net.minecraft.item.Item[]>();
        if(c.itemsTotem)out.add(new net.minecraft.item.Item[]{net.minecraft.item.Items.TOTEM_OF_UNDYING});
        if(c.itemsPearl)out.add(new net.minecraft.item.Item[]{net.minecraft.item.Items.ENDER_PEARL});
        if(c.itemsGapple)out.add(new net.minecraft.item.Item[]{net.minecraft.item.Items.GOLDEN_APPLE,net.minecraft.item.Items.ENCHANTED_GOLDEN_APPLE});
        if(c.itemsCrystal)out.add(new net.minecraft.item.Item[]{net.minecraft.item.Items.END_CRYSTAL});
        if(c.itemsArrow)out.add(new net.minecraft.item.Item[]{net.minecraft.item.Items.ARROW,net.minecraft.item.Items.SPECTRAL_ARROW,net.minecraft.item.Items.TIPPED_ARROW});
        if(c.itemsXp)out.add(new net.minecraft.item.Item[]{net.minecraft.item.Items.EXPERIENCE_BOTTLE});
        // Your own items, by id: "cobweb" or "minecraft:obsidian".
        for(String raw:c.itemsCustom){
            var id=Identifier.tryParse(raw.strip().toLowerCase(Locale.ROOT));
            if(id==null)continue;
            var item=net.minecraft.registry.Registries.ITEM.get(id);
            if(item!=net.minecraft.item.Items.AIR)out.add(new net.minecraft.item.Item[]{item});
        }
        return out;
    }
    /** WASD, mouse buttons (with clicks per second) and the space bar, lit while held. */
    private static Block keystrokes(MinecraftClient client,float sc){
        Config c=ShoreClient.config;var o=client.options;var tr=client.textRenderer;
        int w=KEY*3+KEY_GAP*2,h=KEY*2+KEY_GAP+(c.keystrokesMouse?KEY_GAP+KEY:0)+(c.keystrokesSpace?KEY_GAP+12:0);
        return new Block(w,h,sc,g->{
            key(g,tr,KEY+KEY_GAP,0,KEY,KEY,ShoreClient.keyName(o.forwardKey),o.forwardKey.isPressed());
            int y=KEY+KEY_GAP;
            key(g,tr,0,y,KEY,KEY,ShoreClient.keyName(o.leftKey),o.leftKey.isPressed());
            key(g,tr,KEY+KEY_GAP,y,KEY,KEY,ShoreClient.keyName(o.backKey),o.backKey.isPressed());
            key(g,tr,(KEY+KEY_GAP)*2,y,KEY,KEY,ShoreClient.keyName(o.rightKey),o.rightKey.isPressed());
            y+=KEY+KEY_GAP;
            if(c.keystrokesMouse){
                int half=(w-KEY_GAP)/2;
                key(g,tr,0,y,half,KEY,ShoreTools.cps(true)+" CPS",o.attackKey.isPressed());
                key(g,tr,half+KEY_GAP,y,w-half-KEY_GAP,KEY,ShoreTools.cps(false)+" CPS",o.useKey.isPressed());
                y+=KEY+KEY_GAP;
            }
            if(c.keystrokesSpace){
                boolean down=o.jumpKey.isPressed();
                if(down)notched(g,0,y,w,12,pressedColor());else box(g,0,y,w,12);
                g.fill(w/2-12,y+5,w/2+12,y+7,down?0xFF101010:textColor());
            }
        });
    }
    private static void key(DrawContext g,net.minecraft.client.font.TextRenderer tr,int x,int y,int w,int h,String label,boolean down){
        if(down)notched(g,x,y,w,h,pressedColor());else box(g,x,y,w,h);
        if(label.length()>4&&w<=KEY)label=label.substring(0,1);
        int tw=tr.getWidth(label);
        g.drawText(tr,label,x+(w-tw)/2,y+(h-8)/2+1,down?0xFF101010:textColor(),!down&&ShoreClient.config.hudShadow);
    }
    private static int pressedColor(){Config c=ShoreClient.config;return c.keystrokesAccent?0xD0000000|c.accent:0xD0FFFFFF;}
    private static void notched(DrawContext g,int x,int y,int w,int h,int color){
        g.fill(x+1,y,x+w-1,y+h,color);g.fill(x,y+1,x+1,y+h-1,color);g.fill(x+w-1,y+1,x+w,y+h-1,color);
    }
    static final int[] CROSSHAIR_COLORS={0xFFFFFFFF,-1,0xFF55FF55,0xFFFF5555,0xFF55FFFF,0xFFFFFF55};
    static final String[] CROSSHAIR_NAMES={"White","Accent","Green","Red","Cyan","Yellow","Custom"};
    /** Plus-shaped crosshair with configurable arm length, gap, thickness, color, dot and outline. */
    private static void crosshair(DrawContext ctx){
        Config c=ShoreClient.config;
        int alpha=Math.round(c.crosshairOpacity*2.55f);
        int base=c.crosshairColor==6?0xFF000000|c.crosshairCustomColor:c.crosshairColor==1?0xFF000000|c.accent:CROSSHAIR_COLORS[c.crosshairColor];
        int color=(alpha<<24)|(base&0xFFFFFF);
        int cx=ctx.getScaledWindowWidth()/2,cy=ctx.getScaledWindowHeight()/2;
        if(c.crosshairMode==1){
            Identifier tex=CrosshairImage.texture(c.crosshairImage);
            if(tex!=null){
                int s=c.crosshairImageSize,tint=c.crosshairTint?color:(alpha<<24)|0xFFFFFF;
                ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,tex,cx-s/2,cy-s/2,0f,0f,s,s,s,s,tint);
                return;
            }
        }
        int t=c.crosshairThickness,len=c.crosshairLength,gap=c.crosshairGap,h=t/2;
        int[][] arms={{cx-h,cy-gap-len,t,len},{cx-h,cy+gap+(t%2==0?0:1),t,len},{cx-gap-len,cy-h,len,t},{cx+gap+(t%2==0?0:1),cy-h,len,t}};
        if(c.crosshairOutline){
            int outline=(Math.round(alpha*0.75f)<<24);
            for(int[] a:arms)ctx.fill(a[0]-1,a[1]-1,a[0]+a[2]+1,a[1]+a[3]+1,outline);
            if(c.crosshairDot)ctx.fill(cx-h-1,cy-h-1,cx-h+t+1,cy-h+t+1,outline);
        }
        for(int[] a:arms)ctx.fill(a[0],a[1],a[0]+a[2],a[1]+a[3],color);
        if(c.crosshairDot)ctx.fill(cx-h,cy-h,cx-h+t,cy-h+t,color);
    }

    /** A crosshair picture chosen by the player, loaded from disk and reloaded when the file changes. */
    static final class CrosshairImage {
        private static final Identifier ID=Identifier.of("shoremod","dyn/crosshair_image");
        private static String loadedPath="";
        private static long loadedStamp;
        private static boolean ok;
        private static long nextCheck;
        static void invalidate(){loadedPath="";loadedStamp=0;nextCheck=0;ok=false;}
        static Identifier texture(String path){
            if(path==null||path.isBlank())return null;
            long now=System.nanoTime();
            if(path.equals(loadedPath)&&now<nextCheck)return ok?ID:null;
            nextCheck=now+1_000_000_000L;
            var file=java.nio.file.Path.of(path);
            long stamp;
            try{stamp=java.nio.file.Files.getLastModifiedTime(file).toMillis();}catch(Exception e){loadedPath=path;loadedStamp=0;ok=false;return null;}
            if(!path.equals(loadedPath)||stamp!=loadedStamp){
                loadedPath=path;loadedStamp=stamp;ok=false;
                try(var in=java.nio.file.Files.newInputStream(file)){
                    if(java.nio.file.Files.size(file)>4*1024*1024)throw new java.io.IOException("Image exceeds 4 MiB");
                    try(var header=javax.imageio.ImageIO.createImageInputStream(file.toFile())){
                        var readers=javax.imageio.ImageIO.getImageReaders(header);
                        if(!readers.hasNext())throw new java.io.IOException("Unsupported image");
                        var reader=readers.next();
                        try{reader.setInput(header);if(reader.getWidth(0)>512||reader.getHeight(0)>512)throw new java.io.IOException("Image must be at most 512x512");}finally{reader.dispose();}
                    }
                    var img=net.minecraft.client.texture.NativeImage.read(in);
                    if(img.getWidth()>512||img.getHeight()>512){img.close();ShoreClient.tellError("Crosshair image must be 512x512 or smaller.");return null;}
                    MinecraftClient.getInstance().getTextureManager().registerTexture(ID,new net.minecraft.client.texture.NativeImageBackedTexture(()->"shoremod crosshair",img));
                    ok=true;
                }catch(Exception e){ShoreClient.tellError("Could not load crosshair image: "+e.getMessage());}
            }
            return ok?ID:null;
        }
        /** Copies a chosen picture into config/shoremod/crosshairs so it keeps working if the original moves. */
        static String importFile(java.nio.file.Path source) throws java.io.IOException{
            var image=net.shoresmp.core.CrosshairImages.read(source);
            var dir=folder();java.nio.file.Files.createDirectories(dir);
            var target=java.nio.file.Files.createTempFile(dir,"crosshair-",".png");
            try{if(!javax.imageio.ImageIO.write(image,"png",target.toFile()))throw new java.io.IOException("PNG encoder unavailable");}
            catch(java.io.IOException e){java.nio.file.Files.deleteIfExists(target);throw e;}finally{image.flush();}
            return target.toAbsolutePath().toString();
        }
        static java.nio.file.Path folder(){return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("shoremod/crosshairs");}
    }
    /** Dark box with one-pixel corner notches, like vanilla's own UI rounding. */
    private static void box(DrawContext ctx,int x,int y,int w,int h){
        ctx.fill(x+1,y,x+w-1,y+h,box());
        ctx.fill(x,y+1,x+1,y+h-1,box());
        ctx.fill(x+w-1,y+1,x+w,y+h-1,box());
    }
}

