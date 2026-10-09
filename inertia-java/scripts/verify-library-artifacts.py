#!/usr/bin/env python3
"""Inspect actual library binaries/source/Javadoc archives; no extraction or publication."""
import hashlib
import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

root = pathlib.Path(__file__).resolve().parents[1]
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
pom = ET.parse(root / 'pom.xml').getroot()
version = pom.findtext('m:version', namespaces=ns)
assert version and re.fullmatch(r'[A-Za-z0-9_.-]+', version), 'Invalid project version'
assert pom.findtext('m:name', namespaces=ns)
assert pom.findtext('m:description', namespaces=ns)
assert pom.findtext('m:url', namespaces=ns) == 'https://github.com/royalwang/inertia-omega/tree/main/inertia-java'
assert pom.findtext('m:scm/m:connection', namespaces=ns) == 'scm:git:https://github.com/royalwang/inertia-omega.git'
assert pom.findtext('m:licenses/m:license/m:name', namespaces=ns) == 'Apache License, Version 2.0'
license_bytes = (root / 'LICENSE').read_bytes()
assert b'Apache License' in license_bytes and b'END OF TERMS AND CONDITIONS' in license_bytes
notice_bytes = (root / 'NOTICE').read_bytes()
assert b'Copyright (c) 2026 royalwang' in notice_bytes
modules = ['inertia-core', 'inertia-ssr-http', 'inertia-vite', 'inertia-spring-webmvc', 'inertia-spring-boot-autoconfigure', 'inertia-spring-boot-starter', 'inertia-testing']
artifacts = []
for module in modules:
    base = root / module
    sources = {p.relative_to(base / 'src/main/java').as_posix(): p.read_bytes() for p in (base / 'src/main/java').rglob('*.java')}
    assert sources, f'{module}: source archive must have owned source/documentation'
    module_pom = ET.parse(base / 'pom.xml').getroot()
    assert module_pom.findtext('m:artifactId', namespaces=ns) == module
    assert module_pom.findtext('m:parent/m:version', namespaces=ns) == version
    api_pages = []
    for name, content in sources.items():
        if re.search(rb'(?m)^public\s+(?:(?:final|abstract|sealed|non-sealed)\s+)*(?:class|interface|record|enum|@interface)\s+', content):
            api_pages.append(name.removesuffix('.java') + '.html')
    for classifier in ['', '-sources', '-javadoc']:
        path = base / 'target' / f'{module}-{version}{classifier}.jar'
        with zipfile.ZipFile(path) as archive:
            assert archive.testzip() is None, f'{path}: corrupt archive'
            names = archive.namelist()
            assert len(names) == len(set(names)), f'{path}: duplicate entry'
            assert all(not n.startswith('/') and '..' not in pathlib.PurePosixPath(n).parts for n in names)
            license_path = 'resources/LICENSE' if classifier == '-javadoc' else 'META-INF/LICENSE'
            assert archive.read(license_path) == license_bytes, f'{module}{classifier}: missing/changed license'
            notice_path = 'resources/NOTICE' if classifier == '-javadoc' else 'META-INF/NOTICE'
            assert archive.read(notice_path) == notice_bytes, f'{module}{classifier}: missing/changed copyright notice'
            assert not any(n.startswith(('node_modules/', 'frontend/', 'BOOT-INF/')) for n in names), f'{path}: runtime example leaked into library'
            if classifier == '-sources':
                archived = {n: archive.read(n) for n in names if n.endswith('.java')}
                assert archived == sources, f'{module}: missing/extra/changed Java source'
                assert not any(n.endswith('.class') for n in names)
                for resource in (base / 'src/main/resources').rglob('*'):
                    if resource.is_file():
                        relative = resource.relative_to(base / 'src/main/resources').as_posix()
                        assert archive.read(relative) == resource.read_bytes(), f'{module}: missing/changed source resource {relative}'
            elif classifier == '-javadoc':
                assert archive.read('index.html'), f'{module}: empty documentation'
                assert not any(n.endswith(('.class', '.java')) for n in names)
                for page in api_pages:
                    assert archive.read(page), f'{module}: public API page missing: {page}'
                if module == 'inertia-spring-boot-starter':
                    assert b'no public runtime Java API' in archive.read('index.html'), 'Dependency-only starter must explain its scope'
                else:
                    assert api_pages, f'{module}: no public API inventory'
            else:
                assert not any(n.endswith('.java') for n in names)
                for page in api_pages:
                    assert page.removesuffix('.html') + '.class' in names, f'{module}: binary public API missing: {page}'
        artifacts.append({'module': module, 'classifier': classifier.removeprefix('-') or 'binary', 'file': str(path), 'bytes': path.stat().st_size, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
report = {'format': 1, 'version': version, 'success': True, 'publicationQualified': False,
          'remainingPublicationGates': ['license and attribution review', 'release version/tag policy', 'signatures and repository namespace/credentials'],
          'artifacts': artifacts}
if len(sys.argv) > 1:
    destination = pathlib.Path(sys.argv[1]).resolve()
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, indent=2) + '\n')
print(f'Verified {len(artifacts)} binary/source/Javadoc archives with source bytes and public API inventory; public publication remains unqualified')
