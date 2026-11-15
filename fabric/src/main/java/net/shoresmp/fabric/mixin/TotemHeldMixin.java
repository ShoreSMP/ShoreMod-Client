package net.shoresmp.fabric.mixin;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.shoresmp.fabric.ShoreVisuals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.sugar.Local;
@Mixin(HeldItemRenderer.class)
public abstract class TotemHeldMixin {
 @Inject(method="renderFirstPersonItem",at=@At("HEAD"),cancellable=true)
 private void shore$heldTotem(CallbackInfo ci,@Local(argsOnly=true) ItemStack stack){if(ShoreVisuals.customTotem()&&stack.isOf(Items.TOTEM_OF_UNDYING))ci.cancel();}
}
