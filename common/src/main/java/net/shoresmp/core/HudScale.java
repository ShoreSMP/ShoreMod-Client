package net.shoresmp.core;
/** Convert HUD design units to game GUI units without inheriting Minecraft's GUI zoom. */
public final class HudScale {
 private HudScale(){}
 public static float guiUnits(int framebufferHeight,double minecraftGuiScale,int overallPercent,int elementPercent){
  float screenUnit=Math.clamp(framebufferHeight/720f,0.5f,4f);
  double gui=Double.isFinite(minecraftGuiScale)&&minecraftGuiScale>0?minecraftGuiScale:1;
  return (float)(screenUnit*Math.clamp(overallPercent,50,250)/100f*Math.clamp(elementPercent,50,250)/100f/gui);
 }
}
