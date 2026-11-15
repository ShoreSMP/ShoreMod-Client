package net.shoresmp.fabric;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.*;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.text.*;
import java.util.*;
import org.lwjgl.glfw.GLFW;

/** A single tooltip: title, inventory grid, untouched styled lore, and modifier hints. */
public final class ShoreShulkerTooltip {
 private ItemStack locked=ItemStack.EMPTY,last=ItemStack.EMPTY;
 private boolean controlDown;private int anchorX,anchorY;
 private float left,top,right,bottom;
 public boolean isLocked(){return !locked.isEmpty();}
 public void control(boolean down,ItemStack hovered,int mx,int my){
  if(down&&!controlDown){
   if(!locked.isEmpty())locked=ItemStack.EMPTY;
   else if(isBox(hovered)){locked=hovered.copy();anchorX=mx;anchorY=my;}
   else if(isBox(last))locked=last.copy();
  }
  controlDown=down;
 }
 public static String count(int amount){
  if(amount>=1000000)return String.format(Locale.ROOT,"%.1fm",amount/1000000.0).replace(".0m","m");
  if(amount>=1000)return String.format(Locale.ROOT,"%.1fk",amount/1000.0).replace(".0k","k");
  return Integer.toString(amount);
 }
 public void clear(){locked=ItemStack.EMPTY;last=ItemStack.EMPTY;controlDown=false;}
 public boolean click(double x,double y){
  if(locked.isEmpty())return false;
  if(x>=left&&x<right&&y>=top&&y<bottom)return true;
  clear();return false;
 }
 public static List<ItemStack> previewItems(List<ItemStack> items,boolean full){
  if(full)return items;var grouped=new ArrayList<ItemStack>();
  for(var item:items){if(item.isEmpty())continue;ItemStack match=null;
   for(var old:grouped)if(ItemStack.areItemsAndComponentsEqual(old,item)){match=old;break;}
   if(match==null)grouped.add(item.copy());else match.increment(item.getCount());
  }return grouped;
 }
 public boolean render(DrawContext ctx,ItemStack hovered,int mx,int my,int width,int height){
  var mc=MinecraftClient.getInstance();var c=ShoreClient.config;
  if(c==null||!c.shulkerPreview){clear();return false;}
  long window=mc.getWindow().getHandle();
  boolean ctrl=GLFW.glfwGetKey(window,GLFW.GLFW_KEY_LEFT_CONTROL)==GLFW.GLFW_PRESS;
  control(c.shulkerLock&&ctrl,hovered,mx,my);
  if(!c.shulkerLock)locked=ItemStack.EMPTY;
  ItemStack stack=locked.isEmpty()?hovered:locked;
  if(!isBox(stack)){last=ItemStack.EMPTY;return false;}
  boolean shift=GLFW.glfwGetKey(window,GLFW.GLFW_KEY_LEFT_SHIFT)==GLFW.GLFW_PRESS;
  boolean alt=GLFW.glfwGetKey(window,GLFW.GLFW_KEY_LEFT_ALT)==GLFW.GLFW_PRESS;
  if(c.shulkerMode==0&&!shift&&locked.isEmpty())return false;
  if(locked.isEmpty()){anchorX=mx;anchorY=my;if(last.isEmpty()||last.getCount()!=stack.getCount()||!ItemStack.areItemsAndComponentsEqual(last,stack))last=stack.copy();}
  var items=net.minecraft.util.collection.DefaultedList.ofSize(27,ItemStack.EMPTY);
  stack.getOrDefault(DataComponentTypes.CONTAINER,ContainerComponent.DEFAULT).copyTo(items);
  boolean full=alt&&shift;var preview=previewItems(items,full);
  var copy=stack.copy();copy.remove(DataComponentTypes.CONTAINER);
  var normal=Screen.getTooltipFromItem(mc,copy);
  var tr=mc.textRenderer;float sc=c.shulkerScale/100f;
  int wrap=Math.max(40,(int)((width-12)/sc)-12),lineH=tr.fontHeight+1;
  var title=new ArrayList<OrderedText>();var lore=new ArrayList<OrderedText>();var footer=new ArrayList<OrderedText>();
  if(c.shulkerTitle&&!normal.isEmpty())title.addAll(tr.wrapLines(normal.getFirst(),wrap));
  for(int i=1;i<normal.size();i++)lore.addAll(tr.wrapLines(normal.get(i),wrap));
  int occupied=0;for(var item:items)if(!item.isEmpty())occupied++;
  footer.addAll(tr.wrapLines(Text.literal("Contains "+occupied+" stack(s)").withColor(0xAAAAAA),wrap));
  if(c.shulkerHints){
   footer.addAll(tr.wrapLines(Text.literal("Left Alt+Left Shift: ").withColor(0xFFAA00).append(Text.literal("view full contents").withColor(0xFFFFFF)),wrap));
   if(c.shulkerLock)footer.addAll(tr.wrapLines(Text.literal("Left Control: ").withColor(0xFFAA00).append(Text.literal(locked.isEmpty()?"lock tooltip":"unlock tooltip").withColor(0xFFFFFF)),wrap));
  }
  int cols=full?9:Math.max(1,Math.min(9,preview.size())),rows=full?3:Math.max(1,(preview.size()+8)/9);
  int gridW=cols*18+10,gridH=rows*18+10,gridY=5+title.size()*lineH+3;
  int loreY=gridY+gridH+6,footerY=loreY+lore.size()*lineH;
  int pw=gridW+12;for(var line:title)pw=Math.max(pw,tr.getWidth(line)+12);
  for(var line:lore)pw=Math.max(pw,tr.getWidth(line)+12);for(var line:footer)pw=Math.max(pw,tr.getWidth(line)+12);
  int ph=footerY+footer.size()*lineH+5;
  sc=Math.min(sc,Math.min((width-8)/(float)pw,(height-8)/(float)ph));
  float x=anchorX+12,y=anchorY-12,sw=pw*sc,sh=ph*sc;
  if(x+sw>width-4)x=anchorX-12-sw;x=Math.clamp(x,4,Math.max(4,width-sw-4));y=Math.clamp(y,4,Math.max(4,height-sh-4));
  left=x;top=y;right=x+sw;bottom=y+sh;
  int border=c.shulkerBorderMode==2?0:c.shulkerBorderMode==1?c.accent:c.shulkerBorderColor;
  int alpha=Math.round(c.shulkerBgOpacity*2.55f)<<24;
  ctx.createNewRootLayer();var m=ctx.getMatrices();m.pushMatrix();m.translate(x,y);m.scale(sc,sc);
  ctx.fill(0,0,pw,ph,alpha|c.shulkerBackgroundColor);
  if(border!=0){ctx.fill(0,0,pw,1,0xFF000000|border);ctx.fill(0,ph-1,pw,ph,0xFF000000|border);ctx.fill(0,0,1,ph,0xFF000000|border);ctx.fill(pw-1,0,pw,ph,0xFF000000|border);}
  for(int i=0;i<title.size();i++)ctx.drawTextWithShadow(tr,title.get(i),6,5+i*lineH,0xFFFFFFFF);
  int gridColor=c.shulkerGridColor;
  if(c.shulkerBorderMode==0&&stack.getItem() instanceof BlockItem block&&block.getBlock() instanceof ShulkerBoxBlock box&&box.getColor()!=null)gridColor=box.getColor().getEntityColor();
  ctx.fill(6,gridY,6+gridW,gridY+gridH,alpha|0x242024);
  ctx.fill(7,gridY+1,5+gridW,gridY+gridH-1,alpha|gridColor);
  ItemStack under=ItemStack.EMPTY;
  for(int i=0;i<cols*rows;i++){
   int sx=11+i%cols*18,sy=gridY+5+i/cols*18;var item=i<preview.size()?preview.get(i):ItemStack.EMPTY;
   if(c.shulkerEmptySlots||!item.isEmpty()){ctx.fill(sx-1,sy-1,sx+17,sy+17,0xFF252025);ctx.fill(sx,sy,sx+16,sy+16,alpha|gridColor);}
   if(!item.isEmpty()){ctx.drawItem(item,sx,sy);ctx.drawStackOverlay(tr,item,sx,sy,c.shulkerCompactCount&&item.getCount()>999?count(item.getCount()):null);}
   if(!locked.isEmpty()&&(mx-x)/sc>=sx&&(mx-x)/sc<sx+16&&(my-y)/sc>=sy&&(my-y)/sc<sy+16){under=item;ctx.fill(sx,sy,sx+16,sy+16,0x33FFFFFF);}
  }
  for(int i=0;i<lore.size();i++)ctx.drawTextWithShadow(tr,lore.get(i),6,loreY+i*lineH,0xFFFFFFFF);
  for(int i=0;i<footer.size();i++)ctx.drawTextWithShadow(tr,footer.get(i),6,footerY+i*lineH,0xFFFFFFFF);
  m.popMatrix();
  if(c.shulkerItemTooltip&&!under.isEmpty()){ctx.createNewRootLayer();ctx.drawItemTooltip(tr,under,mx+6,my+6);}
  return true;
 }
 private static boolean isBox(ItemStack stack){return stack!=null&&!stack.isEmpty()&&stack.getItem() instanceof BlockItem block&&block.getBlock() instanceof ShulkerBoxBlock;}
}
