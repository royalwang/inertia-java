import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { tmpdir } from 'node:os'
import { publishAssets } from './release-assets.mjs'
const hash = bytes => createHash('sha256').update(bytes).digest('hex')
function fixture(root, text) {
  const dist = resolve(root, 'dist')
  mkdirSync(resolve(dist, 'client/assets'), { recursive: true })
  mkdirSync(resolve(dist, 'client/.vite'), { recursive: true })
  mkdirSync(resolve(dist, 'ssr'), { recursive: true })
  const files = {
    'client/.vite/manifest.json': '{"src/app.tsx":{"file":"assets/app.js"}}',
    'client/assets/app.js': text,
    'ssr/ssr.js': 'server',
  }
  for (const [path, value] of Object.entries(files)) writeFileSync(resolve(dist, path), value)
  const canonical = { format: 1, files: Object.fromEntries(Object.entries(files).map(([path, value]) => [path, hash(value)])) }
  const buildId = hash(JSON.stringify(canonical))
  writeFileSync(resolve(dist, 'build.json'), JSON.stringify({ ...canonical, buildId }))
  return { dist, buildId }
}
test('two releases preserve old bytes, with idempotent publication', () => {
  const root = mkdtempSync(resolve(tmpdir(), 'inertia-publish-'))
  try {
    const store = resolve(root, 'store')
    const one = fixture(root, 'release one')
    const old = publishAssets(one.dist, store)
    assert.equal(publishAssets(one.dist, store), old)
    const two = fixture(root, 'release two')
    const current = publishAssets(two.dist, store)
    assert.notEqual(one.buildId, two.buildId)
    assert.equal(readFileSync(resolve(old, 'assets/app.js'), 'utf8'), 'release one')
    assert.equal(readFileSync(resolve(current, 'assets/app.js'), 'utf8'), 'release two')
    assert.equal(existsSync(resolve(store, one.buildId)), true)
  } finally { rmSync(root, { recursive: true, force: true }) }
})
test('a corrupted immutable archive is rejected without overwrite', () => {
  const root = mkdtempSync(resolve(tmpdir(), 'inertia-publish-'))
  try {
    const { dist } = fixture(root, 'release')
    const store = resolve(root, 'store')
    const archive = publishAssets(dist, store)
    writeFileSync(resolve(archive, 'assets/app.js'), 'corrupted')
    assert.throws(() => publishAssets(dist, store), /inventory mismatch/)
    assert.equal(readFileSync(resolve(archive, 'assets/app.js'), 'utf8'), 'corrupted')
  } finally { rmSync(root, { recursive: true, force: true }) }
})
test('unrecorded source and invalid receipt never publish', () => {
  const root = mkdtempSync(resolve(tmpdir(), 'inertia-publish-'))
  try {
    const { dist } = fixture(root, 'release')
    const store = resolve(root, 'store')
    writeFileSync(resolve(dist, 'client/extra.js'), 'extra')
    assert.throws(() => publishAssets(dist, store), /inventory mismatch/)
    assert.equal(existsSync(store), false)
    rmSync(resolve(dist, 'client/extra.js'))
    const record = JSON.parse(readFileSync(resolve(dist, 'build.json'), 'utf8'))
    record.buildId = '0'.repeat(64)
    writeFileSync(resolve(dist, 'build.json'), JSON.stringify(record))
    assert.throws(() => publishAssets(dist, store), /Build id mismatch/)
    assert.equal(existsSync(store), false)
  } finally { rmSync(root, { recursive: true, force: true }) }
})
