package net.shoresmp.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.attribute.timeline.TrackAttributeModification;
import net.shoresmp.fabric.ShoreWorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TrackAttributeModification.class)
public abstract class VisualTimeMixin {
    @ModifyExpressionValue(method="applyTimeBased", at=@At(value="INVOKE", target="Ljava/util/function/LongSupplier;getAsLong()J", remap=false))
    private long shore$visualTime(long serverTime) {
        return ShoreWorldView.visualTime(serverTime);
    }
}
