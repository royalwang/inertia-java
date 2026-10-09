import { copyFileSync, mkdirSync, readdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'

// The same source selection is used for publication and frozen verification inputs.
// Root tooling is private; nested example manifests/scripts are reader-facing source.
const privateRoots = new Set(['node_modules', '.vitepress', 'public', 'scripts', 'package.json', 'package-lock.json', '.markdownlint-cli2.jsonc', 'versions.json'])
const generatedDirectories = new Set(['node_modules', '.vitepress', 'target', '.inertia', 'dist'])
export function documentationFiles(root, prefix = '') {
  const files = []
  for (const entry of readdirSync(resolve(root, prefix), { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name, 'en'))) {
    if ((!prefix && privateRoots.has(entry.name)) || generatedDirectories.has(entry.name)) continue
    const path = prefix ? `${prefix}/${entry.name}` : entry.name
    if (!/^[A-Za-z0-9_./@-]+$/.test(path)) throw new Error('Invalid documentation path: ' + path)
    if (entry.isDirectory()) files.push(...documentationFiles(root, path))
    else if (entry.isFile()) files.push(path)
    else throw new Error('Documentation symlink/special file: ' + path)
  }
  return files
}
export function copyDocumentation(source, destination) {
  const files = documentationFiles(source)
  for (const path of files) {
    const target = resolve(destination, path)
    mkdirSync(dirname(target), { recursive: true })
    copyFileSync(resolve(source, path), target)
  }
  return files
}
