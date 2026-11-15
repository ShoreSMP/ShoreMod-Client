package net.shoresmp.fabric.mixin;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.shoresmp.fabric.ShoreClient;
import net.shoresmp.fabric.ShoreCrystals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
@Mixin(EndCrystalEntityRenderer.class)
public abstract class CrystalMixin {
 private static final String SHORE_CRYSTAL_RENDER="render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V";
 private static final String SHORE_CRYSTAL_STATE="updateRenderState(Lnet/minecraft/entity/decoration/EndCrystalEntity;Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;F)V";
 private static final String SHORE_CRYSTAL_LAYER="Lnet/minecraft/client/render/entity/EndCrystalEntityRenderer;END_CRYSTAL:Lnet/minecraft/client/render/RenderLayer;";
 private static final String SHORE_CRYSTAL_BOB="getYOffset";
 @Inject(method=SHORE_CRYSTAL_STATE,at=@At("TAIL"))
 private void shore$crystalState(CallbackInfo ci,@Local(argsOnly=true) EndCrystalEntityRenderState state){
  var c=ShoreClient.config;if(c==null||!c.customCrystals)return;
  state.age*=c.crystalSpeed/100f;state.baseVisible&=c.crystalBase;if(!c.crystalBeam)state.beamOffset=null;
 }
 @Inject(method=SHORE_CRYSTAL_BOB,at=@At("RETURN"),cancellable=true)
 private static void shore$crystalBob(CallbackInfoReturnable<Float> cir){
  var c=ShoreClient.config;if(c!=null&&c.customCrystals)cir.setReturnValue(cir.getReturnValueF()*c.crystalBob/100f);
 }
 @Inject(method=SHORE_CRYSTAL_RENDER,at=@At("HEAD"))
 private void shore$crystalScale(CallbackInfo ci,@Local(argsOnly=true) MatrixStack matrices){
  matrices.push();var c=ShoreClient.config;if(c!=null&&c.customCrystals){float size=c.crystalSize/100f;matrices.scale(size,size,size);}
 }
 @Inject(method=SHORE_CRYSTAL_RENDER,at=@At("RETURN"))
 private void shore$crystalScaleEnd(CallbackInfo ci,@Local(argsOnly=true) MatrixStack matrices){matrices.pop();}
 @ModifyExpressionValue(method=SHORE_CRYSTAL_RENDER,at=@At(value="FIELD",target=SHORE_CRYSTAL_LAYER))
 private RenderLayer shore$crystalLayer(RenderLayer layer){return ShoreCrystals.layer(layer);}
}
