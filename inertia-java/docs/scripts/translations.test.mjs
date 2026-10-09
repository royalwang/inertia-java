import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { checkTranslations } from './translations.mjs'
const root = fileURLToPath(new URL('..', import.meta.url))
const catalog = JSON.parse(readFileSync(resolve(root, '../../docs/inertia-java/open-source-docs-catalog.json')))
const read = path => readFileSync(resolve(root, path), 'utf8')
test('priority translations match actual canonical revisions and executable fences', () => {
  assert.match(checkTranslations(catalog, read), /11 reviewed Chinese pages/)
})
test('an English edit requires translation review', () => {
  assert.throws(() => checkTranslations(catalog, path => read(path) + (path === 'index.md' ? '\nChanged English.\n' : '')), /Stale English revision/)
})
test('a translated command cannot silently change behavior', () => {
  assert.throws(() => checkTranslations(catalog, path => path === 'zh/getting-started/quick-start.md' ? read(path).replace('./mvnw verify', './mvnw test') : read(path)), /Executable fences differ/)
})
test('a translated code import must use the same canonical file', () => {
  assert.throws(() => checkTranslations(catalog, path => path === 'zh/getting-started/first-application.md' ? read(path).replace('<<< @/examples/first-application/Hello.tsx', '<<< @/examples/first-application/HelloController.java') : read(path)), /Canonical code imports differ/)
})
test('priority pages cannot be replaced by missing or unreviewed translations', () => {
  const missing = structuredClone(catalog)
  missing.translations = missing.translations.filter(entry => entry.id !== 'getting-started/overview')
  assert.throws(() => checkTranslations(missing, read), /Missing priority translation/)
  const pending = structuredClone(catalog)
  pending.translations[0].status = 'needs-review'
  assert.throws(() => checkTranslations(pending, read), /Translation needs review/)
})
test('translated version must match the canonical library version', () => {
  assert.throws(() => checkTranslations(catalog, path => path === 'zh/index.md' ? read(path).replace('version: 0.1.0-SNAPSHOT', 'version: 99.0.0') : read(path)), /Wrong translated library version/)
})
test('revision metadata must agree in the catalog and page', () => {
  assert.throws(() => checkTranslations(catalog, path => path === 'zh/index.md' ? read(path).replace('canonicalId: home', 'canonicalId: other') : read(path)), /Wrong translation provenance/)
})
test('translated property names and default literals cannot disappear', () => {
  assert.throws(() => checkTranslations(catalog, path => path === 'zh/reference/configuration.md' ? read(path).replace('`inertia.props-timeout`', '`inertia.future-timeout`') : read(path)), /Missing canonical API\/configuration token/)
})
