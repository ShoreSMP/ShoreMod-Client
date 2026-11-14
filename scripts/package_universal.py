"""One Fabric container; Loader resolves exactly one compatible nested client."""
import io,json,hashlib,sys,zipfile
from pathlib import Path
from verify_compat import verify_clean
TARGETS={'1.21.5','1.21.8','1.21.10','1.21.11','26.1','26.2','26.3'}
VERSION='1.2.3'
def package(folder,output):
 variants={};icon=None
 for path in folder.glob('*.jar'):
  data=path.read_bytes()
  with zipfile.ZipFile(io.BytesIO(data)) as jar:
   if 'fabric.mod.json' not in jar.namelist():continue
   meta=json.loads(jar.read('fabric.mod.json'))
   if meta['id']!='shoremod':continue
   target=meta['depends']['minecraft'];assert target in TARGETS and target not in variants,target
   assert meta['version']==VERSION
   meta['version']=VERSION+'-mc.'+target
   buf=io.BytesIO()
   with zipfile.ZipFile(buf,'w',zipfile.ZIP_DEFLATED) as child:
    for name in jar.namelist():child.writestr(name,json.dumps(meta,indent=2).encode() if name=='fabric.mod.json' else jar.read(name))
   variants[target]=buf.getvalue();icon=jar.read(meta['icon'])
 assert set(variants)==TARGETS,set(variants)
 meta={'schemaVersion':1,'id':'shoremod_universal','version':VERSION,'name':'ShoreMod','description':'Selects the exact ShoreMod implementation for supported Minecraft versions.','environment':'client','icon':'icon.png','depends':{'fabricloader':'>=0.19.2','minecraft':sorted(TARGETS),'java':'>=21','shoremod':'*'},'jars':[{'file':'META-INF/jars/shoremod-'+t+'.jar'} for t in sorted(TARGETS)]}
 output.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as jar:
  jar.writestr('fabric.mod.json',json.dumps(meta,indent=2));jar.writestr('icon.png',icon)
  for target,data in sorted(variants.items()):jar.writestr('META-INF/jars/shoremod-'+target+'.jar',data)
 verify_clean(output.read_bytes())
 output.with_suffix('.jar.sha256').write_text(hashlib.sha256(output.read_bytes()).hexdigest()+'  '+output.name+'\n')
 print('PASS: universal container contains seven exact-version clients')
if __name__=='__main__':package(Path(sys.argv[1]),Path(sys.argv[2]))
