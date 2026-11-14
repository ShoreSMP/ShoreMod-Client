package net.shoresmp.fabric.mixin;
import net.minecraft.block.AbstractBlock;
import net.minecraft.util.math.BlockPos;
import net.shoresmp.fabric.AntiLeakTextures;
import net.shoresmp.fabric.ShoreClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class TextureSeedMixin {
    @Inject(method="getRenderingSeed",at=@At("RETURN"),cancellable=true)
    private void shore$privateTextureSeed(BlockPos pos,CallbackInfoReturnable<Long> ci){
        if(ShoreClient.config!=null&&ShoreClient.config.antiLeakTextures)
            ci.setReturnValue(AntiLeakTextures.seed(ci.getReturnValue()));
    }
}
