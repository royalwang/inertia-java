import { spawn } from 'node:child_process'
import { createWriteStream, mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { resolve } from 'node:path'
import { setTimeout as delay } from 'node:timers/promises'
import { createServer } from 'node:net'

export async function freePort() {
  const server = createServer()
  await new Promise((yes, no) => { server.once('error', no); server.listen(0, '127.0.0.1', yes) })
  const port = server.address().port
  await new Promise(yes => server.close(yes))
  return port
}
export function rehearsal(name) {
  const parent = resolve(process.env.INERTIA_DOCS_OUTPUT ?? tmpdir())
  mkdirSync(parent, { recursive: true })
  const output = mkdtempSync(resolve(parent, name + '-'))
  const children = []
  let interrupted = false
  const interrupt = () => {
    interrupted = true
    for (const child of children) if (!child.closed && child.pid) {
      try { process.platform === 'win32' ? child.kill('SIGTERM') : process.kill(-child.pid, 'SIGTERM') }
      catch (error) { if (error.code !== 'ESRCH') child.failure = error }
    }
  }
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, interrupt)
  const evidence = { success: false, startedAt: new Date().toISOString(), output, phases: [] }
  function start(command, args, cwd, name, extra = {}) {
    const log = createWriteStream(resolve(output, name + '.log'))
    const child = spawn(command, args, { cwd, env: { ...process.env, ...extra }, detached: process.platform !== 'win32', stdio: ['ignore', 'pipe', 'pipe'] })
    child.closed = false; child.transcript = ''; children.push(child)
    for (const stream of [child.stdout, child.stderr]) stream.on('data', data => { log.write(data); child.transcript = (child.transcript + data).slice(-100000) })
    child.once('error', error => { child.failure = error })
    child.once('close', () => { child.closed = true; log.end() })
    return child
  }
  async function wait(label, predicate, ms = 60000) {
    const end = Date.now() + ms
    while (Date.now() < end) {
      if (interrupted) throw new Error('Interrupted documentation rehearsal')
      const failed = children.find(child => child.failure)
      if (failed) throw failed.failure
      if (await predicate()) return
      await delay(100)
    }
    throw new Error('Deadline: ' + label)
  }
  async function run(command, args, cwd, name, extra = {}) {
    const child = start(command, args, cwd, name, extra)
    await wait(name, () => child.closed, 240000)
    evidence.phases.push({ name, exit: child.exitCode })
    if (child.exitCode !== 0) throw new Error(name + ' failed; see ' + resolve(output, name + '.log'))
  }
  async function stop(child) {
    if (child.closed) return
    const signal = name => {
      try { process.platform === 'win32' ? child.kill(name) : process.kill(-child.pid, name) }
      catch (error) { if (error.code !== 'ESRCH') throw error }
    }
    signal('SIGTERM')
    try { await wait('owned process shutdown', () => child.closed, 15000) }
    catch { signal('SIGKILL'); await wait('owned process forced shutdown', () => child.closed, 5000); throw new Error('Owned process required forced shutdown') }
  }
  async function finish(error) {
    if (error || interrupted) { evidence.success = false; evidence.error = error?.stack ?? 'Interrupted'; process.exitCode = 1 }
    for (const signal of ['SIGINT', 'SIGTERM']) process.off(signal, interrupt)
    interrupted = false
    const cleanup = await Promise.allSettled(children.map(stop))
    const failures = cleanup.filter(item => item.status === 'rejected').map(item => String(item.reason))
    if (failures.length) { evidence.cleanupErrors = failures; evidence.success = false; process.exitCode = 1 }
    evidence.finishedAt = new Date().toISOString()
    writeFileSync(resolve(output, 'summary.json'), JSON.stringify(evidence, null, 2) + '\n')
    console.log('Documentation evidence: ' + resolve(output, 'summary.json'))
    if (error) console.error(error.message)
  }
  return { start, run, wait, stop, finish, evidence, output }
}
