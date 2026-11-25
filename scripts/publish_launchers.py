#!/usr/bin/env python3
"""Publish reviewed launcher bytes; never accepts a game binary or a signing key."""
from pathlib import Path
import base64
import concurrent.futures
import hashlib
import json
import os
import re
import subprocess
import urllib.request
import zipfile

REPO = 'alanbustamorag-source/panconqueso'
CERT = '144b63f97269476b81895a3de9ac6ee3172cedde94399f8dae7c2bc4279beb47'
if os.environ['GITHUB_REPOSITORY'] != REPO:
    raise SystemExit('Unexpected repository')
ROOT = Path(__file__).resolve().parents[1]
WORK = Path(os.environ['RUNNER_TEMP']) / 'bodriosafio-launchers'
WORK.mkdir(exist_ok=True)
manifest = json.loads((ROOT / 'distribution/launchers.json').read_text())
if manifest['repository'] != REPO or manifest['schema'] != 1:
    raise SystemExit('Unexpected distribution manifest')


def gh(*args, **kwargs):
    return subprocess.run(['gh', *args], text=True, capture_output=True, **kwargs)


def fetch(sha):
    if not re.fullmatch('[0-9a-f]{40}', sha):
        raise ValueError('Invalid Git object')
    request = urllib.request.Request(f'https://api.github.com/repos/{REPO}/git/blobs/{sha}', headers={
        'Authorization': 'Bearer ' + os.environ['GH_TOKEN'],
        'Accept': 'application/vnd.github+json', 'User-Agent': 'Bodriosafio-release',
    })
    with urllib.request.urlopen(request, timeout=90) as response:
        payload = json.load(response)
    if payload.get('sha') != sha or payload.get('encoding') != 'base64':
        raise ValueError('Unexpected Git object')
    data = base64.b64decode(''.join(payload['content'].split()), validate=True)
    if hashlib.sha1(('blob ' + str(len(data)) + '\0').encode() + data).hexdigest() != sha:
        raise ValueError('Incomplete segment')
    return data


# Recover and verify every artifact before publishing either release.
for item in manifest['files']:
    name = item['name']
    if not re.fullmatch(r'Bodriosafio_Launcher_(Android|Windows)_[0-9_]+\.(apk|zip)', name):
        raise ValueError('Unexpected artifact name')
    saved = WORK / name
    state = gh('release', 'view', item['tag'], '--json', 'assets')
    present = state.returncode == 0 and any(a['name'] == name for a in json.loads(state.stdout)['assets'])
    if present:
        gh('release', 'download', item['tag'], '--pattern', name, '--dir', str(WORK), check=True)
    else:
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            with saved.open('wb') as output:
                for data in pool.map(fetch, item['segments']):
                    output.write(data)
    if saved.stat().st_size != item['size'] or hashlib.sha256(saved.read_bytes()).hexdigest() != item['sha256']:
        raise ValueError('Artifact integrity check failed')
    with zipfile.ZipFile(saved) as archive:
        if archive.testzip() is not None:
            raise ValueError('Corrupt archive')
        if any(Path(n).name.lower() in ('libminecraftpe.so', 'minecraft.windows.exe') for n in archive.namelist()):
            raise ValueError('Game binaries cannot be published')
        if item['platform'] == 'android':
            addon = archive.read('assets/bodriosafio/Bodriosafio_0_3_15.mcaddon')
            if hashlib.sha256(addon).hexdigest() != '900687d53f42d257fcd0642cde8a49952aad696e42b99b467c747879f5e255e3':
                raise ValueError('Unexpected included addon')
        else:
            exe = archive.read('Bodriosafio_Launcher_Windows_0_1_6.exe')
            if not exe.startswith(b'MZ') or hashlib.sha256(exe).hexdigest() != item['exe_sha256']:
                raise ValueError('Unexpected Windows executable')
    if item['platform'] == 'android':
        sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
        signer = sorted(sdk.glob('build-tools/*/apksigner'))[-1]
        signature = subprocess.check_output([str(signer), 'verify', '--verbose', '--print-certs', str(saved)], text=True)
        digests = set(re.findall(r'certificate SHA-256 digest:\s*([0-9a-fA-F]{64})', signature))
        if digests != {CERT} or not re.search(r'^Number of signers:\s*1\s*$', signature, re.MULTILINE):
            raise ValueError('Unexpected Android signer')
        aapt = signer.parent / 'aapt'
        badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(saved)], text=True)
        identity = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.MULTILINE)
        if not identity or identity.groups() != ('org.levimc.launcher', str(item['version_code']), item['version']):
            raise ValueError('Unexpected Android package or revision')
    item['present'] = present
    print(name + ': verified')

for item in manifest['files']:
    saved = WORK / item['name']
    notes = WORK / (item['platform'] + '-notes.md')
    notes.write_text(item['notes'] + '\n\nSHA-256: `' + item['sha256'] + '`\n')
    exists = gh('release', 'view', item['tag']).returncode == 0
    if exists:
        if not item['present']:
            gh('release', 'upload', item['tag'], str(saved), check=True)
    else:
        gh('release', 'create', item['tag'], str(saved), '--draft', '--target', os.environ['GITHUB_SHA'],
           '--title', item['title'], '--notes-file', str(notes), check=True)
    gh('release', 'edit', item['tag'], '--draft=false',
       '--latest=' + ('true' if item['platform'] == 'android' else 'false'), check=True)
    print(item['tag'] + ': published')

# Advertise Android only after its public release exists. Keep a newer channel intact.
android = next(item for item in manifest['files'] if item['platform'] == 'android')
current = json.loads(gh('api', f'repos/{REPO}/contents/launcher-latest.json', check=True).stdout)
old = json.loads(base64.b64decode(current['content']))
feed = dict(old, version=android['version'], version_code=android['version_code'],
            size=android['size'], sha256=android['sha256'], notes=android['notes'],
            url=f"https://github.com/{REPO}/releases/download/{android['tag']}/{android['name']}")
if old['version_code'] < feed['version_code']:
    payload = {'message': 'Publicar canal Android ' + android['version'], 'branch': 'main',
               'sha': current['sha'], 'content': base64.b64encode((json.dumps(feed, ensure_ascii=False, indent=2) + '\n').encode()).decode()}
    gh('api', '--method', 'PUT', f'repos/{REPO}/contents/launcher-latest.json', '--input', '-',
       input=json.dumps(payload), check=True)
    print('Android update channel published')
elif old != feed and old['version_code'] == feed['version_code']:
    raise ValueError('This Android revision already identifies different bytes')
