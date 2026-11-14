"""Compatibility-only patch: preserve every class/resource byte in the verified release."""
import io,json,zipfile,hashlib,sys
from pathlib import Path
from verify_compat import verify_clean
def patch(data):
 out=io.BytesIO()
 with zipfile.ZipFile(io.BytesIO(data)) as old,zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as new:
  meta=json.loads(old.read('fabric.mod.json'));ident=meta['id']
  if ident=='shoremod_universal':meta['version']='1.2.1';meta['name']='ShoreMod';meta['depends']['fabricloader']='>=0.19.2'
  elif ident=='shoremod':
   target=meta['depends']['minecraft'];meta['version']='1.2.1-mc.'+target
   if target.startswith('1.21.'):meta['depends']['fabricloader']='>=0.19.2'
  for name in old.namelist():
   payload=old.read(name)
   if name=='fabric.mod.json':payload=json.dumps(meta,indent=2).encode()
   elif name.endswith('.jar'):
    with zipfile.ZipFile(io.BytesIO(payload)) as nested:info=json.loads(nested.read('fabric.mod.json'))
    if info['id']=='shoremod':payload=patch(payload)
   new.writestr(name,payload)
 return out.getvalue()
def compare(before,after):
 with zipfile.ZipFile(io.BytesIO(before)) as old,zipfile.ZipFile(io.BytesIO(after)) as new:
  assert old.namelist()==new.namelist()
  first=json.loads(old.read('fabric.mod.json'));second=json.loads(new.read('fabric.mod.json'))
  for field in ('version','name'):first.pop(field,None);second.pop(field,None)
  first.get('depends',{}).pop('fabricloader',None);second.get('depends',{}).pop('fabricloader',None)
  assert first==second
  for name in old.namelist():
   if name=='fabric.mod.json':continue
   a,b=old.read(name),new.read(name)
   if a!=b:
    assert name.endswith('.jar'),name
    compare(a,b)
if __name__=='__main__':
 source=Path(sys.argv[1]).read_bytes();result=patch(source);compare(source,result);verify_clean(result)
 output=Path(sys.argv[2]);output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(result)
 output.with_suffix('.jar.sha256').write_text(hashlib.sha256(result).hexdigest()+'  '+output.name+'\n')
 print('PASS: only release labels and loader floors changed; all application bytes are preserved')
