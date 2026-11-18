package net.shoresmp.fabric;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;

/** GPU-only temporal frame blending. Never reads pixels back to the CPU. */
public final class ShoreMotionBlur {
 private static final List<Frame> frames=new ArrayList<>();
 private record Frame(Identifier id,HistoryTexture texture,Object gpu){}
 private static final class HistoryTexture extends AbstractTexture {
  HistoryTexture(Object texture,Object view,Object sampler)throws ReflectiveOperationException{
   for(Field field:AbstractTexture.class.getDeclaredFields()){
    if(Modifier.isStatic(field.getModifiers()))continue;field.setAccessible(true);
    if(field.getType().isInstance(texture))field.set(this,texture);
    else if(view!=null&&field.getType().isInstance(view))field.set(this,view);
    else if(sampler!=null&&field.getType().isInstance(sampler))field.set(this,sampler);
   }
  }
 }
 private static Object world;private static int width,height,next,count,pending=-1;
 private static long lastCapture,lastMovement;private static float yaw,pitch;private static double x,y,z;
 private static Method create,view,encoder,copy;private static Object device,sampler;
 private static String error="";private static boolean failed;
 private ShoreMotionBlur(){}
 public static int frameCount(){return count;}
 public static String status(){return failed?"Unavailable: "+error:count+" GPU history frames";}
 public static void clear(){
  var mc=MinecraftClient.getInstance();
  for(var frame:frames)mc.getTextureManager().destroyTexture(frame.id);
  frames.clear();count=next=0;pending=-1;lastCapture=lastMovement=0;world=null;
 }
 private static Method method(Class<?> type,String name,int parameters){
  for(var m:type.getMethods())if(m.getName().equals(name)&&m.getParameterCount()==parameters){m.setAccessible(true);return m;}
  return null;
 }
 private static void initialize(Object source)throws Exception{
  device=RenderSystem.getDevice();
  create=Arrays.stream(device.getClass().getMethods()).filter(m->m.getName().equals("createTexture")&&(m.getParameterCount()>=5&&m.getParameterCount()<=7)&&(m.getParameterTypes()[0]==String.class||Supplier.class.isAssignableFrom(m.getParameterTypes()[0]))).findFirst().orElseThrow();
  create.setAccessible(true);view=method(device.getClass(),"createTextureView",1);encoder=method(device.getClass(),"createCommandEncoder",0);
  Object command=encoder.invoke(device);copy=method(command.getClass(),"copyTextureToTexture",9);
  if(copy==null)throw new IllegalStateException("GPU texture copies unavailable");
  Method cache=method(RenderSystem.class,"getSamplerCache",0);
  if(cache!=null){Object values=cache.invoke(null);
   for(var m:values.getClass().getMethods())if(m.getName().equals("get")&&m.getParameterCount()==1&&m.getParameterTypes()[0].isEnum()){
    @SuppressWarnings({"rawtypes","unchecked"}) Object nearest=Enum.valueOf((Class)m.getParameterTypes()[0],"NEAREST");sampler=m.invoke(values,nearest);break;
   }
  }
 }
 private static Object allocate(String label,int usage,Object format,int width,int height)throws Exception{
  Object name=create.getParameterTypes()[0]==String.class?label:(Supplier<String>)()->label;
  return switch(create.getParameterCount()){
   case 5->create.invoke(device,name,format,width,height,1);
   case 6->create.invoke(device,name,usage,format,width,height,1);
   default->create.invoke(device,name,usage,format,width,height,1,1);
  };
 }
 public static void verifyBackend(){
  var mc=MinecraftClient.getInstance();Object source=mc.getFramebuffer().getColorAttachment();if(source==null)throw new IllegalStateException("Missing framebuffer");
  Object texture=null;HistoryTexture wrapped=null;
  try{
   initialize(source);Object format=method(source.getClass(),"getFormat",0).invoke(source);int usage=0;
   for(String flag:new String[]{"USAGE_COPY_DST","USAGE_COPY_SRC","USAGE_TEXTURE_BINDING"})for(Class<?> t=source.getClass();t!=null;t=t.getSuperclass())try{usage|=t.getField(flag).getInt(null);break;}catch(NoSuchFieldException ignored){}
   texture=allocate("ShoreMod history check",usage,format,16,16);
   Object textureView=view==null?null:view.invoke(device,texture);
   wrapped=new HistoryTexture(texture,textureView,sampler);
   copy.invoke(encoder.invoke(device),source,texture,0,0,0,0,0,16,16);
   ShoreClient.LOG.info("SHORE_GPU_HISTORY_PASS");
  }catch(Exception e){throw new IllegalStateException("GPU history backend check failed",e);}
  finally{if(wrapped!=null)wrapped.close();else if(texture instanceof AutoCloseable closeable)try{closeable.close();}catch(Exception ignored){}}
 }
 public static void capture(){
  var mc=MinecraftClient.getInstance();var c=ShoreClient.config;
  if(c==null||!c.motionBlur||c.blurStrength==0||failed||mc.player==null||mc.world==null||mc.currentScreen!=null||mc.isPaused()||(c.blurAutoProtect&&mc.getCurrentFps()<c.blurMinFps)){
   if(world!=null)clear();return;
  }
  var fb=mc.getFramebuffer();int w=fb.textureWidth,h=fb.textureHeight;
  if(w<=0||h<=0)return;
  long bytes=(long)w*h*4;
  int capacity=(int)Math.min(c.blurSamples+1,((long)c.blurMemoryMb*1048576)/Math.max(1,bytes));
  if(capacity<2){if(world!=null)clear();return;}
  // During a stationary start the ring is deliberately empty until the first movement.
  boolean reset=world!=mc.world||width!=w||height!=h||(!frames.isEmpty()&&frames.size()!=capacity);
  if(reset){clear();world=mc.world;width=w;height=h;x=mc.player.getX();y=mc.player.getY();z=mc.player.getZ();yaw=mc.player.getYaw();pitch=mc.player.getPitch();}
  double travel=mc.player.squaredDistanceTo(x,y,z);
  if(travel>64){clear();return;}
  float rotation=Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(mc.player.getYaw()-yaw))+Math.abs(mc.player.getPitch()-pitch);
  x=mc.player.getX();y=mc.player.getY();z=mc.player.getZ();yaw=mc.player.getYaw();pitch=mc.player.getPitch();
  long now=System.nanoTime();
  boolean moving=travel>=0.000001||rotation>=0.01f;
  if(moving)lastMovement=now;
  if(c.blurMovingOnly&&!moving&&(lastMovement==0||now-lastMovement>=c.blurMotionTail*1_000_000L)){
   count=0;pending=-1;return;
  }
  if(now-lastCapture<1_000_000_000L/c.blurFps)return;
  try{
   Object source=fb.getColorAttachment();if(source==null)return;
   if(create==null)initialize(source);
   if(frames.isEmpty()){
    Method formatMethod=method(source.getClass(),"getFormat",0);
    Object format=formatMethod.invoke(source);
    int usage=0;
    for(String flag:new String[]{"USAGE_COPY_DST","USAGE_COPY_SRC","USAGE_TEXTURE_BINDING"}){
     for(Class<?> t=source.getClass();t!=null;t=t.getSuperclass())try{usage|=t.getField(flag).getInt(null);break;}catch(NoSuchFieldException ignored){}
    }
    for(int i=0;i<capacity;i++){
     Object texture=allocate("ShoreMod motion history "+i,usage,format,w,h);
     Object textureView=view==null?null:view.invoke(device,texture);
     var wrapped=new HistoryTexture(texture,textureView,sampler);var id=Identifier.of("shoremod","motion_history_"+i);
     mc.getTextureManager().registerTexture(id,wrapped);frames.add(new Frame(id,wrapped,texture));
    }
   }
   copy.invoke(encoder.invoke(device),source,frames.get(next).gpu,0,0,0,0,0,w,h);
   pending=next;lastCapture=now;
  }catch(Exception e){clear();failed=true;error=e.getClass().getSimpleName();ShoreClient.LOG.error("GPU motion history initialization failed",e);}
 }
 /** Called after the world snapshot and before vanilla HUD, so text/crosshairs stay crisp. */
 public static void render(DrawContext ctx){
  var mc=MinecraftClient.getInstance();var c=ShoreClient.config;
  if(c==null||!c.motionBlur||failed||mc.currentScreen!=null||count==0||frames.isEmpty())return;
  int shown=Math.min(count,c.blurSamples);
  float total=c.blurStrength/100f;
  for(int age=shown;age>=1;age--){
   int index=Math.floorMod(next-age,frames.size());
   if(index==pending)continue;
   int alpha=Math.round(255*(total/shown));
   ctx.drawTexture(RenderPipelines.GUI_TEXTURED,frames.get(index).id,0,0,0,height,ctx.getScaledWindowWidth(),ctx.getScaledWindowHeight(),width,-height,width,height,(alpha<<24)|0xFFFFFF);
  }
 }
 public static void finishFrame(){if(pending>=0&&!frames.isEmpty()){next=(pending+1)%frames.size();count=Math.min(count+1,frames.size()-1);pending=-1;}}
}
