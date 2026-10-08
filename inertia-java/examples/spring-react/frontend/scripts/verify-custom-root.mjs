process.env.INERTIA_ROOT_ID = 'portal'
process.env.INERTIA_VERIFY_CSP = 'true'
await import('./verify-ssr-health.mjs')
