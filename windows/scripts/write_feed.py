from pathlib import Path
import json,hashlib,sys
p=Path(sys.argv[1]).resolve();data=p.read_bytes()
feed={'schema':1,'project':'bodriosafio','kind':'launcher','platform':'windows','architecture':'x64','version':'0.1.5','version_code':10005,'url':'https://raw.githubusercontent.com/alanbustamorag-source/panconqueso/main/Bodriosafio_Launcher_Windows_0_1_5.exe','sha256':hashlib.sha256(data).hexdigest(),'size':len(data),'notes':'Migración al nuevo GitHub y modo addon en 26.62/26.63. Los nativos conservan su compilación anterior. Incluye addon 0.3.15 y conserva la autoactualización del launcher.'}
(p.parent/'launcher-windows-latest.json').write_text(json.dumps(feed,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'output':str(p),'bytes':len(data),'sha256':feed['sha256']}))
