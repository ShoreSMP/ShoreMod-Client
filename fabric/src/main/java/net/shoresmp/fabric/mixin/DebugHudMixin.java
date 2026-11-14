package net.shoresmp.fabric.mixin;
import net.minecraft.client.gui.hud.DebugHud;
import net.shoresmp.fabric.ShoreClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(DebugHud.class)
public abstract class DebugHudMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void shore$privacy(CallbackInfo ci){if(ShoreClient.config!=null&&ShoreClient.config.privacy&&ShoreClient.config.hideDebug)ci.cancel();}
}
