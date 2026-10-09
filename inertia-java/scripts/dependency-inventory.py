#!/usr/bin/env python3
"""Collect declared licenses and artifact evidence; never grants publication approval."""
import collections
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import urllib.parse
import zipfile

ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / 'examples/spring-react/frontend'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def read(path):
    return json.loads(path.read_text())


def npm_identity(component):
    return ((component.get('group', '') + '/' if component.get('group') else '') + component['name'], component['version'])


def validate_bom(bom):
    if bom.get('bomFormat') != 'CycloneDX' or not bom.get('components'):
        raise ValueError('Missing or empty CycloneDX inventory')
    refs = [c['bom-ref'] for c in bom['components']]
    if len(set(refs)) != len(refs):
        raise ValueError('Duplicate component identity')
    declared = set(refs) | {bom['metadata']['component']['bom-ref']}
    if any(edge['ref'] not in declared or any(child not in declared for child in edge.get('dependsOn', []))
           for edge in bom.get('dependencies', [])):
        raise ValueError('Dependency graph references undeclared components')


def main():
    output = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(tempfile.mkdtemp(prefix='inertia-dependencies-'))
    if output.exists() and any(output.iterdir()):
        raise ValueError('Evidence directory must be new or empty')
    output.mkdir(parents=True, exist_ok=True)
    result = {'format': 1, 'success': False, 'publicationQualified': False, 'phases': [], 'output': str(output)}
    def save():
        (output / 'summary.json').write_text(json.dumps(result, indent=2) + '\n')
    def run(name, args, cwd, json_output=False):
        log = output / (name + ('.json' if json_output else '.log'))
        with log.open('w') as stream:
            process = subprocess.run(args, cwd=cwd, stdout=stream, stderr=subprocess.PIPE, text=True, timeout=300,
                                     env={**os.environ, 'NODE_ENV': 'development'})
        (output / (name + '.stderr.log')).write_text(process.stderr)
        result['phases'].append({'name': name, 'exit': process.returncode})
        save()
        if process.returncode:
            raise RuntimeError('Dependency inventory command failed: ' + name)
        return read(log) if json_output else None
    save()
    try:
        result['source'] = {'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                            'dirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip())}
        inputs = [ROOT / 'pom.xml', *ROOT.glob('*/pom.xml'), ROOT / 'examples/spring-react/pom.xml',
                  FRONTEND / 'package.json', FRONTEND / 'package-lock.json']
        result['inputs'] = {str(p.relative_to(ROOT)): sha(p.read_bytes()) for p in sorted(inputs)}
        run('maven', [str(ROOT / 'mvnw'), '--batch-mode', '--no-transfer-progress',
             'org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom', '-DoutputDirectory=' + str(output),
             '-DoutputFormat=json', '-DoutputReactorProjects=false', '-DincludeTestScope=true',
             '-DincludeLicenseText=false', '-Dcyclonedx.skipAttach=true'], ROOT)
        maven = read(output / 'bom.json')
        all_npm = run('npm-lock-all', ['npm', 'sbom', '--sbom-format=cyclonedx', '--package-lock-only',
                      '--include=dev', '--include=optional', '--include=peer'], FRONTEND, True)
        production = run('npm-lock-production', ['npm', 'sbom', '--sbom-format=cyclonedx', '--package-lock-only',
                         '--omit=dev', '--include=optional', '--include=peer'], FRONTEND, True)
        for bom in [maven, all_npm, production]:
            validate_bom(bom)
        lock = read(FRONTEND / 'package-lock.json')['packages']
        locked = collections.Counter((v.get('name', k.rsplit('node_modules/', 1)[-1]), v['version']) for k, v in lock.items() if k)
        if locked != collections.Counter(npm_identity(c) for c in all_npm['components']):
            raise ValueError('npm all-platform lock inventory is incomplete')
        prod_refs = {c['bom-ref'] for c in production['components']}
        if not prod_refs <= {c['bom-ref'] for c in all_npm['components']}:
            raise ValueError('Production npm inventory is not a subset of the locked graph')
        components = []
        reviews = []
        def add(component, ecosystem, scopes):
            row = {'ref': component['bom-ref'], 'name': component['name'], 'group': component.get('group'),
                   'version': component['version'], 'ecosystem': ecosystem, 'scopes': scopes,
                   'declaredLicenses': component.get('licenses', []), 'licenseFiles': []}
            components.append(row)
            if not row['declaredLicenses']:
                reviews.append({'ref': row['ref'], 'reason': 'missing-license-declaration'})
            elif len(row['declaredLicenses']) > 1 or any(token in json.dumps(row['declaredLicenses']).upper() for token in ['EPL-', 'GPL', 'MPL-', 'GNU']):
                reviews.append({'ref': row['ref'], 'reason': 'review-reciprocal-or-multiple-license-terms'})
            return row
        add(maven['metadata']['component'], 'maven', ['owned-parent-pom'])
        add(all_npm['metadata']['component'], 'npm', ['owned-frontend'])
        rows = {c['bom-ref']: add(c, 'maven', ['reactor-all-including-test-provided']) for c in maven['components']}
        by_hash = {h['content']: rows[c['bom-ref']] for c in maven['components'] for h in c.get('hashes', []) if h['alg'] == 'SHA-256'}
        owned = {c['name'] + '-' + c['version'] + '.jar': rows[c['bom-ref']] for c in maven['components'] if c.get('group') == 'io.inertia'}
        def license_file(row, name, data):
            if len(data) > 2 * 1024 * 1024:
                raise ValueError('Unbounded license file')
            digest = sha(data)
            relative = 'license-texts/' + digest + '.txt'
            path = output / relative
            path.parent.mkdir(exist_ok=True)
            if not path.exists(): path.write_bytes(data)
            row['licenseFiles'].append({'source': name, 'sha256': digest, 'path': relative})
        def is_license(name):
            return bool(re.match(r'(?i)^(licen[cs]e|notice|copying|copyright)([._-].*)?$', Path(name).name))
        jars = list((ROOT / 'examples/spring-react/target').glob('spring-react-*.jar'))
        if len(jars) != 1: raise ValueError('Build exactly one executable example jar first')
        result['applicationJar'] = {'name': jars[0].name, 'sha256': sha(jars[0].read_bytes())}
        with zipfile.ZipFile(jars[0]) as app:
            artifacts = [n for n in app.namelist() if n.startswith('BOOT-INF/lib/') and n.endswith('.jar')]
            if not artifacts: raise ValueError('Executable dependency archive is empty')
            for entry in artifacts:
                data = app.read(entry)
                digest = sha(data)
                row = by_hash.get(digest)
                if row is None and Path(entry).name in owned:
                    row = owned[Path(entry).name]
                    targets = list(ROOT.glob('*/target/' + Path(entry).name))
                    if len(targets) != 1 or sha(targets[0].read_bytes()) != digest:
                        raise ValueError('Executable owned jar differs from reactor artifact')
                if row is None:
                    ref = 'pkg:generic/' + urllib.parse.quote(Path(entry).name, safe='')
                    row = add({'bom-ref': ref, 'name': Path(entry).name, 'version': 'archive-observed'}, 'packaging', [])
                    reviews.append({'ref': ref, 'reason': 'archive-only-component-not-in-maven-dependency-graph'})
                row['scopes'].append('executable-java-runtime')
                row['artifact'] = {'entry': entry, 'sha256': digest}
                with zipfile.ZipFile(io.BytesIO(data)) as nested:
                    for name in nested.namelist():
                        if is_license(name) and not name.endswith('/'):
                            license_file(row, name, nested.read(name))
                if not row['licenseFiles']:
                    reviews.append({'ref': row['ref'], 'reason': 'runtime-archive-has-no-recognized-license-or-notice-file'})
        installed = 0
        for c in all_npm['components']:
            row = add(c, 'npm', ['lock-all-platforms'] + (['node-production-lock'] if c['bom-ref'] in prod_refs else ['build-dev-lock']))
            matches = [k for k, v in lock.items() if k and (v.get('name', k.rsplit('node_modules/', 1)[-1]), v['version']) == npm_identity(c)]
            row['lockPaths'] = matches
            for key in matches:
                package = FRONTEND / key
                manifest = package / 'package.json'
                if not manifest.is_file(): continue
                actual = read(manifest)
                if (actual['name'], actual['version']) != npm_identity(c):
                    raise ValueError('Installed npm package does not match lock identity')
                installed += 1
                for file in package.rglob('*'):
                    relative = file.relative_to(package)
                    if len(relative.parts) <= 3 and 'node_modules' not in relative.parts and file.is_file() and is_license(file.name):
                        if not file.resolve().is_relative_to(package.resolve()):
                            raise ValueError('License source escaped installed package')
                        license_file(row, str(relative), file.read_bytes())
        result.update({'success': True, 'counts': {'mavenGraph': len(maven['components']), 'javaRuntimeJars': len(artifacts),
                      'npmAllPlatformLock': len(all_npm['components']), 'nodeProductionLock': len(production['components']),
                      'installedNpmPackagesObserved': installed}, 'reviewItems': reviews,
                      'limitations': ['License metadata is a declaration, not legal approval.',
                       'Maven build plugins, JDK, OS, browser and service images are outside the dependency graph.',
                       'All-platform lock components may not be installed on this host; text evidence covers observed runtime jars and installed npm packages.',
                       'Bundled/minified code attribution needs distribution review; production npm dependencies alone do not describe client or SSR bundles.',
                       'Owned Java/npm code declares Apache-2.0; attribution/notice review, release version, signatures and namespace remain separate qualifications.']})
        (output / 'inventory.json').write_text(json.dumps({'components': components}, indent=2) + '\n')
        lines = ['# Dependency and license declaration inventory', '', 'Publication qualified: **false**. Declared metadata and collected files require review.', '',
                 '| Scope | Count |', '|---|---|'] + [f'| {k} | {v} |' for k, v in result['counts'].items()]
        lines += ['', '## Review items', '', '| Component | Reason |', '|---|---|'] + [f"| {r['ref']} | {r['reason']} |" for r in reviews]
        lines += ['', '## Boundaries', ''] + ['- ' + item for item in result['limitations']]
        (output / 'README.md').write_text('\n'.join(lines) + '\n')
        print('Dependency inventory: ' + str(output))
    except BaseException as error:
        result['failure'] = str(error)
        raise
    finally:
        save()


if __name__ == '__main__':
    main()
