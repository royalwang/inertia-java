import { notifyDemoLogout } from '../auth'
import { Head, Link, router, usePage } from '@inertiajs/react'
export default function Account({ username }: { username: string }) {
  const error = (usePage().props.errors as Record<string, string | string[]>)._csrf
  return <main><Head title="Account" /><h1>Your account</h1><p data-testid="identity">Signed in as {username}</p>
    {error && <p role="alert">{Array.isArray(error) ? error[0] : error}</p>}
    <button onClick={() => router.post('/logout', {}, { onSuccess: notifyDemoLogout })}>Sign out</button>
    <nav><Link href="/users">Public users</Link></nav>
  </main>
}
