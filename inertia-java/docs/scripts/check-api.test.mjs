import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import { checkApi } from './check-api.mjs'

const root = fileURLToPath(new URL('..', import.meta.url))
const actual = JSON.parse(readFileSync(resolve(root, 'public/reference/javadoc/0.1.0-SNAPSHOT/api-index.json')))
const read = path => readFileSync(resolve(root, path), 'utf8')
test('actual JDK inventory has documented owners, configuration fields and timers', () => {
  assert.match(checkApi(actual, read), /71 public types/)
})
test('new unmapped public type fails', () => {
  const changed = structuredClone(actual)
  changed.modules[0].types.push({ p: 'io.inertia.core', l: 'UndocumentedType' })
  assert.throws(() => checkApi(changed, read), /Unmapped public API/)
})
test('new configuration accessor fails the reviewed field inventory', () => {
  const changed = structuredClone(actual)
  changed.modules[0].members.push({ p: 'io.inertia.core', c: 'InertiaConfig', l: 'futureSetting()' })
  assert.throws(() => checkApi(changed, read), /review changed configuration fields/)
})
test('new observer operation requires a metric reference entry', () => {
  const changed = structuredClone(actual)
  changed.modules[0].members.push({ p: 'io.inertia.core', c: 'InertiaObserver.Operation', l: 'FUTURE_OPERATION' })
  assert.throws(() => checkApi(changed, read), /Undocumented timer/)
})
test('removing a setting from prose fails even when generated API is unchanged', () => {
  assert.throws(() => checkApi(actual, path => read(path).replace('`inertia.props-timeout`', 'timeout')), /Undocumented setting/)
})
