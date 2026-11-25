#!/usr/bin/env python3
"""Build the project's ARM64 mod. Link stubs are temporary and never packaged."""
from pathlib import Path
import argparse, hashlib, json, subprocess, tempfile
import ziglang

def build(output):
    root=Path(__file__).resolve().parent
    zig=Path(ziglang.__file__).parent/'zig'
    base=[str(zig),'cc','-target','aarch64-linux-android','-shared','-fPIC','-nostdlib','-O2','-fno-stack-protector']
    imports={'c':['dl_iterate_phdr','getpagesize','mmap','munmap','mprotect','memcmp','memcpy','memset','strlen','getenv','snprintf','open','write','close'],
             'dl':['dlopen','dlsym','dladdr'],'log':['__android_log_write']}
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='bodriosafio-link-') as temporary:
        stubs=Path(temporary)
        for lib,names in imports.items():
            source=stubs/(lib+'.c')
            source.write_text('\n'.join('void '+name+'(void) {}' for name in names)+'\n')
            subprocess.run(base+['-fno-builtin','-Wl,-soname,lib'+lib+'.so',str(source),'-o',str(stubs/('lib'+lib+'.so'))],check=True)
        subprocess.run(base+['-Wall','-Wextra','-Werror','-fvisibility=hidden',
            '-Wl,-soname,libBodriosafioEffects.so','-Wl,-z,max-page-size=16384','-Wl,--no-undefined',
            str(root/'effects.c'),'-L'+str(stubs),'-Wl,--no-as-needed',
            *[str(stubs/('lib'+lib+'.so')) for lib in imports],'-o',str(output)],check=True)
    return {'output':str(output),'bytes':output.stat().st_size,
            'sha256':hashlib.sha256(output.read_bytes()).hexdigest()}

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    print(json.dumps(build(parser.parse_args().output)))
