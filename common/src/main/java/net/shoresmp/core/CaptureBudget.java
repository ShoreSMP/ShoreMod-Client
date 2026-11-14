package net.shoresmp.core;
/** Bounds recording work before any GPU readback; one caller observes render frames. */
public final class CaptureBudget {
 private long lastFrame,next;
 private double frameNanos=16_666_667;
 private volatile long processingNanos,readbackNanos;
 public void reset(){lastFrame=next=0;frameNanos=16_666_667;processingNanos=readbackNanos=0;}
 public void observe(long now){if(lastFrame!=0){long elapsed=now-lastFrame;if(elapsed>0&&elapsed<250_000_000L)frameNanos=frameNanos*0.9+elapsed*0.1;}lastFrame=now;}
 public void processed(long elapsed){processingNanos=Math.max(0,elapsed);}
 public void requested(long elapsed){readbackNanos=Math.max(0,elapsed);}
 public boolean protectingSlowFrames(){return frameNanos>50_000_000L;}
 public boolean allow(long now,int requested,long framebufferPixels,boolean protect){
  if(now<next||framebufferPixels<=0||framebufferPixels>16_777_216L)return false;
  int fps=Math.max(1,Math.min(60,requested));
  if(protect){
   if(frameNanos>50_000_000L){next=now+250_000_000L;return false;}
   fps=Math.min(fps,8);
   if(framebufferPixels>8_000_000L)fps=Math.min(fps,2);else if(framebufferPixels>4_000_000L)fps=Math.min(fps,3);
   if(frameNanos>33_000_000L)fps=Math.min(fps,2);else if(frameNanos>22_000_000L)fps=Math.min(fps,4);
  }
  long period=1_000_000_000L/fps;
  if(protect)period=Math.max(period,Math.min(2_000_000_000L,Math.max(readbackNanos*10,processingNanos*2)));
  next=Math.max(now-period,next)+period;return true;
 }
}
