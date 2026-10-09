import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import matter from 'gray-matter'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt()
export const revision = text => createHash('sha256').update(text).digest('hex')
export function checkTranslations(catalog, readPage) {
  const keys = new Set(), paths = new Set()
  const originals = new Map(catalog.pages.map(page => [page.id, page]))
  const code = text => md.parse(matter(text).content, {}).filter(token => token.type === 'fence' && token.info.trim() !== 'mermaid').map(token => ({ language: token.info.trim(), content: token.content }))
  for (const entry of catalog.translations ?? []) {
    const key = entry.locale + ':' + entry.id
    assert.ok(!keys.has(key), 'Duplicate translation: ' + key)
    assert.ok(!paths.has(entry.path), 'Duplicate translated path: ' + entry.path)
    keys.add(key); paths.add(entry.path)
    assert.equal(entry.locale, 'zh-CN', 'Unsupported translation locale')
    assert.equal(entry.status, 'reviewed', 'Translation needs review: ' + key)
    const original = originals.get(entry.id)
    assert.ok(original, 'Unknown canonical page: ' + entry.id)
    assert.equal(entry.path, 'zh/' + original.path, 'Translated page must retain canonical path')
    const english = readPage(original.path), translated = readPage(entry.path)
    assert.equal(entry.sourceRevision, revision(english), 'Stale English revision: ' + key)
    const source = matter(english).data, data = matter(translated).data
    assert.equal(data.title, entry.title)
    assert.equal(data.version, source.version, 'Wrong translated library version: ' + key)
    assert.deepEqual(data.translation, { locale: entry.locale, canonicalId: entry.id, source: original.path, sourceRevision: entry.sourceRevision }, 'Wrong translation provenance: ' + key)
    assert.deepEqual(data.sources, source.sources, 'Translation source evidence differs: ' + key)
    assert.deepEqual(data.verification, source.verification, 'Translation verification evidence differs: ' + key)
    assert.deepEqual(code(translated), code(english), 'Executable fences differ from reviewed English: ' + key)
    const imports = text => [...matter(text).content.matchAll(/^<<<[ \t]*(.+)$/gm)].map(match => match[1].trim())
    assert.deepEqual(imports(translated), imports(english), 'Canonical code imports differ from reviewed English: ' + key)
    const inline = text => new Set(md.parse(matter(text).content, {}).flatMap(token => token.children ?? []).filter(token => token.type === 'code_inline').map(token => token.content))
    const translatedTokens = inline(translated)
    for (const token of inline(english)) assert.ok(translatedTokens.has(token), 'Missing canonical API/configuration token: ' + key + ' / ' + token)
    assert.match(matter(translated).content, /[\u3400-\u9fff]/, 'Translation is not Chinese: ' + key)
  }
  if (catalog.language.translationCoverage === 'all') for (const page of catalog.pages) assert.ok(keys.has('zh-CN:' + page.id), 'Missing Chinese translation: ' + page.id)
  for (const id of catalog.language.translationPriority ?? []) assert.ok(keys.has('zh-CN:' + id), 'Missing priority translation: ' + id)
  return `${keys.size} reviewed Chinese pages; exact English revisions, versions, evidence, executable fences and canonical imports checked`
}
