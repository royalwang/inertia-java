// A same-origin invalidation hint only. Server-side Spring Security remains authoritative.
const logoutKey = 'inertia-java.demo-auth.logout'
export function notifyDemoLogout() {
  try { localStorage.setItem(logoutKey, crypto.randomUUID()) }
  catch { /* Storage may be disabled; the current tab still completes its server logout. */ }
}
export function installDemoAuthSync() {
  if (!document.querySelector('meta[name="inertia-demo-auth"]')) return
  let initial: string | null
  try { initial = localStorage.getItem(logoutKey) } catch { return }
  const login = () => window.location.replace('/login')
  window.addEventListener('storage', event => {
    if (event.key === logoutKey && event.newValue && event.newValue !== initial) login()
  })
  window.addEventListener('pageshow', event => {
    if (!event.persisted) return
    try { if (localStorage.getItem(logoutKey) !== initial) login() } catch { /* No storage access. */ }
  })
}
