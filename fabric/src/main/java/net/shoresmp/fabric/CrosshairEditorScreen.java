package net.shoresmp.fabric;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.shoresmp.core.PixelCanvas;
import net.shoresmp.core.SettingValues;
import java.util.*;
import org.lwjgl.glfw.GLFW;

/** Local pixel painter. Closing without Save leaves the active crosshair unchanged. */
public final class CrosshairEditorScreen extends Screen {
 private final Screen parent;
 private final ShoreUi ui=new ShoreUi();
 private final PixelCanvas canvas;
 private record Hit(float x,float y,float w,float h,Runnable action){}
 private final List<Hit> hits=new ArrayList<>();
 private int color=0xFFFFFFFF,lastX=-1,lastY=-1,strokeColor;
 private boolean eraser,painting,colorFocus;
 private String hex="FFFFFF",message="Left drag paints; right drag erases";
 private float gridX,gridY,cell;
 public CrosshairEditorScreen(Screen parent){super(Text.literal("Paint crosshair"));this.parent=parent;canvas=new PixelCanvas(ShoreClient.config.crosshairPaint);}
 private void button(String label,float x,float y,float w,Runnable action){
  ui.round(x,y,w,28,6,0xFF292F38);ui.centered(label,false,11,x+w/2,y+8,0xFFF4F5F7,w-4);hits.add(new Hit(x,y,w,28,action));
 }
 @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
  float unit=Math.min(ShoreUi.unit(client,100),Math.min(client.getWindow().getFramebufferWidth()/640f,client.getWindow().getFramebufferHeight()/470f));
  ui.begin(ctx,Math.max(0.1f,unit));ui.sans=false;ui.noBold=true;hits.clear();
  ui.rect(0,0,ui.W,ui.H,0xC0101218);
  float w=Math.min(620,ui.W-24),h=Math.min(450,ui.H-20),x=(ui.W-w)/2,y=(ui.H-h)/2;
  ui.outline(x,y,w,h,10,0xFF35404D,0xFF151A22);
  ui.text("Paint your crosshair",true,17,x+18,y+16,0xFFF4F5F7,w-36);
  ui.text(message,false,11,x+18,y+42,0xFFB3BFCC,w-36);
  float side=Math.max(96,Math.min(h-126,w-198));cell=side/PixelCanvas.SIZE;gridX=x+18;gridY=y+64;
  for(int py=0;py<32;py++)for(int px=0;px<32;px++){
   float gx=gridX+px*cell,gy=gridY+py*cell;
   ui.rect(gx,gy,cell,cell,((px+py)&1)==0?0xFF49505A:0xFF303640);
   int p=canvas.get(px,py);if((p>>>24)!=0)ui.rect(gx,gy,cell,cell,p);
  }
  ui.rect(gridX+16*cell,gridY,0.5f,side,0x5590D8FF);ui.rect(gridX,gridY+16*cell,side,0.5f,0x5590D8FF);
  float bx=gridX+side+16,by=gridY,bw=Math.max(110,w-side-52);
  button(eraser?"Eraser selected":"Brush selected",bx,by,bw,()->eraser=!eraser);
  int[] palette={0xFFFFFF,0x111111,0xFF5555,0x55FF88,0x55CCFF,0xFFDD55,0xBB77FF,0xFF88CC};
  for(int i=0;i<palette.length;i++){final int rgb=palette[i];float cx=bx+(i%4)*26,cy=by+38+(i/4)*26;ui.round(cx,cy,22,22,4,0xFF000000|rgb);hits.add(new Hit(cx,cy,22,22,()->{color=0xFF000000|rgb;hex=String.format(Locale.ROOT,"%06X",rgb);eraser=false;}));}
  ui.text("Hex color",false,11,bx,by+98,0xFFB3BFCC,bw);
  boolean valid=SettingValues.color(hex).isPresent();ui.outline(bx,by+115,bw,28,5,valid?0xFF53677A:0xFFFF7777,0xFF202630);
  ui.text("#"+hex+(colorFocus?"_":""),false,12,bx+8,by+123,0xFFF4F5F7,bw-16);hits.add(new Hit(bx,by+115,bw,28,()->{colorFocus=true;hex="";}));
  button("Undo",bx,by+154,bw/2-3,canvas::undo);button("Redo",bx+bw/2+3,by+154,bw/2-3,canvas::redo);
  button("Clear",bx,by+190,bw,canvas::clear);
  ui.text("Preview",false,11,bx,by+230,0xFFB3BFCC,bw);
  ui.round(bx,by+248,bw,54,5,0xFF68784F);
  for(int py=0;py<32;py++)for(int px=0;px<32;px++){int p=canvas.get(px,py);if((p>>>24)!=0)ui.rect(bx+bw/2-16+px,by+259+py,1,1,p);}
  float bottom=y+h-42;button("Cancel",x+w-194,bottom,80,this::close);button("Save and use",x+w-104,bottom,88,this::save);
  ui.end();
 }
 private boolean paint(double gx,double gy,boolean fresh,int button){
  if(cell<=0)return false;
  int px=(int)Math.floor((ui.fromGui(gx)-gridX)/cell),py=(int)Math.floor((ui.fromGui(gy)-gridY)/cell);
  if(!PixelCanvas.inside(px,py)){lastX=lastY=-1;return false;}
  if(fresh){canvas.begin();painting=true;strokeColor=button==1||eraser?0:color;}
  canvas.line(lastX<0?px:lastX,lastY<0?py:lastY,px,py,strokeColor);lastX=px;lastY=py;return true;
 }
 @Override public boolean mouseClicked(Click click,boolean doubled){
  colorFocus=false;int button=click.button();if(button!=0&&button!=1)return true;
  if(paint(click.x(),click.y(),true,button))return true;
  if(button==0){float x=ui.fromGui(click.x()),y=ui.fromGui(click.y());for(Hit hit:hits)if(x>=hit.x()&&y>=hit.y()&&x<hit.x()+hit.w()&&y<hit.y()+hit.h()){hit.action().run();break;}}
  return true;
 }
 @Override public boolean mouseDragged(Click click,double dx,double dy){if(painting)paint(click.x(),click.y(),false,click.button());return true;}
 @Override public boolean mouseReleased(Click click){canvas.end();painting=false;lastX=lastY=-1;return true;}
 @Override public boolean charTyped(CharInput input){if(colorFocus)for(char c:input.asString().toCharArray())if(hex.length()<6&&Character.digit(c,16)>=0){hex+=Character.toUpperCase(c);applyColor();}return true;}
 private void applyColor(){SettingValues.color(hex).ifPresent(rgb->{color=0xFF000000|rgb;eraser=false;});}
 @Override public boolean keyPressed(KeyInput input){if(input.key()==GLFW.GLFW_KEY_ESCAPE){close();return true;}if(colorFocus&&input.key()==GLFW.GLFW_KEY_BACKSPACE&&!hex.isEmpty()){hex=hex.substring(0,hex.length()-1);return true;}if(input.key()==GLFW.GLFW_KEY_ENTER){applyColor();colorFocus=false;}return true;}
 private void save(){
  canvas.end();try{
   var dir=ShoreHud.CrosshairImage.folder();java.nio.file.Files.createDirectories(dir);
   var image=new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);
   image.setRGB(0,0,32,32,canvas.copy(),0,32);
   var temporary=java.nio.file.Files.createTempFile(dir,"paint-",".png");
   try{if(!javax.imageio.ImageIO.write(image,"png",temporary.toFile()))throw new java.io.IOException("PNG encoder unavailable");java.nio.file.Files.move(temporary,dir.resolve("painted-crosshair.png"),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}finally{java.nio.file.Files.deleteIfExists(temporary);}
   var c=ShoreClient.config;c.crosshairPaint=canvas.copy();c.crosshairImage=dir.resolve("painted-crosshair.png").toAbsolutePath().toString();c.crosshairMode=1;c.crosshair=true;c.crosshairTint=false;c.crosshairImageSize=32;c.save();ShoreHud.CrosshairImage.invalidate();
   if(parent instanceof ShoreScreen screen)screen.showModule("crosshair");close();
  }catch(Exception e){message="Could not save; drawing kept open. Try again.";ShoreClient.LOG.error("Crosshair painting save failed",e);}
 }
 @Override public void close(){client.setScreen(parent);}
 @Override public boolean shouldPause(){return false;}
}
