#!/usr/bin/env python3
"""Prepare the complete local source project without publishing or signing an APK."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import stat
import zipfile

HERE = Path(__file__).resolve().parent
CONFIG = json.loads((HERE / 'preparation.json').read_text())
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('source_archive', type=Path)
parser.add_argument('--output', type=Path, required=True)
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

(output / 'PREPARACION_0_1_7.json').write_text(json.dumps(CONFIG, ensure_ascii=False, indent=2) + '\n')
(output / 'PREPARACION_0_1_7.md').write_text(
    '# Android 0.1.7 / 10013: fuentes preparadas\n\n'
    'Origen migrado a alanbustamorag-source/panconqueso.\n'
    'Nativos obligatorios; la adaptación del hotfix objetivo exige el binario exacto.\n'
    'No se ha generado una APK ni se ha usado una clave de firma.\n'
    'Antes de distribuir: verificar la procedencia de las dependencias,\n'
    'compilar, probar en el dispositivo y firmar fuera de GitHub.\n'
    'La distribución firmada usa el canal launcher-latest.json del repositorio.\n')
print(json.dumps({'prepared_project': str(output), 'version': CONFIG['version'],
                  'version_code': CONFIG['version_code'], 'apk_built': False,
                  'keys_copied': False, 'status': CONFIG['apk_distribution_status']}, ensure_ascii=False))
