package net.shoresmp.fabric.mixin;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.shoresmp.fabric.ShoreClient;
import net.shoresmp.fabric.ShoreTools;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method="render",at=@At("TAIL"))
    private void shore$capture(CallbackInfo ci){if(ShoreClient.recorder!=null)ShoreClient.recorder.capture();}
    @Inject(method="showFloatingItem",at=@At("HEAD"),cancellable=true)
    private void shore$totem(net.minecraft.item.ItemStack stack,CallbackInfo ci){if(net.shoresmp.fabric.ShoreVisuals.customTotem()&&stack.isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING)){net.shoresmp.fabric.ShoreVisuals.pop();ci.cancel();}}
    // Only the world FOV (changingFov=true) is zoomed; the held-item render keeps its normal FOV.
    @Inject(method="getFov",at=@At("RETURN"),cancellable=true)
    private void shore$zoom(Camera camera,float tickDelta,boolean changingFov,CallbackInfoReturnable<Float> cir){
        if(!changingFov||ShoreClient.config==null)return;
        double divisor=ShoreTools.fovDivisor();
        if(divisor!=1)cir.setReturnValue((float)(cir.getReturnValueF()/divisor));
        net.shoresmp.fabric.ShoreVisuals.fov(cir.getReturnValueF());
    }
}
