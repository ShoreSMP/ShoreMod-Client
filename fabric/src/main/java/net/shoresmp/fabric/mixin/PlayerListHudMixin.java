package net.shoresmp.fabric.mixin;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.shoresmp.fabric.ShoreClient;
import net.shoresmp.fabric.ShoreBadge;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @Inject(method="getPlayerName",at=@At("RETURN"),cancellable=true)
    private void shore$tabBadge(PlayerListEntry entry,CallbackInfoReturnable<Text> cir){
        if(ShoreClient.config!=null && ShoreClient.config.badges && ShoreClient.config.badgeTab && cir.getReturnValue()!=null
            && ShoreClient.roster.users().containsKey(entry.getProfile().id()))
            cir.setReturnValue(ShoreBadge.prependTab(cir.getReturnValue()));
    }
}
