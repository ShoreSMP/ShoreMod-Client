package net.shoresmp.fabric.mixin;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.shoresmp.fabric.ShoreChatHeads;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets={"net.minecraft.client.gui.hud.ChatHud$Hud","net.minecraft.client.gui.hud.ChatHud$Interactable"})
public abstract class ChatHeadsBackendMixin {
 @Shadow @Final private DrawContext context;
 @Inject(method="text",at=@At("HEAD"))
 private void shore$head(int y,float opacity,OrderedText text,CallbackInfoReturnable<Boolean> ci){ShoreChatHeads.draw(context,text,0,y,Math.round(opacity*255)<<24);}
}
