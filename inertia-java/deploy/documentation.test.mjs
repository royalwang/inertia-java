import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, symlinkSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { resolve, dirname } from 'node:path'
import { copyDocumentation, documentationFiles } from './documentation.mjs'

test('publication and snapshot preserve canonical sources while excluding installed tooling', () => {
  const temporary = mkdtempSync(resolve(tmpdir(), 'inertia-docs-copy-'))
  try {
    const source = resolve(temporary, 'source')
    for (const path of ['README.md', 'api-guide.md', 'examples/CoreApiExample.java', 'examples/app/frontend/package.json', 'examples/app/frontend/scripts/build.mjs', 'versions.json', 'package.json', 'package-lock.json', '.markdownlint-cli2.jsonc', 'scripts/check.mjs', '.vitepress/config.mjs', '.vitepress/dist/index.html', 'public/examples/CoreApiExample.java', 'node_modules/pkg/index.js', 'examples/app/frontend/node_modules/pkg/index.js', 'examples/app/target/output.jar']) {
      mkdirSync(dirname(resolve(source, path)), { recursive: true })
      writeFileSync(resolve(source, path), path)
    }
    const expected = ['README.md', 'api-guide.md', 'examples/CoreApiExample.java', 'examples/app/frontend/package.json', 'examples/app/frontend/scripts/build.mjs'].sort()
    const snapshot = resolve(temporary, 'snapshot')
    const release = resolve(temporary, 'release')
    assert.deepEqual(copyDocumentation(source, snapshot).sort(), expected)
    assert.deepEqual(copyDocumentation(snapshot, release).sort(), expected)
    assert.deepEqual(documentationFiles(release).sort(), expected)
    for (const path of expected) assert.deepEqual(readFileSync(resolve(source, path)), readFileSync(resolve(release, path)))
    symlinkSync(resolve(source, 'README.md'), resolve(source, 'unsafe.md'))
    assert.throws(() => documentationFiles(source), /symlink\/special/)
  } finally { rmSync(temporary, { recursive: true, force: true }) }
})
