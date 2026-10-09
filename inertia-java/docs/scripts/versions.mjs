import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { resolve } from 'node:path'

export const releaseVersion = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/
export const commitId = /^[a-f0-9]{40}$/
export function validBase(base) {
  assert.match(base, /^\/(?:[A-Za-z0-9_-][A-Za-z0-9_.-]*\/)*$/, 'Unsafe documentation base')
  return base
}
export function archiveUrl(version, tag) {
  return `https://github.com/royalwang/inertia-java/releases/download/${encodeURIComponent(tag)}/inertia-java-docs-${version}.tar.gz`
}
export function validateTag(tag) {
  assert.equal(typeof tag, 'string')
  assert.match(tag, /^[A-Za-z0-9][A-Za-z0-9._/-]*$/, 'Unsafe tag')
  execFileSync('git', ['check-ref-format', 'refs/tags/' + tag], { stdio: 'pipe' })
  return tag
}
export function validateRegistry(registry, previous) {
  assert.deepEqual(Object.keys(registry).sort(), ['format', 'releases'])
  assert.equal(registry.format, 1)
  assert.ok(Array.isArray(registry.releases))
  const versions = new Set(), tags = new Set()
  for (const entry of registry.releases) {
    assert.deepEqual(Object.keys(entry).sort(), ['archiveSha256', 'commit', 'tag', 'version'])
    assert.match(entry.version, releaseVersion, 'Only stable release versions can be registered')
    validateTag(entry.tag)
    assert.match(entry.commit, commitId, 'Invalid source commit')
    assert.match(entry.archiveSha256, /^[a-f0-9]{64}$/, 'Invalid archive checksum')
    assert.ok(!versions.has(entry.version), 'Duplicate release version')
    assert.ok(!tags.has(entry.tag), 'Duplicate release tag')
    versions.add(entry.version); tags.add(entry.tag)
  }
  if (previous) {
    validateRegistry(previous)
    for (const entry of previous.releases) assert.deepEqual(registry.releases.find(item => item.version === entry.version), entry, 'Published versions cannot be removed or rewritten')
  }
  return registry
}
export function readRegistry(root) {
  return validateRegistry(JSON.parse(readFileSync(resolve(root, 'versions.json'), 'utf8')))
}
export function siteContext(root, catalog, env = process.env) {
  const version = catalog.site.javadoc.version
  assert.match(version, /^[A-Za-z0-9_.-]+$/, 'Invalid documented version')
  const registry = readRegistry(root)
  const siteBase = validBase(catalog.site.defaultBase)
  const snapshot = env.INERTIA_DOCS_SNAPSHOT ? JSON.parse(env.INERTIA_DOCS_SNAPSHOT) : null
  let base = validBase(env.INERTIA_DOCS_BASE ?? siteBase)
  const entries = [{ label: 'next', path: siteBase }]
  for (const entry of registry.releases) entries.push({ label: entry.version, path: siteBase + 'versions/' + entry.version + '/' })
  if (snapshot) {
    assert.match(snapshot.version, releaseVersion, 'Snapshot must describe a stable release')
    assert.equal(snapshot.version, version, 'Snapshot and documented version differ')
    validateTag(snapshot.tag)
    assert.match(snapshot.commit, commitId)
    const expected = siteBase + 'versions/' + version + '/'
    assert.equal(base, expected, 'Snapshot base does not match its release path')
    const registered = registry.releases.find(entry => entry.version === version)
    if (registered) {
      assert.equal(registered.commit, snapshot.commit, 'Snapshot conflicts with registered source')
      assert.equal(registered.tag, snapshot.tag, 'Snapshot conflicts with registered tag')
    }
    if (!entries.some(entry => entry.path === expected)) entries.push({ label: version, path: expected })
  } else entries[0].path = base
  return { version, base, siteBase, sourceRef: snapshot?.commit ?? 'main', sourceLabel: snapshot?.tag ?? 'main', snapshot,
    versionMenu: { label: snapshot ? version : 'next / ' + version, current: base, entries } }
}
export function resolveRelease(repository, tag) {
  validateTag(tag)
  const ref = 'refs/tags/' + tag
  const commit = execFileSync('git', ['rev-parse', '--verify', ref + '^{commit}'], { cwd: repository, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim()
  assert.match(commit, commitId)
  const show = path => execFileSync('git', ['show', `${commit}:${path}`], { cwd: repository, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })
  const version = execFileSync('python3', ['-c', 'import sys,xml.etree.ElementTree as E; print(E.fromstring(sys.stdin.read()).findtext("{http://maven.apache.org/POM/4.0.0}version"))'], { input: show('inertia-java/pom.xml'), encoding: 'utf8' }).trim()
  assert.match(version, releaseVersion, 'A release snapshot requires a stable Maven version; SNAPSHOT is not a release')
  const catalog = JSON.parse(show('docs/inertia-java/open-source-docs-catalog.json'))
  assert.equal(catalog.site.javadoc.version, version, 'Tagged catalog and Maven version differ')
  const registry = validateRegistry(JSON.parse(show('inertia-java/docs/versions.json')))
  const existing = registry.releases.find(entry => entry.version === version)
  if (existing) assert.equal(existing.commit, commit, 'Release version already belongs to another source')
  return { format: 1, version, tag, commit, siteBase: validBase(catalog.site.defaultBase), base: catalog.site.defaultBase + 'versions/' + version + '/' }
}
