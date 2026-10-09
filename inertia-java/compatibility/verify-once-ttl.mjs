// Runtime semantic gate: Rust's clock is real, so compare bounded timestamps, not frozen output.
import { execFileSync } from 'node:child_process'
import { readFileSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { createHash } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import assert from 'node:assert/strict'
const root = fileURLToPath(new URL('../..', import.meta.url))
const definitions = readFileSync(resolve(root, 'examples/java_once_ttl_cases.json'), 'utf8')
const cases = JSON.parse(definitions)
const result = JSON.parse(execFileSync('cargo', ['run', '--locked', '--quiet', '--no-default-features', '--example', 'java_once_ttl_fixtures'], { cwd: root, encoding: 'utf8', maxBuffer: 1024 * 1024, stdio: ['ignore', 'pipe', 'inherit'] }))
assert.equal(result.cases.length, cases.length)
assert.equal(new Set(result.cases.map(c => c.name)).size, cases.length)
for (let i = 0; i < cases.length; i++) {
  const input = cases[i], actual = result.cases[i]
  assert.equal(actual.name, input.name)
  assert.equal(actual.calls, input.calls, input.name)
  assert.equal(Object.hasOwn(actual.page.props, 'catalog'), input.value, input.name)
  if (input.value) assert.equal(actual.page.props.catalog, 7)
  assert.equal(Object.hasOwn(actual.page, 'onceProps'), input.metadata, input.name)
  assert(Number.isSafeInteger(actual.beforeMillis) && Number.isSafeInteger(actual.afterMillis))
  assert(actual.beforeMillis <= actual.afterMillis, 'Clock moved backwards during export; rerun with stable clock')
  if (!input.metadata) continue
  assert.deepEqual(Object.keys(actual.page.onceProps), ['ttl-cache'])
  const meta = actual.page.onceProps['ttl-cache']
  assert.equal(meta.prop, 'catalog')
  if (!Object.hasOwn(input, 'ttlMillis')) assert.equal(meta.expiresAt, null)
  else {
    assert(Number.isSafeInteger(meta.expiresAt) && meta.expiresAt % 1000 === 0)
    const seconds = Math.floor(input.ttlMillis / 1000)
    const earliest = (Math.floor(actual.beforeMillis / 1000) + seconds) * 1000
    const latest = (Math.floor(actual.afterMillis / 1000) + seconds) * 1000
    assert(meta.expiresAt >= earliest && meta.expiresAt <= latest, input.name + ': expiry outside measured Rust window')
  }
}
console.log(`Verified ${cases.length} live Rust once/TTL cases against measured clock windows and callback traces`)

const output = mkdtempSync(resolve(tmpdir(), 'inertia-java-ttl-'))
writeFileSync(resolve(output, 'summary.json'), JSON.stringify({ format: 1, success: true, verifiedAt: new Date().toISOString(), source: { head: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim(), dirty: execFileSync('git', ['status', '--porcelain=v1'], { cwd: root, encoding: 'utf8' }).trim() !== '', definitionsSha256: createHash('sha256').update(definitions).digest('hex') }, ...result }, null, 2) + '\n')
console.log('TTL evidence: ' + resolve(output, 'summary.json'))
