"""Inspect actual target signatures, resolving the chat render lambda after mapping migration."""
import json,os,re,subprocess,sys
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=sys.argv[1]
cp=json.loads((root/'fabric/build/compat-classpath.json').read_text());classpath=os.pathsep.join(cp)
if not target.startswith('26.'):
 p=subprocess.run(['javap','-p','-c','-classpath',classpath,'net.minecraft.client.gui.hud.ChatHud'],capture_output=True,text=True)
 (root/'compat-api.txt').write_text(p.stdout+p.stderr)
 sys.exit(p.returncode)
classes=['net.minecraft.client.gui.components.ChatComponent','net.minecraft.client.gui.GuiGraphicsExtractor','net.minecraft.world.inventory.ContainerInput']
report=[]
api={}
classes+=['net.minecraft.client.Camera','net.minecraft.client.gui.font.GlyphStitcher','net.minecraft.client.renderer.entity.player.AvatarRenderer','net.minecraft.client.multiplayer.ClientPacketListener']
classes+=['net.minecraft.client.Minecraft','net.minecraft.client.gui.Gui','net.minecraft.client.gui.Hud','net.minecraft.client.renderer.GameRenderer','net.minecraft.client.renderer.LevelRenderer','net.minecraft.client.multiplayer.MultiPlayerGameMode','net.minecraft.client.input.MouseButtonInfo','net.minecraft.client.input.KeyEvent','net.minecraft.client.input.CharacterEvent','com.mojang.blaze3d.platform.InputConstants','com.mojang.blaze3d.Blaze3D','net.minecraft.client.MouseHandler','net.minecraft.client.gui.screens.inventory.AbstractContainerScreen','net.minecraft.client.renderer.texture.AbstractTexture','net.minecraft.client.gui.font.glyphs.BakedGlyph']
for name in classes:
 p=subprocess.run(['javap','-p','-classpath',classpath,name],capture_output=True,text=True);report.append(p.stdout+p.stderr)
 api[name]=p.stdout
 if name.endswith('ChatComponent') and p.returncode==0:
  choices=re.findall(r'(?:private|public).*?\b([\w$]+)\([^;\n]*ChatGraphicsAccess[^;\n]*GuiMessage\$Line[^;\n]*\);',p.stdout)
  if choices:
   file=root/'fabric/src/main/java/net/shoresmp/fabric/mixin/ChatHudMixin.java'
   s=file.read_text(encoding='utf-8').replace('method="method_75802"','method="'+choices[0]+'"')
   file.write_text(s,encoding='utf-8');print('Resolved chat renderer:',choices[0])
(root/'compat-api.txt').write_text('\n'.join(report))
if ' getFov(' not in api['net.minecraft.client.renderer.GameRenderer']:
 camera=api['net.minecraft.client.Camera']
 choices=re.findall(r'\b(float|double)\s+(\w*[Ff]ov\w*)\(float\);',camera)
 choices.sort(key=lambda value:0 if value[1]=='calculateFov' else 1)
 assert choices,'Cannot locate the target world-camera FOV calculation'
 result,method=choices[0]
 suffix='F' if result=='float' else 'D';boxed='Float' if result=='float' else 'Double'
 mixins=root/'fabric/src/main/java/net/shoresmp/fabric/mixin'
 file=mixins/'GameRendererMixin.java';s=file.read_text(encoding='utf-8')
 s=s[:s.index('    // Only the world FOV')]+ '}\n';file.write_text(s,encoding='utf-8')
 code='''package net.shoresmp.fabric.mixin;
import net.minecraft.client.Camera;
import net.shoresmp.fabric.ShoreClient;
import net.shoresmp.fabric.ShoreTools;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Camera.class)
public abstract class CameraZoomMixin {
 @Inject(method="METHOD(F)SUFFIX",at=@At("RETURN"),cancellable=true)
 private void shore$zoom(CallbackInfoReturnable<BOXED> cir){
  if(ShoreClient.config==null)return;
  double divisor=ShoreTools.fovDivisor();
  if(divisor!=1)cir.setReturnValue((RESULT)(cir.getReturnValueSUFFIX()/divisor));
 }
}
'''.replace('METHOD',method).replace('SUFFIX',suffix).replace('BOXED',boxed).replace('RESULT',result)
 (mixins/'CameraZoomMixin.java').write_text(code,encoding='utf-8')
 file=root/'fabric/src/main/resources/shoremod.mixins.json';meta=json.loads(file.read_text());meta['client'].append('CameraZoomMixin');file.write_text(json.dumps(meta,indent=2))
 print('Resolved world camera zoom:',method,result)
print('Saved target API signature report')

