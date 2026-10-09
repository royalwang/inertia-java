import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { resolve } from 'node:path'
import { execFileSync } from 'node:child_process'
import { verifyRemoteRelease } from './release-snapshot.mjs'
import { validateRegistry, siteContext, resolveRelease, validBase } from './versions.mjs'

// Synthetic identities are used only in isolated fixtures, never the published registry.
const entry = { version: '2.3.4', tag: 'java-v2.3.4', commit: 'a'.repeat(40), archiveSha256: 'b'.repeat(64) }
test('registry rejects snapshots, duplicates and malformed identities', () => {
  const registry = { format: 1, releases: [entry] }
  assert.equal(validateRegistry(registry), registry)
  for (const release of [ { ...entry, version: '2.3.4-SNAPSHOT' }, { ...entry, tag: '../main' }, { ...entry, commit: 'main' }, { ...entry, archiveSha256: 'missing' } ]) {
    assert.throws(() => validateRegistry({ format: 1, releases: [release] }))
  }
  assert.throws(() => validateRegistry({ format: 1, releases: [entry, entry] }), /Duplicate/)
  assert.equal(validBase('/inertia-java/versions/2.3.4/'), '/inertia-java/versions/2.3.4/')
  for (const base of ['/../', '//evil.example/', '/a/../../', '/a?x/']) assert.throws(() => validBase(base))
})
test('published mappings are append-only', () => {
  const previous = { format: 1, releases: [entry] }
  validateRegistry({ format: 1, releases: [entry, { ...entry, version: '2.3.5', tag: 'java-v2.3.5' }] }, previous)
  assert.throws(() => validateRegistry({ format: 1, releases: [] }, previous), /cannot be removed/)
  assert.throws(() => validateRegistry({ format: 1, releases: [{ ...entry, archiveSha256: 'c'.repeat(64) }] }, previous), /cannot be removed/)
})
test('snapshot navigation uses the site root and immutable source commit', () => {
  const root = mkdtempSync(resolve(tmpdir(), 'inertia-version-context-'))
  try {
    writeFileSync(resolve(root, 'versions.json'), JSON.stringify({ format: 1, releases: [] }))
    const catalog = { site: { defaultBase: '/inertia-java/', javadoc: { version: '2.3.4' } } }
    const snapshot = { version: entry.version, tag: entry.tag, commit: entry.commit }
    const env = { INERTIA_DOCS_BASE: '/inertia-java/versions/2.3.4/', INERTIA_DOCS_SNAPSHOT: JSON.stringify(snapshot) }
    const context = siteContext(root, catalog, env)
    assert.equal(context.sourceRef, entry.commit)
    assert.deepEqual(context.versionMenu.entries, [{ label: 'next', path: '/inertia-java/' }, { label: '2.3.4', path: '/inertia-java/versions/2.3.4/' }])
    assert.throws(() => siteContext(root, catalog, { ...env, INERTIA_DOCS_BASE: '/inertia-java/' }), /does not match/)
    assert.throws(() => siteContext(root, catalog, { ...env, INERTIA_DOCS_SNAPSHOT: JSON.stringify({ ...snapshot, version: '2.3.5' }) }), /differ/)
  } finally { rmSync(root, { recursive: true, force: true }) }
})
test('release identity comes from an actual tag, not a branch or current working files', () => {
  const root = mkdtempSync(resolve(tmpdir(), 'inertia-release-ref-'))
  const git = (...args) => execFileSync('git', args, { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim()
  const pom = version => `<project xmlns="http://maven.apache.org/POM/4.0.0"><version>${version}</version></project>`
  const catalog = version => JSON.stringify({ site: { defaultBase: '/inertia-java/', javadoc: { version } } })
  try {
    git('init', '--initial-branch=main')
    for (const path of ['inertia-java/docs', 'docs/inertia-java']) mkdirSync(resolve(root, path), { recursive: true })
    writeFileSync(resolve(root, 'inertia-java/pom.xml'), pom('2.3.4'))
    writeFileSync(resolve(root, 'inertia-java/docs/versions.json'), '{"format":1,"releases":[]}')
    writeFileSync(resolve(root, 'docs/inertia-java/open-source-docs-catalog.json'), catalog('2.3.4'))
    git('add', '.'); git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-m', 'Synthetic release metadata fixture')
    const commit = git('rev-parse', 'HEAD'); git('tag', 'java-v2.3.4'); git('branch', 'branch-only')
    // A local origin exercises exact remote refs without creating any public tags.
    git('remote', 'add', 'origin', root)
    verifyRemoteRelease(root, resolveRelease(root, 'java-v2.3.4'))
    git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'tag', '-a', 'annotated-v2.3.4', '-m', 'Synthetic annotated tag', commit)
    verifyRemoteRelease(root, resolveRelease(root, 'annotated-v2.3.4'))
    assert.throws(() => verifyRemoteRelease(root, { tag: 'missing-tag', commit }), /absent from origin/)
    assert.throws(() => verifyRemoteRelease(root, { tag: 'java-v2.3.4', commit: 'b'.repeat(40) }), /different commit/)
    writeFileSync(resolve(root, 'inertia-java/pom.xml'), pom('9.9.9-SNAPSHOT'))
    assert.equal(resolveRelease(root, 'java-v2.3.4').version, '2.3.4')
    assert.equal(resolveRelease(root, 'java-v2.3.4').commit, commit)
    assert.throws(() => resolveRelease(root, 'branch-only'))
    assert.throws(() => resolveRelease(root, 'missing-tag'))
    writeFileSync(resolve(root, 'inertia-java/pom.xml'), pom('2.3.5-SNAPSHOT'))
    writeFileSync(resolve(root, 'docs/inertia-java/open-source-docs-catalog.json'), catalog('2.3.5-SNAPSHOT'))
    git('add', '.'); git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-m', 'Synthetic snapshot fixture'); git('tag', 'snapshot-tag')
    assert.throws(() => resolveRelease(root, 'snapshot-tag'), /stable Maven version/)
  } finally { rmSync(root, { recursive: true, force: true }) }
})
