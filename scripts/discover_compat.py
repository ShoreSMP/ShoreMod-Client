"""Read official dependency metadata for the requested Fabric targets."""
import json, urllib.request, urllib.parse, xml.etree.ElementTree as ET
from pathlib import Path
TARGETS=['26.3','26.2','26.1','1.21.10','1.21.8','1.21.5']
def read(url):
 req=urllib.request.Request(url,headers={'User-Agent':'ShoreMod compatibility build'})
 with urllib.request.urlopen(req,timeout=90) as response:return response.read()
def data(url):return json.loads(read(url))
api=ET.fromstring(read('https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml'))
api_versions=[v.text for v in api.findall('./versioning/versions/version')]
result=[]
for target in TARGETS:
 yarn=[] if target.startswith('26.') else data('https://meta.fabricmc.net/v2/versions/yarn/'+target)
 fabric=[v for v in api_versions if v.endswith('+'+target)]
 row={'minecraft':target,'yarn':yarn[0]['version'] if yarn else None,'fabric_api':fabric[-1] if fabric else None}
 result.append(row)
print(json.dumps(result,indent=2))
Path('dependency-plan.json').write_text(json.dumps(result,indent=2))
