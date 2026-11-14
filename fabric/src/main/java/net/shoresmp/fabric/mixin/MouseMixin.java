package net.shoresmp.fabric.mixin;
import net.minecraft.client.Mouse;
import net.shoresmp.fabric.ShoreClient;
import net.shoresmp.fabric.ShoreTools;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Mouse.class)
public abstract class MouseMixin {
    // Counts clicks made while playing (not in menus) for the CPS display.
    @Inject(method="onMouseButton",at=@At("HEAD"))
    private void shore$cps(long window,net.minecraft.client.input.MouseInput input,int action,CallbackInfo ci){
        if(action==1&&net.minecraft.client.MinecraftClient.getInstance().currentScreen==null)ShoreTools.click(input.button());
    }
    // While zoomed, scale how far the camera turns. Your mouse sensitivity setting is left untouched.
    @ModifyArg(method="updateMouse",at=@At(value="INVOKE",target="Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"),index=0)
    private double shore$zoomYaw(double dx){return dx*ShoreTools.lookScale();}
    @ModifyArg(method="updateMouse",at=@At(value="INVOKE",target="Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"),index=1)
    private double shore$zoomPitch(double dy){return dy*ShoreTools.lookScale();}
    // While zooming, the scroll wheel adjusts zoom instead of switching hotbar slots.
    @Inject(method="onMouseScroll",at=@At("HEAD"),cancellable=true)
    private void shore$zoomScroll(long window,double horizontal,double vertical,CallbackInfo ci){
        if(ShoreClient.config!=null&&ShoreTools.scroll(vertical))ci.cancel();
    }
}
