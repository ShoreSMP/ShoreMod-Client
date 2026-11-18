package net.shoresmp.fabric.mixin;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.shoresmp.fabric.ShoreMobOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class MobStateMixin implements ShoreMobOverlay.State {
    @Unique private int shore$mobFill;
    @Override public int shore$getFill() { return shore$mobFill; }
    @Override public void shore$setFill(int value) { shore$mobFill = value; }
}
