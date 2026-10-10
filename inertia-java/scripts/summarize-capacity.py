#!/usr/bin/env python3
"""Summarize repeated local benchmark receipts without inventing a capacity SLO."""
import argparse
import hashlib
import json
from pathlib import Path
from statistics import median

parser = argparse.ArgumentParser()
for name in ['baseline', 'current', 'output']:
    parser.add_argument('--' + name, type=Path, required=True)
parser.add_argument('--application', type=Path)
args = parser.parse_args()


def load(directory):
    records = []
    for path in sorted(directory.glob('*/summary.json')):
        data = json.loads(path.read_text())
        if data.get('success'):
            records.append((path, data))
    if len(records) < 3:
        raise ValueError('At least three successful repetitions required: ' + str(directory))
    settings = records[0][1]['settings']
    for _, data in records:
        if data['settings'] != settings:
            raise ValueError('Cannot aggregate different load settings')
        if data['build']['jarSha256'] != records[0][1]['build']['jarSha256']:
            raise ValueError('Cannot aggregate different application binaries')
        if data['build'].get('driverSha256') != records[0][1]['build'].get('driverSha256'):
            raise ValueError('Cannot aggregate different measured drivers')
        if data['build']['receipt']['buildId'] != records[0][1]['build']['receipt']['buildId']:
            raise ValueError('Cannot aggregate mismatched frontend identities')
    return records


def key(phase):
    return phase['mode'], phase.get('scenario', 'users'), phase['concurrency']


def aggregate(records):
    expected = {key(p) for p in records[0][1]['phases']}
    if any({key(p) for p in data['phases']} != expected for _, data in records):
        raise ValueError('Incomplete repeated phase matrix')
    rows = []
    for identity in sorted(expected):
        phases = [next(p for p in data['phases'] if key(p) == identity) for _, data in records]
        if any(p['statuses'] != {'200': p['requests']} or p['clientErrors'] for p in phases):
            raise ValueError('Failed responses cannot enter successful capacity comparison')
        rows.append({'mode': identity[0], 'scenario': identity[1], 'concurrency': identity[2],
                     'requestsPerRun': phases[0]['requests'],
                     'p50Ms': median(p['latencyMs']['p50'] for p in phases),
                     'p95Ms': median(p['latencyMs']['p95'] for p in phases),
                     'p95RangeMs': [min(p['latencyMs']['p95'] for p in phases), max(p['latencyMs']['p95'] for p in phases)],
                     'p99Ms': median(p['latencyMs']['p99'] for p in phases),
                     'requestsPerSecond': median(p['requestsPerSecond'] for p in phases),
                     'ssrRatio': median(p['ssrRatio'] for p in phases),
                     'responseKind': phases[0].get('responseKind', 'html'),
                     'maxSampledHeapUsedBytes': max(p.get('jvm', {}).get('maxSampledHeapUsedBytes', 0) for p in phases) or None,
                     'maxSampledQueue': max(p.get('jvm', {}).get('maxSampledQueuedTasks', 0) for p in phases),
                     'httpReasons': [p['httpReasons'] for p in phases]})
    return rows


baseline, current = (load(getattr(args, name)) for name in ['baseline', 'current'])
application = load(args.application) if args.application else []
if baseline[0][1]['settings'] != current[0][1]['settings']:
    raise ValueError('Transport baseline and current settings must match exactly')
if baseline[0][1]['build']['receipt']['buildId'] != current[0][1]['build']['receipt']['buildId']:
    raise ValueError('Baseline frontend differs from current')
base_rows, current_rows = map(aggregate, [baseline, current])
app_rows = aggregate(application) if application else []
comparisons = []
for old, new in zip(base_rows, current_rows):
    if key(old) != key(new):
        raise ValueError('Comparison phase identity mismatch')
    p95 = (new['p95Ms'] / old['p95Ms'] - 1) * 100
    throughput = (new['requestsPerSecond'] / old['requestsPerSecond'] - 1) * 100
    comparisons.append({'mode': old['mode'], 'concurrency': old['concurrency'],
                        'baselineP95Ms': old['p95Ms'], 'currentP95Ms': new['p95Ms'],
                        'baselineP95RangeMs': old['p95RangeMs'], 'currentP95RangeMs': new['p95RangeMs'],
                        'p95ChangePercent': p95, 'throughputChangePercent': throughput,
                        'baselineSsrRatio': old['ssrRatio'], 'currentSsrRatio': new['ssrRatio'],
                        'investigate': abs(p95) > 10 or abs(throughput) > 10})
report = {'format': 1, 'success': True, 'scope': 'Repeated same-host closed-loop comparison; descriptive medians, no significance test or production SLO',
          'receipts': {name: [{'path': str(path), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                              'jarSha256': data['build']['jarSha256'], 'driverSha256': data['build'].get('driverSha256'), 'runtime': data['runtime']} for path, data in records]
                       for name, records in [('baseline', baseline), ('current', current), ('application', application)]},
          'settings': {'transport': baseline[0][1]['settings'], 'application': application[0][1]['settings'] if application else None},
          'totalMeasuredRequests': sum(p['requests'] for records in [baseline, current, application] for _, data in records for p in data['phases']),
          'transportComparison': comparisons, 'transportMeasurements': {'baseline': base_rows, 'current': current_rows}, 'application': app_rows,
          'limits': [f"Warmups: transport={baseline[0][1]['settings']['warmupRequests']}; bounded phases remain sensitive to JIT/GC/host activity.",
                     'SSR overload changes SSR/CSR mixture; compare ratios as well as latency.',
                     'Heap/RSS/queue are sampled, not a long-duration memory-leak qualification.',
                     'JSON visits are separate from HTML SSR/CSR; no database or browser rendering measured.',
                     'Changes above 10% request investigation, not an automatic claim of regression or improvement.']}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(report, indent=2) + '\n')
print('Capacity summary: ' + str(args.output))
print('Measured requests: ' + str(report['totalMeasuredRequests']))
print('Transport rows requesting investigation: ' + str(sum(row['investigate'] for row in comparisons)))
