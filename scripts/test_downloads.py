#!/usr/bin/env python3
"""Run addon transfer/installation tests with the pinned host JSON implementation."""
from pathlib import Path
import hashlib
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
jar = Path(sys.argv[1]).resolve()
if hashlib.sha256(jar.read_bytes()).hexdigest() != '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed':
    raise SystemExit('Unexpected host JSON dependency')
output = ROOT / 'build/download-tests'
output.mkdir(parents=True, exist_ok=True)
source = ROOT / 'android/overlay/app/src/main/java/org/levimc/launcher/bodriosafio'
files = [source / name for name in (
    'ProfileRules.java', 'update/AddonChannel.java', 'update/AddonInstaller.java',
    'update/LauncherChannel.java',
)] + [ROOT / 'android/tests/AddonUpdaterHostTest.java']
subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-cp', str(jar),
                '-d', str(output)] + [str(file) for file in files], check=True)
subprocess.run(['java', '-cp', str(output) + ':' + str(jar), 'AddonUpdaterHostTest',
                str(ROOT / 'Bodriosafio_0_3_15.mcaddon'), str(ROOT / 'latest.json')], check=True)
