// Local executor saturation and recovery, using an opt-in fixture and owned JVM.
import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, createWriteStream } from 'node:fs'
import { tmpdir } from 'node:os'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { setTimeout as delay } from 'node:timers/promises'
const frontend = fileURLToPath(new URL('..', import.meta.url))
const parent = process.env.INERTIA_CAPACITY_OUTPUT ?? tmpdir()
mkdirSync(parent, { recursive: true })
const output = mkdtempSync(resolve(parent, 'inertia-capacity-'))
const receipt = JSON.parse(readFileSync(resolve(frontend, 'dist/build.json')))
const summary = { success: false, startedAt: new Date().toISOString(), output, phases: [] }
const events = []
const log = createWriteStream(resolve(output, 'java.log'))
const java = spawn('java', ['-Xmx128m', '-jar', 'target/spring-react-0.1.0-SNAPSHOT.jar',
  '--server.address=127.0.0.1', '--server.port=0', '--inertia.benchmark-enabled=true',
  '--inertia.benchmark-observations=true', '--inertia.ssr-health-enabled=false',
  '--inertia.executor-core-size=2', '--inertia.executor-max-size=2', '--inertia.executor-queue-capacity=2'],
{ cwd: resolve(frontend, '..'), detached: process.platform !== 'win32', stdio: ['ignore', 'pipe', 'pipe'] })
let port, error, failure, interrupted = false
const partial = ['', '']
for (const [i, stream] of [java.stdout, java.stderr].entries()) stream.on('data', chunk => {
  log.write(chunk)
  const lines = (partial[i] + chunk.toString()).split('\n')
  partial[i] = lines.pop()
  for (const line of lines) {
    port ??= line.match(/Tomcat started on port (\d+)/)?.[1]
    const at = line.indexOf('{"operation":')
    if (at >= 0) try { events.push(JSON.parse(line.slice(at))) } catch { /* startup output */ }
  }
})
java.on('error', value => { failure = value })
java.on('close', () => log.end())
const interrupt = () => { interrupted = true }
for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, interrupt)
async function wait(predicate, ms = 20000, allowInterrupt = false) {
  const end = Date.now() + ms
  while (Date.now() < end) {
    if (failure) throw failure
    if (interrupted && !allowInterrupt) throw new Error('Interrupted')
    if (await predicate()) return
    await delay(20)
  }
  throw new Error('Capacity rehearsal deadline')
}
try {
  await wait(() => { if (java.exitCode !== null || java.signalCode !== null) throw new Error('JVM exited'); return port })
  const base = `http://127.0.0.1:${port}`
  const runtime = async () => {
    const response = await fetch(base + '/benchmark/resources', { signal: AbortSignal.timeout(5000) })
    assert.equal(response.status, 200)
    return response.json()
  }
  const headers = { 'X-Inertia': 'true', 'X-Inertia-Version': receipt.buildId,
    'X-Inertia-Partial-Component': 'Users/Index', 'X-Inertia-Partial-Data': 'payload' }
  const visit = async delayMs => {
    const response = await fetch(base + '/benchmark/page?bytes=64&delayMs=' + delayMs, { headers, signal: AbortSignal.timeout(5000) })
    const body = await response.text()
    if (response.status === 200) assert.equal(JSON.parse(body).props.payload.length, 64)
    return response.status
  }
  const before = await runtime()
  assert.equal(before.queueCapacity, 2)
  assert.equal(before.queuedTasks, 0)
  const samples = []
  let sampling = true
  const sampler = (async () => {
    while (sampling && !interrupted) { samples.push(await runtime()); await delay(25) }
  })()
  let statuses
  try { statuses = await Promise.all(Array.from({ length: 32 }, () => visit(500))) }
  finally { sampling = false; await sampler }
  assert.ok(statuses.every(status => status === 200 || status === 500))
  assert.ok(statuses.includes(500), 'Load must actually saturate the bounded executor')
  await wait(() => events.some(event => event.operation === 'PROPS' && event.reason === 'OVERLOADED'))
  assert.ok(samples.length > 0)
  assert.ok(samples.every(sample => sample.queuedTasks <= 2 && sample.activeThreads <= 2 && sample.heapUsedBytes <= sample.heapMaxBytes))
  summary.phases.push({ name: 'bounded-executor-pressure', requests: statuses.length,
    success: statuses.filter(status => status === 200).length, rejected: statuses.filter(status => status === 500).length,
    maxSampledQueue: Math.max(...samples.map(sample => sample.queuedTasks)),
    maxSampledHeapBytes: Math.max(...samples.map(sample => sample.heapUsedBytes)), heapLimitBytes: before.heapMaxBytes })
  await wait(async () => { const state = await runtime(); return state.queuedTasks === 0 && state.activeThreads === 0 })
  assert.equal(await visit(0), 200)
  const after = await runtime()
  assert.equal(after.queuedTasks, 0)
  summary.phases.push({ name: 'post-load-recovery', healthyStatus: 200, queue: after.queuedTasks, activeThreads: after.activeThreads })
  summary.samples = samples
  summary.events = events
  summary.success = true
} catch (caught) { error = caught; summary.error = caught.stack; process.exitCode = 1 }
finally {
  for (const signal of ['SIGTERM', 'SIGINT']) process.off(signal, interrupt)
  if (java.exitCode === null && java.signalCode === null) {
    try { process.platform === 'win32' ? java.kill('SIGTERM') : process.kill(-java.pid, 'SIGTERM') }
    catch (caught) { if (caught.code !== 'ESRCH') throw caught }
    try { await wait(() => java.exitCode !== null || java.signalCode !== null, 10000, true) }
    catch (caught) {
      process.platform === 'win32' ? java.kill('SIGKILL') : process.kill(-java.pid, 'SIGKILL')
      summary.success = false; summary.cleanupError = caught.message; process.exitCode = 1
      await wait(() => java.exitCode !== null || java.signalCode !== null, 5000, true)
    }
  }
  summary.finishedAt = new Date().toISOString()
  writeFileSync(resolve(output, 'summary.json'), JSON.stringify(summary, null, 2) + '\n')
  console.log('Capacity recovery evidence: ' + resolve(output, 'summary.json'))
  if (error) console.error(error.message)
}
