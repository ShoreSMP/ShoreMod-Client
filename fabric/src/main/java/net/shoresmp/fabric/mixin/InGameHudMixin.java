package net.shoresmp.fabric.mixin;
import net.minecraft.client.gui.hud.InGameHud;
import net.shoresmp.fabric.ShoreClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method="renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",at=@At("HEAD"),cancellable=true)
    private void shore$privacy(CallbackInfo ci){if(ShoreClient.config!=null&&ShoreClient.config.privacy&&ShoreClient.config.hideScoreboard)ci.cancel();}
}
