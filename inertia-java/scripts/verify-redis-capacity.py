#!/usr/bin/env python3
"""Repeat owned TCP/TLS Redis capacity measurements and validate all raw receipts."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--redis-server', type=Path, required=True)
parser.add_argument('--redis-cli', type=Path, required=True)
parser.add_argument('--tls-fixture', type=Path, required=True,
                    help='Temporary CA/server/trust material produced by verify-redis.py --tls')
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--repetitions', type=int, default=3)
args = parser.parse_args()
if args.repetitions < 3:
    parser.error('At least three repetitions are required')
for executable in [args.redis_server, args.redis_cli]:
    if not executable.is_file() or not os.access(executable, os.X_OK):
        parser.error('Redis executable unavailable: ' + str(executable))
for name in ['ca.crt', 'server.crt', 'server.key', 'trust.p12']:
    if not (args.tls_fixture / name).is_file():
        parser.error('Missing temporary TLS fixture: ' + name)
output = args.output.resolve()
output.mkdir(parents=True, exist_ok=True)
if any(output.iterdir()):
    parser.error('Output must be new or empty')
report = {'success': False, 'runs': []}
try:
    for mode in ['tcp', 'tls']:
        group = output / mode
        group.mkdir()
        for repetition in range(1, args.repetitions + 1):
            env = dict(os.environ)
            env.pop('INERTIA_BENCH_REDIS_TLS_FIXTURE', None)
            env.update(INERTIA_BENCH_PROFILE='redis', INERTIA_BENCH_MODES='redis-json',
                       INERTIA_BENCH_REQUESTS='128', INERTIA_BENCH_CONCURRENCY='1,8,32',
                       INERTIA_BENCH_WARMUPS='8', INERTIA_BENCH_OUTPUT=str(group),
                       INERTIA_BENCH_REDIS_SERVER=str(args.redis_server.resolve()),
                       INERTIA_BENCH_REDIS_CLI=str(args.redis_cli.resolve()))
            if mode == 'tls':
                env['INERTIA_BENCH_REDIS_TLS_FIXTURE'] = str(args.tls_fixture.resolve())
            existing = set(group.glob('*/summary.json'))
            with (output / f'{mode}-{repetition}.log').open('w') as log:
                result = subprocess.run(['node', str(ROOT / 'examples/spring-react/frontend/scripts/benchmark.mjs')],
                                        cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=600)
            created = set(group.glob('*/summary.json')) - existing
            source = next(iter(created)) if len(created) == 1 else None
            success = bool(source and json.loads(source.read_text()).get('success') and result.returncode == 0)
            report['runs'].append({'mode': mode, 'repetition': repetition, 'summary': str(source),
                                   'exit': result.returncode, 'success': success})
            if not success:
                raise ValueError(f'Capacity measurement failed: {mode} repetition {repetition}')
    subprocess.run([sys.executable, str(ROOT / 'scripts/summarize-redis-capacity.py'),
                    '--tcp', str(output / 'tcp'), '--tls', str(output / 'tls'),
                    '--output', str(output / 'aggregate.json')], check=True)
    report['success'] = True
finally:
    (output / 'runs.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Redis capacity evidence: ' + str(output))
