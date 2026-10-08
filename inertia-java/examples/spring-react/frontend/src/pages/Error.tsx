import { Head, Link } from '@inertiajs/react'
export default function Error({ status }: { status: number }) { return <main><Head title={`Error ${status}`} /><h1>Error {status}</h1><Link href="/users">Back to users</Link></main> }
