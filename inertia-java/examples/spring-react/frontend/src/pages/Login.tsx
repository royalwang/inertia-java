import { Head, Link, useForm, usePage } from '@inertiajs/react'
export default function Login({ signedIn }: { signedIn: boolean }) {
  const form = useForm({ username: '', password: '' })
  const csrfError = (usePage().props.errors as Record<string, string | string[]>)._csrf
  const error = (form.errors as Record<string, string | string[]>).credentials
  return <main><Head title="Sign in" /><h1>Sign in</h1>
    <p>Local identity demonstration. Use the credentials configured by the operator.</p>
    {csrfError && <p role="alert">{Array.isArray(csrfError) ? csrfError[0] : csrfError}</p>}
    {signedIn ? <Link href="/account">Open account</Link> : <form onSubmit={event => {
      event.preventDefault(); form.post('/login', { forceFormData: true, onFinish: () => form.reset('password') })
    }}>
      <label htmlFor="username">Username</label><input id="username" autoComplete="username" value={form.data.username} onChange={event => form.setData('username', event.target.value)} />
      <label htmlFor="password">Password</label><input id="password" type="password" autoComplete="current-password" value={form.data.password} onChange={event => form.setData('password', event.target.value)} />
      {error && <p role="alert">{Array.isArray(error) ? error[0] : error}</p>}
      <button disabled={form.processing}>Sign in</button>
    </form>}
    <nav><Link href="/users">Public users</Link> · <Link href="/account">Open protected account</Link></nav>
  </main>
}
