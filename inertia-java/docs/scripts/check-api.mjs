import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'

const root = fileURLToPath(new URL('..', import.meta.url))
export function checkApi(api, readPage) {
const maps = {
  'inertia-core': 'reference/core-api.md',
  'inertia-ssr-http': 'reference/ssr-vite-api.md',
  'inertia-vite': 'reference/ssr-vite-api.md',
  'inertia-spring-webmvc': 'reference/spring-api.md',
  'inertia-spring-boot-autoconfigure': 'reference/spring-api.md',
  'inertia-testing': 'testing/assertable-page.md',
}
for (const module of api.modules) {
  if (module.id === 'inertia-spring-boot-starter') continue
  const text = readPage(maps[module.id])
  for (const type of module.types) {
    // Nested signatures remain in generated Javadoc; the owning public type must have a prose map.
    const owner = type.l.split('.')[0]
    assert.ok(text.includes('`' + owner + '`'), `Unmapped public API: ${module.id}/${type.l}`)
  }
}
const configuration = readPage('reference/configuration.md')
const fields = {
  InertiaConfig: ['version', 'rootId', 'components', 'rootView', 'gateway', 'shared', 'preserveBigIntegers', 'encryptHistory', 'allErrors', 'exposeSharedPropKeys', 'urlResolver'],
  InertiaProperties: ['propsTimeout', 'responseTimeout', 'propsConcurrency', 'executorCoreSize', 'executorMaxSize', 'executorQueueCapacity', 'allErrors', 'sessionNamespace'],
}
for (const [type, expected] of Object.entries(fields)) {
  const members = api.modules.flatMap(module => module.members).filter(member => member.c === type)
  const nonFields = new Set(['hashCode()', 'toString()', 'equals(Object)', 'withAllErrors(boolean)', 'withUrlResolver(Function<InertiaRequest, String>)', 'withSharedPropKeys(boolean)', 'pageUrl(InertiaRequest)', 'requireRootId(String)', 'basic(String, Set<String>)'])
  const accessors = members.filter(member => !member.l.startsWith(type + '(') && !nonFields.has(member.l)).map(member => member.l.replace(/\(\)$/, ''))
  assert.deepEqual(accessors.sort(), [...expected].sort(), `${type}: review changed configuration fields`)
  for (const field of expected) {
    const token = type === 'InertiaProperties' ? 'inertia.' + field.replace(/[A-Z]/g, letter => '-' + letter.toLowerCase()) : field
    assert.ok(configuration.includes('`' + token + '`'), `Undocumented setting: ${token}`)
  }
}
const metrics = readPage('reference/metrics.md')
const operations = api.modules.flatMap(module => module.members).filter(member => member.c === 'InertiaObserver.Operation' && /^[A-Z_]+$/.test(member.l))
assert.equal(operations.length > 0, true)
for (const operation of operations) assert.ok(metrics.includes('`inertia.' + operation.l.toLowerCase() + '`'), 'Undocumented timer: ' + operation.l)
return `API map: ${api.modules.reduce((count, module) => count + module.types.length, 0)} public types, 19 configuration fields and ${operations.length} timers checked; generated member anchors checked during preparation`
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const catalog = JSON.parse(readFileSync(resolve(root, '../../docs/inertia-java/open-source-docs-catalog.json')))
  const api = JSON.parse(readFileSync(resolve(root, 'public/reference/javadoc', catalog.site.javadoc.version, 'api-index.json')))
  assert.equal(api.version, catalog.site.javadoc.version)
  console.log(checkApi(api, path => readFileSync(resolve(root, path), 'utf8')))
}
