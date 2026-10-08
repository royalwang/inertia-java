import { Head, Link, Deferred, useForm, usePage } from '@inertiajs/react'
export default function Users({ users, largeId, stats }: { users: { id: number; name: string }[]; largeId: bigint; stats?: { total: number } }) {
  const form = useForm({ name: '' })
  const page = usePage()
  const toast = (page.flash as { toast?: string }).toast
  return <main><Head title="Users" /><h1>Inertia Java</h1><p>Java routes, React pages, Node rendering.</p>
    <nav><Link href="/about">About this app</Link></nav>
    {toast && <p role="status">{toast}</p>}
    <form onSubmit={event => { event.preventDefault(); form.post('/users') }}>
      <label htmlFor="name">Name</label><input id="name" value={form.data.name} onChange={event => form.setData('name',event.target.value)} />
      {form.errors.name && <p role="alert">{form.errors.name}</p>}
      <button disabled={form.processing}>Save demo name</button>
    </form>
    <h2>Users</h2><ul>{users.map(user => <li key={user.id}>{user.name}</li>)}</ul>
    <p data-testid="large-id">Exact ID: {String(largeId)}</p>
    <Deferred data="stats" fallback={<p>Loading statistics…</p>}><p data-testid="stats">Total: {stats?.total}</p></Deferred>
  </main>
}
