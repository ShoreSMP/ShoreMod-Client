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
        if(config==null||(!config.fullbright&&!(config.fovChanger&&config.customBaseFov)))return;
        var client=MinecraftClient.getInstance();
        if(client==null||client.options==null)return;
        if(config.fullbright&&(Object)this==client.options.getGamma()
            &&cir.getReturnValue() instanceof Double vanilla&&!net.shoresmp.fabric.BrightnessState.saving()){
            cir.setReturnValue(config.brightnessMode==1?config.brightnessFixed/100.0:SettingValues.brightness(vanilla,config.fullbrightStrength));
        }else if(config.fovChanger&&config.customBaseFov&&(Object)this==client.options.getFov()&&!net.shoresmp.fabric.BrightnessState.saving()){
            cir.setReturnValue(config.baseFov);
        }
    }
}
