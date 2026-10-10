#!/usr/bin/env python3
"""Run owned real Redis contracts, optionally building the pinned local runtime from source."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REDIS_VERSION = '7.2.11'
REDIS_SHA256 = '2f9886eca68d30114ad6a01da65631f8007d802fd3e6c9fac711251e6390323d'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--redis-server', type=Path, help='Existing redis-server executable (no shared server is used)')
parser.add_argument('--build-runtime', action='store_true', help='Download, verify and build pinned Redis in the output directory')
parser.add_argument('--output', type=Path)
args = parser.parse_args()
if args.redis_server and args.build_runtime:
    parser.error('Select an existing executable or a local source build')
output = (args.output or Path(tempfile.mkdtemp(prefix='inertia-redis-qualification-'))).resolve()
output.mkdir(parents=True, exist_ok=True)
if any(output.iterdir()):
    parser.error('Output must be new or empty')
report = {'format': 1, 'success': False, 'scope': 'Real standalone Redis state/transport contracts; not two-JVM, host identity or failover qualification', 'output': str(output)}
try:
    executable = args.redis_server
    if args.build_runtime:
        url = f'https://download.redis.io/releases/redis-{REDIS_VERSION}.tar.gz'
        archive = output / f'redis-{REDIS_VERSION}.tar.gz'
        subprocess.run(['curl', '--fail', '--location', '--silent', '--show-error',
                        '--proto', '=https', '--connect-timeout', '15', '--max-time', '120',
                        url, '--output', str(archive)], check=True)
        if hashlib.sha256(archive.read_bytes()).hexdigest() != REDIS_SHA256:
            raise ValueError('Redis source archive digest mismatch')
        with tarfile.open(archive) as source:
            source.extractall(output, filter='data')
        runtime = output / f'redis-{REDIS_VERSION}'
        with (output / 'redis-build.log').open('w') as log:
            subprocess.run(['make', '-j4', 'MALLOC=libc'], cwd=runtime, stdout=log, stderr=subprocess.STDOUT, check=True)
        executable = runtime / 'src/redis-server'
        report['sourceArchive'] = {'url': url, 'sha256': REDIS_SHA256}
    if executable is None:
        path = os.environ.get('INERTIA_REDIS_SERVER') or shutil.which('redis-server')
        if not path:
            raise ValueError('Supply --redis-server, INERTIA_REDIS_SERVER or --build-runtime; mock/skip fallback is forbidden')
        executable = Path(path)
    executable = executable.resolve()
    if not executable.is_file() or not os.access(executable, os.X_OK):
        raise ValueError('Redis executable unavailable')
    report['runtime'] = {'version': subprocess.check_output([str(executable), '--version'], text=True).strip(),
                         'executableSha256': hashlib.sha256(executable.read_bytes()).hexdigest()}
    report['source'] = {'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                        'dirty': bool(subprocess.check_output(['git', 'status', '--porcelain=v1'], cwd=ROOT, text=True).strip())}
    command = [str(ROOT / 'mvnw'), '--batch-mode', '--no-transfer-progress', '-pl', 'inertia-session-redis', '-am', 'test',
               '-Dtest=RedisSessionStoreIT,RedisLostReplyIT,RedisSessionOptionsTest', '-Dsurefire.failIfNoSpecifiedTests=false',
               '-Dinertia.redis.server=' + str(executable)]
    with (output / 'maven.log').open('w') as log:
        subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)
    report['suites'] = []
    for name in ['RedisSessionStoreIT', 'RedisLostReplyIT', 'RedisSessionOptionsTest']:
        path = ROOT / 'inertia-session-redis/target/surefire-reports' / ('TEST-io.inertia.redis.' + name + '.xml')
        suite = ET.parse(path).getroot()
        counts = {key: int(suite.get(key, '0')) for key in ['tests', 'failures', 'errors', 'skipped']}
        if counts['tests'] < 1 or any(counts[key] for key in ['failures', 'errors', 'skipped']):
            raise ValueError('Incomplete real backend suite: ' + name)
        shutil.copyfile(path, output / path.name)
        report['suites'].append({'name': name, **counts})
    report['success'] = True
finally:
    (output / 'summary.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Redis contract evidence: ' + str(output / 'summary.json'))
