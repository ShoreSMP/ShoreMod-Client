package net.shoresmp.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.shoresmp.fabric.ShoreClient;
import net.shoresmp.fabric.ShoreDropPose;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class DroppedItemMixin {
    @Unique private final Quaternionf shore$rotation = new Quaternionf();

    @Inject(method="updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V", at=@At("TAIL"))
    private void shore$extractDrop(ItemEntity entity, ItemEntityRenderState state, float delta, CallbackInfo ci) {
        if (ShoreClient.config == null || !ShoreClient.config.itemPhysics) return;
        ((ShoreDropPose)state).shore$setPose(entity.isOnGround(), entity.getStack().getItem() instanceof BlockItem,
            entity.age + delta, (float)(entity.getId() * 2.3999632297 % (2 * Math.PI)));
    }

    @Inject(method="render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V", at=@At(value="INVOKE", target="Lnet/minecraft/client/render/entity/ItemEntityRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/ItemStackEntityRenderState;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/Box;)V"))
    private void shore$drawDrop(CallbackInfo ci, @Local(argsOnly=true) ItemEntityRenderState state, @Local(argsOnly=true) MatrixStack matrices) {
        var c = ShoreClient.config;
        if (c == null || !c.itemPhysics) return;
        var pose = (ShoreDropPose)state;
        boolean settled = c.dropSettle && pose.shore$grounded();
        float spin = settled ? 0 : pose.shore$age() * c.dropSpin * 0.0008f;
        float pitch = settled ? (pose.shore$block() ? 0 : (float)Math.PI / 2) : (c.dropTumble ? spin : 0);
        var bounds = state.itemRenderState.getModelBoundingBox();
        float lift = (float)(settled && !pose.shore$block() ? bounds.maxZ : -bounds.minY) + 0.015625f;

        // Replace the isolated vanilla bob and spin immediately before the original stack submission.
        matrices.pop();
        matrices.push();
        matrices.translate(0, lift, 0);
        matrices.multiply(shore$rotation.rotationYXZ(pose.shore$heading() + spin, pitch, 0));
    }
}
