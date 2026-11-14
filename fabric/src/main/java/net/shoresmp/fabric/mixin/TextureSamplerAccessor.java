package net.shoresmp.fabric.mixin;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.gl.GpuSampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(AbstractTexture.class)
public interface TextureSamplerAccessor {
    @Accessor("sampler") void shore$setSampler(GpuSampler sampler);
}
