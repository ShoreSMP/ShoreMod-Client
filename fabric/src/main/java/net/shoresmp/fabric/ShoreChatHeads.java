package net.shoresmp.fabric;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.*;
import java.util.*;
import java.util.regex.*;
/** Uses skins already managed by Minecraft; never contacts a third-party avatar service. */
public final class ShoreChatHeads {
 private static final Map<Text,SkinTextures> MESSAGES=new WeakHashMap<>();
 private static final Map<OrderedText,SkinTextures> LINES=new WeakHashMap<>();
 private static final Pattern SENDER=Pattern.compile("(?<![A-Za-z0-9_])([A-Za-z0-9_]{1,16})\\s*[:>»]");
 private ShoreChatHeads(){}
 public static Text decorate(Text text){
  if(ShoreClient.config==null||!ShoreClient.config.chatHeads)return text;
  var mc=MinecraftClient.getInstance();if(mc.getNetworkHandler()==null)return text;
  String s=text.getString();var matcher=SENDER.matcher(s.substring(0,Math.min(96,s.length())));
  while(matcher.find())for(var entry:mc.getNetworkHandler().getPlayerList())if(entry.getProfile().name().equalsIgnoreCase(matcher.group(1))){
   Text result=Text.literal("   ").append(text);MESSAGES.put(result,entry.getSkinTextures());return result;
  }
  return text;
 }
 public static void remember(Text text,java.util.List<net.minecraft.client.gui.hud.ChatHudLine.Visible> visible){
  var skin=MESSAGES.get(text);if(skin==null||visible.isEmpty())return;
  for(int i=0;i<visible.size();i++)if(i+1==visible.size()||visible.get(i+1).endOfEntry()){LINES.put(visible.get(i).content(),skin);break;}
 }
 public static void draw(DrawContext ctx,OrderedText text,int x,int y,int color){
  if(ShoreClient.config==null||!ShoreClient.config.chatHeads)return;var skin=LINES.get(text);if(skin==null)return;
  int size=ShoreClient.config.chatHeadSize;
  ctx.drawTexture(RenderPipelines.GUI_TEXTURED,skin.body().texturePath(),x,y,8,8,size,size,8,8,64,64,(color&0xFF000000)|0xFFFFFF);
  if(ShoreClient.config.chatHeadHat)ctx.drawTexture(RenderPipelines.GUI_TEXTURED,skin.body().texturePath(),x,y,40,8,size,size,8,8,64,64,(color&0xFF000000)|0xFFFFFF);
 }
}
