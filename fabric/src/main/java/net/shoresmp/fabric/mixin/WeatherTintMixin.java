package net.shoresmp.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.WeatherRendering;
import net.shoresmp.fabric.ShoreClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WeatherRendering.class)
public abstract class WeatherTintMixin {
    @ModifyExpressionValue(method="renderPieces", at=@At(value="INVOKE", target="Lnet/minecraft/util/math/ColorHelper;getWhite(F)I"))
    private int shore$precipitationColor(int color) {
        var c = ShoreClient.config;
        return c != null && c.weatherChanger ? (color & 0xFF000000) | c.weatherTint : color;
    }
}
