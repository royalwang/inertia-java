import { execFileSync } from 'node:child_process'
import { cpSync, mkdirSync, rmSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
const root = fileURLToPath(new URL('..', import.meta.url))
const destination = resolve(root, 'public/examples')
rmSync(destination, { recursive: true, force: true })
mkdirSync(destination, { recursive: true })
for (const name of ['CoreApiExample.java', 'SpringApiExample.java']) cpSync(resolve(root, 'examples', name), resolve(destination, name))
console.log('Prepared canonical Java downloads')
execFileSync('python3', [resolve(root, 'scripts/prepare-javadoc.py')], { stdio: 'inherit' })
