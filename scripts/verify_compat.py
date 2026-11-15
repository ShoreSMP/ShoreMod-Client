"""Verify exact-version bytecode and reject removed components recursively."""
import io,json,struct,sys,zipfile,hashlib
from pathlib import Path
FORBIDDEN=(b'de/keksuccino/',b'com/cinemamod/',b'org/cef/',b'TicketsScreen',b'TicketPolicy',b'BrowserStartup',b'BrowserWindow')
def verify_clean(data):
 with zipfile.ZipFile(io.BytesIO(data)) as jar:
  for name in jar.namelist():
   assert not any(word in name.lower() for word in ('rinku','mcef','chromium','ticket')),name
   content=jar.read(name)
   if name.endswith('.class'):assert not any(word in content for word in FORBIDDEN),name
   if name.endswith('.jar'):verify_clean(content)
  if 'fabric.mod.json' in jar.namelist():
   meta=json.loads(jar.read('fabric.mod.json'));assert not {'rinku','mcef'} & set(meta.get('depends',{}))
def verify(target):
 root=Path(__file__).resolve().parents[1]
 jars=[p for p in (root/'fabric/build/libs').glob('*.jar') if not p.name.endswith('-sources.jar')]
 assert len(jars)==1,jars
 path=jars[0];data=path.read_bytes();verify_clean(data);java=25 if target.startswith('26.') else 21
 with zipfile.ZipFile(io.BytesIO(data)) as jar:
  meta=json.loads(jar.read('fabric.mod.json'))
  assert meta['depends']['minecraft']==target and meta['custom']['shoremod:build-target']==target
  assert meta['depends']['java']==f'>={java}' and meta['version']=='1.3.0'
  classes=[n for n in jar.namelist() if n.startswith('net/shoresmp/fabric/') and n.endswith('.class')]
  assert len(classes)>25 and all(struct.unpack('>H',jar.read(n)[6:8])[0]<=java+44 for n in classes)
  assert meta['icon'] in jar.namelist() and 'net/shoresmp/fabric/mixin/BrightnessMixin.class' in jar.namelist()
 result={'minecraft':target,'file':path.name,'sha256':hashlib.sha256(data).hexdigest(),'java':java,'checks':'exact metadata, bytecode, logo, removed components absent'}
 (root/'fabric/build/compat-verification.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2))
if __name__=='__main__':verify(sys.argv[1])
