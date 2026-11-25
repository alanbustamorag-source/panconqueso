#!/usr/bin/env python3
"""Identify a local game engine for a native port; emit metadata, never game files."""
from pathlib import Path
import argparse
import hashlib
import json
import struct
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def build_id(file):
    file.seek(0)
    header = file.read(64)
    if len(header) != 64 or header[:6] != b'\x7fELF\x02\x01':
        raise ValueError('Se necesita una biblioteca ELF64 little-endian de Android.')
    fields = struct.unpack('<16sHHIQQQIHHHHHH', header)
    machine, phoff, phsize, phcount = fields[2], fields[5], fields[9], fields[10]
    if phsize < 56 or phcount > 4096:
        raise ValueError('Cabecera de segmentos ELF no válida.')
    for index in range(phcount):
        file.seek(phoff + index * phsize)
        raw = file.read(56)
        if len(raw) != 56:
            raise ValueError('Tabla ELF incompleta.')
        segment = struct.unpack('<IIQQQQQQ', raw)
        if segment[0] != 4:
            continue
        if segment[5] > 1024 * 1024:
            raise ValueError('Segmento de notas ELF demasiado grande.')
        file.seek(segment[2])
        notes = file.read(segment[5])
        cursor = 0
        while cursor + 12 <= len(notes):
            namesize, descsize, kind = struct.unpack_from('<III', notes, cursor)
            cursor += 12
            name = notes[cursor:cursor + namesize]
            cursor += (namesize + 3) & ~3
            end = cursor + descsize
            if end > len(notes):
                raise ValueError('Nota ELF incompleta.')
            descriptor = notes[cursor:end]
            cursor += (descsize + 3) & ~3
            if kind == 3 and name.rstrip(b'\0') == b'GNU':
                return machine, descriptor.hex()
    return machine, None


def inspect(path):
    support = json.loads((ROOT / 'native-support.json').read_text())
    with tempfile.TemporaryFile() as engine:
        if path.suffix.lower() in ('.apk', '.zip'):
            with zipfile.ZipFile(path) as archive:
                candidates = [item for item in archive.infolist()
                              if item.filename.endswith('/libminecraftpe.so')
                              and '/arm64-v8a/' in item.filename]
                if len(candidates) != 1:
                    raise ValueError('El archivo debe contener un único libminecraftpe.so ARM64.')
                entry = candidates[0]
                if entry.file_size > 1024 * 1024 * 1024:
                    raise ValueError('El motor excede el límite local de análisis.')
                source = archive.open(entry)
                try:
                    while chunk := source.read(1024 * 1024):
                        engine.write(chunk)
                finally:
                    source.close()
        else:
            with path.open('rb') as source:
                while chunk := source.read(1024 * 1024):
                    engine.write(chunk)
        size = engine.tell()
        engine.seek(0)
        digest = hashlib.file_digest(engine, 'sha256').hexdigest()
        machine, identifier = build_id(engine)
    if machine != 183:
        raise ValueError('El motor no es ARM64/AArch64.')
    def matching_versions(entries):
        return [p['minecraft'] for p in entries if p['platform'] == 'android'
                and p.get('engine_sha256') == digest and p.get('engine_build_id') == identifier]

    return {'platform': 'android', 'architecture': 'arm64-v8a', 'engine_bytes': size,
            'engine_sha256': digest, 'engine_build_id': identifier,
            'matching_profile_versions': matching_versions(support['profiles']),
            'matching_pending_versions': matching_versions(support['pending_targets']),
            'new_native_port_verified': False, 'gameplay_tested': False,
            'game_binary_exported': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('game_archive_or_library', type=Path)
    args = parser.parse_args()
    try:
        result = inspect(args.game_archive_or_library)
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        parser.error(str(error))
    print(json.dumps(result, ensure_ascii=False, indent=2))
