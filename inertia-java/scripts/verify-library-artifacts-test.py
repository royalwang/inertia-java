#!/usr/bin/env python3
"""Exercise artifact gate rejection on an isolated copy, preserving live build outputs."""
import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile
root = pathlib.Path(__file__).resolve().parents[1]
namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
version = ET.parse(root / 'pom.xml').getroot().findtext('m:version', namespaces=namespace)
modules = ['inertia-core', 'inertia-ssr-http', 'inertia-vite', 'inertia-session-redis', 'inertia-spring-webmvc', 'inertia-spring-boot-autoconfigure', 'inertia-spring-boot-starter', 'inertia-testing']
with tempfile.TemporaryDirectory(prefix='inertia-artifact-contracts-') as directory:
    fixture = pathlib.Path(directory)
    shutil.copy2(root / 'pom.xml', fixture / 'pom.xml')
    for name in ['LICENSE', 'NOTICE']:
        shutil.copy2(root / name, fixture / name)
    (fixture / 'scripts').mkdir()
    shutil.copy2(root / 'scripts/verify-library-artifacts.py', fixture / 'scripts/verify-library-artifacts.py')
    for module in modules:
        base = fixture / module
        base.mkdir()
        shutil.copy2(root / module / 'pom.xml', base / 'pom.xml')
        if (root / module / 'src').exists():
            shutil.copytree(root / module / 'src/main', base / 'src/main')
        (base / 'target').mkdir()
        for classifier in ['', '-sources', '-javadoc']:
            name = f'{module}-{version}{classifier}.jar'
            shutil.copy2(root / module / 'target' / name, base / 'target' / name)
    def verify(expected, message=''):
        run = subprocess.run([sys.executable, str(fixture / 'scripts/verify-library-artifacts.py')], capture_output=True, text=True)
        assert (run.returncode == 0) == expected, run.stdout + run.stderr
        if message:
            assert message in run.stderr, run.stderr
    def rewrite(path, drop=None, replace=None):
        original = path.read_bytes()
        with zipfile.ZipFile(path) as source:
            entries = [(entry, source.read(entry.filename)) for entry in source.infolist()]
        with zipfile.ZipFile(path, 'w') as target:
            for entry, value in entries:
                if entry.filename == drop:
                    continue
                if replace and entry.filename == replace[0]:
                    value = replace[1]
                target.writestr(entry, value)
        return original
    verify(True)
    source = fixture / 'inertia-core/target' / f'inertia-core-{version}-sources.jar'
    original = rewrite(source, replace=('io/inertia/core/Page.java', b'// tampered source\n'))
    verify(False, 'missing/extra/changed Java source')
    source.write_bytes(original)
    docs = fixture / 'inertia-core/target' / f'inertia-core-{version}-javadoc.jar'
    original = rewrite(docs, drop='io/inertia/core/Page.html')
    verify(False, 'Page.html')
    docs.write_bytes(original)
    binary = fixture / 'inertia-core/target' / f'inertia-core-{version}.jar'
    original = rewrite(binary, drop='io/inertia/core/Page.class')
    verify(False, 'binary public API missing')
    binary.write_bytes(original)
    docs.unlink()
    verify(False, '-javadoc.jar')
    docs.write_bytes((root / 'inertia-core/target' / docs.name).read_bytes())
    verify(True)
print('Verified 6 isolated artifact-gate checks: valid, changed source, missing API docs/class, missing classifier, restored')
