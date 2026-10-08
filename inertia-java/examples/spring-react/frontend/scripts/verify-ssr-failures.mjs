import http from 'node:http'
import { spawn } from 'node:child_process'
import { createWriteStream, mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import { setTimeout as delay } from 'node:timers/promises'

// Run after Maven verify and npm build. All peers bind to loopback, on ephemeral ports.
const frontend = fileURLToPath(new URL('..', import.meta.url))
const sample = resolve(frontend, '..')
const output = process.env.INERTIA_FAILURE_OUTPUT ?? '/tmp/inertia-java-ssr-failures'
mkdirSync(output, { recursive: true })
let mode = 'status'
let calls = 0
let credentialLeak = false
const peer = http.createServer((request, response) => {
  if (request.url !== '/render' || request.method !== 'POST') {
    response.writeHead(404).end()
    return
  }
  calls++
  credentialLeak ||= Boolean(request.headers.cookie || request.headers.authorization)
  request.resume()
  if (mode === 'build-mismatch') response.writeHead(200, { 'content-type': 'application/json' }).end(JSON.stringify({ head: [], body: '<div id="app" data-server-rendered="true">wrong release</div>', buildId: 'different-release' }))
  else if (mode === 'status') response.writeHead(503).end('Renderer unavailable')
  else if (mode === 'oversize') {
    response.writeHead(200, { 'content-type': 'application/json' })
    response.end('x'.repeat(2 * 1024 * 1024 + 1))
  } else {
    response.writeHead(200, { 'content-type': 'application/json', 'content-length': 4096 })
    response.write('{') // Stall after headers; Java must bound the whole response body.
  }
})
await new Promise(resolve => peer.listen(0, '127.0.0.1', resolve))
const peerPort = peer.address().port
const log = createWriteStream(resolve(output, 'java.log'))
const java = spawn('java', [
  `-Dinertia.ssr=http://127.0.0.1:${peerPort}/render`,
  '-jar', 'target/spring-react-0.1.0-SNAPSHOT.jar',
  '--server.address=127.0.0.1', '--server.port=0',
], { cwd: sample, stdio: ['ignore', 'pipe', 'pipe'] })
let javaPort
let startup = ''
java.stdout.on('data', chunk => {
  log.write(chunk)
  startup = (startup + chunk.toString()).slice(-32000)
  const match = startup.match(/Tomcat started on port (\d+)/)
  if (match) javaPort = Number(match[1])
})
java.stderr.on('data', chunk => log.write(chunk))
let spawnError
java.on('error', error => { spawnError = error })

function runBrowser() {
  return new Promise((resolveRun, rejectRun) => {
    const browser = spawn(process.execPath, [
      'node_modules/@playwright/test/cli.js', 'test', '--grep', 'CSR fallback',
    ], {
      cwd: frontend,
      env: { ...process.env, INERTIA_BASE_URL: `http://127.0.0.1:${javaPort}`,
        INERTIA_EXPECT_CSR: 'true', INERTIA_E2E_OUTPUT: resolve(output, mode) },
      stdio: 'inherit',
    })
    browser.on('error', rejectRun)
    browser.on('exit', code => code === 0 ? resolveRun() : rejectRun(new Error(`Browser failed: ${mode}, exit ${code}`)))
  })
}

try {
  const deadline = Date.now() + 30000
  while (!javaPort) {
    if (spawnError) throw spawnError
    if (java.exitCode !== null) throw new Error(`Java exited before readiness: ${java.exitCode}; see ${output}/java.log`)
    if (Date.now() > deadline) throw new Error('Java readiness deadline exceeded')
    await delay(100)
  }
  for (const scenario of ['status', 'slow-body', 'oversize', 'build-mismatch']) {
    mode = scenario
    calls = 0
    console.log(`SSR failure acceptance: ${mode}`)
    await runBrowser()
    if (calls === 0) throw new Error(`Renderer scenario was never exercised: ${mode}`)
    if (credentialLeak) throw new Error('Credentials reached renderer')
    console.log(`Verified ${mode}: ${calls} renderer call(s), CSR mount/navigation/form/flash passed`)
  }
} finally {
  java.kill('SIGTERM')
  const deadline = Date.now() + 5000
  while (java.exitCode === null && java.signalCode === null && !spawnError && Date.now() < deadline) await delay(100)
  if (java.exitCode === null && java.signalCode === null && !spawnError) java.kill('SIGKILL')
  peer.closeAllConnections()
  await new Promise(resolveClose => peer.close(resolveClose))
  log.end()
}
