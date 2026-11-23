#!/usr/bin/env python3
"""Validate distribution inputs and run available, platform-independent tests."""
from pathlib import Path
from io import BytesIO
import hashlib
import json
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / 'build/validation'
BUILD.mkdir(parents=True, exist_ok=True)
REPOSITORY = 'alanbustamorag-source/panconqueso'
ADDON_HASH = '900687d53f42d257fcd0642cde8a49952aad696e42b99b467c747879f5e255e3'
BINARIES = {
    'Bodriosafio_0_3_15.mcaddon': ADDON_HASH,
    'windows/assets/Bodriosafio_0_3_15.mcaddon': ADDON_HASH,
    'windows/assets/windows-x64-BuildLimitChanger.dll': 'b6f1d16b1d455c6127b1c9f91b3b400c9a7c1c734c52381bc52387ca626afcf8',
    'windows/assets/BodriosafioEffects.dll': '24ca8d216db6a23acc5f6ec5ccd99735abb5eb1a05bc6ecc1d155fcf9335ea6a',
}
FORBIDDEN = {'.jks', '.keystore', '.p12', '.pfx', '.pem', '.key', '.apk', '.exe', '.so', '.aab'}
SECRET = re.compile(rb'gh[pousr]_[A-Za-z0-9]{25,}|github_pat_[A-Za-z0-9_]{25,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----')


def run(command, cwd=ROOT):
    result = subprocess.run(command, cwd=cwd, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout.strip()


files = []
for file in ROOT.rglob('*'):
    if not file.is_file() or any(part in ('build', '.git', '__pycache__') for part in file.relative_to(ROOT).parts):
        continue
    relative = file.relative_to(ROOT).as_posix()
    if file.suffix.lower() in FORBIDDEN or file.name in ('local.properties', 'signing.properties', 'key.properties') or file.name.startswith('.env'):
        raise RuntimeError('Archivo excluido de publicación: ' + relative)
    data = file.read_bytes()
    if SECRET.search(data):
        raise RuntimeError('Posible credencial en ' + relative)
    if file.name.lower() in ('minecraft.windows.exe', 'libminecraftpe.so'):
        raise RuntimeError('No se distribuyen binarios del juego')
    if relative in BINARIES and hashlib.sha256(data).hexdigest() != BINARIES[relative]:
        raise RuntimeError('Componente inesperado: ' + relative)
    files.append(relative)

addon = ROOT / 'Bodriosafio_0_3_15.mcaddon'
feed = json.loads((ROOT / 'latest.json').read_text())
assert feed['addon']['size'] == addon.stat().st_size
assert feed['addon']['sha256'] == hashlib.sha256(addon.read_bytes()).hexdigest() == ADDON_HASH
assert feed['addon']['url'] == f'https://raw.githubusercontent.com/{REPOSITORY}/main/{addon.name}'
pack_ids = {item['uuid']: item['version'] for item in feed['packs']}
with zipfile.ZipFile(addon) as outer:
    assert len(outer.namelist()) == 2
    for item in outer.infolist():
        assert item.filename.endswith('.mcpack')
        with zipfile.ZipFile(BytesIO(outer.read(item))) as pack:
            assert pack.testzip() is None
            for name in pack.namelist():
                path = Path(name)
                assert not path.is_absolute() and '..' not in path.parts
                assert path.suffix.lower() not in FORBIDDEN
            manifest = json.loads(pack.read('manifest.json'))
            assert pack_ids[manifest['header']['uuid']] == manifest['header']['version']
            assert 'LICENSE.Mojang.txt' in pack.namelist()

channel_sources = [
    ROOT / 'android/overlay/app/src/main/java/org/levimc/launcher/bodriosafio/update/AddonChannel.java',
    ROOT / 'android/overlay/app/src/main/java/org/levimc/launcher/bodriosafio/update/LauncherChannel.java',
    ROOT / 'windows/update_channel.hpp', ROOT / 'windows/launcher_update.hpp',
]
for source in channel_sources:
    text = source.read_text()
    assert REPOSITORY in text and 'Bnnnlss/bodriosafio-updates' not in text

java = shutil.which('java')
cxx = shutil.which('g++')
if not java or not cxx:
    raise RuntimeError('Se necesitan JDK 21 y g++ para ejecutar todas las comprobaciones del host')
java_output = BUILD / 'java'
java_output.mkdir(exist_ok=True)
run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(java_output),
     str(ROOT / 'android/overlay/app/src/main/java/org/levimc/launcher/bodriosafio/ProfileRules.java'),
     str(ROOT / 'android/tests/ProfileRulesHostTest.java')])
results = {'android_profile': run([java, '-cp', str(java_output), 'ProfileRulesHostTest'])}
for name, arguments in [
    ('profile', []), ('channel', ['tests/latest_0_3_13.json']),
    ('integrated', ['assets/BodriosafioEffects.dll']), ('effects_readiness', []),
]:
    executable = BUILD / ('test_' + name)
    run([cxx, '-std=c++17', '-O2', '-Wall', '-Wextra', '-Werror',
         str(ROOT / 'windows' / ('test_' + name + '.cpp')), '-o', str(executable)])
    results['windows_' + name] = run([str(executable)] + arguments, ROOT / 'windows')

report = {
    'checked_on': '2026-11-23', 'repository': REPOSITORY,
    'files_reviewed': len(files), 'addon_sha256': ADDON_HASH,
    'host_tests': results,
    'not_performed': ['Android APK compilation/signing', 'Windows EXE cross-compilation',
                      'Android device execution', 'Minecraft Windows execution',
                      'Anonymous download from private repository'],
}
(ROOT / 'validation').mkdir(exist_ok=True)
(ROOT / 'validation/resultados.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print(json.dumps(report, ensure_ascii=False, indent=2))
