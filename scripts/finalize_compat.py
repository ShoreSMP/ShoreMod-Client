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
  net.shoresmp.fabric.ShoreVisuals.fov((float)cir.getReturnValueSUFFIX());
 }
}
'''.replace('METHOD',method).replace('SUFFIX',suffix).replace('BOXED',boxed).replace('RESULT',result)
 (mixins/'CameraZoomMixin.java').write_text(code,encoding='utf-8')
 file=root/'fabric/src/main/resources/shoremod.mixins.json';meta=json.loads(file.read_text());meta['client'].append('CameraZoomMixin');file.write_text(json.dumps(meta,indent=2))
 print('Resolved world camera zoom:',method,result)
print('Saved target API signature report')


# Resolve GUI resizing against the target API rather than assuming the old no-arg event.
if target.startswith('26.'):
 screen=root/'fabric/src/main/java/net/shoresmp/fabric/ShoreScreen.java'
 code=screen.read_text()
 if 'mc.resizeDisplay();' in code and 'void resizeDisplay();' not in api['net.minecraft.client.Minecraft']:
  choices=re.findall(r'public void (\w*[Rr]esize\w*)\(([^)]*)\);',api['net.minecraft.client.Minecraft'])
  print('Minecraft resize candidates:',choices)
  for name,params in choices:
   if params=='':
    code=code.replace('mc.resizeDisplay();',f'mc.{name}();');break
   if params=='int, int':
    code=code.replace('mc.resizeDisplay();',f'mc.{name}(mc.getWindow().getWidth(),mc.getWindow().getHeight());');break
  else:
   print(api['net.minecraft.client.Minecraft']);raise RuntimeError('Resolve the GUI scale resize event for '+target)
  screen.write_text(code)
 import zipfile
 candidates=set()
 for entry in cp:
  if not entry.endswith('.jar') or not Path(entry).is_file():continue
  with zipfile.ZipFile(entry) as jar:
   for name in jar.namelist():
    leaf=name.rsplit('/',1)[-1].lower()
    if name.startswith('net/minecraft/client/') and '$' not in leaf and leaf.endswith('renderer.class') and any(word in leaf for word in ['hand','firstperson','arm']):
     candidates.add(name[:-6].replace('/','.'))
 print('First-person renderer candidates:',sorted(candidates))
 resolved=[];details=[]
 for name in sorted(candidates):
  output=subprocess.run(['javap','-p','-classpath',classpath,name],capture_output=True,text=True).stdout
  details.append(output)
  for line in output.splitlines():
   match=re.search(r'\bvoid (\w+)\(([^)]*)\);',line)
   if not match or 'net.minecraft.world.item.ItemStack' not in match[2]:continue
   method,args=match.groups()
   if 'InteractionHand' in args:resolved.append((0,name,method))
   elif any(word in method.lower() for word in ['render','submit','extract']):resolved.append((1,name,method))
 print('First-person totem hooks:',resolved)
 if not resolved:
  print('\n'.join(details));raise RuntimeError('Cannot locate the target first-person ItemStack hook')
 _,name,method=sorted(resolved)[0]
 print('Resolved first-person totem hook:',name,method)
 file=root/'fabric/src/main/java/net/shoresmp/fabric/mixin/TotemHeldMixin.java'
 code=file.read_text().replace('net.minecraft.client.renderer.ItemInHandRenderer',name)
 code=re.sub(r'\bItemInHandRenderer\b',name.rsplit('.',1)[-1],code)
 code=code.replace('method="renderArmWithItem"','method="'+method+'"');file.write_text(code)
 file=root/'fabric/src/main/java/net/shoresmp/fabric/ShoreCompatPreflight.java'
 if file.exists():file.write_text(file.read_text().replace('net.minecraft.client.renderer.ItemInHandRenderer',name))
 output=subprocess.run(['javap','-p','-classpath',classpath,'net.minecraft.client.gui.screens.Screen'],capture_output=True,text=True).stdout
 file=root/'fabric/src/main/java/net/shoresmp/fabric/mixin/ScreenBackgroundMixin.java'
 if ' extractBackground(' in output:
  code=file.read_text().replace('method="renderBackground"','method="extractBackground"');file.write_text(code)
  print('Resolved Screen background: extractBackground')

if target.startswith('26.'):
 output=subprocess.run(['javap','-p','-classpath',classpath,'net.minecraft.client.gui.components.DebugScreenOverlay'],capture_output=True,text=True).stdout
 choices=re.findall(r'public void (\w+)\(net\.minecraft\.client\.gui\.GuiGraphicsExtractor\);',output)
 choices.sort(key=lambda n:0 if n=='extractRenderState' else 1 if 'extract' in n.lower() else 2)
 if choices:
  file=root/'fabric/src/main/java/net/shoresmp/fabric/mixin/CleanDebugMixin.java'
  code=file.read_text().replace('method="render"','method="'+choices[0]+'"');file.write_text(code)
  print('Resolved debug panel:',choices[0])
 if ' displayItemActivation(' not in api['net.minecraft.client.renderer.GameRenderer']:
  candidates=set(['net.minecraft.client.renderer.ScreenEffectRenderer'])
  for entry in cp:
   if not entry.endswith('.jar') or not Path(entry).is_file():continue
   with zipfile.ZipFile(entry) as jar:
    for name in jar.namelist():
     leaf=name.rsplit('/',1)[-1].lower()
     if name.startswith('net/minecraft/client/') and '$' not in leaf and leaf.endswith('.class') and any(w in leaf for w in ['activation','screeneffect','overlayrenderer']):candidates.add(name[:-6].replace('/','.'))
  hooks=[];details=[]
  for name in sorted(candidates):
   output=subprocess.run(['javap','-p','-classpath',classpath,name],capture_output=True,text=True).stdout;details.append(output)
   for line in output.splitlines():
    match=re.search(r'\bvoid (\w+)\(([^)]*)\);',line)
    if match and 'net.minecraft.world.item.ItemStack' in match[2] and any(w in match[1].lower() for w in ['activat','floating']):hooks.append((name,match[1]))
  print('Item activation hooks:',hooks)
  if not hooks:print('\n'.join(details));raise RuntimeError('Cannot resolve the totem pop hook')
  name,method=hooks[0]
  file=root/'fabric/src/main/java/net/shoresmp/fabric/mixin/GameRendererMixin.java';code=file.read_text()
  code=re.sub(r'    @Inject\(method="displayItemActivation"[^\n]*\n    private void shore\$totem[^\n]*\n','',code);file.write_text(code)
  template="""package net.shoresmp.fabric.mixin;
import TARGET;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.shoresmp.fabric.ShoreVisuals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.sugar.Local;
@Mixin(SIMPLE.class)
public abstract class TotemPopMixin {
 @Inject(method="METHOD",at=@At("HEAD"),cancellable=true)
 private void shore$pop(CallbackInfo ci,@Local(argsOnly=true) ItemStack stack){if(ShoreVisuals.customTotem()&&stack.is(Items.TOTEM_OF_UNDYING)){ShoreVisuals.pop();ci.cancel();}}
}
""".replace('TARGET',name).replace('SIMPLE',name.rsplit('.',1)[-1]).replace('METHOD',method)
  (root/'fabric/src/main/java/net/shoresmp/fabric/mixin/TotemPopMixin.java').write_text(template)
  file=root/'fabric/src/main/resources/shoremod.mixins.json';meta=json.loads(file.read_text());meta['client'].append('TotemPopMixin');file.write_text(json.dumps(meta,indent=2))
  file=root/'fabric/src/main/java/net/shoresmp/fabric/ShoreCompatPreflight.java';code=file.read_text().replace('for(String name:new String[]{','for(String name:new String[]{'+json.dumps(name)+',');file.write_text(code)

if target=='26.3':
 graphics=api['net.minecraft.client.gui.GuiGraphicsExtractor']
 match=re.search(r'\bblitSprite\(([\w.]+),',graphics)
 if not match:print(graphics);raise RuntimeError('Cannot resolve the GUI render pipeline')
 descriptor='L'+match[1].replace('.','/')+';'
 file=root/'fabric/src/main/java/net/shoresmp/fabric/mixin/GuiOpacityMixin.java'
 code=file.read_text().replace('Lcom/mojang/blaze3d/pipeline/RenderPipeline;',descriptor);file.write_text(code)
 print('Resolved GUI render pipeline:',match[1])
