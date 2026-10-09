import { execFileSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, writeFileSync, renameSync, rmSync, mkdtempSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createHash } from 'node:crypto'
import { parseArgs } from 'node:util'
import assert from 'node:assert/strict'
import { resolveRelease } from './versions.mjs'
import { rehearsal } from './processes.mjs'

export function verifyRemoteRelease(repository, release) {
  const ref = 'refs/tags/' + release.tag
  const lines = execFileSync('git', ['ls-remote', '--tags', 'origin', ref, ref + '^{}'], { cwd: repository, encoding: 'utf8', timeout: 60000, stdio: ['ignore', 'pipe', 'pipe'] }).trim().split('\n').filter(Boolean).map(line => line.split(/\s+/))
  const remote = lines.find(([, name]) => name === ref + '^{}') ?? lines.find(([, name]) => name === ref)
  assert.equal(remote?.[0], release.commit, 'Release tag is absent from origin or resolves to a different commit')
}
async function main() {
  const { values } = parseArgs({ options: { tag: { type: 'string' }, output: { type: 'string' } } })
  assert.ok(values.tag && values.output, 'Use --tag ACTUAL_RELEASE_TAG --output NEW_DIRECTORY')
  const repository = fileURLToPath(new URL('../../..', import.meta.url))
  const release = resolveRelease(repository, values.tag)
  verifyRemoteRelease(repository, release)
  const output = resolve(values.output)
  assert.ok(!existsSync(output), 'Snapshot output already exists')
  const check = rehearsal('inertia-docs-snapshot')
  let error, stage
  try {
    const archiveTool = fileURLToPath(new URL('site-archive.py', import.meta.url))
    const source = resolve(check.output, 'source')
    execFileSync('git', ['archive', '--format=tar', '--output=' + resolve(check.output, 'source.tar'), release.commit], { cwd: repository })
    execFileSync('python3', [archiveTool, 'extract-source', '--input', resolve(check.output, 'source.tar'), '--output', source])
    const java = resolve(source, 'inertia-java'), docs = resolve(java, 'docs')
    assert.ok(existsSync(resolve(docs, 'scripts/versions.mjs')), 'Release tag predates versioned documentation tooling')
    const env = { INERTIA_DOCS_BASE: release.base, INERTIA_DOCS_SNAPSHOT: JSON.stringify(release), INERTIA_DOCS_BASE_COMMIT: '', INERTIA_DOCS_PREVIOUS_REGISTRY: '' }
    await check.run(resolve(java, 'mvnw'), ['--batch-mode', '-DskipTests', 'package'], java, 'matching-classifiers')
    await check.run('npm', ['ci'], docs, 'locked-documentation-tools')
    if (process.env.INERTIA_BROWSER_CHANNEL === 'chromium') await check.run('npx', ['--no-install', 'playwright', 'install', ...(process.env.GITHUB_ACTIONS === 'true' ? ['--with-deps'] : []), '--no-shell', 'chromium'], docs, 'tagged-browser')
    for (const phase of ['check', 'build', 'smoke']) await check.run('npm', ['run', 'docs:' + phase], docs, phase, env)
    mkdirSync(dirname(output), { recursive: true })
    stage = mkdtempSync(resolve(dirname(output), '.inertia-docs-bundle-'))
    const metadata = resolve(check.output, 'metadata.json'); writeFileSync(metadata, JSON.stringify(release))
    const name = 'inertia-java-docs-' + release.version + '.tar.gz'
    execFileSync('python3', [archiveTool, 'pack', '--input', resolve(docs, '.vitepress/dist'), '--output', resolve(stage, name), '--metadata', metadata])
    const archiveSha256 = createHash('sha256').update(readFileSync(resolve(stage, name))).digest('hex')
    const entry = { version: release.version, tag: release.tag, commit: release.commit, archiveSha256 }
    writeFileSync(resolve(stage, 'registry-entry.json'), JSON.stringify(entry, null, 2) + '\n')
    assert.ok(!existsSync(output), 'Snapshot output appeared during build')
    renameSync(stage, output); stage = undefined
    check.evidence.release = release; check.evidence.archiveSha256 = archiveSha256; check.evidence.bundle = output; check.evidence.success = true
  } catch (failure) { error = failure }
  finally {
    // Remove only this run's exported source and dependency/build trees, retaining bounded logs/receipt.
    await check.finish(error)
    try {
      rmSync(resolve(check.output, 'source'), { recursive: true, force: true })
      rmSync(resolve(check.output, 'source.tar'), { force: true })
      if (stage) rmSync(stage, { recursive: true, force: true })
    } catch (cleanupError) {
      check.evidence.success = false; check.evidence.cleanupErrors = [...(check.evidence.cleanupErrors ?? []), String(cleanupError)]
      writeFileSync(resolve(check.output, 'summary.json'), JSON.stringify(check.evidence, null, 2) + '\n')
      throw cleanupError
    }
  }
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main().catch(error => { console.error(error.message); process.exitCode = 1 })
