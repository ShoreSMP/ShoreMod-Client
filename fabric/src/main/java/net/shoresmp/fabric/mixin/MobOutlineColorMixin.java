package net.shoresmp.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.shoresmp.fabric.ShoreClient;
import net.shoresmp.fabric.ShoreMobOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public abstract class MobOutlineColorMixin {
    @WrapOperation(method="updateRenderState", at=@At(value="INVOKE", target="Lnet/minecraft/entity/Entity;getTeamColorValue()I"))
    private int shore$outlineColor(Entity entity, Operation<Integer> original) {
        return ShoreMobOverlay.selected(entity) ? ShoreClient.config.mobOutlineColor : original.call(entity);
    }
}
