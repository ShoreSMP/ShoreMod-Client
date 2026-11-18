package net.shoresmp.fabric.mixin;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import net.shoresmp.fabric.ShoreClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class LocalWeatherMixin {
    @Inject(method="getRainGradient", at=@At("HEAD"), cancellable=true)
    private void shore$localRain(CallbackInfoReturnable<Float> result) {
        var c = ShoreClient.config;
        if (c != null && c.weatherChanger && c.weatherMode != 0 && (Object)this instanceof ClientWorld world && world.getDimension().hasSkyLight()) {
            result.setReturnValue(c.weatherMode == 1 ? 0f : c.weatherIntensity / 100f);
        }
    }

    @Inject(method="getThunderGradient", at=@At("HEAD"), cancellable=true)
    private void shore$localThunder(CallbackInfoReturnable<Float> result) {
        var c = ShoreClient.config;
        if (c != null && c.weatherChanger && c.weatherMode != 0 && (Object)this instanceof ClientWorld world && world.getDimension().hasSkyLight()) {
            result.setReturnValue(c.weatherMode == 3 ? c.weatherIntensity / 100f : 0f);
        }
    }
}
