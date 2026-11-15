package net.shoresmp.fabric.mixin;

import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.text.*;
import net.minecraft.util.Identifier;
import net.shoresmp.fabric.ShoreClient;
import net.shoresmp.fabric.ShoreBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerBadgeMixin {
    @Inject(method="updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",at=@At("TAIL"))
    private void shore$badge(PlayerLikeEntity player,PlayerEntityRenderState state,float delta,CallbackInfo ci){
        if(ShoreClient.config==null||!ShoreClient.config.badges||!ShoreClient.config.badgeNametags||!ShoreBadge.hasBadge(player.getUuid()))return;
        if(state.displayName==null&&ShoreBadge.isLocal(player.getUuid()))state.displayName=player.getDisplayName();
        if(state.displayName!=null)state.displayName=ShoreBadge.prepend(state.displayName);
    }
}
