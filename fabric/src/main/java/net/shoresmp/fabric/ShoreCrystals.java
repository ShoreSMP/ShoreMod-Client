package net.shoresmp.fabric;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.util.Identifier;
import java.io.*;
/** Recolors the resource-pack crystal texture once per change; never changes collision geometry. */
public final class ShoreCrystals {
 private static final Identifier SOURCE=Identifier.of("minecraft","textures/entity/end_crystal/end_crystal.png");
 private static final Identifier CUSTOM=Identifier.of("shoremod","custom_end_crystal");
 private static NativeImage original;private static NativeImageBackedTexture texture;private static int key;
 private static long rainbowAt;private static int rainbowRgb;
 private ShoreCrystals(){}
 public static boolean ready(){return texture!=null;}
 public static RenderLayer layer(RenderLayer vanilla){
  var c=ShoreClient.config;if(c==null||!c.customCrystals)return vanilla;
  try{
   int color=c.crystalColor;
   if(c.crystalRainbow){long bucket=System.nanoTime()/200_000_000L;if(bucket!=rainbowAt){rainbowAt=bucket;rainbowRgb=java.awt.Color.HSBtoRGB((bucket%100)/100f,0.75f,1)&0xFFFFFF;}color=rainbowRgb;}
   int wanted=java.util.Objects.hash(color,c.crystalOpacity,c.crystalStyle);
   if(texture==null||key!=wanted){
    if(original==null){var resource=MinecraftClient.getInstance().getResourceManager().getResource(SOURCE).orElseThrow();try(var stream=resource.getInputStream()){original=NativeImage.read(stream);}}
    var image=new NativeImage(original.getWidth(),original.getHeight(),false);
    for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
     int pixel=original.getColorArgb(x,y),a=pixel>>>24;
     int light=c.crystalStyle==1?255:Math.max(pixel>>16&255,Math.max(pixel>>8&255,pixel&255));
     int opacity=c.crystalStyle==2?c.crystalOpacity/2:c.crystalOpacity;
     int r=(color>>16&255)*light/255,g=(color>>8&255)*light/255,b=(color&255)*light/255;
     image.setColorArgb(x,y,(a*opacity/100<<24)|(r<<16)|(g<<8)|b);
    }
    if(texture==null){texture=new NativeImageBackedTexture(()->"ShoreMod custom crystal",image);MinecraftClient.getInstance().getTextureManager().registerTexture(CUSTOM,texture);}
    else texture.setImage(image);
    texture.upload();key=wanted;
   }
   return RenderLayers.entityTranslucent(CUSTOM);
  }catch(Exception e){ShoreClient.LOG.warn("Could not prepare custom crystal texture",e);return vanilla;}
 }
 public static void reload(){if(original!=null){original.close();original=null;}texture=null;key=0;}
}
