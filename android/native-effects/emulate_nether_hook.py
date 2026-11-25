#!/usr/bin/env python3
"""Check the unchanged, pinned Nether hook against the new dimension argument layout.

Executes the actual module's ARM64 argument scanner and v2 detour. The config
lookup and original constructor are controlled callbacks. This is an ABI test,
not an Android or world-generation test.
"""
from pathlib import Path
import argparse, hashlib, json, struct
from elftools.elf.elffile import ELFFile
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_CODE
from unicorn.arm64_const import UC_ARM64_REG_PC, UC_ARM64_REG_SP, UC_ARM64_REG_LR, UC_ARM64_REG_X0, UC_ARM64_REG_X1, UC_ARM64_REG_X2, UC_ARM64_REG_X3, UC_ARM64_REG_CPACR_EL1

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('module',type=Path)
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
data=args.module.read_bytes()
if hashlib.sha256(data).hexdigest()!='4791a3d41884a4c49bc6ba02327684433ede70a8ef03fc3a351c01f321b97a17':
    raise ValueError('A different module needs its own ABI test profile')
BASE=0x100000000
ARENA=0x70000000
STACK=0x71000000
CALLBACK=ARENA+0x8000
STOP=ARENA+0x9000
uc=Uc(UC_ARCH_ARM64,UC_MODE_ARM)
with args.module.open('rb') as f:
    elf=ELFFile(f)
    segments=[s for s in elf.iter_segments() if s['p_type']=='PT_LOAD']
    size=(max(s['p_vaddr']+s['p_memsz'] for s in segments)+4095)&~4095
    uc.mem_map(BASE,size)
    for s in segments:uc.mem_write(BASE+s['p_vaddr'],s.data())
uc.mem_map(ARENA,0x10000)
uc.mem_map(STACK,0x10000)
uc.reg_write(UC_ARM64_REG_CPACR_EL1,3<<20)
# The pinned module's dimension_hook.original field.
uc.mem_write(BASE+0xb840,struct.pack('<Q',CALLBACK))
scenario={}

def cstr(at):
    raw=bytearray()
    while (b:=uc.mem_read(at+len(raw),1)[0]):raw.append(b)
    return bytes(raw)

def hook(machine,address,size,unused):
    if address==BASE+0x2044:
        name=cstr(uc.reg_read(UC_ARM64_REG_X0))
        length=uc.reg_read(UC_ARM64_REG_X1)
        lo=uc.reg_read(UC_ARM64_REG_X2)&0xffff
        hi=uc.reg_read(UC_ARM64_REG_X3)&0xffff
        scenario['lookup']=(name[:length].decode(),lo,hi)
        result=(256<<16) if name[:length]==b'Nether' else lo|(hi<<16)
        uc.reg_write(UC_ARM64_REG_X0,result)
        uc.reg_write(UC_ARM64_REG_PC,uc.reg_read(UC_ARM64_REG_LR))
    elif address==CALLBACK:
        assert uc.reg_read(UC_ARM64_REG_X0)==ARENA+0x1000
        assert uc.reg_read(UC_ARM64_REG_X1)==ARENA
        scenario['forwarded']=True
        uc.reg_write(UC_ARM64_REG_PC,uc.reg_read(UC_ARM64_REG_LR))

uc.hook_add(UC_HOOK_CODE,hook)
results=[]
for name,low,high in [('Nether',0,128),('Overworld',-64,320),('TheEnd',0,256)]:
    # The exact engine constructor reads dim ID/range/name at 0x60/0x64/0x68.
    blob=bytearray(256)
    struct.pack_into('<ihh',blob,0x60,1,low,high)
    raw=name.encode()
    blob[0x68]=len(raw)*2
    blob[0x69:0x69+len(raw)]=raw
    uc.mem_write(ARENA,bytes(blob))
    scenario.clear()
    uc.reg_write(UC_ARM64_REG_SP,STACK+0xf000)
    uc.reg_write(UC_ARM64_REG_LR,STOP)
    uc.reg_write(UC_ARM64_REG_X0,ARENA+0x1000)
    uc.reg_write(UC_ARM64_REG_X1,ARENA)
    uc.emu_start(BASE+0x1804,STOP,count=100000)
    assert uc.reg_read(UC_ARM64_REG_PC)==STOP
    assert scenario['forwarded']
    assert scenario['lookup']==(name,low&0xffff,high)
    actual=struct.unpack('<hh',uc.mem_read(ARENA+0x64,4))
    expected=(0,256) if name=='Nether' else (low,high)
    assert actual==expected,(name,actual)
    results.append({'dimension':name,'range_before':[low,high],'range_after':list(actual),'original_arguments_forwarded':True})
report={'compiled_v2_hook_abi_verified':True,'engine_build_id':'e96ddd68541814923c35460f8b99f27a66c51232',
        'dimension_arguments_offset':96,'scenarios':results,'full_android_gameplay_tested':False}
args.output.write_text(json.dumps(report,indent=2)+'\n')
print('Native Nether hook ABI verified; other dimensions preserved')
