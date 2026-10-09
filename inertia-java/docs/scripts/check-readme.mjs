import { readFileSync, existsSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createHash } from 'node:crypto'
import assert from 'node:assert/strict'
import MarkdownIt from 'markdown-it'
import GithubSlugger from 'github-slugger'
import matter from 'gray-matter'

const root = resolve(fileURLToPath(new URL('../../..', import.meta.url)))
const java = resolve(root, 'inertia-java'), file = resolve(java, 'README.md')
const mapping = JSON.parse(readFileSync(resolve(root, 'docs/inertia-java/readme-topic-migration.json')))
const catalog = JSON.parse(readFileSync(resolve(root, 'docs/inertia-java/open-source-docs-catalog.json')))
const md = new MarkdownIt({ html: true })
const headings = text => {
  const slugger = new GithubSlugger(), result = [], tokens = md.parse(matter(text).content, {})
  for (let i = 0; i < tokens.length; i++) if (tokens[i].type === 'heading_open') {
    const heading = tokens[i + 1].content.replace(/`/g, ''), level = Number(tokens[i].tag.slice(1)), anchor = slugger.slug(heading)
    result.push({ heading, level, anchor })
  }
  return result
}
const snapshot = resolve(root, mapping.sourceSnapshot.path)
assert.ok(snapshot.startsWith(resolve(root, 'docs/inertia-java/verification') + '/'))
const original = readFileSync(snapshot)
assert.equal(createHash('sha256').update(original).digest('hex'), mapping.sourceSnapshot.sha256)
assert.deepEqual(mapping.topics.map(({ heading, level, anchor }) => ({ heading, level, anchor })), headings(original.toString()).filter(item => item.level > 1))
for (const link of mapping.upstreamReferences ?? []) assert.ok(readFileSync(resolve(root, link.target), 'utf8').includes(link.href), 'Lost upstream reference: ' + link.href)
for (const link of mapping.linkCorrections ?? []) assert.ok(readFileSync(resolve(root, link.destination), 'utf8').includes(link.current), 'Lost corrected reference: ' + link.current)
const current = readFileSync(file, 'utf8'), actual = headings(current)
for (const entry of mapping.topics) {
  assert.ok(actual.some(item => item.heading === entry.heading && item.level === entry.level && item.anchor === entry.anchor), 'Lost README anchor: ' + entry.anchor)
  assert.ok(entry.destinations.length)
  for (const target of entry.destinations) {
    assert.ok(target.startsWith('inertia-java/docs/') && existsSync(resolve(root, target)), 'Missing topic destination: ' + target)
    if (!target.endsWith('/docs/README.md')) assert.ok(catalog.pages.some(page => target === 'inertia-java/docs/' + page.path && page.status !== 'planned'), 'Topic target is not a written canonical page')
    assert.ok(current.includes('](' + target.slice('inertia-java/'.length) + ')'), 'README does not link to topic destination: ' + target)
  }
}
for (const token of md.parse(current, {})) for (const child of token.children ?? []) {
  if (child.type !== 'link_open') continue
  const href = child.attrGet('href')
  if (/^(https?:|mailto:)/.test(href)) continue
  const [path, anchor] = decodeURIComponent(href).split('#')
  const target = resolve(dirname(file), path || 'README.md')
  assert.ok(target.startsWith(root + '/') && existsSync(target), 'Broken README target: ' + href)
  if (anchor && target.endsWith('.md')) assert.ok(headings(readFileSync(target, 'utf8')).some(item => item.anchor === anchor), 'Broken README fragment: ' + href)
}
console.log(`README migration: ${mapping.topics.length} original headings/anchors retained; all topic destinations written; historical bytes verified`)
