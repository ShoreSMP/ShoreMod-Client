"""Use the same settings and mixin smoke checks on the original 1.21.11 target."""
from pathlib import Path
import json,runpy
root=Path(__file__).resolve().parents[1]
src=root/'fabric/src/main/java';res=root/'fabric/src/main/resources'
p=res/'fabric.mod.json';meta=json.loads(p.read_text(encoding='utf-8'))
meta['custom']={'shoremod:build-target':'1.21.11'}
p.write_text(json.dumps(meta,indent=2))
runpy.run_path(str(root/'scripts/add_compat_smoke.py'),init_globals={'root':root,'src':src,'res':res,'modern':False,'target':'1.21.11'})
