import { Head, Link } from '@inertiajs/react'
export default function History({ mode, visits, marker }: { mode: string, visits: number, marker: string }) {
  return <main><Head title="History demo" /><h1>History demo</h1>
    <p data-testid="history-mode">{mode}</p><p data-testid="history-visits">{visits}</p>
    <p>{marker}</p>
    <nav>{['encrypted', 'plain', 'clear', 'csr'].map(value => <p key={value}>
      <Link href={`/demo-history/${value}`}>{value}</Link></p>)}</nav>
    <p>This demo changes browser history settings; it does not sign users out.</p>
  </main>
}
