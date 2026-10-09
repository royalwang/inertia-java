import { LinkChecker } from 'linkinator'
import MarkdownIt from 'markdown-it'
import matter from 'gray-matter'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { resolve, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'
import { rehearsal } from './processes.mjs'

const docs = fileURLToPath(new URL('..', import.meta.url))
const repository = resolve(docs, '../..')
const md = new MarkdownIt({ html: true })

export function destinationKind(href, root = repository) {
  const url = new URL(href)
  assert.ok(['https:', 'http:'].includes(url.protocol) && !url.username && !url.password, 'Invalid external URL')
  const host = url.hostname.toLowerCase()
  if (host === 'localhost' || host === '[::1]' || /^(127\.|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(host)
      || !host.includes('.') || /(^|\.)(example\.(com|org|net)|invalid|test|local)$/.test(host)) return { kind: 'example-or-local-address' }
  if (host === 'github.com' && url.pathname.startsWith('/royalwang/inertia-java/')) {
    const match = url.pathname.match(/^\/royalwang\/inertia-java\/(?:blob|tree)\/main\/(.+)$/)
    if (match) {
      const path = resolve(root, decodeURIComponent(match[1]))
      assert.ok(path.startsWith(root + '/'), 'Repository URL escapes source tree')
      assert.ok(existsSync(path), 'Repository URL has no local target: ' + href)
      return { kind: 'repository-source-local-only', path: relative(root, path) }
    }
  }
  return { kind: 'external-http' }
}

export function collectLinks(files, root = repository) {
  const links = new Map()
  for (const file of files) for (const token of md.parse(matter(readFileSync(file, 'utf8')).content, {})) {
    for (const child of token.children ?? []) {
      if (!['link_open', 'image'].includes(child.type)) continue
      const href = child.attrGet(child.type === 'image' ? 'src' : 'href')
      if (!/^https?:\/\//i.test(href ?? '')) continue
      const url = new URL(href), original = url.href
      url.hash = ''
      const classification = destinationKind(url.href, root)
      const entry = links.get(url.href) ?? { url: url.href, ...classification, references: [] }
      entry.references.push({ file: relative(root, file), href: original })
      links.set(url.href, entry)
    }
  }
  return [...links.values()].sort((a, b) => a.url.localeCompare(b.url))
}

export function classify(result) {
  if (result.status === 404 || result.status === 410) return 'not-found'
  if (result.state === 'OK' && result.status >= 200 && result.status < 400) return 'reachable'
  if (!result.status || result.status === 408 || result.status === 429 || result.status >= 500 && result.status < 600) return 'transient-or-network-unverified'
  return 'access-or-response-unverified'
}

export async function checkDestinations(output, urls, timeout = 5000) {
  const html = '<!doctype html><ul>' + urls.map(url => '<li><a href="' + md.utils.escapeHtml(url) + '">reference</a></li>').join('') + '</ul>'
  writeFileSync(resolve(output, 'links.html'), html)
  const allowed = new Set(urls)
  const result = await new LinkChecker().check({ path: 'links.html', serverRoot: output, recurse: false,
    concurrency: 8, timeout, retry: false, retryErrors: false, checkFragments: false,
    linksToSkip: async href => {
      const url = new URL(href)
      return !allowed.has(href) && !(url.hostname === 'localhost' && url.pathname === '/links.html')
    } })
  const checked = result.links.filter(link => allowed.has(link.url)).map(link => ({ url: link.url, status: link.status ?? 0,
    state: link.state, classification: classify(link),
    failures: (link.failureDetails ?? []).filter(error => error instanceof Error).map(error => ({ name: error.name, message: error.message.slice(0, 300) })) }))
  for (const url of allowed) assert.ok(checked.some(link => link.url === url), 'Checker omitted destination: ' + url)
  return checked
}

async function main() {
  if (process.argv[2] === '--worker') {
    const output = resolve(process.argv[3])
    const urls = JSON.parse(readFileSync(resolve(output, 'seeds.json')))
    writeFileSync(resolve(output, 'http-results.json'), JSON.stringify(await checkDestinations(output, urls), null, 2) + '\n')
    return
  }
  assert.equal(process.argv.length, 2, 'Use npm run docs:links; output is selected by INERTIA_DOCS_OUTPUT')
  const catalog = JSON.parse(readFileSync(resolve(repository, 'docs/inertia-java/open-source-docs-catalog.json')))
  const files = [...catalog.pages, ...(catalog.translations ?? [])].filter(page => page.status !== 'planned').map(page => resolve(docs, page.path))
  files.push(resolve(docs, 'README.md'), resolve(docs, '../README.md'), resolve(docs, '../CONTRIBUTING.md'), resolve(docs, '../SECURITY.md'))
  const check = rehearsal('inertia-docs-external-links')
  let error
  try {
    const destinations = collectLinks(files)
    writeFileSync(resolve(check.output, 'destinations.json'), JSON.stringify(destinations, null, 2) + '\n')
    const urls = destinations.filter(link => link.kind === 'external-http').map(link => link.url)
    writeFileSync(resolve(check.output, 'seeds.json'), JSON.stringify(urls))
    await check.run(process.execPath, [fileURLToPath(import.meta.url), '--worker', check.output], docs, 'linkinator')
    const results = JSON.parse(readFileSync(resolve(check.output, 'http-results.json')))
    check.evidence.documents = files.length
    check.evidence.destinations = destinations.length
    check.evidence.externalHttp = urls.length
    check.evidence.classifications = Object.fromEntries([...new Set(results.map(link => link.classification))].map(kind => [kind, results.filter(link => link.classification === kind).length]))
    check.evidence.localOrSourceReferences = destinations.filter(link => link.kind !== 'external-http').length
    check.evidence.allExternalHttpVerified = results.every(link => link.classification === 'reachable')
    check.evidence.limitations = ['Reachability checks HTTP destinations only, not remote fragments or semantic content.',
      'Local/example URLs are not contacted; main source URLs are checked against local paths only, not attested as published.',
      'Access restrictions, rate limits and network/server failures remain unverified; collection success is not universal reachability.']
    assert.ok(!results.some(link => link.classification === 'not-found'), 'HTTP 404/410 references found; inspect destinations.json and http-results.json')
    check.evidence.success = true
  } catch (failure) { error = failure }
  finally {
    await check.finish(error)
  }
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main().catch(error => { console.error(error.message); process.exitCode = 1 })
