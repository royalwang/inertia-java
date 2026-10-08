process.env.INERTIA_VERIFY_HISTORY = 'true'
process.env.INERTIA_HEALTH_OUTPUT ??= '/tmp/inertia-java-history'
await import('./verify-ssr-health.mjs')
