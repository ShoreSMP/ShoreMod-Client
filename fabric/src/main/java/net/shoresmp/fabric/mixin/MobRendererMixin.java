package net.shoresmp.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.shoresmp.fabric.ShoreMobOverlay;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class MobRendererMixin {
    @Inject(method="updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at=@At("TAIL"))
    private void shore$select(LivingEntity entity, LivingEntityRenderState state, float delta, CallbackInfo ci) {
        ((ShoreMobOverlay.State)state).shore$setFill(ShoreMobOverlay.fill(entity));
    }

    @ModifyExpressionValue(method="render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
        at=@At(value="INVOKE", target="Lnet/minecraft/client/render/entity/LivingEntityRenderer;getMixColor(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;)I"))
    private int shore$fill(int vanilla, @Local(argsOnly=true) LivingEntityRenderState state) {
        int color = ((ShoreMobOverlay.State)state).shore$getFill();
        return color == 0 ? vanilla : ColorHelper.mix(vanilla, color);
    }
}
