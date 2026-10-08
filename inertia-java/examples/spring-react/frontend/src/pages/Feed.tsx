import { Head, InfiniteScroll, Link, router } from '@inertiajs/react'
export default function Feed({ items, catalog }: { items: { data: { id: number; name: string }[] }; catalog: { load: number } }) {
  return <main><Head title="Feed" /><h1>Feed</h1><Link href="/about">About this app</Link>
    <p data-testid="catalog">Catalog load: {catalog.load}</p>
    <button onClick={() => router.reload({ only: ['catalog'] })}>Refresh catalog</button>
    <InfiniteScroll data="items" manual preserveUrl
      previous={({ fetch, hasPrevious }) => hasPrevious && <button onClick={fetch}>Load previous</button>}
      next={({ fetch, hasNext }) => hasNext && <button onClick={fetch}>Load next</button>}>
      <ul data-testid="feed-items">{items.data.map(item => <li key={item.id}>{item.name}</li>)}</ul>
    </InfiniteScroll>
    <button onClick={() => router.reload({ only: ['items'], data: { page: 1 }, reset: ['items'] })}>Reset feed</button>
  </main>
}
