package net.shoresmp.fabric.mixin;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.client.gui.DrawContext;
import net.shoresmp.fabric.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(DebugHud.class)
public abstract class CleanDebugMixin {
 @Inject(method="render",at=@At("HEAD"),cancellable=true)
 private void shore$debug(DrawContext ctx,CallbackInfo ci){if(ShoreClient.config!=null&&ShoreClient.config.betterF3&&ShoreDebug.isPanelOpen()){ShoreVisuals.debug(ctx);ci.cancel();}}
}
