import { spawn, spawnSync } from 'node:child_process'
import { cpSync, mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync, createWriteStream } from 'node:fs'
import { tmpdir } from 'node:os'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { setTimeout as delay } from 'node:timers/promises'

const frontend = fileURLToPath(new URL('..', import.meta.url))
const output = process.env.INERTIA_BUILD_OUTPUT ?? '/tmp/inertia-java-build-integrity'
mkdirSync(output, { recursive: true })
const record = JSON.parse(readFileSync(resolve(frontend, 'dist/build.json'), 'utf8'))
const asset = Object.keys(record.files).find(path => path.startsWith('client/assets/') && path.endsWith('.js'))
if (!asset) throw new Error('Missing client JS fixture')
for (const scenario of ['valid', 'mixed-ssr', 'missing-client', 'extra-client']) {
  const copy = mkdtempSync(resolve(tmpdir(), 'inertia-build-'))
  const dist = resolve(copy, 'dist')
  mkdirSync(dist)
  for (const name of ['client', 'ssr', 'build.json']) cpSync(resolve(frontend, 'dist', name), resolve(dist, name), { recursive: true })
  if (scenario === 'mixed-ssr') writeFileSync(resolve(dist, 'ssr/ssr.js'), 'wrong release')
  if (scenario === 'missing-client') rmSync(resolve(dist, asset))
  if (scenario === 'extra-client') writeFileSync(resolve(dist, 'client/extra.js'), 'unexpected')
  const log = createWriteStream(resolve(output, scenario + '.log'))
  const child = spawn('java', [`-Dinertia.frontend=${copy}`, `-Dinertia.asset-store=${resolve(frontend, ".inertia/assets")}`, '-jar', 'target/spring-react-0.1.0-SNAPSHOT.jar',
    '--server.address=127.0.0.1', '--server.port=0'], { cwd: resolve(frontend, '..'), stdio: ['ignore', 'pipe', 'pipe'] })
  let transcript = ''
  let failure
  child.on('error', error => { failure = error })
  child.stdout.on('data', data => { log.write(data); transcript += data.toString() })
  child.stderr.on('data', data => { log.write(data); transcript += data.toString() })
  try {
    const deadline = Date.now() + 20000
    let port
    while (child.exitCode === null && child.signalCode === null && Date.now() < deadline) {
      if (failure) throw failure
      port = transcript.match(/Tomcat started on port (\d+)/)?.[1]
      if (port) break
      await delay(100)
    }
    if (scenario === 'valid') {
      if (!port) throw new Error('Valid release did not start')
      const page = await fetch(`http://127.0.0.1:${port}/users`)
      if (!page.ok || !(await page.text()).includes(`"version":"${record.buildId}"`)) throw new Error('Page version does not match receipt')
    } else {
      if (port || child.exitCode === null || child.exitCode === 0) throw new Error('Mixed release was not rejected: ' + scenario)
      if (!transcript.includes('Vite build') && !transcript.includes('NoSuchFileException')) throw new Error('Startup failed for an unrelated reason')
    }
    console.log('Verified build integrity: ' + scenario)
  } finally {
    if (child.exitCode === null && child.signalCode === null) child.kill('SIGTERM')
    const deadline = Date.now() + 5000
    while (child.exitCode === null && child.signalCode === null && !failure && Date.now() < deadline) await delay(100)
    if (child.exitCode === null && child.signalCode === null && !failure) child.kill('SIGKILL')
    log.end()
    rmSync(copy, { recursive: true, force: true })
  }
}

const nodeCopy = mkdtempSync(resolve(frontend, 'dist/node-integrity-'))
try {
  const entry = resolve(nodeCopy, 'ssr.mjs')
  writeFileSync(entry, readFileSync(resolve(frontend, 'dist/ssr/ssr.js'), 'utf8') + '\n// mixed release\n')
  const failed = spawnSync(process.execPath, [entry], { cwd: frontend, timeout: 10000, encoding: 'utf8' })
  writeFileSync(resolve(output, 'mixed-node.log'), (failed.stdout ?? '') + (failed.stderr ?? ''))
  if (failed.status === 0 || !failed.stderr?.includes('SSR build content mismatch')) throw new Error('Node accepted mixed bundle or failed for an unrelated reason')
  console.log('Verified Node refuses changed bundle before listening')
} finally { rmSync(nodeCopy, { recursive: true, force: true }) }
