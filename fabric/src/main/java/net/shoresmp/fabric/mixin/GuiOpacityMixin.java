package net.shoresmp.fabric.mixin;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.shoresmp.fabric.ShoreVisuals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import com.llamalad7.mixinextras.sugar.Local;
@Mixin(DrawContext.class)
public abstract class GuiOpacityMixin {
 @ModifyVariable(method="drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIII)V",at=@At("HEAD"),argsOnly=true,ordinal=4)
 private int shore$guiOpacity(int color,@Local(argsOnly=true) Identifier texture){return texture.getPath().startsWith("container/")?ShoreVisuals.guiTint(color):color;}
 @ModifyVariable(method="drawTexturedQuad(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIIFFFFI)V",at=@At("HEAD"),argsOnly=true,ordinal=4)
 private int shore$textureOpacity(int color,@Local(argsOnly=true) Identifier texture){return texture.getPath().startsWith("textures/gui/container/")?ShoreVisuals.guiTint(color):color;}
}
