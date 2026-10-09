import { spawn, execFileSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, createWriteStream, writeFileSync, copyFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { tmpdir } from 'node:os'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('..', import.meta.url))
const frontend = resolve(root, 'examples/spring-react/frontend')
const output = process.env.INERTIA_VERIFY_OUTPUT
  ? resolve(process.env.INERTIA_VERIFY_OUTPUT)
  : mkdtempSync(resolve(tmpdir(), 'inertia-java-verify-'))
mkdirSync(output, { recursive: true })
if (process.platform === 'win32') throw new Error('The aggregate runner currently requires a POSIX host; use the documented Maven/npm commands on Windows.')
const [major, minor] = process.versions.node.split('.').map(Number)
if (major < 22 || (major === 22 && minor < 12)) throw new Error('Node >=22.12 is required')
const stages = []
const startedAt = new Date().toISOString()
const source = {
  head: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim(),
  dirty: execFileSync('git', ['status', '--porcelain=v1'], { cwd: root, encoding: 'utf8' }).trim() !== '',
}
const env = { ...process.env,
  INERTIA_VERIFY_CSP: 'false', INERTIA_VERIFY_HISTORY: 'false',
  INERTIA_EXPECT_CSP: 'false', INERTIA_EXPECT_HISTORY: 'false',
  INERTIA_EXPECT_CSR: 'false', INERTIA_EXPECT_ALL_ERRORS: 'false', INERTIA_EXPECT_FAILURES: 'false',
  INERTIA_ROOT_ID: 'app', SSR_ROOT_ID: 'app',
  INERTIA_MATRIX_OUTPUT: resolve(output, 'browser-matrix'),
  INERTIA_FAILURE_OUTPUT: resolve(output, 'ssr-failures'),
  INERTIA_SWITCH_OUTPUT: resolve(output, 'release-switch'),
  INERTIA_HEALTH_OUTPUT: resolve(output, 'ssr-health'),
  INERTIA_E2E_OUTPUT: resolve(output, 'e2e'),
}
async function run(name, command, args, cwd = frontend, extraEnv = {}) {
  console.log('Verification stage: ' + name)
  const started = Date.now()
  const log = createWriteStream(resolve(output, name + '.log'))
  const child = spawn(command, args, { cwd, env: { ...env, ...extraEnv }, stdio: ['ignore', 'pipe', 'pipe'] })
  child.stdout.on('data', data => { log.write(data); process.stdout.write(data) })
  child.stderr.on('data', data => { log.write(data); process.stderr.write(data) })
  const result = await new Promise(done => {
    child.on('error', error => { log.write(error.message + '\n'); done({ error: error.message }) })
    child.on('close', (code, signal) => done({ code, signal }))
  })
  await new Promise(done => log.end(done))
  stages.push({ name, ...result, elapsedMs: Date.now() - started })
  if (result.code !== 0) throw new Error('Verification failed at ' + name + '; see ' + output)
}
let failure
let receipt = null
try {
  await run('java-runtime', 'java', ['-version'], root)
  await run('npm-runtime', 'npm', ['--version'])
  await run('maven', resolve(root, 'mvnw'), ['--batch-mode', '--no-transfer-progress', 'clean', 'spotless:check', 'verify'], root)
  await run('library-artifacts', 'python3', [resolve(root, 'scripts/verify-library-artifacts.py'), resolve(output, 'library-artifacts.json')], root)
  await run('npm-ci', 'npm', ['ci'])
  await run('typecheck', 'npm', ['run', 'typecheck'])
  await run('build', 'npm', ['run', 'build'])
  copyFileSync(resolve(frontend, 'dist/build.json'), resolve(output, 'build.json'))
  receipt = 'build.json'
  for (const [name, script] of [
    ['asset-contracts', 'test:assets'], ['browser-matrix', 'test:browser-matrix'],
    ['build-integrity', 'test:build-integrity'], ['ssr-failures', 'test:ssr-failures'],
    ['csp', 'test:csp'], ['custom-root', 'test:custom-root'],
    ['history', 'test:history'], ['release-switch', 'test:release-switch'],
  ]) {
    await run(name, 'npm', ['run', script], frontend, {
      INERTIA_HEALTH_OUTPUT: resolve(output, name), INERTIA_E2E_OUTPUT: resolve(output, name, 'e2e'),
    })
  }
  await run('deployment', process.execPath, [resolve(root, 'deploy/verify-release.mjs')], root, { INERTIA_DEPLOY_OUTPUT: resolve(output, 'deployment') })
} catch (error) {
  failure = error.message
  process.exitCode = 1
} finally {
  writeFileSync(resolve(output, 'summary.json'), JSON.stringify({ format: 1, startedAt,
    finishedAt: new Date().toISOString(), source, node: process.version,
    browserChannel: process.env.INERTIA_BROWSER_CHANNEL ?? 'chrome', success: !failure,
    failure, stages, receipt,
  }, null, 2) + '\n')
  console.log('Verification evidence: ' + output)
  if (failure) console.error(failure)
}
