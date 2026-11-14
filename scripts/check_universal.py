"""Run Fabric Loader's own dependency solver against the release JAR."""
from pathlib import Path
import json,os,subprocess,sys,urllib.request
root=Path(__file__).resolve().parents[1]
work=root/'build/universal-checks';work.mkdir(parents=True,exist_ok=True)
def read(url):
 with urllib.request.urlopen(url,timeout=90) as response:return response.read()
loader=sys.argv[2] if len(sys.argv)>2 else '0.20.1'
profile=json.loads(read('https://meta.fabricmc.net/v2/versions/loader/1.21.11/'+loader+'/profile/json'))
jars=[]
for library in profile['libraries']:
 group,artifact,version=library['name'].split(':')
 relative=group.replace('.','/')+'/'+artifact+'/'+version+'/'+artifact+'-'+version+'.jar'
 path=work/(artifact+'-'+version+'.jar')
 if not path.exists():path.write_bytes(read(library.get('url','https://libraries.minecraft.net/').rstrip('/')+'/'+relative))
 jars.append(path)
cp=os.pathsep.join(map(str,jars))
subprocess.run(['javac','--release','21','-cp',cp,'-d',str(work),str(root/'scripts/UniversalResolutionCheck.java')],check=True)
targets=[{'minecraft':'1.21.11','fabric_api':'0.141.6+1.21.11'}]+json.loads((root/'scripts/compat-targets.json').read_text())
if loader=='0.19.2':targets=[row for row in targets if not row['minecraft'].startswith('26.')]
args=[value for row in targets for value in (row['minecraft'],row['fabric_api'])]
subprocess.run(['java','-Dshoremod.testLoader='+loader,'-cp',str(work)+os.pathsep+cp,'net.shoresmp.tests.UniversalResolutionCheck',sys.argv[1],*args],check=True)
