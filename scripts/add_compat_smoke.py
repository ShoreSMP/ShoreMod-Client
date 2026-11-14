"""Install a development-only client startup/menu smoke-test hook."""
from pathlib import Path
current='client.gui.screen()' if target in ['26.2','26.3'] else 'client.screen' if modern else 'client.currentScreen'
stop='client.stop()' if modern else 'client.scheduleStop()'
overlay='client.gui.overlay()' if target in ['26.2','26.3'] else 'client.getOverlay()'
code=f'''package net.shoresmp.fabric;
public final class ShoreCompatSmoke {{
 private static final boolean ACTIVE=Boolean.getBoolean("shoremod.compatSmoke");
 private static long started;
 private static volatile int frames;
 private static boolean done;
 private static long lastOpen;
 private static int stage;
 public static void rendered(){{if(ACTIVE)frames++;}}
 public static void install(){{
  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(client->{{
   ShoreSettingsChecks.run();
   started=System.nanoTime();
  }});
  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->{{
   if(started==0||done)return;
   if(!({current} instanceof ShoreScreen)&&!({current} instanceof CrosshairEditorScreen)&&{overlay}==null&&System.nanoTime()-lastOpen>1_000_000_000L){{lastOpen=System.nanoTime();ShoreClient.openNextTick(()->new ShoreScreen({current}));}}
   if({current} instanceof CrosshairEditorScreen&&{overlay}==null){{frames++;if(frames>=110){{done=true;ShoreClient.LOG.info("SHORE_COMPAT_SMOKE_PASS target={target}");{stop};}}}}
   if({current} instanceof ShoreScreen&&{overlay}==null){{
    if(frames>=20&&stage==0){{((ShoreScreen){current}).showModule("fullbright");stage++;}}
    if(frames>=40&&stage==1){{((ShoreScreen){current}).showModule("crosshair");stage++;}}
    if(frames>=60&&stage==2){{((ShoreScreen){current}).showPage("GENERAL");stage++;}}
    if(frames>=80&&stage==3){{ShoreClient.openNextTick(()->new CrosshairEditorScreen(null));stage++;}}
   }}
   if(!done&&System.nanoTime()-started>150_000_000_000L){{done=true;ShoreClient.LOG.error("SHORE_COMPAT_SMOKE_FAIL target={target} screen={{}} overlay={{}} frames={{}}",{current},{overlay},frames);{stop};}}
  }});
 }}
}}
'''
(src/'net/shoresmp/fabric/ShoreCompatSmoke.java').write_text(code,encoding='utf-8')
p=src/'net/shoresmp/fabric/ShoreClient.java';s=p.read_text(encoding='utf-8').replace('config = Config.load();','config = Config.load(); if(Boolean.getBoolean("shoremod.compatSmoke"))ShoreCompatSmoke.install();');p.write_text(s,encoding='utf-8')
p=src/'net/shoresmp/fabric/ShoreScreen.java';s=p.read_text(encoding='utf-8')
s=s.replace('int guiX,int guiY,float delta){','int guiX,int guiY,float delta){ShoreCompatSmoke.rendered();')
p.write_text(s,encoding='utf-8')
with (root/'fabric/build.gradle').open('a',encoding='utf-8') as output:
 output.write("\nif (providers.gradleProperty('compatSmoke').isPresent()) { loom { runs { client { vmArg '-Dshoremod.compatSmoke=true'; programArgs '--username', 'ShoreModTest', '--width', '1280', '--height', '720' } } } }\n")
registered=set(__import__('json').loads((res/'shoremod.mixins.json').read_text())['client'])
for p in (src/'net/shoresmp/fabric/mixin').glob('*.java'):
 if p.stem not in registered:p.unlink()

# Validate every target before opening a graphics device, collecting all hook errors
# in one run instead of discovering only the first class reached during startup.
import re,json
targets=set()
for p in (src/'net/shoresmp/fabric/mixin').glob('*.java'):
 source=p.read_text(encoding='utf-8')
 match=re.search(r'@Mixin\(([\w.]+)\.class\)',source)
 if not match:continue
 target_name=match[1];outer,*nested=target_name.split('.')
 if target_name.startswith(('net.','com.')):resolved=target_name
 else:
  imported=re.search(r'import\s+([\w.]+\.'+re.escape(outer)+r');',source)
  if not imported:raise RuntimeError('Unresolved mixin target: '+target_name)
  resolved=imported[1]+(''.join('$'+name for name in nested))
 targets.add(resolved)
if modern:targets.add('net.minecraft.client.Camera')
code='''package net.shoresmp.fabric;
public final class ShoreCompatPreflight implements net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint {
 public void onPreLaunch(){
  if(!Boolean.getBoolean("shoremod.compatSmoke")&&!Boolean.getBoolean("shoremod.compatPreflight"))return;
  int failures=0;
  for(String name:new String[]{TARGETS}){
   try{Class.forName(name,false,Thread.currentThread().getContextClassLoader());System.out.println("SHORE_HOOK_PASS "+name);}
   catch(Throwable error){failures++;System.err.println("SHORE_HOOK_FAIL "+name);error.printStackTrace();}
  }
  if(failures!=0){System.err.println("SHORE_COMPAT_MIXINS_FAIL count="+failures);System.exit(1);}
  System.out.println("SHORE_COMPAT_MIXINS_PASS");
  if(Boolean.getBoolean("shoremod.compatPreflight"))System.exit(0);
 }
}
'''.replace('TARGETS',','.join(json.dumps(name) for name in sorted(targets)))
(src/'net/shoresmp/fabric/ShoreCompatPreflight.java').write_text(code,encoding='utf-8')
p=res/'fabric.mod.json';meta=json.loads(p.read_text());meta['entrypoints']['preLaunch']=['net.shoresmp.fabric.ShoreCompatPreflight'];p.write_text(json.dumps(meta,indent=2))
with (root/'fabric/build.gradle').open('a',encoding='utf-8') as output:
 output.write("\nif (providers.gradleProperty('compatPreflight').isPresent()) { loom { runs { client { vmArg '-Dshoremod.compatPreflight=true' } } } }\n")

