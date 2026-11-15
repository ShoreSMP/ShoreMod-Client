package net.shoresmp.fabric.mixin;
import net.minecraft.client.render.GameRenderer;
import net.shoresmp.fabric.ShoreMotionBlur;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public abstract class MotionBlurMixin {
 private static final String SHORE_WORLD_RENDER="renderWorld";
 @Inject(method=SHORE_WORLD_RENDER,at=@At("TAIL"))
 private void shore$worldHistory(CallbackInfo ci){ShoreMotionBlur.capture();}
 @Inject(method="render",at=@At("TAIL"))
 private void shore$historyAdvance(CallbackInfo ci){ShoreMotionBlur.finishFrame();}
}
