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
 private void shore$debug(DrawContext ctx,CallbackInfo ci){
     if(ShoreF3.active()){
         ShoreF3.draw(ctx);
         if(!ShoreF3.charts())ci.cancel();
     }
 }
 @Inject(method="drawText",at=@At("HEAD"),cancellable=true)
 private void shore$hideVanillaText(CallbackInfo ci){if(ShoreF3.active())ci.cancel();}
}
