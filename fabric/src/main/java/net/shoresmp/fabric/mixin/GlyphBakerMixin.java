package net.shoresmp.fabric.mixin;
import net.minecraft.client.font.GlyphBaker;
import net.minecraft.client.font.GlyphAtlasTexture;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.List;
import java.util.Set;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Smooth only ShoreMod's oversampled UI fonts; leave vanilla text and logos untouched. */
@Mixin(GlyphBaker.class)
public abstract class GlyphBakerMixin {
    @Shadow @Final private Identifier fontId;
    @Shadow @Final private List<GlyphAtlasTexture> glyphAtlases;
    @Unique private static final Set<String> SHORE_UI_FONTS=Set.of("ui","ui-bold","ui-small","heading");
    @Inject(method="bake",at=@At("RETURN"))
    private void shore$smoothUiAtlas(CallbackInfoReturnable<?> ci){
        if(!fontId.getNamespace().equals("shoremod"))return;
        if(!SHORE_UI_FONTS.contains(fontId.getPath())&&!fontId.getPath().startsWith("px_"))return;
        var linear=RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        for(var atlas:glyphAtlases)if(atlas.getSampler()!=linear)
            ((TextureSamplerAccessor)(Object)atlas).shore$setSampler(linear);
    }
}
