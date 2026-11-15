"""Prepare a version-specific working copy in CI; shared sources stay unchanged."""
from pathlib import Path
import json,re,sys
target=sys.argv[1]
root=Path(__file__).resolve().parents[1]
cfg=next(r for r in json.loads((root/'scripts/compat-targets.json').read_text(encoding='utf-8-sig')) if r['minecraft']==target)
modern=target.startswith('26.')
src=root/'fabric/src/main/java';res=root/'fabric/src/main/resources'
def edit(path,fn):
 p=root/path;p.write_text(fn(p.read_text(encoding='utf-8')),encoding='utf-8')
def remove_mixin(name):
 p=res/'shoremod.mixins.json';j=json.loads(p.read_text(encoding='utf-8-sig'));j['client']=[x for x in j['client'] if x!=name];p.write_text(json.dumps(j,indent=2))
 p=src/'net/shoresmp/fabric/mixin'/str(name+'.java')
 if p.exists():p.unlink()
if not modern:
 for name in ['GlyphBakerMixin','TextureSamplerAccessor']:remove_mixin(name)
 if target in ['1.21.5','1.21.8','1.21.10']:
  remove_mixin('ChatHeadsBackendMixin')
  callback='CallbackInfoReturnable<Integer>' if target=='1.21.5' else 'CallbackInfo'
  result='I' if target=='1.21.5' else 'V'
  (src/'net/shoresmp/fabric/mixin/LegacyChatHeadsMixin.java').write_text("""package net.shoresmp.fabric.mixin;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.shoresmp.fabric.ShoreChatHeads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(DrawContext.class)
public abstract class LegacyChatHeadsMixin {
 @Inject(method="drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)RESULT",at=@At("HEAD"))
 private void shore$head(TextRenderer font,OrderedText text,int x,int y,int color,boolean shadow,CALLBACK ci){ShoreChatHeads.draw((DrawContext)(Object)this,text,x,y,color);}
}
""".replace('CALLBACK',callback).replace('RESULT',result))
  p=res/'shoremod.mixins.json';meta=json.loads(p.read_text());meta['client'].append('LegacyChatHeadsMixin');p.write_text(json.dumps(meta,indent=2))

(src/'net/shoresmp/fabric/ShoreSelfTest.java').write_text('package net.shoresmp.fabric; public final class ShoreSelfTest { public static void register(){} }')
loader='0.20.1' if modern else '0.19.2'
api=cfg['fabric_api'];plugin="net.fabricmc.fabric-loom" if modern else "fabric-loom";loom="1.18.2" if modern else "1.14.10";conf="implementation" if modern else "modImplementation"
deps=f"""plugins {{ id '{plugin}' version '{loom}' }}
dependencies {{
    minecraft 'com.mojang:minecraft:{target}'
"""
if not modern:deps+=f"    mappings 'net.fabricmc:yarn:{cfg['yarn']}:v2'\n"
deps+=f"    {conf} 'net.fabricmc:fabric-loader:{loader}'\n    {conf} 'net.fabricmc.fabric-api:fabric-api:{api}'\n"
deps+="    implementation project(':common')\n    include project(':common')\n}\n"
deps+=f"base {{ archivesName = 'ShoreMod-Fabric-{target}' }}\n"
if modern:deps+="java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }\ntasks.withType(JavaCompile).configureEach { options.release = 25 }\n"
deps+='\ntasks.withType(Jar).configureEach { from(rootProject.file("LICENSE")) }\n'
(root/'fabric/build.gradle').write_text(deps)
meta=json.loads((res/'fabric.mod.json').read_text(encoding='utf-8-sig'));meta['version']='1.3.0';meta['depends']['minecraft']=target
meta['depends']['java']='>=25' if modern else '>=21';meta['depends']['fabricloader']='>='+loader;meta['depends']['fabric-api']='>='+api.split('+')[0];meta['depends'].pop('rinku',None)
meta['custom']={'shoremod:build-target':target}
(res/'fabric.mod.json').write_text(json.dumps(meta,indent=2))
j=json.loads((res/'shoremod.mixins.json').read_text(encoding='utf-8-sig'));j['compatibilityLevel']='JAVA_25' if modern else 'JAVA_21';(res/'shoremod.mixins.json').write_text(json.dumps(j,indent=2))
if not modern:
 for p in src.rglob('*.java'):
  s=p.read_text(encoding='utf-8-sig')
  if target!='1.21.10':s=s.replace('net.minecraft.entity.PlayerLikeEntity','net.minecraft.client.network.AbstractClientPlayerEntity').replace('PlayerLikeEntity','AbstractClientPlayerEntity').replace('Lnet/minecraft/entity/PlayerLikeEntity;','Lnet/minecraft/client/network/AbstractClientPlayerEntity;').replace('Lnet/minecraft/entity/AbstractClientPlayerEntity;','Lnet/minecraft/client/network/AbstractClientPlayerEntity;')
  if target!='1.21.10':s=s.replace('import net.minecraft.text.StyleSpriteSource;','').replace('new StyleSpriteSource.Font(','(')
  if target!='1.21.10':s=s.replace('.getProfile().id()', '.getProfile().getId()').replace('.getProfile().name()', '.getProfile().getName()')
  s=s.replace('.getCameraPos()', '.getPos()')
  if target in ['1.21.5','1.21.8']:s=s.replace('skin.body().texturePath()', 'skin.texture()').replace('net.minecraft.entity.player.SkinTextures','net.minecraft.client.util.SkinTextures')
  s=s.replace('PositionedSoundInstance.ui(', 'PositionedSoundInstance.master(')
  p.write_text(s,encoding='utf-8')
 if target in ['1.21.8','1.21.5']:
  compat=src/'net/shoresmp/fabric/compat';compat.mkdir(parents=True,exist_ok=True)
  (compat/'Click.java').write_text('package net.shoresmp.fabric.compat; public record Click(double x,double y,int button){public boolean hasShift(){return net.minecraft.client.gui.screen.Screen.hasShiftDown();}}')
  (compat/'KeyInput.java').write_text('package net.shoresmp.fabric.compat; public record KeyInput(int key,int scancode,int modifiers){}')
  (compat/'CharInput.java').write_text('package net.shoresmp.fabric.compat; public record CharInput(char character,int modifiers){public boolean isValidChar(){return character>=32&&character!=127;}public String asString(){return String.valueOf(character);}}')
  for p in src.rglob('*.java'):
   s=p.read_text(encoding='utf-8-sig').replace('net.minecraft.client.gui.Click','net.shoresmp.fabric.compat.Click').replace('net.minecraft.client.input.KeyInput','net.shoresmp.fabric.compat.KeyInput').replace('net.minecraft.client.input.CharInput','net.shoresmp.fabric.compat.CharInput')
   s=s.replace('mouseClicked(Click click,boolean doubled){','mouseClicked(double mouseX,double mouseY,int mouseButton){var click=new Click(mouseX,mouseY,mouseButton);boolean doubled=false;')
   s=s.replace('mouseReleased(Click click){','mouseReleased(double mouseX,double mouseY,int mouseButton){var click=new Click(mouseX,mouseY,mouseButton);')
   s=s.replace('mouseDragged(Click click,double dx,double dy){','mouseDragged(double mouseX,double mouseY,int mouseButton,double dx,double dy){var click=new Click(mouseX,mouseY,mouseButton);')
   for method in ['keyPressed','keyReleased']:s=s.replace(method+'(KeyInput input){',method+'(int keyCode,int scanCode,int modifierMask){var input=new KeyInput(keyCode,scanCode,modifierMask);')
   s=s.replace('charTyped(CharInput input){','charTyped(char character,int modifierMask){var input=new CharInput(character,modifierMask);')
   s=s.replace('super.mouseClicked(click,doubled)','super.mouseClicked(click.x(),click.y(),click.button())').replace('super.mouseReleased(click)','super.mouseReleased(click.x(),click.y(),click.button())').replace('super.mouseDragged(click,dx,dy)','super.mouseDragged(click.x(),click.y(),click.button(),dx,dy)')
   s=s.replace('InputUtil.fromKeyCode(input)', 'InputUtil.fromKeyCode(input.key(),input.scancode())')
   s=s.replace('isClickOutsideBounds(double mouseX,double mouseY,int left,int top);','isClickOutsideBounds(double mouseX,double mouseY,int left,int top,int button);').replace('isClickOutsideBounds(mx,my,x,y)','isClickOutsideBounds(mx,my,x,y,button)')
   s=s.replace('super.keyPressed(input)','super.keyPressed(input.key(),input.scancode(),input.modifiers())').replace('super.keyReleased(input)','super.keyReleased(input.key(),input.scancode(),input.modifiers())').replace('super.charTyped(input)','super.charTyped(character,input.modifiers())')
   s=s.replace('shore$press(Click click,boolean doubled,CallbackInfoReturnable<Boolean> cir){','shore$press(double mouseX,double mouseY,int mouseButton,CallbackInfoReturnable<Boolean> cir){var click=new Click(mouseX,mouseY,mouseButton);boolean doubled=false;')
   s=s.replace('shore$drag(Click click,double dx,double dy,CallbackInfoReturnable<Boolean> cir){','shore$drag(double mouseX,double mouseY,int mouseButton,double dx,double dy,CallbackInfoReturnable<Boolean> cir){var click=new Click(mouseX,mouseY,mouseButton);')
   s=s.replace('shore$release(Click click,CallbackInfoReturnable<Boolean> cir){','shore$release(double mouseX,double mouseY,int mouseButton,CallbackInfoReturnable<Boolean> cir){var click=new Click(mouseX,mouseY,mouseButton);')
   s=s.replace('long window,net.minecraft.client.input.MouseInput input,int action,CallbackInfo ci','long window,int button,int action,int mods,CallbackInfo ci').replace('ShoreTools.click(input.button())','ShoreTools.click(button)')
   s=s.replace('InputUtil.isKeyPressed(client.getWindow(),','InputUtil.isKeyPressed(client.getWindow().getHandle(),')
   s=s.replace('private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("shoremod", "controls"));','private static final String CATEGORY = "key.categories.shoremod";')
   p.write_text(s,encoding='utf-8')
 if target=='1.21.5':
  edit(Path('fabric/src/main/java/net/shoresmp/fabric/mixin/HandledScreenMixin.java'),lambda s:s.replace('method="renderMain"','method="render"'))
  for p in src.rglob('*.java'):
   s=p.read_text(encoding='utf-8-sig').replace('import net.minecraft.client.gl.RenderPipelines;','import net.minecraft.client.render.RenderLayer;').replace('RenderPipelines.GUI_TEXTURED','RenderLayer::getGuiTextured')
   s=s.replace('.pushMatrix()','.push()').replace('.popMatrix()','.pop()').replace('.scale(1f/gs,1f/gs)','.scale(1f/gs,1f/gs,1f)');p.write_text(s,encoding='utf-8')

if not modern:
 # ChatHud's 1.21.11 extracted-render backend did not exist in older releases.
 p=src/'net/shoresmp/fabric/mixin/ChatHudMixin.java';s=p.read_text(encoding='utf-8-sig')
 start=s.index('    /** Replaces the dark background')
 end=s.index('    @Unique private int shore$count',start)
 s=s[:start]+"""    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="render",
        slice=@Slice(from=@At(value="INVOKE",target="Ljava/util/List;get(I)Ljava/lang/Object;",ordinal=0)),
        at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V",ordinal=0))
    private void shore$highlight(net.minecraft.client.gui.DrawContext context,int x1,int y1,int x2,int y2,int color,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original,
            @com.llamalad7.mixinextras.sugar.Local ChatHudLine.Visible line){
        var config=ShoreClient.config;
        if(config==null||!config.mentionHighlight||!SHORE_MENTION_LINES.contains(line)){original.call(context,x1,y1,x2,y2,color);return;}
        int rgb=config.mentionColor==5?config.mentionCustomColor:config.mentionColor==1?config.accent:SHORE_MENTION_COLORS[config.mentionColor];
        original.call(context,x1,y1,x2,y2,(color&0xFF000000)|rgb);
        original.call(context,x1,y1,x1+2,y2,0xFF000000|rgb);
    }
""" +s[end:]
 if target!='1.21.5':
  s=s.replace('WrapOperation(method="render",','WrapOperation(method="method_71992",')
  s=s.replace('        slice=@Slice(from=@At(value="INVOKE",target="Ljava/util/List;get(I)Ljava/lang/Object;",ordinal=0)),\n','')
  s=s.replace('@com.llamalad7.mixinextras.sugar.Local ChatHudLine.Visible line','@com.llamalad7.mixinextras.sugar.Local(argsOnly=true) ChatHudLine.Visible line')
 p.write_text(s,encoding='utf-8')
 if target=='1.21.5':
  for p in src.rglob('*.java'):
   s=p.read_text(encoding='utf-8-sig').replace('gs=w.getScaleFactor()','gs=(int)w.getScaleFactor()')
   s=s.replace('m.translate(x,y)','m.translate(x,y,0)').replace('m.translate(p(x),Math.round(y*u+offset))','m.translate(p(x),Math.round(y*u+offset),0)')
   s=s.replace('m.rotate(angle)','m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotation(angle))')
   s=s.replace('Lcom/mojang/blaze3d/pipeline/RenderPipeline;', 'Ljava/util/function/Function;')
   s=s.replace('m.scale(b.scale(),b.scale())','m.scale(b.scale(),b.scale(),1)').replace('m.scale(sc,sc)','m.scale(sc,sc,1)').replace('m.scale(k,k)','m.scale(k,k,1)')
   s=s.replace('net.minecraft.client.gl.RenderLayer::getGuiTextured','net.minecraft.client.render.RenderLayer::getGuiTextured')
   p.write_text(s,encoding='utf-8')
  p=src/'net/shoresmp/fabric/ShoreHud.java';s=p.read_text(encoding='utf-8-sig').replace('import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;','import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;')
  start=s.index('    public static void register(){');end=s.index('    private static Text ui',start)
  s=s[:start]+'    public static void register(){HudRenderCallback.EVENT.register((ctx,tick)->render(ctx));}\n'+s[end:]
  s=s.replace('private static void crosshair(', 'public static void crosshair(');p.write_text(s,encoding='utf-8')
  (src/'net/shoresmp/fabric/mixin/LegacyCrosshairMixin.java').write_text("""package net.shoresmp.fabric.mixin;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.MinecraftClient;
import net.shoresmp.fabric.ShoreClient;
import net.shoresmp.fabric.ShoreHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(InGameHud.class)
public abstract class LegacyCrosshairMixin {
 @Inject(method="renderCrosshair",at=@At("HEAD"),cancellable=true)
 private void shore$crosshair(DrawContext ctx,RenderTickCounter tick,CallbackInfo ci){
  var client=MinecraftClient.getInstance();var c=ShoreClient.config;
  if(c!=null&&c.crosshair&&client.options.getPerspective().isFirstPerson()&&!client.options.hudHidden
   &&!client.getDebugHud().shouldShowDebugHud()&&client.player!=null&&!client.player.isSpectator()){
   ShoreHud.crosshair(ctx);ci.cancel();
  }
 }
}""")
  p=res/'shoremod.mixins.json';j=json.loads(p.read_text(encoding='utf-8-sig'));j['client'].append('LegacyCrosshairMixin');p.write_text(json.dumps(j,indent=2))
else:
 for p in src.rglob('*.java'):
  s=p.read_text(encoding='utf-8-sig')
  for package in ['net.minecraft.text','net.minecraft.item','net.minecraft.screen','net.minecraft.screen.slot']:
   s=s.replace('import '+package+'.*;','')
  s=s.replace('GuiGraphics','GuiGraphicsExtractor')
  s=s.replace('public void render(GuiGraphicsExtractor','public void extractRenderState(GuiGraphicsExtractor').replace('super.render(ctx,','super.extractRenderState(ctx,')
  if target in ['26.2','26.3']:
   s=s.replace('.gui.getChat()', '.hud.getChat()')
   s=re.sub(r'\b(client|minecraft|mc)\.screen\b',r'\1.gui.screen()',s)
   s=re.sub(r'\b(client|minecraft|mc)\.setScreen\(',r'\1.gui.setScreen(',s)
   s=s.replace('Minecraft.getInstance().setScreen(', 'Minecraft.getInstance().gui.setScreen(').replace('Minecraft.getInstance().screen','Minecraft.getInstance().gui.screen()')
  p.write_text(s,encoding='utf-8')

# A TAIL hook runs only after vanilla's client-thread handoff succeeds; its descriptor
# stays stable across releases that changed the handoff helper's third argument.
p=src/'net/shoresmp/fabric/mixin/EntityStatusMixin.java'
s=p.read_text(encoding='utf-8')
s=re.sub(r'at=@At\(value="INVOKE",target="[^"]+",shift=At.Shift.AFTER\)', 'at=@At("TAIL")',s)
p.write_text(s,encoding='utf-8')
if modern:
 import runpy
 runpy.run_path(str(root/'scripts/compat_modern.py'),init_globals={'src':src,'res':res,'target':target})
(root/'compat-build.json').write_text(json.dumps(cfg,indent=2))
print('Prepared',target)

# Export the actual compile classpath for signature verification before building.
with (root/'fabric/build.gradle').open('a',encoding='utf-8') as output:
 output.write("\ntasks.register('compatClasspath') { doLast { file('build/compat-classpath.json').parentFile.mkdirs(); file('build/compat-classpath.json').text = groovy.json.JsonOutput.toJson(configurations.compileClasspath.files*.absolutePath) } }\n")

import runpy
runpy.run_path(str(root/'scripts/add_compat_smoke.py'),init_globals={'src':src,'res':res,'root':root,'modern':modern,'target':target})


