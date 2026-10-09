import { execFileSync } from 'node:child_process'
import { readFileSync, existsSync, readdirSync } from 'node:fs'
import { resolve, dirname, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import MarkdownIt from 'markdown-it'
import matter from 'gray-matter'
import { siteHeadings } from './markdown-headings.mjs'
import { readRegistry, validateRegistry } from './versions.mjs'
import { checkTranslations } from './translations.mjs'

const root = resolve(fileURLToPath(new URL('..', import.meta.url)))
const repository = resolve(root, '../..')
const catalog = JSON.parse(readFileSync(resolve(repository, 'docs/inertia-java/open-source-docs-catalog.json'), 'utf8'))
const registry = readRegistry(root)
if (process.env.INERTIA_DOCS_PREVIOUS_REGISTRY) validateRegistry(registry, JSON.parse(readFileSync(process.env.INERTIA_DOCS_PREVIOUS_REGISTRY, 'utf8')))
const previousCommit = process.env.INERTIA_DOCS_BASE_COMMIT
if (previousCommit && !/^0+$/.test(previousCommit)) {
  if (!/^[a-f0-9]{40}$/.test(previousCommit)) throw new Error('Invalid previous registry commit')
  execFileSync('git', ['rev-parse', '--verify', previousCommit + '^{commit}'], { cwd: repository, stdio: 'pipe' })
  let previous = { format: 1, releases: [] }
  const path = previousCommit + ':inertia-java/docs/versions.json'
  let exists = false
  try { execFileSync('git', ['cat-file', '-e', path], { cwd: repository, stdio: 'pipe' }); exists = true } catch {}
  if (exists) previous = JSON.parse(execFileSync('git', ['show', path], { cwd: repository, encoding: 'utf8', stdio: 'pipe' }))
  validateRegistry(registry, previous)
}
const md = new MarkdownIt({ html: true })
const errors = []
const fail = message => errors.push(message)
const ids = new Map(catalog.pages.map(page => [page.id, page]))
if (ids.size !== catalog.pages.length) fail('Duplicate page IDs')
if (new Set(catalog.pages.map(page => page.path)).size !== catalog.pages.length) fail('Duplicate page paths')
const headings = file => new Set(siteHeadings(readFileSync(file, 'utf8'), file))
const visit = (id, path = []) => {
  if (path.includes(id)) { fail('Dependency cycle: ' + [...path, id].join(' -> ')); return }
  for (const dependency of ids.get(id)?.dependsOn ?? []) {
    if (!ids.has(dependency)) fail(`${id}: missing dependency ${dependency}`)
    else visit(dependency, [...path, id])
  }
}
for (const page of catalog.pages) visit(page.id)
const translatedPages = (catalog.translations ?? []).map(entry => ({ ...catalog.pages.find(page => page.id === entry.id), ...entry, status: 'written' }))
for (const page of [...catalog.pages, ...translatedPages]) {
  if (!['planned', 'written', 'existing-markdown'].includes(page.status)) fail(`${page.id}: invalid status ${page.status}`)
  if (!['home', 'legacy-api-guide'].includes(page.id) && !catalog.sections.some(section => section.id === page.section)) fail(`${page.id}: unknown section ${page.section}`)
  if (!/^[a-z0-9/-]+\.md$/.test(page.path)) fail(`${page.id}: unsafe page path ${page.path}`)
  const file = resolve(root, page.path)
  if (!existsSync(file)) {
    if (page.status !== 'planned') fail(`${page.path}: declared ${page.status} but missing`)
    continue
  }
  if (page.status === 'planned') fail(`${page.path}: exists but still marked planned`)
  const parsed = matter(readFileSync(file, 'utf8'))
  const tokens = md.parse(parsed.content, {})
  if (page.id !== 'legacy-api-guide') {
    if (parsed.data.title !== page.title) fail(`${page.path}: title does not match catalog`)
    if (parsed.data.version !== catalog.site.javadoc.version) fail(`${page.path}: missing version`)
    if (!Array.isArray(parsed.data.sources) || !parsed.data.sources.length) fail(`${page.path}: missing source anchors`)
    if (!Array.isArray(parsed.data.verification) || !parsed.data.verification.length) fail(`${page.path}: missing verification anchors`)
    for (const source of [...(parsed.data.sources ?? []), ...(parsed.data.verification ?? [])]) if (!existsSync(resolve(repository, source))) fail(`${page.path}: missing evidence source ${source}`)
  }
  if (tokens.filter(token => token.type === 'heading_open' && token.tag === 'h1').length !== 1) fail(`${page.path}: expected one H1`)
  for (const token of tokens) for (const child of token.children ?? []) {
    if (child.type !== 'link_open' && child.type !== 'image') continue
    const href = child.attrGet(child.type === 'image' ? 'src' : 'href')
    if (!href || /^(https?:|mailto:|data:)/.test(href)) continue
    const [path, anchor] = decodeURIComponent(href).split('#')
    let target = path ? resolve(path.startsWith('/') ? root : dirname(file), path.replace(/^\//, '').split('?')[0]) : file
    if (!target.startsWith(repository + '/')) { fail(`${page.path}: link escapes repository ${href}`); continue }
    if (!existsSync(target) && target.startsWith(root + '/reference/javadoc/')) target = resolve(root, 'public', relative(root, target))
    if (!existsSync(target)) { fail(`${page.path}: missing local link ${href}`); continue }
    if (anchor && target.endsWith('.md') && !headings(target).has(anchor)) fail(`${page.path}: missing anchor ${href}`)
  }
}
const actualTranslatedPaths = existsSync(resolve(root, 'zh')) ? readdirSync(resolve(root, 'zh'), { recursive: true }).filter(path => path.endsWith('.md')).map(path => 'zh/' + path).sort() : []
const declaredTranslatedPaths = (catalog.translations ?? []).map(page => page.path).sort()
if (JSON.stringify(actualTranslatedPaths) !== JSON.stringify(declaredTranslatedPaths)) fail('Chinese Markdown manifest differs from translation catalog; unindexed placeholders are not allowed')
for (const section of catalog.sections) for (const path of [...section.sourceAnchors, ...section.verificationAnchors]) if (!existsSync(resolve(repository, path))) fail(`Missing section evidence ${path}`)
if (errors.length) throw new Error(errors.join('\n'))
console.log(checkTranslations(catalog, path => readFileSync(resolve(root, path), 'utf8')))
console.log(`Documentation catalog: ${catalog.pages.length} entries; ${catalog.pages.filter(page => existsSync(resolve(root, page.path))).length} available pages; local links and evidence checked`)
