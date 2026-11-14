package net.shoresmp.fabric.mixin;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.DisconnectionInfo;
import net.shoresmp.fabric.ShoreClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientCommonNetworkHandler.class)
public abstract class DisconnectMixin {
    @Inject(method="onDisconnected",at=@At("HEAD"))
    private void shore$disconnect(DisconnectionInfo info,CallbackInfo ci){
        String reason=info.reason().getString();
        net.shoresmp.fabric.ShoreTools.lastDisconnectReason=reason;
        if(ShoreClient.recorder!=null)ShoreClient.recorder.disconnected(reason);
    }
}
