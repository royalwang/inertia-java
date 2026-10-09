import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { resolve } from 'node:path'
import { tmpdir } from 'node:os'
import { collectLinks, destinationKind, classify, checkDestinations } from './external-links.mjs'

test('external collection preserves provenance, deduplicates fragments and separates source/demo URLs', () => {
  const root = mkdtempSync(resolve(tmpdir(), 'inertia-links-collection-'))
  try {
    mkdirSync(resolve(root, 'inertia-java'))
    writeFileSync(resolve(root, 'inertia-java/README.md'), '# Fixture')
    const file = resolve(root, 'page.md')
    writeFileSync(file, '[One](https://upstream.invalid-docs.org/page#a) [Two](https://upstream.invalid-docs.org/page#b) [Demo](http://127.0.0.1:18080/users) [Code](https://github.com/royalwang/inertia-java/blob/main/inertia-java/README.md)')
    const links = collectLinks([file], root)
    assert.equal(links.length, 3)
    assert.equal(links.find(link => link.kind === 'external-http').references.length, 2)
    assert.ok(links.some(link => link.kind === 'example-or-local-address'))
    assert.ok(links.some(link => link.kind === 'repository-source-local-only'))
    assert.throws(() => destinationKind('https://github.com/royalwang/inertia-java/blob/main/missing.md', root), /no local target/)
    assert.throws(() => destinationKind('https://user:password@upstream.invalid-docs.org/'), /Invalid external URL/)
  } finally { rmSync(root, { recursive: true, force: true }) }
})

test('statuses retain the distinction between not-found and unverified responses', () => {
  assert.equal(classify({ state: 'OK', status: 200 }), 'reachable')
  for (const status of [404, 410]) assert.equal(classify({ state: 'BROKEN', status }), 'not-found')
  for (const status of [0, 408, 429, 503]) assert.equal(classify({ state: 'BROKEN', status }), 'transient-or-network-unverified')
  for (const status of [401, 403, 999]) assert.equal(classify({ state: 'SKIPPED', status }), 'access-or-response-unverified')
})

test('actual Linkinator observes HTTP responses, redirects and a bounded stalled peer without crawling unrelated links', async () => {
  const root = mkdtempSync(resolve(tmpdir(), 'inertia-links-http-'))
  let outsideCalls = 0
  const server = createServer((request, response) => {
    if (request.url === '/stall') return
    if (request.url === '/redirect') { response.writeHead(302, { location: '/ok' }); response.end(); return }
    if (request.url === '/outside') outsideCalls++
    const status = ({ '/missing': 404, '/gone': 410, '/blocked': 403, '/unavailable': 503 })[request.url] ?? 200
    response.writeHead(status, { 'content-type': 'text/html' })
    response.end('<a href="/outside">Not part of the supplied references</a>')
  })
  try {
    await new Promise(yes => server.listen(0, '127.0.0.1', yes))
    const origin = `http://127.0.0.1:${server.address().port}`
    const urls = ['/ok', '/missing', '/gone', '/blocked', '/unavailable', '/redirect', '/stall'].map(path => origin + path)
    const result = await checkDestinations(root, urls, 300)
    assert.equal(result.length, urls.length)
    for (const path of ['/ok', '/redirect']) assert.equal(result.find(link => link.url === origin + path).classification, 'reachable')
    for (const path of ['/missing', '/gone']) assert.equal(result.find(link => link.url === origin + path).classification, 'not-found')
    assert.equal(result.find(link => link.url === origin + '/blocked').classification, 'access-or-response-unverified')
    for (const path of ['/stall', '/unavailable']) assert.equal(result.find(link => link.url === origin + path).classification, 'transient-or-network-unverified')
    assert.equal(outsideCalls, 0)
  } finally { server.closeAllConnections(); await new Promise(yes => server.close(yes)); rmSync(root, { recursive: true, force: true }) }
})
