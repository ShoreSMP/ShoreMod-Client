package net.shoresmp.fabric.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.shoresmp.fabric.ShoreCommandRouting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ShoreCommandTreeMixin {
    @Inject(method="onCommandTree", at=@At("TAIL"))
    private void shore$commands(CallbackInfo info) {
        ShoreCommandRouting.addSuggestions(((ClientPlayNetworkHandler)(Object)this).getCommandDispatcher());
    }
}
