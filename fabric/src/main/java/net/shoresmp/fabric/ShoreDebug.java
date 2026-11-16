package net.shoresmp.fabric;
import net.minecraft.client.MinecraftClient;
/** The full F3 panel is distinct from individual enabled debug entries such as hitboxes. */
public final class ShoreDebug {
 private ShoreDebug(){}
 public static boolean isPanelOpen(){var mc=MinecraftClient.getInstance();return mc.debugHudEntryList.isF3Enabled();}
}
