package net.shoresmp.fabric.mixin;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.shoresmp.fabric.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
 @Inject(method="renderBackground",at=@At("HEAD"),cancellable=true)
 private void shore$clearBackground(CallbackInfo ci){var c=ShoreClient.config;if(c!=null&&c.guiTweaks&&c.guiNoDim&&(Object)this instanceof HandledScreen)ci.cancel();}
}
