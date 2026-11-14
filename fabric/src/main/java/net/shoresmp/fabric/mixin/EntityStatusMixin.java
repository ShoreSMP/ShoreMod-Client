package net.shoresmp.fabric.mixin;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.shoresmp.fabric.ShorePvp;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPlayNetworkHandler.class)
public abstract class EntityStatusMixin {
    // After forceMainThread, so this only runs once, on the client thread, with the world available.
    @Inject(method="onEntityStatus",at=@At(value="INVOKE",target="Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",shift=At.Shift.AFTER))
    private void shore$totemPops(EntityStatusS2CPacket packet,CallbackInfo ci){
        var client=net.minecraft.client.MinecraftClient.getInstance();
        if(client.world==null)return;
        var entity=packet.getEntity(client.world);
        if(entity!=null)ShorePvp.onEntityStatus(entity,packet.getStatus());
    }
}
