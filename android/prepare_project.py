#!/usr/bin/env python3
"""Prepare the complete local source project without publishing or signing an APK."""
from pathlib import Path
import argparse
import hashlib
import io
import json
import shutil
import stat
import urllib.request
import zipfile

HERE = Path(__file__).resolve().parent
CONFIG = json.loads((HERE / 'preparation.json').read_text())
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('source_archive', type=Path)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--native-release-apk', type=Path, help='Copia local opcional de la APK publicada android-0.1.6 para preparar sin red.')
args = parser.parse_args()
source = args.source_archive.resolve()
output = args.output.resolve()
if hashlib.sha256(source.read_bytes()).hexdigest() != CONFIG['source_archive_sha256']:
    parser.error('La base no es el archivo 0.1.5 r7 revisado. No se aplicó el overlay.')
if output.exists():
    parser.error('El destino debe ser una carpeta nueva; no se sobrescribe un proyecto existente.')

if args.native_release_apk:
    release = args.native_release_apk.read_bytes()
else:
    with urllib.request.urlopen(CONFIG['effects_release_url'], timeout=60) as response:
        release = response.read(32 * 1024 * 1024)
if hashlib.sha256(release).hexdigest() != CONFIG['effects_release_sha256']:
    parser.error('La APK de origen no coincide con la publicación Android 0.1.6.')
with zipfile.ZipFile(io.BytesIO(release)) as release_zip:
    effects = release_zip.read('assets/bodriosafio/libBodriosafioEffects.so')
if hashlib.sha256(effects).hexdigest() != CONFIG['effects_26_66_1_sha256']:
    parser.error('La biblioteca de Anticombustión de 26.66.1 no coincide con el perfil.')
addon = HERE.parent / 'Bodriosafio_0_3_16.mcaddon'
if hashlib.sha256(addon.read_bytes()).hexdigest() != '41c85c8da165240a2c6e3c4b5a33fcff7fbdc679745d5cfd4636ea02b75f798d':
    parser.error('El addon incluido no coincide con la compilación revisada.')

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

assets = output / 'app/src/main/assets/bodriosafio'
(assets / '26.66.1').mkdir(exist_ok=True)
(assets / '26.66.1/libBodriosafioEffects.so').write_bytes(effects)
shutil.copy2(addon, assets / addon.name)
(assets / 'Bodriosafio_0_3_15.mcaddon').unlink(missing_ok=True)

record = 'PREPARACION_' + CONFIG['version'].replace('.', '_')
(output / (record + '.json')).write_text(json.dumps(CONFIG, ensure_ascii=False, indent=2) + '\n')
(output / (record + '.md')).write_text(
    f"# Android {CONFIG['version']} / {CONFIG['version_code']}\n\n"
    'Para compilar la APK, usar el proyecto preparado y firmar fuera del repositorio.\n'
    'El estado del perfil de cada versión de Minecraft está en native-support.json del repositorio.\n')
print(json.dumps({'prepared_project': str(output), 'version': CONFIG['version'],
                  'version_code': CONFIG['version_code'], 'apk_built': False,
                  'keys_copied': False, 'status': CONFIG['apk_distribution_status']}, ensure_ascii=False))
