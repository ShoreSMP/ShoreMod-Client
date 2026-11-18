package net.shoresmp.fabric.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.shoresmp.fabric.ShoreClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class PlayerFovMixin {
    @Inject(method="getFovMultiplier", at=@At("RETURN"), cancellable=true)
    private void shore$dynamicFov(boolean firstPerson, float effectsScale, CallbackInfoReturnable<Float> result) {
        var c = ShoreClient.config;
        if (c == null || !c.fovChanger || effectsScale == 0) return;
        var player = (AbstractClientPlayerEntity)(Object)this;
        if (firstPerson && player.isUsingSpyglass()) return;
        float adjustment = 0;
        if (player.isSprinting()) adjustment += c.sprintFov;
        if (player.getAbilities().flying) adjustment += c.flyingFov;
        if (c.swiftnessFov != 0 && player.hasStatusEffect(StatusEffects.SPEED)) adjustment += c.swiftnessFov;
        if (c.slownessFov != 0 && player.hasStatusEffect(StatusEffects.SLOWNESS)) adjustment -= c.slownessFov;
        if (c.bowFov != 0 && player.isUsingItem() && player.getActiveItem().isOf(Items.BOW)) {
            float pull = Math.min(1, player.getItemUseTime() / 20f);
            adjustment -= c.bowFov * pull * pull;
        }
        if (adjustment != 0) result.setReturnValue(Math.clamp(result.getReturnValueF() + effectsScale * adjustment / 100f, 0.05f, 3f));
    }
}
