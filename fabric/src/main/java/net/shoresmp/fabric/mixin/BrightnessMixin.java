package net.shoresmp.fabric.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.shoresmp.fabric.ShoreClient;
import net.shoresmp.core.SettingValues;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A temporary render-time brightness override; never writes vanilla's gamma setting. */
@Mixin(SimpleOption.class)
public abstract class BrightnessMixin {
    @Inject(method="getValue",at=@At("RETURN"),cancellable=true)
    private void shore$brightness(CallbackInfoReturnable<Object> cir){
        var config=ShoreClient.config;
        if(config==null||!config.fullbright||config.fullbrightStrength==0||net.shoresmp.fabric.BrightnessState.saving())return;
        var client=MinecraftClient.getInstance();
        if(client==null||client.options==null||(Object)this!=client.options.getGamma())return;
        if(cir.getReturnValue() instanceof Double vanilla)
            cir.setReturnValue(SettingValues.brightness(vanilla,config.fullbrightStrength));
    }
}
