"""Collect the separate locked documentation-tool graph and observed license texts."""
import collections
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
DOCS = Path(__file__).resolve().parents[1]
JAVA = DOCS.parent
spec = importlib.util.spec_from_file_location('inertia_dependency_inventory', JAVA / 'scripts/dependency-inventory.py')
shared = importlib.util.module_from_spec(spec)
spec.loader.exec_module(shared)


def inventory(bom, lock, root, output):
    packages = lock['packages']
    expected = collections.Counter((v.get('name', key.rsplit('node_modules/', 1)[-1]), v['version'])
                                   for key, v in packages.items() if key)
    if expected != collections.Counter(shared.npm_identity(c) for c in bom['components']):
        raise ValueError('Documentation SBOM differs from the complete all-platform lock graph')
    # npm emits repeated bom-ref values for identical packages at nested lock paths.
    # Preserve the raw SBOM. Validate each path and only coalesce identical metadata
    # for graph-reference validation; a conflicting declaration must still fail.
    unique, seen_paths = {}, set()
    for component in bom['components']:
        locations = [p['value'] for p in component.get('properties', []) if p['name'] == 'cdx:npm:package:path']
        if len(locations) != 1 or locations[0] in seen_paths:
            raise ValueError('Missing or duplicate npm package path')
        key = locations[0]
        seen_paths.add(key)
        if key not in packages or shared.npm_identity(component) != (
                packages[key].get('name', key.rsplit('node_modules/', 1)[-1]), packages[key]['version']):
            raise ValueError('SBOM path does not match locked package')
        canonical = dict(component, properties=[p for p in component.get('properties', [])
                                               if p['name'] != 'cdx:npm:package:path'])
        previous = unique.setdefault(component['bom-ref'], canonical)
        if previous != canonical:
            raise ValueError('Conflicting declarations for repeated npm component identity')
    if seen_paths != set(packages) - {''}:
        raise ValueError('Documentation SBOM omits locked package paths')
    shared.validate_bom(dict(bom, components=list(unique.values())))
    components, reviews = [], []
    observed = 0
    for component in unique.values():
        name, version = shared.npm_identity(component)
        paths = [key for key, value in packages.items() if key and
                 (value.get('name', key.rsplit('node_modules/', 1)[-1]), value['version']) == (name, version)]
        row = {'ref': component['bom-ref'], 'name': name, 'version': version,
               'declaredLicenses': component.get('licenses', []), 'lockPaths': paths,
               'scope': 'private-documentation-toolchain', 'observedPaths': [], 'licenseFiles': []}
        for key in paths:
            # Lock paths must remain package paths; never inspect arbitrary checkout files.
            parts = Path(key).parts
            if not parts or parts[0] != 'node_modules' or Path(key).is_absolute() or '..' in parts or '\\' in key:
                raise ValueError('Unsafe locked package path')
            package = root / key
            if not (package / 'package.json').is_file():
                if not packages[key].get('optional'):
                    raise ValueError('Required documentation package is not installed: ' + key)
                reviews.append({'ref': row['ref'], 'reason': 'optional-platform-package-not-observed', 'path': key})
                continue
            if not package.resolve().is_relative_to((root / 'node_modules').resolve()):
                raise ValueError('Installed package escapes documentation dependencies')
            if not (package / 'package.json').resolve().is_relative_to(package.resolve()):
                raise ValueError('Installed package manifest escapes package')
            actual = shared.read(package / 'package.json')
            if (actual['name'], actual['version']) != (name, version):
                raise ValueError('Installed documentation package differs from lock identity: ' + key)
            observed += 1
            row['observedPaths'].append(key)
            for path in package.rglob('*'):
                relative = path.relative_to(package)
                if len(relative.parts) > 3 or 'node_modules' in relative.parts or not path.is_file():
                    continue
                if not re.match(r'(?i)^(licen[cs]e|notice|copying|copyright)([._-].*)?$', path.name):
                    continue
                if not path.resolve().is_relative_to(package.resolve()) or path.stat().st_size > 2 * 1024 * 1024:
                    raise ValueError('Escaped or oversized license file')
                data = path.read_bytes()
                digest = shared.sha(data)
                destination = output / 'license-texts' / (digest + '.txt')
                destination.parent.mkdir(exist_ok=True)
                if not destination.exists():
                    destination.write_bytes(data)
                row['licenseFiles'].append({'source': key + '/' + relative.as_posix(), 'sha256': digest,
                                            'path': destination.relative_to(output).as_posix()})
        if not row['declaredLicenses']:
            reviews.append({'ref': row['ref'], 'reason': 'missing-license-declaration'})
        if row['observedPaths'] and not row['licenseFiles']:
            reviews.append({'ref': row['ref'], 'reason': 'no-recognized-installed-license-file'})
        if len(row['declaredLicenses']) > 1 or any(term in json.dumps(row['declaredLicenses']).upper()
                                                   for term in ['EPL-', 'GPL', 'MPL-', 'GNU']):
            reviews.append({'ref': row['ref'], 'reason': 'review-reciprocal-or-multiple-license-terms'})
        components.append(row)
    return {'ownedPackage': bom['metadata']['component'], 'components': components, 'reviewItems': reviews,
            'counts': {'allPlatformLockedPackagePaths': sum(expected.values()),
                       'uniqueComponents': len(components), 'installedPackagePathsObserved': observed,
                       'licenseFileObservations': sum(len(row['licenseFiles']) for row in components)}}


def main():
    output = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(tempfile.mkdtemp(prefix='inertia-docs-dependencies-'))
    if output.exists() and any(output.iterdir()):
        raise ValueError('Evidence directory must be new or empty')
    output.mkdir(parents=True, exist_ok=True)
    result = {'format': 1, 'success': False, 'publicationQualified': False, 'output': str(output),
              'scope': 'Separate private documentation build toolchain; not Java or application frontend dependencies',
              'limitations': ['Declared metadata and collected texts do not grant legal publication approval.',
                              'All-platform lock graph includes optional packages not installed on this host.',
                              'No claim that every tool dependency is bundled in the website; generated bundle attribution requires separate review.',
                              'JDK, Node, npm, Python, browsers, OS and service images are outside this package graph.']}
    try:
        result['inputs'] = {name: shared.sha((DOCS / name).read_bytes()) for name in ['package.json', 'package-lock.json']}
        result['collectorInputs'] = {str(p.relative_to(JAVA)): shared.sha(p.read_bytes()) for p in
                                     [Path(__file__).resolve(), JAVA / 'scripts/dependency-inventory.py']}
        result['source'] = {'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=JAVA, text=True).strip(),
                            'dirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=JAVA, text=True).strip())}
        with (output / 'npm-lock-all.json').open('w') as stream:
            command = subprocess.run(['npm', 'sbom', '--sbom-format=cyclonedx', '--package-lock-only',
                                      '--include=dev', '--include=optional', '--include=peer'], cwd=DOCS,
                                     stdout=stream, stderr=subprocess.PIPE, text=True, timeout=120,
                                     env={**os.environ, 'NODE_ENV': 'development'})
        (output / 'npm.stderr.log').write_text(command.stderr)
        if command.returncode:
            raise RuntimeError('npm SBOM failed; see npm.stderr.log')
        collected = inventory(shared.read(output / 'npm-lock-all.json'), shared.read(DOCS / 'package-lock.json'), DOCS, output)
        (output / 'inventory.json').write_text(json.dumps(collected, indent=2) + '\n')
        result.update({'success': True, 'counts': collected['counts'], 'reviewItems': collected['reviewItems']})
        lines = ['# Documentation toolchain declarations', '', 'Publication qualified: **false**.', '',
                 '| Package | Version | Declared terms | Observed texts |', '| --- | --- | --- | --- |']
        lines += ['| ' + ' | '.join([row['name'], row['version'], json.dumps(row['declaredLicenses']),
                                    str(len(row['licenseFiles']))]) + ' |' for row in collected['components']]
        lines += ['', '## Boundaries', ''] + ['- ' + item for item in result['limitations']]
        (output / 'README.md').write_text('\n'.join(lines) + '\n')
    except BaseException as error:
        result['failure'] = str(error)
        raise
    finally:
        (output / 'summary.json').write_text(json.dumps(result, indent=2) + '\n')
        print('Documentation dependency evidence: ' + str(output / 'summary.json'))


if __name__ == '__main__':
    main()
