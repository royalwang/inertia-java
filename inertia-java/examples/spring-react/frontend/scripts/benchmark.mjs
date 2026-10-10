// Full-body HTTP latency, closed-loop concurrency; intentionally no browser/DB claim.
import { spawn, execFile } from 'node:child_process'
import { createServer } from 'node:http'
import { createServer as reserveServer } from 'node:net'
import { createHash } from 'node:crypto'
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync, createWriteStream } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import { tmpdir, cpus, platform, release, totalmem, loadavg } from 'node:os'
import { setTimeout as delay } from 'node:timers/promises'
import { promisify } from 'node:util'

const exec = promisify(execFile)
const frontend = fileURLToPath(new URL('..', import.meta.url))
const example = resolve(frontend, '..')
const jar = process.env.INERTIA_BENCH_JAR ? resolve(process.env.INERTIA_BENCH_JAR) : resolve(example, 'target/spring-react-0.1.0-SNAPSHOT.jar')
const receipt = JSON.parse(readFileSync(resolve(frontend, 'dist/build.json'), 'utf8'))
function integer(name, fallback, max) {
  const value = Number(process.env[name] ?? fallback)
  if (!Number.isInteger(value) || value < 1 || value > max) throw new Error(`${name}: expected integer 1..${max}`)
  return value
}
const profile = process.env.INERTIA_BENCH_PROFILE ?? 'transport'
if (!['transport', 'application'].includes(profile)) throw new Error('Profile must be transport or application')
const scenarios = profile === 'application' ? [
  { name: 'small-html', bytes: 64 },
  { name: 'large-html', bytes: 262144 },
  { name: 'small-json', bytes: 64, json: true },
  { name: 'large-json', bytes: 262144, json: true },
  { name: 'partial', bytes: 262144, json: true, select: 'payload' },
  { name: 'deferred', bytes: 262144, json: true, select: 'stats' },
  { name: 'same-session', bytes: 64, session: true },
] : [{ name: 'users' }]
const warmupRequests = integer('INERTIA_BENCH_WARMUPS', 8, 1000)
const slowWarmupRequests = integer('INERTIA_BENCH_SLOW_WARMUPS', 2, 10)
const modes = process.env.INERTIA_BENCH_MODES?.split(',') ?? (profile === 'application' ? ['ssr', 'excluded-csr'] : ['ssr', 'excluded-csr', 'connection-refused', 'stalled-body'])
const allowedModes = profile === 'application' ? ['ssr', 'excluded-csr'] : ['ssr', 'excluded-csr', 'connection-refused', 'stalled-body']
if (!modes.length || new Set(modes).size !== modes.length || modes.some(mode => !allowedModes.includes(mode))) throw new Error('Invalid benchmark modes')
const requests = integer('INERTIA_BENCH_REQUESTS', 128, 10000)
const slowRequests = integer('INERTIA_BENCH_SLOW_REQUESTS', 24, 1000)
const concurrency = (process.env.INERTIA_BENCH_CONCURRENCY ?? '1,8,32').split(',').map(Number)
if (!concurrency.length || new Set(concurrency).size !== concurrency.length || concurrency.some(n => !Number.isInteger(n) || n < 1 || n > 64)) throw new Error('Concurrency must contain distinct integers 1..64')
const output = process.env.INERTIA_BENCH_OUTPUT
  ? mkdtempSync((mkdirSync(process.env.INERTIA_BENCH_OUTPUT, { recursive: true }), resolve(process.env.INERTIA_BENCH_OUTPUT, 'run-')))
  : mkdtempSync(resolve(tmpdir(), 'inertia-java-benchmark-'))
const children = []
let peer
let interrupted = false
const summary = {
  format: 1, label: process.env.INERTIA_BENCH_LABEL ?? 'current', success: false, startedAt: new Date().toISOString(), output,
  runtime: { node: process.version, platform: platform(), release: release(), cpu: cpus()[0]?.model, logicalCpus: cpus().length, totalMemoryBytes: totalmem(), initialLoadAverage: loadavg() },
  settings: { profile, modes, scenarios, requests, slowRequests, concurrency, warmupRequests, slowWarmupRequests, clientDeadlineMs: 10000, ssrDeadlineMs: 1000, ssrPermits: 16, route: profile === 'application' ? '/benchmark/page' : '/users', databaseQueries: 0, userRows: profile === 'application' ? 1 : 2 },
  build: { receipt, driverSha256: createHash('sha256').update(readFileSync(fileURLToPath(import.meta.url))).digest('hex'), jarSha256: createHash('sha256').update(readFileSync(jar)).digest('hex') },
  phases: [],
  limits: ['same-host closed-loop HTTP measurement; no browser paint/hydration or database', 'logging observer enabled; logging overhead included', 'RSS is sampled, not exact peak; ps CPU is process-lifetime reported percentage, not interval utilization', profile === 'application' ? 'one shared cookie only for same-session scenario; synthetic in-memory props, no database' : 'no cookies retained: independent initial HTML visits; existing JVM reused within each mode', 'stress permits overload fallback; no production capacity or throughput guarantee'],
}
async function source() {
  const [head, status, java] = await Promise.all([
    exec('git', ['rev-parse', 'HEAD'], { cwd: example }), exec('git', ['status', '--porcelain'], { cwd: example }), exec('java', ['-version']),
  ])
  summary.source = { head: head.stdout.trim(), dirty: Boolean(status.stdout.trim()), status: status.stdout.trim() }
  summary.runtime.java = java.stderr.trim()
}
function start(command, args, cwd, name, env = {}) {
  const log = createWriteStream(resolve(output, name + '.log'))
  const child = spawn(command, args, { cwd, env: { ...process.env, ...env }, detached: process.platform !== 'win32', stdio: ['ignore', 'pipe', 'pipe'] })
  child.transcript = ''; child.events = []; child.buffers = ['', '']; children.push(child)
  for (const [i, stream] of [child.stdout, child.stderr].entries()) stream.on('data', data => {
    log.write(data); child.transcript = (child.transcript + data.toString()).slice(-64000)
    const lines = (child.buffers[i] + data.toString()).split('\n'); child.buffers[i] = lines.pop()
    for (const line of lines) {
      const at = line.indexOf('{"operation":')
      if (at >= 0) { try { child.events.push(JSON.parse(line.slice(at))) } catch { /* non-event log line */ } }
    }
  })
  child.on('error', error => { child.failure = error })
  child.on('exit', () => log.end())
  return child
}
async function wait(label, predicate, ms = 20000, checkInterrupt = true) {
  const end = Date.now() + ms
  while (Date.now() < end) {
    if (checkInterrupt && interrupted) throw new Error('Interrupted')
    const failed = children.find(c => c.failure)
    if (failed) throw failed.failure
    if (await predicate()) return
    await delay(50)
  }
  throw new Error(`Deadline exceeded: ${label}`)
}
async function stop(child) {
  if (!child || child.exitCode !== null || child.signalCode !== null) return
  const signal = kind => { try { process.platform === 'win32' ? child.kill(kind) : process.kill(-child.pid, kind) } catch (e) { if (e.code !== 'ESRCH') throw e } }
  signal('SIGTERM')
  const end = Date.now() + 5000
  while (child.exitCode === null && child.signalCode === null && Date.now() < end) await delay(50)
  if (child.exitCode === null && child.signalCode === null) {
    signal('SIGKILL')
    await wait('process cleanup', () => child.exitCode !== null || child.signalCode !== null, 5000, false)
  }
}
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => { interrupted = true })
async function reservePort() {
  const server = reserveServer()
  await new Promise((done, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', done) })
  const port = server.address().port
  await new Promise(done => server.close(done))
  return port
}
async function get(url, headers = {}, scenario) {
  const begin = performance.now()
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(10000), redirect: 'error', headers })
    const body = await response.text()
    if (scenario?.json && response.status === 200) {
      const page = JSON.parse(body)
      if (page.component !== 'Users/Index' || page.version !== receipt.buildId) throw new Error('Page identity mismatch')
      if (scenario.select === 'stats') {
        if (page.props.stats?.total !== 1 || 'payload' in page.props) throw new Error('Deferred selection mismatch')
      } else if (page.props.payload?.length !== scenario.bytes || (scenario.select && 'users' in page.props)) throw new Error('Payload/partial selection mismatch')
    }
    return { elapsedMs: performance.now() - begin, status: response.status, bytes: Buffer.byteLength(body), ssr: body.includes('data-server-rendered'), error: null }
  } catch (error) {
    return { elapsedMs: performance.now() - begin, status: 0, bytes: 0, ssr: false, error: error.name }
  }
}
function percentile(values, p) {
  const sorted = values.toSorted((a, b) => a - b)
  return sorted[Math.max(0, Math.ceil(sorted.length * p) - 1)] ?? null
}
function counts(values) {
  const result = {}
  for (const value of values) result[value] = (result[value] ?? 0) + 1
  return result
}
async function resourceSample(java, node, base) {
  const ids = [java?.pid, node?.pid, process.pid].filter(Boolean)
  try {
    const { stdout } = await exec('ps', ['-p', ids.join(','), '-o', 'pid=,pcpu=,rss='], { env: { ...process.env, LC_ALL: 'C' } })
    const runtime = profile === 'application' ? await fetch(base + '/benchmark/resources', { signal: AbortSignal.timeout(1000) }).then(r => { if (!r.ok) throw new Error('Runtime sample failed'); return r.json() }) : undefined
    return { at: new Date().toISOString(), javaRuntime: runtime, processes: stdout.trim().split('\n').filter(Boolean).map(line => {
      const [pid, cpu, rss] = line.trim().split(/\s+/).map(Number)
      return { role: pid === java?.pid ? 'java' : pid === node?.pid ? 'node' : 'driver', pid, reportedCpuPercent: cpu, rssBytes: rss * 1024 }
    }) }
  } catch (error) { return { at: new Date().toISOString(), unavailable: error.code ?? error.name } }
}
async function phase(mode, java, node, base, parallelism, scenario) {
  const path = profile === 'application' ? `/benchmark/page?bytes=${scenario.bytes}` : '/users'
  const headers = scenario.json ? { 'X-Inertia': 'true', 'X-Inertia-Version': receipt.buildId } : {}
  if (scenario.select) Object.assign(headers, { 'X-Inertia-Partial-Component': 'Users/Index', 'X-Inertia-Partial-Data': scenario.select })
  if (scenario.session) {
    const response = await fetch(base + '/benchmark/session')
    const cookie = response.headers.getSetCookie().map(value => value.split(';')[0]).find(value => value.startsWith('JSESSIONID='))
    if (!response.ok || !cookie?.startsWith('JSESSIONID=')) throw new Error('Shared session setup failed')
    headers.Cookie = cookie
  }
  const slow = mode === 'stalled-body'
  const count = slow ? slowRequests : requests
  const warmups = slow ? slowWarmupRequests : warmupRequests
  const beforeWarmup = java.events.filter(e => e.operation === 'RESPONSE').length
  for (let i = 0; i < warmups; i++) {
    const warm = await get(base + path, headers, scenario)
    if (warm.status !== 200) throw new Error('Warmup failed: ' + JSON.stringify(warm))
  }
  // Complete warmup write observations before delimiting this measurement.
  await wait('warmup observation drain', () => java.events.filter(e => e.operation === 'RESPONSE').length >= beforeWarmup + warmups, 5000)
  java.events.length = 0
  const resources = []; const samples = []; let sampling = true
  const sampler = (async () => {
    while (sampling) { resources.push(await resourceSample(java, node, base)); await delay(200) }
  })()
  let cursor = 0
  const begin = performance.now()
  let wallMs
  try {
    const workers = await Promise.allSettled(Array.from({ length: Math.min(parallelism, count) }, async () => {
      while (cursor < count) {
        if (interrupted) throw new Error('Interrupted')
        const index = cursor++
        samples[index] = { index, ...await get(base + path, headers, scenario) }
      }
    }))
    const failed = workers.find(w => w.status === 'rejected')
    if (failed) throw failed.reason
    wallMs = performance.now() - begin
  } finally { sampling = false; await sampler }
  await wait('response observation drain', () => java.events.filter(e => e.operation === 'RESPONSE').length >= count, 5000)
  const http = java.events.filter(e => e.operation === 'SSR_HTTP')
  const values = samples.map(s => s.elapsedMs)
  const result = {
    mode, scenario: scenario.name, concurrency: parallelism, effectiveConcurrency: Math.min(parallelism, count), requests: count, wallMs, requestsPerSecond: count / (wallMs / 1000),
    latencyMs: { min: Math.min(...values), p50: percentile(values, .5), p95: percentile(values, .95), p99: percentile(values, .99), max: Math.max(...values) },
    statuses: counts(samples.map(s => s.status)), clientErrors: counts(samples.filter(s => s.error).map(s => s.error)),
    ssrResponses: samples.filter(s => s.ssr).length, csrResponses: samples.filter(s => !scenario.json && !s.ssr && s.status === 200).length,
    jsonResponses: scenario.json ? samples.filter(s => s.status === 200).length : 0,
    ssrRatio: samples.filter(s => s.ssr).length / count,
    rendererTimeoutRatio: http.filter(e => e.reason === 'TIMEOUT').length / count,
    clientTimeoutRatio: samples.filter(s => s.error === 'TimeoutError').length / count,
    latencyByResponseMs: (scenario.json ? ['json'] : ['ssr', 'csr']).map(kind => {
      const durations = samples.filter(s => s.status === 200 && (kind === 'json' || s.ssr === (kind === 'ssr'))).map(s => s.elapsedMs)
      return { kind, count: durations.length, p50: percentile(durations, .5), p95: percentile(durations, .95), p99: percentile(durations, .99) }
    }),
    responseBytes: { min: Math.min(...samples.map(s => s.bytes)), max: Math.max(...samples.map(s => s.bytes)) },
    responseKind: scenario.json ? 'json' : 'html',
    propsObservations: counts(java.events.filter(e => e.operation === 'PROPS').map(e => e.reason)),
    httpObservations: http.length, httpReasons: counts(http.map(e => e.reason)),
    jvm: profile === 'application' ? {
      samples: resources.filter(s => s.javaRuntime).length,
      maxSampledHeapUsedBytes: Math.max(...resources.map(s => s.javaRuntime?.heapUsedBytes ?? 0)),
      maxSampledQueuedTasks: Math.max(...resources.map(s => s.javaRuntime?.queuedTasks ?? 0)),
      maxSampledActiveThreads: Math.max(...resources.map(s => s.javaRuntime?.activeThreads ?? 0)),
    } : undefined,
    resources: ['java', 'node', 'driver'].map(role => {
      const entries = resources.flatMap(s => s.processes ?? []).filter(p => p.role === role)
      return { role, samples: entries.length, maxSampledRssBytes: entries.length ? Math.max(...entries.map(p => p.rssBytes)) : null, maxReportedCpuPercent: entries.length ? Math.max(...entries.map(p => p.reportedCpuPercent)) : null }
    }),
  }
  const stem = profile === 'transport' ? `${mode}-c${parallelism}` : `${mode}-${scenario.name}-c${parallelism}`
  writeFileSync(resolve(output, stem + '.json'), JSON.stringify({ result, samples, resources, events: java.events }, null, 2))
  summary.phases.push(result)
  console.log(`${stem}: p50=${result.latencyMs.p50.toFixed(1)}ms p95=${result.latencyMs.p95.toFixed(1)}ms SSR=${result.ssrResponses}/${count} reasons=${JSON.stringify(result.httpReasons)}`)
  if (profile === 'application' && (!result.jvm.samples || resources.some(s => s.javaRuntime && s.javaRuntime.queuedTasks > s.javaRuntime.queueCapacity))) throw new Error('Missing or invalid runtime capacity samples')
  if (samples.some(s => s.status !== 200) || http.length !== (scenario.json ? 0 : count)) throw new Error('Invalid phase: status or observation count')
  if (!scenario.json && mode === 'ssr' && parallelism === 1 && result.ssrResponses !== count) throw new Error('Baseline SSR missing')
  if (mode !== 'ssr' && result.ssrResponses !== 0) throw new Error('Fallback mode unexpectedly rendered')
  const expected = { 'excluded-csr': 'EXCLUDED_OR_UNAVAILABLE', 'connection-refused': 'CONNECTION', 'stalled-body': 'TIMEOUT' }[mode]
  if (!scenario.json && expected && !result.httpReasons[expected]) throw new Error('Expected fallback reason missing')
}
try {
  await source()
  for (const mode of modes) {
    const rendererPort = await reservePort()
    let node
    if (mode === 'ssr' || mode === 'excluded-csr') {
      node = start(process.execPath, ['dist/ssr/ssr.js'], frontend, mode + '-node', { SSR_PORT: String(rendererPort), SSR_ROOT_ID: 'app' })
      await wait('Node health', async () => { try { return (await fetch(`http://127.0.0.1:${rendererPort}/health`, { signal: AbortSignal.timeout(500) })).ok } catch { return false } })
    } else if (mode === 'stalled-body') {
      peer = createServer((req, res) => { req.resume(); res.writeHead(200, { 'Content-Type': 'application/json' }); res.write('{'); /* never complete body */ })
      await new Promise((done, reject) => { peer.once('error', reject); peer.listen(rendererPort, '127.0.0.1', done) })
    }
    const java = start('java', [`-Dinertia.ssr=http://127.0.0.1:${rendererPort}/render`, '-jar', jar,
      '--server.address=127.0.0.1', '--server.port=0', '--inertia.benchmark-observations=true', '--inertia.ssr-health-enabled=false',
      '--inertia.props-timeout=3s', '--inertia.response-timeout=5s', '--inertia.props-concurrency=8', '--inertia.executor-core-size=8', '--inertia.executor-max-size=32', '--inertia.executor-queue-capacity=256',
      ...(profile === 'application' ? ['--inertia.benchmark-enabled=true'] : []),
      ...(mode === 'excluded-csr' ? ['--inertia.ssr-except=' + (profile === 'application' ? '/benchmark/page' : '/users')] : []),
    ], example, mode + '-java')
    let port
    await wait('Java startup', () => { if (java.exitCode !== null || java.signalCode !== null) throw new Error('Java exited before startup'); port = java.transcript.match(/Tomcat started on port (\d+)/)?.[1]; return Boolean(port) })
    const base = `http://127.0.0.1:${port}`
    const probePath = profile === 'application' ? '/benchmark/page?bytes=64' : '/users'
    const response = await fetch(base + probePath, { headers: { 'X-Inertia': 'true', 'X-Inertia-Version': receipt.buildId, Accept: 'application/json' }, signal: AbortSignal.timeout(10000) })
    const page = await response.json()
    if (response.status !== 200 || page.component !== 'Users/Index' || page.version !== receipt.buildId) throw new Error('Page/build verification failed')
    summary.page = { component: page.component, propsKeys: Object.keys(page.props), propsJsonBytes: Buffer.byteLength(JSON.stringify(page.props)), version: page.version }
    summary.initialHtmlProbes ??= []
    const initial = await get(base + probePath)
    if (initial.status !== 200) throw new Error('Initial HTML probe failed')
    summary.initialHtmlProbes.push({ mode, ...initial })
    await wait('initial response observations', () => java.events.filter(e => e.operation === 'RESPONSE').length >= 2, 5000)
    for (const scenario of scenarios) for (const n of concurrency) await phase(mode, java, node, base, n, scenario)
    await stop(java); await stop(node)
    if (peer) { peer.closeAllConnections(); await new Promise(done => peer.close(done)); peer = undefined }
  }
  summary.success = true
} catch (error) {
  summary.error = error.stack
  process.exitCode = 1
} finally {
  const cleanup = await Promise.allSettled(children.toReversed().map(stop))
  const failures = cleanup.filter(r => r.status === 'rejected')
  if (failures.length) { summary.success = false; summary.cleanupErrors = failures.map(r => String(r.reason)); process.exitCode = 1 }
  if (peer) { peer.closeAllConnections(); await new Promise(done => peer.close(done)) }
  summary.finishedAt = new Date().toISOString()
  writeFileSync(resolve(output, 'summary.json'), JSON.stringify(summary, null, 2))
  console.log('Benchmark evidence: ' + resolve(output, 'summary.json'))
  if (!summary.success) console.error(summary.error)
}
