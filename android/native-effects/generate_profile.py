#!/usr/bin/env python3
"""Validate an exact local engine and generate mod metadata, never game binaries."""
from pathlib import Path
import argparse, hashlib, json, struct
from elftools.elf.elffile import ELFFile

def generate(profile_path, engine_path, output):
    profile=json.loads(profile_path.read_text())
    with engine_path.open('rb') as engine:
        if hashlib.file_digest(engine,'sha256').hexdigest()!=profile['engine_sha256']:
            raise ValueError('The engine SHA-256 does not match this exact profile')
        engine.seek(0)
        elf=ELFFile(engine)
        if elf['e_machine']!='EM_AARCH64':raise ValueError('ARM64 is required')
        segments=[s for s in elf.iter_segments() if s['p_type']=='PT_LOAD']
        def read(addr,n):
            for s in segments:
                if s['p_vaddr']<=addr and addr+n<=s['p_vaddr']+s['p_filesz']:
                    engine.seek(addr-s['p_vaddr']+s['p_offset'])
                    return engine.read(n)
            raise ValueError(f'Unmapped address: {addr:x}')
        def word(addr):return struct.unpack('<I',read(addr,4))[0]
        build=bytes.fromhex(profile['engine_build_id'])
        if read(profile['build_note_offset'],len(build))!=build:
            raise ValueError('The GNU Build ID does not match')
        lines=['/* Generated exact-build metadata; contains no game implementation. */']
        keys={'REGISTRY_OFFSET':'registry','INIT_OFFSET':'init_effects','HUD_CTOR_OFFSET':'hud_constructor',
              'BUILD_NOTE_OFFSET':'build_note_offset','STRING_CTOR_OFFSET':'string_ctor','FACTORY_OFFSET':'factory',
              'GET_BY_NAME_OFFSET':'get_by_name','GET_BY_ID_OFFSET':'get_by_id','ALLOCATE_OFFSET':'allocate',
              'RELEASE_OFFSET':'release','RECT_CTOR_OFFSET':'rect_constructor'}
        lines.extend(f'#define {macro} 0x{profile[key]:x}UL' for macro,key in keys.items())
        lines.extend([f'#define MODULE_VERSION "{profile["module_version"]}"',
                      f'#define ENGINE_BUILD_ID "{profile["engine_build_id"]}"',
                      'static const unsigned char expected_build_id[20] = {'+','.join(f'0x{x:02x}' for x in build)+'};',
                      'struct WordPatch { unsigned long offset; unsigned int before, after; };',
                      'static const struct WordPatch word_patches[] = {'])
        for at,before,after,reason in profile['instruction_patches']:
            if word(at)!=before:raise ValueError(f'Instruction mismatch at {at:x}')
            lines.append(f'  {{0x{at:x}UL,0x{before:08x}U,0x{after:08x}U}}, /* {reason} */')
        lines+=['};','struct TablePatch { unsigned long offset; unsigned int before; };',
                'static const struct TablePatch table_patches[] = {']
        for at in profile['table_references']:
            before=word(at)
            if before&0x9f000000!=0x90000000:raise ValueError(f'ADRP expected at {at:x}')
            lines.append(f'  {{0x{at:x}UL,0x{before:08x}U}},')
        lines+=['};','struct FunctionWord { unsigned long offset; unsigned int expected; };',
                'static const struct FunctionWord function_words[] = {']
        for at,expected in profile['function_fingerprints']:
            if word(at)!=expected:raise ValueError(f'Function mismatch at {at:x}')
            lines.append(f'  {{0x{at:x}UL,0x{expected:08x}U}},')
        lines+=['};','']
    output.write_text('\n'.join(lines))
    return {'range_patches':len(profile['instruction_patches']),
            'table_references':len(profile['table_references']),
            'function_fingerprints':len(profile['function_fingerprints'])}

if __name__=='__main__':
    root=Path(__file__).resolve().parent
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('engine',type=Path)
    parser.add_argument('--profile',type=Path,default=root/'profile.json')
    parser.add_argument('--output',type=Path,default=root/'profile.h')
    args=parser.parse_args()
    print(json.dumps(generate(args.profile,args.engine,args.output)))
