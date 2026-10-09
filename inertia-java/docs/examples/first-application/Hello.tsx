import { Head, Link, useForm, usePage } from '@inertiajs/react'

export default function Hello({ message }: { message: string }) {
  const form = useForm({ name: '' })
  const page = usePage()
  const error = form.errors.name
  const toast = (page.flash as { toast?: string }).toast
  return <main>
    <Head title="My first Inertia application" />
    <h1>{message}</h1>
    <Link href="/about">About this application</Link>
    {toast && <p role="status">{toast}</p>}
    <form onSubmit={event => { event.preventDefault(); form.post('/hello') }}>
      <label htmlFor="name">Name</label>
      <input id="name" value={form.data.name} onChange={event => form.setData('name', event.target.value)} />
      {error && <p role="alert">{Array.isArray(error) ? error.join(' ') : error}</p>}
      <button disabled={form.processing}>Say hello</button>
    </form>
  </main>
}
