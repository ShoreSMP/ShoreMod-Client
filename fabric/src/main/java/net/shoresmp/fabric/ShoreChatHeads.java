package net.shoresmp.fabric;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.*;
import java.util.*;
import net.shoresmp.core.ChatSenders;
/** Uses skins already managed by Minecraft; never contacts a third-party avatar service. */
public final class ShoreChatHeads {
 private static final Map<Text,SkinTextures> MESSAGES=new WeakHashMap<>();
 private static final Map<OrderedText,SkinTextures> LINES=new WeakHashMap<>();
 private ShoreChatHeads(){}
 public static Text decorate(Text text){
  if(ShoreClient.config==null||!ShoreClient.config.chatHeads)return text;
  var mc=MinecraftClient.getInstance();if(mc.getNetworkHandler()==null)return text;
  var entries=new TreeMap<String,net.minecraft.client.network.PlayerListEntry>(String.CASE_INSENSITIVE_ORDER);
  for(var entry:mc.getNetworkHandler().getPlayerList()){
   entries.put(entry.getProfile().name(),entry);
   if(entry.getDisplayName()!=null){
    String alias=ChatSenders.displayAlias(entry.getDisplayName().getString());
    if(alias!=null)entries.putIfAbsent(alias,entry);
   }
  }
  // Real account names take priority over a nickname belonging to another player.
  for(var entry:mc.getNetworkHandler().getPlayerList())entries.put(entry.getProfile().name(),entry);
  String sender=ChatSenders.sender(text.getString(),entries.keySet());
  if(sender!=null){
   var entry=entries.get(sender);
   int spaces=Math.max(1,(int)Math.ceil((ShoreClient.config.chatHeadSize+2)/(double)Math.max(1,mc.textRenderer.getWidth(" "))));
   Text result=Text.literal(" ".repeat(spaces)).append(text);MESSAGES.put(result,entry.getSkinTextures());return result;
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
