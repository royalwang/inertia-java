import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
const root = fileURLToPath(new URL('../..', import.meta.url))
// Compile the distributed API examples, then exercise the canonical browser tutorial.
// Requires installed/built Maven artifacts, built sample frontend and a browser.
for (const [command, args] of [
  ['python3', ['scripts/verify-maven-consumer.py']],
  [process.execPath, ['docs/scripts/verify-first-application.mjs']],
]) {
  const result = spawnSync(command, args, { cwd: root, stdio: 'inherit', env: process.env })
  if (result.error) throw result.error
  if (result.status !== 0) { process.exitCode = result.status ?? 1; break }
}
