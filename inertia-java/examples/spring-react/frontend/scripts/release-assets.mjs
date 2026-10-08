import { createHash, randomUUID } from 'node:crypto'
import { readFileSync, writeFileSync, readdirSync, mkdirSync, existsSync, renameSync, rmSync, lstatSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const hash = bytes => createHash('sha256').update(bytes).digest('hex')
const safe = path => path.split('/').every(segment => segment && segment !== '.' && segment !== '..') && /^[A-Za-z0-9_./-]+$/.test(path)
function inventory(root, prefix = '') {
  const files = {}
  for (const entry of readdirSync(resolve(root, prefix), { withFileTypes: true })) {
    const path = prefix ? prefix + '/' + entry.name : entry.name
    if (entry.isDirectory()) Object.assign(files, inventory(root, path))
    else if (entry.isFile()) files[path] = hash(readFileSync(resolve(root, path)))
    else throw new Error('Unsupported published asset: ' + path)
  }
  return files
}
function compare(expected, actual) {
  if (Object.keys(expected).length !== Object.keys(actual).length
      || Object.entries(expected).some(([path, digest]) => actual[path] !== digest)) throw new Error('Published asset inventory mismatch')
}
export function publishAssets(dist, store) {
  const record = JSON.parse(readFileSync(resolve(dist, 'build.json'), 'utf8'))
  if (record.format !== 1 || !record.files || typeof record.files !== 'object' || Array.isArray(record.files)) throw new Error('Invalid build receipt')
  const sorted = Object.fromEntries(Object.entries(record.files).sort(([a], [b]) => a < b ? -1 : 1))
  if (record.buildId !== hash(JSON.stringify({ format: 1, files: sorted }))) throw new Error('Build id mismatch')
  const expected = {}
  for (const [path, digest] of Object.entries(sorted)) {
    if (!safe(path) || !/^[a-f0-9]{64}$/.test(digest) || !(path.startsWith('client/') || path.startsWith('ssr/'))) throw new Error('Invalid build file')
    if (path.startsWith('client/')) expected[path.slice(7)] = digest
  }
  if (!expected['.vite/manifest.json']) throw new Error('Missing client manifest')
  compare(expected, inventory(resolve(dist, 'client')))
  mkdirSync(store, { recursive: true })
  const release = resolve(store, record.buildId)
  if (existsSync(release)) {
    if (lstatSync(release).isSymbolicLink()) throw new Error('Published release must not be a symlink')
    compare(expected, inventory(release))
    return release
  }
  const staging = resolve(store, '.staging-' + randomUUID())
  mkdirSync(staging)
  try {
    for (const path of Object.keys(expected)) {
      const target = resolve(staging, path)
      mkdirSync(dirname(target), { recursive: true })
      writeFileSync(target, readFileSync(resolve(dist, 'client', path)))
    }
    compare(expected, inventory(staging))
    // Publish only a complete directory. Concurrent publishers must never overwrite a release.
    try { renameSync(staging, release) }
    catch (error) {
      if (!existsSync(release)) throw error
      compare(expected, inventory(release))
    }
    return release
  } finally { rmSync(staging, { recursive: true, force: true }) }
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const frontend = fileURLToPath(new URL('..', import.meta.url))
  console.log(publishAssets(resolve(frontend, 'dist'), resolve(process.argv[2] ?? resolve(frontend, '.inertia/assets'))))
}
