#!/usr/bin/env python3
"""Aggregate repeated owned Redis HTTP measurements, retaining rejection rates and scope."""
import argparse
import hashlib
import json
from pathlib import Path
from statistics import median

parser = argparse.ArgumentParser(description=__doc__)
for name in ['tcp', 'tls', 'output']:
    parser.add_argument('--' + name, type=Path, required=True)
args = parser.parse_args()


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(path, tls):
    records = []
    for source in sorted(path.glob('*/summary.json')):
        data = json.loads(source.read_text())
        if not data.get('success') or data.get('settings', {}).get('profile') != 'redis':
            raise ValueError('Failed or non-Redis repetition: ' + str(source))
        if data['redisRuntime']['tls'] != tls:
            raise ValueError('Wrong transport group')
        records.append((source, data))
    if len(records) < 3:
        raise ValueError('At least three successful repetitions per transport required')
    expected = records[0][1]
    for _, data in records:
        if data['settings'] != expected['settings'] or data['build'] != expected['build']:
            raise ValueError('Repeated settings or measured binary/driver identities differ')
        if data['redisRuntime'] != expected['redisRuntime']:
            raise ValueError('Redis executable or TLS certificate identities differ')
    return records


def identity(phase):
    return phase['scenario'], phase['concurrency']


def distribution(values):
    return {'median': median(values), 'min': min(values), 'max': max(values)}


def aggregate(records):
    expected = {identity(p) for p in records[0][1]['phases']}
    if expected != {(scenario, c) for scenario in ['different-session', 'same-session']
                    for c in records[0][1]['settings']['concurrency']}:
        raise ValueError('Incomplete session/concurrency matrix')
    rows = []
    for key in sorted(expected):
        phases = []
        for path, data in records:
            if {identity(p) for p in data['phases']} != expected or len(data['phases']) != len(expected):
                raise ValueError('Incomplete or duplicate phases')
            phase = next(p for p in data['phases'] if identity(p) == key)
            raw = json.loads((path.parent / f"redis-json-{key[0]}-c{key[1]}.json").read_text())
            if raw['result'] != phase or len(raw['samples']) != phase['requests']:
                raise ValueError('Phase receipt mismatch')
            if phase['clientErrors'] or set(phase['statuses']) - {'200', '500'}:
                raise ValueError('Unexpected HTTP/client failure')
            if sum(phase['statuses'].values()) != phase['requests'] or phase['serverLatencyMs']['count'] != phase['requests']:
                raise ValueError('Incomplete HTTP/server timing samples')
            if phase['redis']['recoveryStatus'] != 200 or phase['redis']['deliveryConnections'] < 0:
                raise ValueError('Missing recovery or invalid connection accounting')
            failure_events = {k: v for k, v in phase['redis']['events'].items() if '/FAILURE/' in k}
            if any(k.rsplit('/', 1)[-1] not in {'OVERLOADED', 'REDIS_CAPACITY', 'REDIS_CONTENTION', 'REDIS_BUDGET_EXHAUSTED'}
                   for k in failure_events):
                raise ValueError('Healthy load contains an unexpected transport/state failure')
            if sum(failure_events.values()) < phase['statuses'].get('500', 0):
                raise ValueError('HTTP failures lack corresponding bounded Redis failure observations')
            if key[1] == 1 and phase['statuses'].get('500', 0):
                raise ValueError('Sequential baseline failed')
            if not raw['resources'] or any(not sample.get('redis') or not sample.get('javaRuntime') for sample in raw['resources']):
                raise ValueError('Missing resource samples')
            for sample in [phase['redis']['before'], phase['redis']['after'], *[s['redis'] for s in raw['resources']]]:
                if sample['domains'] < 1 or not 0 < sample['minTtlMs'] <= sample['maxTtlMs'] <= 120000:
                    raise ValueError('Invalid TTL or domain sample')
                if sample['maxDomainReservations'] > 16 or sample['maxDomainTerminals'] > 4096 or sample['evicted_keys']:
                    raise ValueError('Configured bound or noeviction contract violated')
            phases.append(phase)
        success_latency = [next(p for p in phase['latencyByStatusMs'] if p['status'] == 200) for phase in phases]
        reasons = sorted({reason for phase in phases for reason in phase['redis']['events']})
        rows.append({
            'scenario': key[0], 'concurrency': key[1], 'requestsPerRun': phases[0]['requests'],
            'successfulRequestsPerRun': [p['statuses'].get('200', 0) for p in phases],
            'rejectedRequestsPerRun': [p['statuses'].get('500', 0) for p in phases],
            'httpP95Ms': distribution([p['latencyMs']['p95'] for p in phases]),
            'httpP99Ms': distribution([p['latencyMs']['p99'] for p in phases]),
            'successfulHttpP95Ms': distribution([p['p95'] for p in success_latency]),
            'successfulHttpP99Ms': distribution([p['p99'] for p in success_latency]),
            'serverP95Ms': distribution([p['serverLatencyMs']['p95'] for p in phases]),
            'serverP99Ms': distribution([p['serverLatencyMs']['p99'] for p in phases]),
            'requestsPerSecond': distribution([p['requestsPerSecond'] for p in phases]),
            'successfulRequestsPerSecond': distribution([p['statuses'].get('200', 0) / (p['wallMs'] / 1000) for p in phases]),
            'deliveryConnectionsPerRun': [p['redis']['deliveryConnections'] for p in phases],
            'redisEventsPerRun': {reason: [p['redis']['events'].get(reason, 0) for p in phases] for reason in reasons},
            'commandLatencyPerRun': [p['redis']['commandLatencyMs'] for p in phases],
            'maxSampledRedisMemoryBytes': max(p['redis']['maxSampledMemoryBytes'] for p in phases),
            'maxSampledJvmHeapBytes': max(p['jvm']['maxSampledHeapUsedBytes'] for p in phases),
            'maxSampledJvmRssBytes': max(next(r for r in p['resources'] if r['role'] == 'java')['maxSampledRssBytes'] for p in phases),
            'maxSampledRedisRssBytes': max(next(r for r in p['resources'] if r['role'] == 'redis')['maxSampledRssBytes'] for p in phases),
            'maxSampledDomainReservations': max(p['redis']['maxSampledReservations'] for p in phases),
            'maxSampledDomainTerminals': max(p['redis']['maxSampledTerminals'] for p in phases),
            'afterLoadRecoveryStatuses': [p['redis']['recoveryStatus'] for p in phases],
        })
    return rows


tcp, tls = load(args.tcp, False), load(args.tls, True)
left, right = (dict(records[0][1]['settings']) for records in [tcp, tls])
left['redis'] = {k: v for k, v in left['redis'].items() if k != 'tls'}
right['redis'] = {k: v for k, v in right['redis'].items() if k != 'tls'}
if left != right or tcp[0][1]['build'] != tls[0][1]['build']:
    raise ValueError('Transport comparison changed other load parameters or measured identities')
tcp_rows, tls_rows = aggregate(tcp), aggregate(tls)
comparisons = []
for a, b in zip(tcp_rows, tls_rows):
    if identity(a) != identity(b):
        raise ValueError('Transport comparison phase mismatch')
    p95 = (b['successfulHttpP95Ms']['median'] / a['successfulHttpP95Ms']['median'] - 1) * 100
    rate = (b['successfulRequestsPerSecond']['median'] / a['successfulRequestsPerSecond']['median'] - 1) * 100
    comparisons.append({'scenario': a['scenario'], 'concurrency': a['concurrency'],
                        'successfulP95ChangePercent': p95, 'successfulThroughputChangePercent': rate,
                        'investigateAboveTenPercent': abs(p95) > 10 or abs(rate) > 10})
report = {
    'format': 1, 'success': True,
    'scope': 'Repeated single-JVM MVC JSON delivery with native host sessions and owned standalone Redis; TCP/TLS, distinct workers versus one hotspot',
    'settings': {name: records[0][1]['settings'] for name, records in [('tcp', tcp), ('tls', tls)]},
    'build': tcp[0][1]['build'],
    'receipts': {name: [{'path': str(path), 'sha256': digest(path), 'runtime': data['runtime'], 'redisRuntime': data['redisRuntime']}
                       for path, data in records] for name, records in [('tcp', tcp), ('tls', tls)]},
    'measurements': {'tcp': tcp_rows, 'tls': tls_rows}, 'transportComparison': comparisons,
    'totalMeasuredRequests': sum(p['requests'] for records in [tcp, tls] for _, data in records for p in data['phases']),
    'limits': [
        'Closed-loop same-host synthetic props; JSON only, no browser/SSR/database measurement or production SLO.',
        'Native HttpSession mutex serializes same-domain operations in this JVM; zero measured CAS conflicts does not imply multi-JVM contention cannot occur.',
        'Success latency/throughput are separate from total rates because fast overload rejection must not be presented as successful capacity.',
        'Application logging and INFO/aggregate sampling overhead included; connections subtract only known sampler CLI connections on this owned server.',
        'Heap/RSS/state occupancy are sampled; they are not exact peaks, memory-leak qualification or a proof that a bound was reached.',
        'One original seeded session is probed after each load phase; explicit bound/lease/budget/restart fault contracts are separate.',
        'TCP group precedes TLS; phase order, JIT/GC and host activity affect results. Three descriptive repetitions do not establish statistical significance.',
        'Changes above 10% require investigation; TLS handshake overhead is expected, not an automatic regression claim.',
    ],
}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(report, indent=2) + '\n')
print('Redis capacity summary: ' + str(args.output))
print('Measured requests: ' + str(report['totalMeasuredRequests']))
