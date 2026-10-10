#!/usr/bin/env python3
"""Real javac/jar/japicmp acceptance, including source-only incompatibility."""
import argparse
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import sys

sys.dont_write_bytecode = True
import zipfile

spec = importlib.util.spec_from_file_location('api_compatibility', Path(__file__).with_name('api-compatibility.py'))
api = importlib.util.module_from_spec(spec)
spec.loader.exec_module(api)
parser = argparse.ArgumentParser()
parser.add_argument('--tool', required=True, type=Path)
args = parser.parse_args()
root = Path(tempfile.mkdtemp(prefix='inertia-api-contracts-'))


def candidate(name, body):
    output = root / name
    output.mkdir()
    source = output / 'Example.java'
    source.write_text('public class Example { ' + body + ' }\n')
    classes = output / 'classes'
    classes.mkdir()
    subprocess.run(['javac', '-d', str(classes), str(source)], check=True)
    jar = output / 'example.jar'
    subprocess.run(['jar', '--create', '--file', str(jar), '-C', str(classes), '.'], check=True)
    records = []
    for module in api.MODULES:
        target = output / (module + '.jar')
        if module == 'inertia-core':
            target.write_bytes(jar.read_bytes())
        else:
            with zipfile.ZipFile(target, 'w'):
                pass
        records.append({'module': module, 'artifact': {'path': target.name, 'sha256': api.digest(target)},
                        'classpath': [], 'signatures': []})
    (output / 'candidate.json').write_text(json.dumps({'format': 1, 'modules': records, 'source': {'fixture': name}}))
    return output


old = candidate('old', 'public String existing() { return "old"; } protected int extension() { return 1; }')
scenarios = [
    ('unchanged', 'public String existing() { return "new"; } protected int extension() { return 2; }', True, None),
    ('additive', 'public String existing() { return "old"; } protected int extension() { return 1; } public void added() {}', True, None),
    ('removed-public', 'protected int extension() { return 1; }', False, 'existing'),
    ('removed-protected', 'public String existing() { return "old"; }', False, 'extension'),
    ('source-only-throws', 'public String existing() throws java.io.IOException { return "old"; } protected int extension() { return 1; }', False, 'IOException'),
]
results = []
for name, body, expected, marker in scenarios:
    new = candidate(name, body)
    output = root / (name + '-report')
    result = subprocess.run(['python3', str(Path(__file__).with_name('api-compatibility.py')), 'compare',
                             '--old', str(old), '--new', str(new), '--tool', str(args.tool), '--output', str(output)],
                            capture_output=True, text=True)
    report = json.loads((output / 'summary.json').read_text())
    assert (result.returncode == 0) == expected, result.stdout + result.stderr
    assert report['success'] == expected
    if marker:
        assert marker in (output / 'inertia-core.log').read_text()
    if name == 'source-only-throws':
        log = (output / 'inertia-core.log').read_text()
        assert 'METHOD_NOW_THROWS_CHECKED_EXCEPTION' in log, log
        binary = subprocess.run(['java', '-jar', str(args.tool), '--old', str(old / 'inertia-core.jar'),
                                 '--new', str(new / 'inertia-core.jar'), '--error-on-binary-incompatibility'],
                                capture_output=True, text=True)
        assert binary.returncode == 0, binary.stdout + binary.stderr
    results.append({'name': name, 'expectedCompatible': expected, 'exit': result.returncode})
# Reviewed legacy candidate can gain a new module, but removing it must fail.
legacy = candidate('legacy-seven', 'public String existing() { return "old"; } protected int extension() { return 1; }')
manifest = json.loads((legacy / 'candidate.json').read_text())
manifest['modules'] = [m for m in manifest['modules'] if m['module'] != 'inertia-session-redis']
(legacy / 'candidate.json').write_text(json.dumps(manifest))
api.compare(legacy, old, args.tool, root / 'new-module-report')
try:
    api.compare(old, legacy, args.tool, root / 'removed-module-report')
    raise AssertionError('A removed library module was accepted')
except ValueError as error:
    assert 'module was removed' in str(error)
results.append({'name': 'reviewed-module-addition-and-removal', 'success': True})
# Input drift must fail before japicmp can mistake it for an approved baseline.
(old / 'inertia-core.jar').write_bytes(b'tampered')
try:
    api.load_candidate(old)
    raise AssertionError('Tampered candidate was accepted')
except ValueError as error:
    assert 'digest mismatch' in str(error)
results.append({'name': 'changed-baseline-input', 'rejected': True})
(root / 'summary.json').write_text(json.dumps({'success': True, 'tests': results}, indent=2) + '\n')
print('API compatibility evidence: ' + str(root / 'summary.json'))
