import { createHash, randomUUID } from 'node:crypto'
import { readFileSync, writeFileSync, readdirSync, mkdirSync, lstatSync, existsSync, renameSync, rmSync, copyFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { publishAssets } from '../examples/spring-react/frontend/scripts/release-assets.mjs'

const projectRoot = fileURLToPath(new URL('..', import.meta.url))
const hash = bytes => createHash('sha256').update(bytes).digest('hex')
const safe = path => /^[A-Za-z0-9_./@-]+$/.test(path) && path.split('/').every(p => p && p !== '.' && p !== '..')
const sorted = files => Object.fromEntries(Object.entries(files).sort(([a], [b]) => a < b ? -1 : 1))
function inventory(root, prefix = '', ignoreDependencies = false) {
  const files = {}
  for (const entry of readdirSync(resolve(root, prefix), { withFileTypes: true })) {
    const path = prefix ? prefix + '/' + entry.name : entry.name
    if (ignoreDependencies && path === 'frontend/node_modules') continue
    if (!safe(path)) throw new Error('Invalid artifact path: ' + path)
    if (entry.isDirectory()) Object.assign(files, inventory(root, path, ignoreDependencies))
    else if (entry.isFile()) files[path] = hash(readFileSync(resolve(root, path)))
    else throw new Error('Artifact symlink/special file: ' + path)
  }
  return sorted(files)
}
function compare(expected, actual) {
  if (JSON.stringify(sorted(expected)) !== JSON.stringify(sorted(actual))) throw new Error('Artifact inventory mismatch')
}
function copy(source, dest) { mkdirSync(dirname(dest), { recursive: true }); copyFileSync(source, dest) }
function copyTree(source, dest) {
  const files = inventory(source)
  for (const path of Object.keys(files)) copy(resolve(source, path), resolve(dest, path))
  compare(files, inventory(dest))
}
export function packageRelease(store, root = projectRoot) {
  const frontend = resolve(root, 'examples/spring-react/frontend')
  const version = readFileSync(resolve(root, 'pom.xml'), 'utf8').match(/<version>([^<]+)<\/version>/)?.[1]
  if (!version || !/^[A-Za-z0-9_.-]+$/.test(version)) throw new Error('Invalid Maven version')
  const receipt = JSON.parse(readFileSync(resolve(frontend, 'dist/build.json'), 'utf8'))
  if (receipt.format !== 1 || !receipt.files || Array.isArray(receipt.files) || !/^[a-f0-9]{64}$/.test(receipt.buildId)) throw new Error('Invalid frontend receipt')
  if (receipt.buildId !== hash(JSON.stringify({ format: 1, files: sorted(receipt.files) }))) throw new Error('Frontend build ID mismatch')
  compare({ ...receipt.files, 'build.json': hash(readFileSync(resolve(frontend, 'dist/build.json'))) }, inventory(resolve(frontend, 'dist')))
  mkdirSync(store, { recursive: true })
  const staging = resolve(store, '.staging-' + randomUUID())
  mkdirSync(staging)
  try {
    copy(resolve(root, `examples/spring-react/target/spring-react-${version}.jar`), resolve(staging, 'app.jar'))
    copyTree(resolve(frontend, 'dist'), resolve(staging, 'frontend/dist'))
    for (const name of ['package.json', 'package-lock.json']) copy(resolve(frontend, name), resolve(staging, 'frontend', name))
    copy(resolve(root, 'deploy/runtime.mjs'), resolve(staging, 'runtime.mjs'))
    copy(resolve(root, 'README.md'), resolve(staging, 'README.md'))
    copyTree(resolve(root, 'docs'), resolve(staging, 'docs'))
    copy(resolve(root, 'deploy/README.md'), resolve(staging, 'RUNBOOK.md'))
    copyTree(resolve(root, 'deploy/systemd'), resolve(staging, 'operations/systemd'))
    publishAssets(resolve(staging, 'frontend/dist'), resolve(staging, 'assets'))
    const modules = ['inertia-core', 'inertia-ssr-http', 'inertia-vite', 'inertia-spring-webmvc', 'inertia-spring-boot-autoconfigure', 'inertia-spring-boot-starter', 'inertia-testing']
    copy(resolve(root, 'pom.xml'), resolve(staging, `maven/io/inertia/inertia-java/${version}/inertia-java-${version}.pom`))
    for (const module of modules) {
      const target = resolve(staging, `maven/io/inertia/${module}/${version}`)
      for (const classifier of ["", "-sources", "-javadoc"])
        copy(resolve(root, module, `target/${module}-${version}${classifier}.jar`), resolve(target, `${module}-${version}${classifier}.jar`))
      copy(resolve(root, module, 'pom.xml'), resolve(target, `${module}-${version}.pom`))
    }
    const canonical = { format: 1, version, buildId: receipt.buildId, files: inventory(staging) }
    const releaseId = hash(JSON.stringify(canonical))
    writeFileSync(resolve(staging, 'release.json'), JSON.stringify({ ...canonical, releaseId }, null, 2) + '\n')
    const target = resolve(store, releaseId)
    if (existsSync(target)) {
      if (lstatSync(target).isSymbolicLink()) throw new Error('Existing release is a symlink')
      compare(inventory(staging), inventory(target, '', true))
    } else {
      try { renameSync(staging, target) }
      catch (error) {
        if (!existsSync(target) || lstatSync(target).isSymbolicLink()) throw error
        compare(inventory(staging), inventory(target, '', true))
      }
    }
    return target
  } finally { rmSync(staging, { recursive: true, force: true }) }
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (process.argv.length !== 3) throw new Error('Usage: node deploy/release.mjs /absolute/release-store (build Maven/frontend first)')
  console.log(packageRelease(resolve(process.argv[2])))
}
