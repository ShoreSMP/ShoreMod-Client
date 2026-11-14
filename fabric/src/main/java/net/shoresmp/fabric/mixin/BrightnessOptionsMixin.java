package net.shoresmp.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.option.GameOptions;
import net.shoresmp.fabric.BrightnessState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameOptions.class)
public abstract class BrightnessOptionsMixin {
    @WrapMethod(method="write")
    private void shore$preserveSavedBrightness(Operation<Void> original){
        BrightnessState.withoutOverride(()->original.call());
    }
}
