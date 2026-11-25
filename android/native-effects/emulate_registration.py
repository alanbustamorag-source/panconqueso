"""Execute the supplied engine's ARM64 effect factory in isolation.

The engine's C++ constructor, hash/name map and effect lookups execute their
actual instructions. Only external libc allocation/string/locking calls are
supplied by this harness. This is not an Android/gameplay test.
"""
from pathlib import Path
import argparse
import json,struct
import numpy as np
from elftools.elf.elffile import ELFFile
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_CODE
from unicorn.arm64_const import *

ROOT=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('engine',type=Path)
parser.add_argument('cpp_library',type=Path)
parser.add_argument('module',type=Path)
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
profile=json.loads((ROOT/'profile.json').read_text())
BASE=0x100000000
STUB_BASE=0x60000000
HEAP=0x78000000
STACK=0x71000000
TLS=0x70000000
TABLE=0x68000000+(profile['registry']&4095)
CPP_BASE=0x200000000
uc=Uc(UC_ARCH_ARM64,UC_MODE_ARM)
stubs={};names={};allocations={};heap_next=HEAP

def stub(name):
    if name not in stubs:
        address=STUB_BASE+len(stubs)*16
        stubs[name]=address;names[address]=name
    return stubs[name]

def load_engine(path,base,providers=None):
    providers=providers or {}
    exports={}
    with open(path,'rb') as f:
        e=ELFFile(f)
        segments=[p for p in e.iter_segments() if p['p_type']=='PT_LOAD']
        size=(max(p['p_vaddr']+p['p_memsz'] for p in segments)+4095)&~4095
        image=bytearray(size)
        for p in segments:image[p['p_vaddr']:p['p_vaddr']+p['p_filesz']]=p.data()
        view=np.frombuffer(image,dtype='<u8')
        raw=e.get_section_by_name('.dynsym').data()
        syms=np.frombuffer(raw,dtype=np.dtype([('name','<u4'),('info','u1'),('other','u1'),('shndx','<u2'),('value','<u8'),('size','<u8')]))
        symbol_section=e.get_section_by_name('.dynsym')
        for s in symbol_section.iter_symbols():
            if s['st_value']:exports[s.name]=base+s['st_value']
        for section_name in ['.rela.dyn','.rela.plt']:
            s=e.get_section_by_name(section_name)
            if s is None:continue
            a=np.frombuffer(s.data(),dtype=np.dtype([('offset','<u8'),('info','<u8'),('addend','<i8')]))
            types=a['info']&0xffffffff
            relative=a[types==1027]
            assert np.all(relative['offset']%8==0)
            view[relative['offset']//8]=base+relative['addend'].astype(np.uint64)
            for r in a[types!=1027]:
                typ=int(r['info'])&0xffffffff
                if typ not in [257,1025,1026]:continue
                index=int(r['info'])>>32
                value=int(syms[index]['value'])
                name=symbol_section.get_symbol(index).name
                resolved=base+value if value else providers.get(name)
                if resolved is None:resolved=stub(name)
                struct.pack_into('<Q',image,int(r['offset']),resolved+int(r['addend']))
        uc.mem_map(base,size)
        uc.mem_write(base,bytes(image))
    print(Path(path).name,'mapped and dynamically relocated',flush=True)
    return exports

def alloc(n):
    global heap_next
    n=max(int(n),1);p=heap_next;heap_next=(p+n+15)&~15
    if heap_next>=HEAP+0x2000000:raise RuntimeError('emulator heap full')
    allocations[p]=n
    return p

def cstr(p):
    out=bytearray()
    while True:
        b=uc.mem_read(p+len(out),1)[0]
        if not b:return bytes(out)
        out.append(b)
        if len(out)>100000:raise RuntimeError('unterminated C string')

def ret(value=0):
    uc.reg_write(UC_ARM64_REG_X0,value&0xffffffffffffffff)
    uc.reg_write(UC_ARM64_REG_PC,uc.reg_read(UC_ARM64_REG_LR))

external_calls={}
def external(machine,address,size,data):
    name=names.get(address)
    if not name:return
    external_calls[name]=external_calls.get(name,0)+1
    args=[uc.reg_read(UC_ARM64_REG_X0+i) for i in range(4)]
    a,b,c,d=args
    if name in ['malloc','_Znwm','_Znam']:ret(alloc(a))
    elif name=='calloc':
        p=alloc(a*b);uc.mem_write(p,b'\0'*(a*b));ret(p)
    elif name in ['free','_ZdlPv','_ZdaPv']:ret()
    elif name in ['memcpy','memmove','__memcpy_chk','__memmove_chk']:
        uc.mem_write(a,bytes(uc.mem_read(b,c)));ret(a)
    elif name in ['memset','__memset_chk']:
        uc.mem_write(a,bytes([b&255])*c);ret(a)
    elif name=='strlen':ret(len(cstr(a)))
    elif name in ['strcmp','strncmp','memcmp','bcmp']:
        aa=cstr(a) if name in ['strcmp','strncmp'] else bytes(uc.mem_read(a,c))
        bb=cstr(b) if name in ['strcmp','strncmp'] else bytes(uc.mem_read(b,c))
        if name=='strncmp':aa=aa[:c];bb=bb[:c]
        ret((aa>bb)-(aa<bb))
    elif name=='memchr':
        q=bytes(uc.mem_read(a,c)).find(bytes([b&255]));ret(a+q if q>=0 else 0)
    elif name=='__cxa_guard_acquire':ret(0 if uc.mem_read(a,1)[0] else 1)
    elif name=='__cxa_guard_release':uc.mem_write(a,b'\x01');ret()
    elif name=='__cxa_atexit' or name.startswith('pthread_mutex_') or name.startswith('pthread_rwlock_'):ret()
    elif name=='snprintf':
        uc.mem_write(a,b'{}\0');ret(2)
    elif name=='__android_log_write' or name=='emulated_engine_init':ret(0)
    elif name in ['__stack_chk_fail','abort','__assert2','__cxa_throw']:
        raise RuntimeError('native failure: '+name)
    else:raise RuntimeError(f'Unhandled external {name}: {[hex(x) for x in args]}')

def call(offset,args=(),indirect_result=0):
    uc.reg_write(UC_ARM64_REG_SP,STACK+0x1ff000)
    uc.reg_write(UC_ARM64_REG_LR,STUB_BASE+0xff000)
    uc.reg_write(UC_ARM64_REG_X8,indirect_result)
    for i,a in enumerate(args):uc.reg_write(UC_ARM64_REG_X0+i,a)
    uc.emu_start(BASE+offset,STUB_BASE+0xff000,count=2000000)
    pc=uc.reg_read(UC_ARM64_REG_PC)
    if pc!=STUB_BASE+0xff000:raise RuntimeError(f'native call did not return: {pc:x}')
    return uc.reg_read(UC_ARM64_REG_X0)

def string(value):
    raw=value.encode();data=alloc(len(raw)+1);uc.mem_write(data,raw+b'\0')
    result=alloc(24);uc.mem_write(result,b'\0'*24)
    call(profile['string_ctor'],[result,data,len(raw)])
    return result

cpp=load_engine(args.cpp_library,CPP_BASE)
load_engine(args.engine,BASE,cpp)
uc.mem_map(STUB_BASE,0x100000)
uc.mem_map(HEAP,0x2000000)
uc.mem_map(STACK,0x200000)
uc.mem_map(TLS,0x10000)
uc.mem_map(TABLE&~4095,0x1000)
uc.reg_write(UC_ARM64_REG_TPIDR_EL0,TLS)
uc.reg_write(UC_ARM64_REG_CPACR_EL1,3<<20)
uc.hook_add(UC_HOOK_CODE,external,begin=STUB_BASE,end=STUB_BASE+0xeffff)

# Exact instructions generated for the module, applied to the engine image.
for at,before,after,_ in profile['instruction_patches']:
    assert struct.unpack('<I',uc.mem_read(BASE+at,4))[0]==before
    uc.mem_write(BASE+at,struct.pack('<I',after))
for at in profile['table_references']:
    before=struct.unpack('<I',uc.mem_read(BASE+at,4))[0]
    diff=((TABLE&~4095)-((BASE+at)&~4095))>>12
    assert -(1<<20)<=diff<(1<<20)
    imm=diff&0x1fffff
    word=(before&0x9f00001f)|((imm&3)<<29)|((imm>>2)<<5)
    uc.mem_write(BASE+at,struct.pack('<I',word))
# The normal native static initializer sets the name map load factor to 1.
uc.mem_write(BASE+profile['name_map_load_factor'],struct.pack('<f',1.0))
name=string('bodriosafio:anticombustion')
loc=alloc(64);uc.mem_write(loc,b'effect.bodriosafio.anticombustion\0')
harmful=alloc(8);uc.mem_write(harmful,b'\0'*8)
color=alloc(8);uc.mem_write(color,struct.pack('<I',0xff9166))
icon=alloc(8);uc.mem_write(icon,b'\0'*8)
effect=call(profile['factory'],[39,name,loc,harmful,color,icon])
native_id=struct.unpack('<I',uc.mem_read(effect+8,4))[0]
by_name=call(profile['get_by_name'],[name])
by_id=call(profile['get_by_id'],[39])
assert native_id==39 and by_name==effect and by_id==effect
# The native icon getter must return the independent resource-pack icon stem.
icon_data=alloc(16);uc.mem_write(icon_data,b'anticombustion\0')
call(profile['string_ctor'],[effect+0xb0,icon_data,14])
icon_string=call(profile['icon_getter'],[effect])
assert uc.mem_read(icon_string,1)[0]==28 and cstr(icon_string+1)==b'anticombustion'
# Coexistence: register ordinary native entries using the same actual factory.
ordinary={}
for ident,key in [(0,'empty'),(1,'speed'),(28,'bad_omen'),(38,'freezing')]:
    n=string(key)
    ordinary[ident]=call(profile['factory'],[ident,n,loc,harmful,color,icon])
    assert call(profile['get_by_name'],[n])==ordinary[ident]
    assert call(profile['get_by_id'],[ident])==ordinary[ident]
assert call(profile['get_by_name'],[name])==effect
assert call(profile['get_by_id'],[39])==effect
# The script-facing string-or-EffectType resolver must accept the custom
# namespace unchanged, and retain the existing minecraft: namespace behavior.
script_argument=alloc(40)
uc.mem_write(script_argument,b'\0'*40)
uc.mem_write(script_argument,bytes(uc.mem_read(name,24)))
assert call(profile['script_resolver'],[script_argument])==effect
vanilla_name=string('minecraft:bad_omen')
uc.mem_write(script_argument,bytes(uc.mem_read(vanilla_name,24)))
assert call(profile['script_resolver'],[script_argument])==ordinary[28]
# The real native instance constructor accepts ID 39 and preserves its duration.
instance=alloc(0xa0)
call(profile['instance_constructor'],[instance,39,6000])
assert struct.unpack('<II',uc.mem_read(instance,8))==(39,6000)
assert uc.mem_read(instance+0x43,1)[0]==1
# Exercise the compiled module's HUD detour against the real game constructor,
# not a Python recreation. A 40-slot actor vector would overrun the original
# 39-slot HUD cache, so the extra slot and unchanged entries are both checked.
MOD_BASE=0x300000000
module_path=str(args.module)
load_engine(module_path,MOD_BASE)
with open(module_path,'rb') as mod_file:
    mod_elf=ELFFile(mod_file)
    module_symbols={s.name:MOD_BASE+s['st_value'] for s in mod_elf.get_section_by_name('.symtab').iter_symbols() if s['st_value']}
uc.mem_write(module_symbols['game_base'],struct.pack('<Q',BASE))
uc.mem_write(module_symbols['original_hud_ctor'],struct.pack('<Q',BASE+profile['hud_constructor']))
hud_original=alloc(0x80)
call(profile['hud_constructor'],[hud_original,0])
start,end,capacity=struct.unpack('<QQQ',uc.mem_read(hud_original+0x18,24))
assert end-start==capacity-start==39*36
original_hud=bytes(uc.mem_read(start,39*36))
hud=alloc(0x80)
call(module_symbols['hud_ctor_hook']-BASE,[hud,0])
start,end,capacity=struct.unpack('<QQQ',uc.mem_read(hud+0x18,24))
assert end-start==capacity-start==40*36
assert bytes(uc.mem_read(start,39*36))==original_hud
assert bytes(uc.mem_read(start+39*36,36))==original_hud[-36:]
# Execute the compiled C registration routine as well as the engine factory.
uc.mem_write(module_symbols['new_registry'],struct.pack('<Q',TABLE))
uc.mem_write(module_symbols['original_init'],struct.pack('<Q',stub('emulated_engine_init')))
call(module_symbols['init_effects_hook']-BASE,[0,0])
assert struct.unpack('<i',uc.mem_read(module_symbols['state'],4))[0]==2
registered=struct.unpack('<Q',uc.mem_read(module_symbols['effect'],8))[0]
assert call(profile['get_by_name'],[name])==registered
assert call(profile['get_by_id'],[39])==registered
assert call(module_symbols['bodriosafio_effect_state']-BASE)==2
assert call(profile['get_by_id'],[40])==0
# A different owner of ID 39 must be left intact and registration must fail.
foreign=string('foreign:reserved_effect')
occupied=call(profile['factory'],[39,foreign,loc,harmful,color,icon])
call(module_symbols['init_effects_hook']-BASE,[0,0])
assert struct.unpack('<i',uc.mem_read(module_symbols['state'],4))[0]==-1
assert call(profile['get_by_id'],[39])==occupied
assert call(profile['get_by_name'],[foreign])==occupied
print('NATIVE REGISTRATION VERIFIED',json.dumps({'id':native_id,'pointer':hex(effect),'lookup_by_name':hex(by_name),'lookup_by_id':hex(by_id)}),flush=True)
result={'engine_sha256':profile['engine_sha256'],'engine_build_id':profile['engine_build_id'],'minecraft':profile['minecraft'],'emulated_engine_registration':True,'native_id':39,'name_lookup_matches':True,'id_lookup_matches':True,'script_argument_custom_namespace_verified':True,'script_argument_vanilla_namespace_preserved':True,'own_icon_stem_verified':True,'native_instance_duration_ticks':6000,'ordinary_ids_preserved':list(ordinary),'compiled_module_hud_detour_verified':True,'compiled_c_registration_verified':True,'native_id_collision_refused':True,'out_of_range_id_refused':True,'hud_original_entries_preserved':39,'hud_expanded_entries':40,'extra_hud_entry_matches_native_initialization':True,'external_libc_calls':external_calls,'full_android_gameplay_tested':False}
args.output.write_text(json.dumps(result,indent=2)+'\n')
