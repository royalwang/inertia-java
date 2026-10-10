import { fileURLToPath } from 'node:url'
import { buildFrontend } from '../../../shared/build-frontend.mjs'
buildFrontend(fileURLToPath(new URL('..', import.meta.url)), 'src/ssr.ts')
