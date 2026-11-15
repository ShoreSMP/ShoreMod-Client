package net.shoresmp.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.Arm;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import java.util.*;

/** Optional, bounded visual modules; no changes to hit detection or server state. */
public final class ShoreVisuals {
    private ShoreVisuals(){}
    private static final Identifier TOTEM=Identifier.of("minecraft","textures/item/totem_of_undying.png");
    private static final int[][] EDGES={{0,1},{0,2},{0,4},{1,3},{1,5},{2,3},{2,6},{3,7},{4,5},{4,6},{5,7},{6,7}};
    private static List<Entity> entities=List.of();
    private static int ticks;private static long session=-1,popAt;private static Object world;
    private static float fov=70,focal,centerX,centerY;
    private static final Quaternionf inverseView=new Quaternionf();
    private static double cameraX,cameraY,cameraZ;
    private static String serverScope="";
    private static List<String> debug=List.of();
    private static String blockName="";private static BlockPos blockPos;
    public static void fov(float value){if(Float.isFinite(value)&&value>0.01f&&value<179)fov=value;}
    public static void pop(){popAt=System.nanoTime();}
    public static boolean customTotem(){return ShoreClient.config!=null&&ShoreClient.config.totemTweaks;}
    public static int guiTint(int color){
        var c=ShoreClient.config;var mc=MinecraftClient.getInstance();
        if(c==null||!c.guiTweaks||!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen))return color;
        return (Math.round((color>>>24)*c.inventoryOpacity/100f)<<24)|(color&0xFFFFFF);
    }
    public static String scope(MinecraftClient mc){
        if(mc.getCurrentServerEntry()!=null)return mc.getCurrentServerEntry().address.toLowerCase(Locale.ROOT);
        var local=mc.getServer();return local==null?"local": "local:"+local.getSavePath(net.minecraft.util.WorldSavePath.ROOT).normalize().getFileName();
    }
    public static boolean saveWaypoint(){
        var mc=MinecraftClient.getInstance();var c=ShoreClient.config;if(mc.player==null||mc.world==null){ShoreClient.tellError("Join a world first.");return false;}
        if(c.savedWaypoints.size()>=64){ShoreClient.tellError("Delete a waypoint first (limit 64).");return false;}
        var w=new Config.Waypoint();w.name=c.waypointName.strip();if(w.name.isEmpty())w.name="Waypoint";
        w.server=scope(mc);w.dimension=mc.world.getRegistryKey().getValue().toString();w.color=c.waypointColor;
        w.x=mc.player.getBlockX();w.y=mc.player.getBlockY();w.z=mc.player.getBlockZ();
        if(!c.waypointCoordinates.isBlank())try{
            var a=c.waypointCoordinates.strip().split("[\\s,]+");if(a.length!=3)throw new NumberFormatException();
            w.x=Integer.parseInt(a[0]);w.y=Integer.parseInt(a[1]);w.z=Integer.parseInt(a[2]);
            if(Math.abs((long)w.x)>30000000||Math.abs((long)w.z)>30000000||w.y< -2048||w.y>2048)throw new NumberFormatException();
        }catch(NumberFormatException e){ShoreClient.tellError("Enter valid X Y Z coordinates.");return false;}
        c.savedWaypoints.add(w);c.waypoints=true;c.save();ShoreClient.tell("Saved waypoint: "+w.name);return true;
    }
    public static void tick(MinecraftClient mc){
        var c=ShoreClient.config;
        if(world!=mc.world||session!=ShoreClient.sessionEpoch){world=mc.world;session=ShoreClient.sessionEpoch;entities=List.of();popAt=0;debug=List.of();serverScope=mc.world==null?"":scope(mc);}
        if(mc.player==null||mc.world==null)return;
        if(!c.coloredHitboxes&&!c.blockOverlay&&!c.betterF3)return;
        if(++ticks%4!=0)return;
        if(c.coloredHitboxes){entities=mc.world.getOtherEntities(mc.player,mc.player.getBoundingBox().expand(c.hitboxRange),e->e.isAlive()&&!e.isInvisible()&&(!c.hitboxPlayersOnly||e instanceof PlayerEntity)&&e.squaredDistanceTo(mc.player)<=c.hitboxRange*c.hitboxRange).stream().sorted(Comparator.comparingDouble(e->e.squaredDistanceTo(mc.player))).limit(64).filter(mc.player::canSee).limit(32).toList();}else entities=List.of();
        blockPos=mc.crosshairTarget instanceof BlockHitResult hit&&hit.getType()==net.minecraft.util.hit.HitResult.Type.BLOCK?hit.getBlockPos():null;
        blockName=blockPos==null?"":mc.world.getBlockState(blockPos).getBlock().getName().getString();
        if(c.betterF3&&mc.getDebugHud().shouldShowDebugHud()){
            var out=new ArrayList<String>();out.add("ShoreMod | "+mc.getCurrentFps()+" FPS");
            if(c.debugCoordinates&&!mc.player.hasReducedDebugInfo()){out.add("XYZ "+mc.player.getBlockX()+" / "+mc.player.getBlockY()+" / "+mc.player.getBlockZ());out.add(mc.player.getHorizontalFacing().asString()+" | "+mc.world.getRegistryKey().getValue());out.add("Biome: "+mc.world.getBiome(mc.player.getBlockPos()).getKey().map(k->k.getValue().getPath()).orElse("Unknown"));}
            if(c.debugTarget&&blockPos!=null){out.add("Looking at: "+blockName);if(!mc.player.hasReducedDebugInfo())out.add(blockPos.getX()+" / "+blockPos.getY()+" / "+blockPos.getZ());}
            if(c.debugMemory){var rt=Runtime.getRuntime();out.add("Memory "+((rt.totalMemory()-rt.freeMemory())/1048576)+" / "+(rt.maxMemory()/1048576)+" MB");}
            out.add("Recording: "+ShoreClient.recorder.status());debug=List.copyOf(out);
        }
    }
    public static void debug(DrawContext ctx){
        var mc=MinecraftClient.getInstance();int y=6;for(String line:debug){int w=mc.textRenderer.getWidth(line);ctx.fill(4,y-2,12+w,y+10,0xD0101217);ctx.drawText(mc.textRenderer,line,8,y,0xFFFFFFFF,false);y+=14;}
    }
    private record Point(float x,float y){}
    private static Point project(MinecraftClient mc,DrawContext ctx,double x,double y,double z){
        var v=new Vector3f((float)(x-cameraX),(float)(y-cameraY),(float)(z-cameraZ));inverseView.transform(v);
        if(v.z>=-0.08f)return null;
        return new Point(centerX+v.x*focal/-v.z,centerY-v.y*focal/-v.z);
    }
    private static void line(DrawContext ctx,Point a,Point b,int color,int width){
        if(a==null||b==null)return;float x=a.x,y=a.y,dx=b.x-x,dy=b.y-y,len=(float)Math.hypot(dx,dy),angle=(float)Math.atan2(dy,dx);
        if(!Float.isFinite(len)||len>Math.max(ctx.getScaledWindowWidth(),ctx.getScaledWindowHeight())*4)return;
        var m=ctx.getMatrices();m.pushMatrix();m.translate(x,y);m.rotate(angle);ctx.fill(0,-width/2,Math.max(1,(int)len),Math.max(1,width-width/2),color);m.popMatrix();
    }
    private static void box(MinecraftClient mc,DrawContext ctx,Box b,int color,int width){
        Point[] p=new Point[8];for(int i=0;i<8;i++)p[i]=project(mc,ctx,(i&1)==0?b.minX:b.maxX,(i&2)==0?b.minY:b.maxY,(i&4)==0?b.minZ:b.maxZ);
        for(int[] e:EDGES)line(ctx,p[e[0]],p[e[1]],color,width);
    }
    public static void render(DrawContext ctx){
        var mc=MinecraftClient.getInstance();var c=ShoreClient.config;if(c==null||mc.player==null||mc.world==null||mc.options.hudHidden)return;
        boolean debugOpen=mc.getDebugHud().shouldShowDebugHud();
        if(!debugOpen&&(c.blockOverlay||c.coloredHitboxes||c.waypoints)){
            var camera=mc.gameRenderer.getCamera();var position=camera.getCameraPos();
            cameraX=position.x;cameraY=position.y;cameraZ=position.z;inverseView.set(camera.getRotation()).conjugate();
            focal=(float)(ctx.getScaledWindowHeight()/(2*Math.tan(Math.toRadians(fov)/2)));centerX=ctx.getScaledWindowWidth()/2f;centerY=ctx.getScaledWindowHeight()/2f;
        }
        if(!debugOpen&&(c.blockOverlay||c.coloredHitboxes)){
            ctx.enableScissor(0,0,ctx.getScaledWindowWidth(),ctx.getScaledWindowHeight());
            if(c.blockOverlay&&mc.crosshairTarget instanceof BlockHitResult hit&&hit.getType()==net.minecraft.util.hit.HitResult.Type.BLOCK){var pos=hit.getBlockPos();var shape=mc.world.getBlockState(pos).getOutlineShape(mc.world,pos);if(!shape.isEmpty())for(Box b:shape.getBoundingBoxes())box(mc,ctx,b.offset(pos),(Math.round(c.blockOpacity*2.55f)<<24)|c.blockColor,c.outlineWidth);}
            if(c.coloredHitboxes)for(Entity e:entities)if(!e.isRemoved())box(mc,ctx,e.getBoundingBox(),0xFF000000|c.hitboxColor,1);
            ctx.disableScissor();
            if(c.blockOverlay&&c.blockInfo&&!blockName.isEmpty()){String text=blockName;ctx.drawText(mc.textRenderer,text,ctx.getScaledWindowWidth()/2+12,ctx.getScaledWindowHeight()/2+12,0xFF000000|c.blockColor,true);}
        }
        if(!debugOpen&&c.waypoints){String server=serverScope,dim=mc.world.getRegistryKey().getValue().toString();int shown=0;
            for(var w:c.savedWaypoints){if(!w.enabled||!w.server.equals(server)||!w.dimension.equals(dim))continue;double distance=mc.player.squaredDistanceTo(w.x+0.5,w.y+0.5,w.z+0.5);if(distance>(double)c.waypointRange*c.waypointRange)continue;Point p=project(mc,ctx,w.x+0.5,w.y+1.5,w.z+0.5);if(p==null)continue;
                float sc=c.waypointScale/100f;
                String text=mc.textRenderer.trimToWidth(w.name+" ["+(int)Math.sqrt(distance)+"m]",Math.max(20,(int)((ctx.getScaledWindowWidth()-16)/sc)));
                int width=mc.textRenderer.getWidth(text);float half=(width/2f+4)*sc;
                float x=Math.clamp(p.x,half,ctx.getScaledWindowWidth()-half),y=Math.clamp(p.y,16,ctx.getScaledWindowHeight()-28);
                var m=ctx.getMatrices();m.pushMatrix();m.translate(x,y);m.scale(sc,sc);ctx.fill(-width/2-3,-3,width/2+4,11,0xB0101217);ctx.drawText(mc.textRenderer,text,-width/2,0,0xFF000000|w.color,false);m.popMatrix();if(++shown==16)break;
            }
        }
        if(c.totemTweaks&&!mc.player.isSpectator()&&mc.options.getPerspective().isFirstPerson()){
            boolean right=mc.player.getMainArm()==Arm.RIGHT;
            if(mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING))totem(ctx,right,c.heldTotemSize,c.heldTotemOpacity);
            if(mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING))totem(ctx,!right,c.heldTotemSize,c.heldTotemOpacity);
        }
        if(c.totemTweaks&&popAt!=0){float t=(System.nanoTime()-popAt)/1_000_000_000f;if(t>1.5f)popAt=0;else{int size=Math.round(96*c.popTotemSize/100f*(1+0.15f*(float)Math.sin(t*8)));int alpha=Math.round(c.popTotemOpacity*2.55f*Math.min(1,(1.5f-t)*3));sprite(ctx,ctx.getScaledWindowWidth()/2-size/2,ctx.getScaledWindowHeight()/2-size/2,size,alpha);}}
    }
    private static void totem(DrawContext ctx,boolean right,int scale,int opacity){int size=48*scale/100;int x=right?ctx.getScaledWindowWidth()-size-16:16;sprite(ctx,x,ctx.getScaledWindowHeight()-size-25,size,Math.round(opacity*2.55f));}
    private static void sprite(DrawContext ctx,int x,int y,int size,int alpha){if(alpha>0)ctx.drawTexture(RenderPipelines.GUI_TEXTURED,TOTEM,x,y,0,0,size,size,16,16,16,16,(Math.clamp(alpha,0,255)<<24)|0xFFFFFF);}
}
