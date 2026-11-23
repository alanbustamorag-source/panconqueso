from pathlib import Path
import hashlib,re
root=Path(__file__).resolve().parents[1]
p=root/'profile.hpp';text=p.read_text()
for name,asset in [('native_sha256','windows-x64-BuildLimitChanger.dll'),('addon_sha256','Bodriosafio_0_3_15.mcaddon')]:
    digest=hashlib.sha256((root/'assets'/asset).read_bytes()).hexdigest()
    text=re.sub(r'(constexpr const char\* '+name+r' = ")[0-9a-f]{64}(";)',lambda m:m[1]+digest+m[2],text)
digest=hashlib.sha256((root/'assets/BodriosafioEffects.dll').read_bytes()).hexdigest()
text=re.sub(r'(#define BODRIO_EFFECTS_SHA256 ")[0-9a-f]{64}(")',lambda m:m[1]+digest+m[2],text)
p.write_text(text)
