package net.shoresmp.fabric.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.shoresmp.fabric.ShoreMobOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MobOutlineMixin {
    @Inject(method="hasOutline", at=@At("HEAD"), cancellable=true)
    private void shore$outline(Entity entity, CallbackInfoReturnable<Boolean> ci) {
        if (ShoreMobOverlay.selected(entity)) ci.setReturnValue(true);
    }
}
