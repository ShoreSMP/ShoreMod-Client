package net.shoresmp.fabric.mixin;

import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.shoresmp.fabric.ShoreDropPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public abstract class DropPoseStateMixin implements ShoreDropPose {
    @Unique private boolean shore$grounded, shore$block;
    @Unique private float shore$age, shore$heading;

    public void shore$setPose(boolean grounded, boolean block, float age, float heading) {
        shore$grounded = grounded;
        shore$block = block;
        shore$age = age;
        shore$heading = heading;
    }
    public boolean shore$grounded() { return shore$grounded; }
    public boolean shore$block() { return shore$block; }
    public float shore$age() { return shore$age; }
    public float shore$heading() { return shore$heading; }
}
