"""Resolve F3 text and mob model hooks against each Minecraft target."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys

stage, target = sys.argv[1:]
root = Path(__file__).resolve().parents[1]
src = root / 'fabric/src/main/java/net/shoresmp/fabric'
modern = target.startswith('26.')

if stage == 'prepare':
    if target in ('1.21.5', '1.21.8'):
        file = src / 'ShoreF3.java'
        file.write_text(file.read_text(encoding='utf-8').replace(
            'mc.debugHudEntryList.toggleF3Enabled()', 'mc.getDebugHud().toggleDebugHud()'), encoding='utf-8')
    sys.exit()

classpath = os.pathsep.join(json.loads((root/'fabric/build/compat-classpath.json').read_text()))
def dump(name):
    return subprocess.run(['javap', '-classpath', classpath, '-c', '-s', '-p', name],
                          check=True, capture_output=True, text=True).stdout

def methods(name):
    """Get each method name, JVM descriptor and its associated code."""
    text = dump(name)
    starts = list(re.finditer(r'^  [^\s].*\b([\w$]+)\([^\n]*\)[^\n]*;\s*\n    descriptor: (\S+)', text, re.M))
    return [(m[1],m[2],text[m.end():starts[i+1].start() if i+1<len(starts) else len(text)])
            for i,m in enumerate(starts)]

def unique(values, what):
    if len(values) != 1: raise ValueError((what, values))
    return values[0]

living = 'net.minecraft.client.renderer.entity.LivingEntityRenderer' if modern else 'net.minecraft.client.render.entity.LivingEntityRenderer'
state = ('net.minecraft.client.renderer.entity.state.LivingEntityRenderState' if modern else
         'net.minecraft.client.render.entity.state.LivingEntityRenderState').replace('.','/')
entity = ('net.minecraft.world.entity.LivingEntity' if modern else 'net.minecraft.entity.LivingEntity').replace('.','/')
renderer = living.replace('LivingEntityRenderer', 'EntityRenderer')
level_renderer = 'net.minecraft.client.renderer.LevelRenderer' if modern else 'net.minecraft.client.render.WorldRenderer'
base_entity = entity.replace('/LivingEntity', '/Entity')
hud = 'net.minecraft.client.gui.components.DebugScreenOverlay' if modern else 'net.minecraft.client.gui.hud.DebugHud'
mixin = src / 'mixin'

living_methods = methods(living)
update_name = 'extractRenderState' if modern else 'updateRenderState'
update = unique([n+d for n,d,b in living_methods if n == update_name and d == '(L'+entity+';L'+state+';F)V'], 'mob render-state extraction')
tint_name = 'getModelTint' if modern else 'getMixColor'
tint_desc = '(L'+state+';)I'
render = unique([n+d for n,d,b in living_methods if tint_name+':'+tint_desc in b and d.startswith('(L'+state+';')], 'mob model tint')
f = mixin / 'MobRendererMixin.java'; code=f.read_text(encoding='utf-8')
code=re.sub(r'(@Inject\(method\s*=\s*")[^"]+',lambda m:m[1]+update,code)
code=re.sub(r'(@ModifyExpressionValue\(method\s*=\s*")[^"]+',lambda m:m[1]+render,code)
code=re.sub(r'(target\s*=\s*")[^"]+',lambda m:m[1]+'L'+living.replace('.','/')+';'+tint_name+tint_desc,code)
f.write_text(code,encoding='utf-8')

color_name = 'getTeamColor' if modern else 'getTeamColorValue'
color_call = base_entity+'.'+color_name+':()I'
candidates=[]
for owner in (renderer, level_renderer):
    for n,d,body in methods(owner):
        if color_call in body: candidates.append((owner,n,d))
    if candidates: break
owner, n, d = unique(candidates,'mob outline color')
f = mixin / 'MobOutlineColorMixin.java'; code=f.read_text(encoding='utf-8')
code=re.sub(r'import [\w.]+\.EntityRenderer;', 'import '+owner+';',code)
code=code.replace('@Mixin(EntityRenderer.class)', '@Mixin('+owner.rsplit('.',1)[-1]+'.class)')
code=re.sub(r'(@WrapOperation\(method\s*=\s*")[^"]+',lambda m:m[1]+n+d,code)
code=re.sub(r'(target\s*=\s*")[^"]+',lambda m:m[1]+'L'+base_entity+';'+color_name+'()I',code)
f.write_text(code,encoding='utf-8')

f = mixin/'MobOutlineMixin.java'; code=f.read_text(encoding='utf-8')
f.write_text(re.sub(r'(@Inject\(method\s*=\s*")[^"]+',lambda m:m[1]+('shouldEntityAppearGlowing' if modern else 'hasOutline'),code),encoding='utf-8')
graphics_desc='L'+('net/minecraft/client/gui/GuiGraphicsExtractor' if modern else 'net/minecraft/client/gui/DrawContext')+';'
draw=unique([n+d for n,d,b in methods(hud) if d.startswith('('+graphics_desc+'Ljava/util/List;Z') and d.endswith(')V')], 'debug text')
f=mixin/'CleanDebugMixin.java';code=f.read_text(encoding='utf-8')
code=re.sub(r'(@Inject\(method\s*=\s*")[^"]+("\s*,[^)]*\)[^\n]*\n\s*private void shore\$hideVanillaText)',lambda m:m[1]+draw+m[2],code)
f.write_text(code,encoding='utf-8')

if modern:
    f=src/'ShoreF3.java';code=f.read_text(encoding='utf-8')
    sound_api=dump('net.minecraft.client.sounds.SoundManager')
    if ' getChannelDebugString()' in sound_api:
        code=re.sub(r'case SOUNDS -> [^;]+;', 'case SOUNDS -> mc.getSoundManager().getChannelDebugString();', code)
    level_api=dump(level_renderer)
    if ' getSectionStatistics(' not in level_api:
        code=re.sub(r'case CHUNK_INFO -> [^;]+;', 'case CHUNK_INFO -> mc.levelExtractor.sectionStatistics();', code)
    game_api=dump('net.minecraft.client.renderer.GameRenderer')
    if ' currentPostEffect(' not in game_api and ' spectatedEntityPostEffect(' in game_api:
        code=re.sub(r'case SHADER -> [^;]+;', 'case SHADER -> mc.gameRenderer.getAppliedPostEffects().isEmpty() ? "None" : mc.gameRenderer.getAppliedPostEffects().toString();',code)
    if 'getDeviceInfo:' in dump('net.minecraft.client.gui.components.debug.DebugEntrySystemSpecs'):
        code=re.sub(r'case RENDERER -> [^;]+;', 'case RENDERER -> RenderSystem.getDevice().getDeviceInfo().name();',code)
        code=re.sub(r'case GL -> [^;]+;', 'case GL -> RenderSystem.getDevice().getDeviceInfo().backendName() + " " + RenderSystem.getDevice().getDeviceInfo().driverInfo().lines().findFirst().orElse("");',code)
    f.write_text(code,encoding='utf-8')
print('Custom F3 and mob overlay:', target, draw, update, render, owner, n)

