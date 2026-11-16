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
    private static List<Entity> entities=List.of();private static boolean wasAlive=true;
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
        return (Math.round((color>>>24)*c.inventoryOpacity/100f)<<24)|((color>>16&255)*(c.guiTintColor>>16&255)/255<<16)|((color>>8&255)*(c.guiTintColor>>8&255)/255<<8)|((color&255)*(c.guiTintColor&255)/255);
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
        w.name=w.name.substring(0,Math.min(32,w.name.length()));c.savedWaypoints.add(w);c.waypoints=true;c.save();ShoreClient.tell("Saved waypoint: "+w.name);return true;
    }
    public static boolean hitboxInRange(Config c,double distanceSquared){return c.hitboxLoadedRange||distanceSquared<=(double)c.hitboxRange*c.hitboxRange;}
    public static void tick(MinecraftClient mc){
        var c=ShoreClient.config;
        if(world!=mc.world||session!=ShoreClient.sessionEpoch){world=mc.world;session=ShoreClient.sessionEpoch;wasAlive=mc.player==null||mc.player.isAlive();entities=List.of();popAt=0;debug=List.of();serverScope=mc.world==null?"":scope(mc);}
        if(mc.player==null||mc.world==null)return;
        if(c.waypointAutoDeath&&wasAlive&&!mc.player.isAlive()&&c.savedWaypoints.size()<64){
            String name=c.waypointName,coords=c.waypointCoordinates;c.waypointName="Death "+(c.savedWaypoints.size()+1);c.waypointCoordinates="";
            saveWaypoint();c.waypointName=name;c.waypointCoordinates=coords;c.save();
        }
        wasAlive=mc.player.isAlive();
        if(!c.coloredHitboxes&&!c.blockOverlay&&!c.betterF3)return;
        if(++ticks%4!=0)return;
        if(c.coloredHitboxes){
            var distance=Comparator.<Entity>comparingDouble(e->e.squaredDistanceTo(mc.player));
            var nearest=new PriorityQueue<Entity>(distance.reversed());
            int budget=Math.min(512,c.hitboxLimit*4);
            Iterable<Entity> candidates=c.hitboxLoadedRange?mc.world.getEntities():mc.world.getOtherEntities(mc.player,mc.player.getBoundingBox().expand(c.hitboxRange),e->true);
            for(Entity entity:candidates){
                if(entity==mc.player||!entity.isAlive()||entity.isInvisible()||(c.hitboxPlayersOnly&&!(entity instanceof PlayerEntity)))continue;
                if(!hitboxInRange(c,entity.squaredDistanceTo(mc.player)))continue;
                nearest.add(entity);if(nearest.size()>budget)nearest.poll();
            }
            var ordered=new ArrayList<>(nearest);ordered.sort(distance);
            var visible=new ArrayList<Entity>();
            for(Entity entity:ordered){
                var ray=mc.world.raycast(new net.minecraft.world.RaycastContext(mc.player.getEyePos(),entity.getEyePos(),net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,mc.player));
                if(ray.getType()==net.minecraft.util.hit.HitResult.Type.MISS)visible.add(entity);
                if(visible.size()>=c.hitboxLimit)break;
            }
            entities=List.copyOf(visible);
        }else entities=List.of();
        blockPos=mc.crosshairTarget instanceof BlockHitResult hit&&hit.getType()==net.minecraft.util.hit.HitResult.Type.BLOCK?hit.getBlockPos():null;
        blockName=blockPos==null?"":mc.world.getBlockState(blockPos).getBlock().getName().getString();
        if(c.betterF3&&ShoreDebug.isPanelOpen()){
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
    private static Vector3f view(double x,double y,double z){return inverseView.transform(new Vector3f((float)(x-cameraX),(float)(y-cameraY),(float)(z-cameraZ)));}
    private static void worldLine(MinecraftClient mc,DrawContext ctx,double ax,double ay,double az,double bx,double by,double bz,int color,int width){
        var a=view(ax,ay,az);var b=view(bx,by,bz);float near=-0.08f;
        if(a.z>=near&&b.z>=near)return;
        if(a.z>=near){float t=(near-b.z)/(a.z-b.z);a.lerp(b,1-t);}
        if(b.z>=near){float t=(near-a.z)/(b.z-a.z);b.lerp(a,1-t);}
        line(ctx,new Point(centerX+a.x*focal/-a.z,centerY-a.y*focal/-a.z),new Point(centerX+b.x*focal/-b.z,centerY-b.y*focal/-b.z),color,width);
    }
    private static void box(MinecraftClient mc,DrawContext ctx,Box b,int color,int width){box(mc,ctx,b,color,width,0);}
    private static void box(MinecraftClient mc,DrawContext ctx,Box b,int color,int width,int style){
        for(int[] edge:EDGES){
            int i=edge[0],j=edge[1];
            double ax=(i&1)==0?b.minX:b.maxX,ay=(i&2)==0?b.minY:b.maxY,az=(i&4)==0?b.minZ:b.maxZ;
            double bx=(j&1)==0?b.minX:b.maxX,by=(j&2)==0?b.minY:b.maxY,bz=(j&4)==0?b.minZ:b.maxZ;
            if(style==2&&((i&2)!=0||(j&2)!=0))continue;
            if(style==1){
                worldLine(mc,ctx,ax,ay,az,ax+(bx-ax)*0.25,ay+(by-ay)*0.25,az+(bz-az)*0.25,color,width);
                worldLine(mc,ctx,bx,by,bz,bx+(ax-bx)*0.25,by+(ay-by)*0.25,bz+(az-bz)*0.25,color,width);
            }else worldLine(mc,ctx,ax,ay,az,bx,by,bz,color,width);
        }
    }
    public static void render(DrawContext ctx){
        var mc=MinecraftClient.getInstance();var c=ShoreClient.config;if(c==null||mc.player==null||mc.world==null||mc.options.hudHidden)return;
        boolean debugOpen=ShoreDebug.isPanelOpen();
        if(!debugOpen&&(c.blockOverlay||c.coloredHitboxes||c.waypoints)){
            var camera=mc.gameRenderer.getCamera();var position=camera.getCameraPos();
            cameraX=position.x;cameraY=position.y;cameraZ=position.z;inverseView.set(camera.getRotation()).conjugate();
            focal=(float)(ctx.getScaledWindowHeight()/(2*Math.tan(Math.toRadians(fov)/2)));centerX=ctx.getScaledWindowWidth()/2f;centerY=ctx.getScaledWindowHeight()/2f;
        }
        if(!debugOpen&&(c.blockOverlay||c.coloredHitboxes)){
            ctx.enableScissor(0,0,ctx.getScaledWindowWidth(),ctx.getScaledWindowHeight());
            if(c.blockOverlay&&mc.crosshairTarget instanceof BlockHitResult hit&&hit.getType()==net.minecraft.util.hit.HitResult.Type.BLOCK){var pos=hit.getBlockPos();var shape=mc.world.getBlockState(pos).getOutlineShape(mc.world,pos);if(!shape.isEmpty())for(Box b:shape.getBoundingBoxes())box(mc,ctx,b.offset(pos),(Math.round(c.blockOpacity*2.55f)<<24)|c.blockColor,c.outlineWidth);}
            if(c.coloredHitboxes)for(Entity e:entities)if(!e.isRemoved()&&!e.isInvisible()){
                float delta=mc.getRenderTickCounter().getTickProgress(false);
                var pos=e.getLerpedPos(delta);var offset=pos.subtract(e.getX(),e.getY(),e.getZ());var bounds=e.getBoundingBox().offset(offset);
                float fade=c.hitboxDistanceFade?Math.clamp(1-(float)Math.sqrt(e.squaredDistanceTo(mc.player))/(c.hitboxLoadedRange?Math.max(16,mc.options.getViewDistance().getValue()*16):c.hitboxRange),0.15f,1):1;
                int alpha=Math.round(c.hitboxOpacity*2.55f*fade);
                int rgb=c.hitboxTargetColor&&mc.targetedEntity==e?c.hitboxTargetRgb:c.hitboxColor;
                box(mc,ctx,bounds,(alpha<<24)|rgb,c.hitboxWidth,c.hitboxStyle);
                double eye=e.getEyeY()+offset.y;
                if(c.hitboxEyes)worldLine(mc,ctx,bounds.minX,eye,bounds.minZ,bounds.maxX,eye,bounds.maxZ,(alpha<<24)|c.hitboxEyeColor,c.hitboxWidth);
                if(c.hitboxLook){var direction=e.getRotationVec(delta).multiply(1.5);worldLine(mc,ctx,pos.x,eye,pos.z,pos.x+direction.x,eye+direction.y,pos.z+direction.z,(alpha<<24)|c.hitboxLookColor,c.hitboxWidth);}
            }
            ctx.disableScissor();
            if(c.blockOverlay&&c.blockInfo&&!blockName.isEmpty()){String text=blockName;ctx.drawText(mc.textRenderer,text,ctx.getScaledWindowWidth()/2+12,ctx.getScaledWindowHeight()/2+12,0xFF000000|c.blockColor,true);}
        }
        if(!debugOpen&&c.waypoints){
            String dim=mc.world.getRegistryKey().getValue().toString();
            var markers=c.savedWaypoints.stream().filter(w->w.enabled&&w.server.equals(serverScope)&&w.dimension.equals(dim))
                .sorted(Comparator.<Config.Waypoint>comparingInt(w->w.tracked?0:1).thenComparingDouble(w->mc.player.squaredDistanceTo(w.x,w.y,w.z))).toList();
            int shown=0;
            for(var w:markers){
                double distance=Math.sqrt(mc.player.squaredDistanceTo(w.x+0.5,w.y+0.5,w.z+0.5));
                if(distance>c.waypointRange)continue;
                Point p=project(mc,ctx,w.x+0.5,w.y+1.5,w.z+0.5);
                float dx=w.x+0.5f-(float)cameraX,dz=w.z+0.5f-(float)cameraZ;
                double angle=Math.toDegrees(Math.atan2(-dx,dz))-mc.player.getYaw();
                angle=(angle%360+540)%360-180;
                boolean off=p==null||p.x<8||p.x>ctx.getScaledWindowWidth()-8||p.y<8||p.y>ctx.getScaledWindowHeight()-28;
                if(off&&!c.waypointOffscreen)continue;
                String text=(w.tracked?"* ":"")+(off?(angle<0?"< ":"> "):"")+w.name+(c.waypointDistances?" ["+(int)distance+"m]":"");
                if(c.waypointCoordinatesShown)text+=" "+w.x+" "+w.y+" "+w.z;
                float sc=c.waypointScale/100f;
                text=mc.textRenderer.trimToWidth(text,Math.max(20,(int)((ctx.getScaledWindowWidth()-20)/sc)));
                int width=mc.textRenderer.getWidth(text);float half=(width/2f+4)*sc;
                float x=off?(angle<0?half+4:ctx.getScaledWindowWidth()-half-4):Math.clamp(p.x,half,ctx.getScaledWindowWidth()-half);
                float y=off?ctx.getScaledWindowHeight()/2f:Math.clamp(p.y,16,ctx.getScaledWindowHeight()-28);
                if(w.tracked&&c.waypointCompass){x=ctx.getScaledWindowWidth()/2f;y=18;}
                var m=ctx.getMatrices();m.pushMatrix();m.translate(x,y);m.scale(sc,sc);
                if(c.waypointBackground)ctx.fill(-width/2-3,-3,width/2+4,11,(Math.round(c.waypointOpacity*2.55f)<<24)|c.hudBackgroundColor);
                ctx.drawText(mc.textRenderer,text,-width/2,0,0xFF000000|w.color,true);m.popMatrix();
                if(++shown>=c.waypointMaxVisible)break;
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
