import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync, readdirSync, rmSync, renameSync } from 'node:fs'
import { resolve } from 'node:path'
import { publishAssets } from '../spring-react/frontend/scripts/release-assets.mjs'


export function buildFrontend(root, ssrEntry) {
  const receipt = resolve(root, 'dist/build.json')
  rmSync(receipt, { force: true })
  for (const args of [['build'], ['build', '--ssr', ssrEntry]]) {
    const result = spawnSync(process.execPath, ['node_modules/vite/bin/vite.js', ...args], { cwd: root, stdio: 'inherit' })
    if (result.error) throw result.error
    if (result.status !== 0) process.exit(result.status ?? 1)
  }
  const files = {}
  const hash = bytes => createHash('sha256').update(bytes).digest('hex')
  function walk(relative) {
    for (const entry of readdirSync(resolve(root, 'dist', relative), { withFileTypes: true }).sort((a, b) => a.name < b.name ? -1 : 1)) {
      const path = relative + '/' + entry.name
      if (entry.isDirectory()) walk(path)
      else if (entry.isFile()) files[path] = hash(readFileSync(resolve(root, 'dist', path)))
      else throw new Error('Unsupported build file: ' + path)
    }
  }
  walk('client')
  walk('ssr')
  const canonical = { format: 1, files: Object.fromEntries(Object.entries(files).sort(([a], [b]) => a < b ? -1 : 1)) }
  const record = { ...canonical, buildId: hash(JSON.stringify(canonical)) }
  writeFileSync(receipt + '.tmp', JSON.stringify(record, null, 2) + '\n')
  renameSync(receipt + '.tmp', receipt)
  console.log('Verified build receipt: ' + record.buildId)

  console.log("Published client assets: " + publishAssets(resolve(root, "dist"), resolve(root, ".inertia/assets")))
}
