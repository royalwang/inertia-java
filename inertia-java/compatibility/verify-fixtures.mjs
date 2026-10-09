// Optional maintainer gate: Java consumers only need the checked-in fixtures.
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import { isDeepStrictEqual } from 'node:util'
const root = fileURLToPath(new URL('../..', import.meta.url))
const generated = JSON.parse(execFileSync('cargo', ['run', '--locked', '--quiet', '--no-default-features', '--example', 'java_contract_fixtures'], {
  cwd: root, encoding: 'utf8', maxBuffer: 2 * 1024 * 1024, stdio: ['ignore', 'pipe', 'inherit'],
}))
const committed = JSON.parse(readFileSync(resolve(root, 'inertia-java/compatibility/fixtures/pages.json'), 'utf8'))
if (!isDeepStrictEqual(generated, committed)) throw new Error('Rust export differs from checked-in fixtures; regenerate and review, do not hand-edit expectedPage')
const definitions = JSON.parse(readFileSync(resolve(root, 'examples/java_contract_cases.json'), 'utf8'))
if (definitions.length !== generated.cases.length || new Set(generated.cases.map(c => c.name)).size !== definitions.length) throw new Error('Missing/duplicate fixture')
console.log(`Verified ${generated.cases.length} complete Rust exports against checked-in fixtures`)
