#!/usr/bin/env python3
"""Prepare the complete local source project without publishing or signing an APK."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import stat
import subprocess
import sys
import re
import zipfile

HERE = Path(__file__).resolve().parent
CONFIG = json.loads((HERE / 'preparation.json').read_text())
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('source_archive', type=Path)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--native-effects-library', type=Path,
                    help='Optional prebuilt module; must match the pinned SHA-256')
args = parser.parse_args()
source = args.source_archive.resolve()
output = args.output.resolve()
if hashlib.sha256(source.read_bytes()).hexdigest() != CONFIG['source_archive_sha256']:
    parser.error('La base no es el archivo 0.1.5 r7 revisado. No se aplicó el overlay.')
if output.exists():
    parser.error('El destino debe ser una carpeta nueva; no se sobrescribe un proyecto existente.')

with zipfile.ZipFile(source) as archive:
    entries = [item for item in archive.infolist() if not item.is_dir()]
    prefixes = {Path(item.filename).parts[0] for item in entries}
    if len(prefixes) != 1 or sum(item.file_size for item in entries) > 150 * 1024 * 1024:
        parser.error('Estructura inesperada en el archivo base.')
    pending = []
    for item in entries:
        raw = Path(item.filename)
        if raw.is_absolute() or '..' in raw.parts or stat.S_ISLNK(item.external_attr >> 16):
            parser.error('El archivo base contiene una ruta insegura.')
        relative = Path(*raw.parts[1:])
        if not relative.parts or relative.parts[0] in ('.git', '.github'):
            continue
        if relative.suffix.lower() in ('.jks', '.keystore', '.p12', '.pfx', '.key'):
            parser.error('El archivo base contiene material de firma que no debe copiarse.')
        pending.append((item, relative))
    output.mkdir(parents=True)
    for item, relative in pending:
        target = output / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(archive.read(item))

for file in (HERE / 'overlay').rglob('*'):
    if file.is_file():
        target = output / file.relative_to(HERE / 'overlay')
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(file, target)

shutil.copytree(HERE / 'native-effects', output / 'native-effects', dirs_exist_ok=True,
                ignore=shutil.ignore_patterns('__pycache__'))
native_output = output / 'app/src/main/assets/bodriosafio/libBodriosafioEffects.so'
if args.native_effects_library:
    shutil.copy2(args.native_effects_library, native_output)
else:
    subprocess.run([sys.executable, str(HERE / 'native-effects/build_native.py'),
                    '--output', str(native_output)], check=True)
effects_profile = (HERE / 'overlay/app/src/main/java/org/levimc/launcher/bodriosafio/NativeEffectsProfile.java').read_text()
expected_native_hash = re.search(r'NATIVE_SHA256 = "([0-9a-f]{64})"', effects_profile).group(1)
if hashlib.sha256(native_output.read_bytes()).hexdigest() != expected_native_hash:
    parser.error('El módulo compilado no coincide con el hash del perfil de Anticombustión.')

(output / 'PREPARACION_0_1_6.json').write_text(json.dumps(CONFIG, ensure_ascii=False, indent=2) + '\n')
(output / 'PREPARACION_0_1_6.md').write_text(
    '# Android 0.1.6 / 10012: fuentes preparadas\n\n'
    'Origen migrado a alanbustamorag-source/panconqueso.\n'
    'Nether y Anticombustión adaptados al motor ARM64 26.66 identificado.\n'
    'No se ha generado una APK ni se ha usado una clave de firma.\n'
    'Registro y HUD de Anticombustión, y ABI del Nether comprobados en emulación.\n'
    'Antes de distribuir: compilar, firmar y probar dentro del juego en Android.\n'
    'La distribución automática sigue pendiente de un destino accesible a jugadores.\n')
print(json.dumps({'prepared_project': str(output), 'version': CONFIG['version'],
                  'version_code': CONFIG['version_code'], 'apk_built': False,
                  'keys_copied': False, 'status': CONFIG['apk_distribution_status']}, ensure_ascii=False))
