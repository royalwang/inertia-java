#!/usr/bin/env python3
"""Archive local candidates and compare their public APIs using pinned japicmp.

Snapshots are portable private build inputs, not a public release or compatibility
claim. Nothing downloads automatically during comparison; supply a local tool jar.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MODULES = ['inertia-core', 'inertia-ssr-http', 'inertia-vite', 'inertia-spring-webmvc',
           'inertia-spring-boot-autoconfigure', 'inertia-spring-boot-starter', 'inertia-testing']
JAPICMP_VERSION = '0.26.2'
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def execute(args, **kwargs):
    return subprocess.run([str(a) for a in args], check=True, text=True, **kwargs)


def new_directory(path):
    path = path.resolve()
    if path.exists() and (not path.is_dir() or any(path.iterdir())):
        raise ValueError('Output must be new or empty: ' + str(path))
    path.mkdir(parents=True, exist_ok=True)
    return path


def copy_input(source, output, relative):
    target = output / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)
    return {'path': relative.as_posix(), 'sha256': digest(target)}


def signatures(jar):
    with zipfile.ZipFile(jar) as archive:
        classes = sorted(n[:-6].replace('/', '.') for n in archive.namelist()
                         if n.endswith('.class') and not n.startswith('META-INF/')
                         and not n.endswith(('package-info.class', 'module-info.class')))
    if not classes:
        return []  # Dependency-only starter.
    output = execute(['javap', '-protected', '-s', '-constants', '-classpath', jar, *classes],
                     stdout=subprocess.PIPE).stdout
    blocks, current = [], []
    for line in output.splitlines():
        if line.startswith('Compiled from '):
            continue
        current.append(line)
        if line == '}':
            if current and current[0].startswith(('public ', 'protected ')):
                blocks.extend(current)
            current = []
    return blocks


def snapshot(output):
    output = new_directory(output)
    pom = ET.parse(ROOT / 'pom.xml').getroot()
    version = pom.findtext('m:version', namespaces=NS)
    execute([sys.executable, ROOT / 'scripts/verify-library-artifacts.py'], cwd=ROOT, stdout=subprocess.PIPE)
    records = []
    # No install, publication, or rebuilding hidden in this command.
    for module in MODULES:
        source = ROOT / module / 'target' / f'{module}-{version}.jar'
        if not source.is_file():
            raise ValueError('Build and install the complete reactor first: ' + str(source))
    for module in MODULES:
        source = ROOT / module / 'target' / f'{module}-{version}.jar'
        classpath_file = output / (module + '.classpath.tmp')
        with (output / (module + '.classpath.log')).open('w') as log:
            execute([ROOT / 'mvnw', '--batch-mode', '--no-transfer-progress', '-pl', module,
                     'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath',
                     '-DincludeScope=compile', '-Dmdep.outputFile=' + str(classpath_file)],
                    cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        dependencies = []
        for value in classpath_file.read_text().strip().split(os.pathsep):
            if not value:
                continue
            path = Path(value)
            relative = Path('dependencies') / (digest(path) + '.jar')
            dependencies.append(copy_input(path, output, relative))
        classpath_file.unlink()
        records.append({'module': module,
                        'artifact': copy_input(source, output, Path('artifacts') / (module + '.jar')),
                        'classpath': dependencies, 'signatures': signatures(source)})
    report = {'format': 1, 'version': version,
              'source': {'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                         'dirty': bool(subprocess.check_output(['git', 'status', '--porcelain=v1'], cwd=ROOT, text=True).strip())},
              'modules': records}
    (output / 'candidate.json').write_text(json.dumps(report, indent=2) + '\n')
    print(output / 'candidate.json')
    return report


def load_candidate(directory):
    directory = directory.resolve()
    data = json.loads((directory / 'candidate.json').read_text())
    if data.get('format') != 1 or [m['module'] for m in data['modules']] != MODULES:
        raise ValueError('Candidate must contain the exact library module manifest')
    for module in data['modules']:
        for item in [module['artifact'], *module['classpath']]:
            relative = Path(item['path'])
            path = (directory / relative).resolve()
            if relative.is_absolute() or '..' in relative.parts or not path.is_relative_to(directory):
                raise ValueError('Unsafe candidate path')
            if digest(path) != item['sha256']:
                raise ValueError('Candidate input digest mismatch: ' + item['path'])
    return data


def compare(old, new, tool, output):
    old, new, tool = old.resolve(), new.resolve(), tool.resolve()
    with zipfile.ZipFile(tool) as archive:
        properties = archive.read('META-INF/maven/com.github.siom79.japicmp/japicmp/pom.properties').decode()
    if 'version=' + JAPICMP_VERSION not in properties.splitlines():
        raise ValueError('Use the pinned japicmp ' + JAPICMP_VERSION + ' tool')
    before, after = load_candidate(old), load_candidate(new)
    output = new_directory(output)
    report = {'format': 1, 'toolVersion': JAPICMP_VERSION, 'toolSha256': digest(tool),
              'oldSource': before['source'], 'newSource': after['source'], 'success': False,
              'scope': 'japicmp binary/source checks; behavior, defaults and protocol require separate tests',
              'modules': []}
    # Fail closed for missing dependency classes; never use --ignore-missing-classes.
    for a, b in zip(before['modules'], after['modules']):
        name = a['module']
        args = ['java', '-jar', tool, '--old', old / a['artifact']['path'],
                '--new', new / b['artifact']['path'], '-a', 'protected',
                '--only-modified', '--error-on-binary-incompatibility', '--error-on-source-incompatibility',
                '--old-classpath', os.pathsep.join(str(old / d['path']) for d in a['classpath']),
                '--new-classpath', os.pathsep.join(str(new / d['path']) for d in b['classpath']),
                '--xml-file', output / (name + '.xml')]
        with (output / (name + '.log')).open('w') as log:
            result = subprocess.run([str(v) for v in args], text=True, stdout=log, stderr=subprocess.STDOUT)
        report['modules'].append({'module': name, 'exit': result.returncode})
    report['success'] = all(m['exit'] == 0 for m in report['modules'])
    (output / 'summary.json').write_text(json.dumps(report, indent=2) + '\n')
    print(output / 'summary.json')
    if not report['success']:
        raise SystemExit(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    capture = commands.add_parser('snapshot')
    capture.add_argument('--output', type=Path, required=True)
    capture.add_argument('--signatures', type=Path, help='Explicitly freeze reviewed text signatures in a new file')
    check = commands.add_parser('compare')
    for flag in ['old', 'new', 'tool', 'output']:
        check.add_argument('--' + flag, type=Path, required=True)
    args = parser.parse_args()
    if args.command == 'snapshot':
        if args.signatures and args.signatures.exists():
            parser.error('Signature baseline exists; review migration rather than overwriting it')
        result = snapshot(args.output)
        if args.signatures:
            args.signatures.parent.mkdir(parents=True, exist_ok=True)
            data = {'format': 1, 'version': result['version'], 'source': result['source'],
                    'modules': [{'module': m['module'], 'signatures': m['signatures']} for m in result['modules']]}
            args.signatures.write_text(json.dumps(data, indent=2) + '\n')
    else:
        compare(args.old, args.new, args.tool, args.output)


if __name__ == '__main__':
    main()
