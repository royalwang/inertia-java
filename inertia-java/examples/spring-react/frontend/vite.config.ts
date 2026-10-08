import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import inertia from '@inertiajs/vite'
import { mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { resolve } from 'node:path'

function hotFile(): Plugin {
  const file = resolve('.inertia/hot')
  return {
    name: 'inertia-java-hot-file',
    configureServer(server) {
      server.httpServer?.once('listening', () => {
        mkdirSync(resolve('.inertia'), { recursive: true })
        writeFileSync(file, 'http://127.0.0.1:15173')
      })
      server.httpServer?.once('close', () => rmSync(file, { force: true }))
    },
  }
}
export default defineConfig(({ isSsrBuild, command }) => ({
  plugins: [react(), inertia({ ssr: { entry: 'src/ssr.tsx', host: '127.0.0.1' } }), hotFile()],
  base: command === 'serve' ? '/' : './',
  server: { host: '127.0.0.1', port: 15173, strictPort: true, cors: { origin: ['http://127.0.0.1:18082', 'http://127.0.0.1:18080'] } },
  build: {
    outDir: isSsrBuild ? 'dist/ssr' : 'dist/client',
    manifest: !isSsrBuild,
    rollupOptions: { input: isSsrBuild ? 'src/ssr.tsx' : 'src/app.tsx' },
  },
}))
