import { readFileSync, writeFileSync, mkdtempSync, rmSync, realpathSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { tmpdir } from 'node:os'
import { parseArgs } from 'node:util'
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import assert from 'node:assert/strict'
import { archiveUrl, readRegistry, validateRegistry, resolveRelease } from './versions.mjs'
import { verifyRemoteRelease } from './release-snapshot.mjs'

export async function downloadArchive(entry) {
  const response = await fetch(archiveUrl(entry.version, entry.tag), { signal: AbortSignal.timeout(60000) })
  assert.ok(response.ok && new URL(response.url).protocol === 'https:', 'Release archive download failed')
  const reader = response.body.getReader(), chunks = []
  let size = 0
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      size += value.length
      assert.ok(size <= 64 * 1024 * 1024, 'Compressed archive exceeds limit')
      chunks.push(value)
    }
  } finally { await reader.cancel() }
  const data = Buffer.concat(chunks)
  assert.equal(createHash('sha256').update(data).digest('hex'), entry.archiveSha256, 'Release archive checksum mismatch')
  return data
}
async function main() {
  const { values } = parseArgs({ options: { register: { type: 'string' }, assemble: { type: 'string' } } })
  assert.ok(Boolean(values.register) !== Boolean(values.assemble), 'Choose --register RECEIPT or --assemble SITE_DIST')
  const docs = fileURLToPath(new URL('..', import.meta.url)), repository = resolve(docs, '../..')
  const catalog = JSON.parse(readFileSync(resolve(repository, 'docs/inertia-java/open-source-docs-catalog.json')))
  const previous = readRegistry(docs)
  const entries = values.register ? [JSON.parse(readFileSync(resolve(values.register)))] : previous.releases
  validateRegistry({ format: 1, releases: entries })
  const distribution = values.assemble ? realpathSync(resolve(values.assemble)) : null
  if (distribution) assert.ok(readFileSync(resolve(distribution, 'index.html')).length, 'Build the documentation site before assembly')
  const temporary = mkdtempSync(resolve(tmpdir(), 'inertia-version-artifacts-'))
  try {
    for (const entry of entries) {
      if (values.register) {
        const release = resolveRelease(repository, entry.tag)
        assert.equal(release.version, entry.version); assert.equal(release.commit, entry.commit)
        verifyRemoteRelease(repository, release)
      }
      const archive = resolve(temporary, entry.version + '.tar.gz')
      writeFileSync(archive, await downloadArchive(entry))
      const meta = { format: 1, ...entry, siteBase: catalog.site.defaultBase, base: catalog.site.defaultBase + 'versions/' + entry.version + '/' }
      const metadata = resolve(temporary, entry.version + '.json'); writeFileSync(metadata, JSON.stringify(meta))
      const destination = values.assemble ? resolve(distribution, 'versions', entry.version) : resolve(temporary, 'verified', entry.version)
      execFileSync('python3', [resolve(docs, 'scripts/site-archive.py'), 'install', '--input', archive, '--output', destination, '--metadata', metadata])
    }
    if (values.register) {
      const entry = entries[0], current = previous.releases.find(item => item.version === entry.version)
      if (current) assert.deepEqual(current, entry, 'Published snapshot cannot be rewritten')
      const next = { format: 1, releases: current ? previous.releases : [...previous.releases, entry] }
      validateRegistry(next, previous)
      writeFileSync(resolve(docs, 'versions.json'), JSON.stringify(next, null, 2) + '\n')
      console.log('Verified public archive and registered ' + entry.version + '; review and commit the mapping')
    } else console.log('Assembled ' + entries.length + ' checksum-verified immutable release snapshots')
  } finally { rmSync(temporary, { recursive: true, force: true }) }
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main().catch(error => { console.error(error.message); process.exitCode = 1 })
