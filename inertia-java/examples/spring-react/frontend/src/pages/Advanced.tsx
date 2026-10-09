import { Head, Link, router, useForm, usePage } from '@inertiajs/react'
type Profile = { name: string; preferences: { theme: string; language: string }; members: { id: number; name: string }[] }
export default function Advanced({ profile, expensive, optional, status }: { profile: Profile; expensive: number; optional?: number; status: number }) {
  const personal = useForm({ name: '' })
  const team = useForm({ name: '' })
  const toast = (usePage().flash as { toast?: string }).toast
  return <main><Head title="Advanced props" /><h1>Advanced props</h1>
    <p>Snapshots and deltas are demo data. Forms do not write to a database.</p>
    <p data-testid="profile-name">{profile.name}</p>
    <p data-testid="preferences">{profile.preferences.theme} / {profile.preferences.language}</p>
    <ul data-testid="members">{profile.members.map(member => <li key={member.id}>{member.id}: {member.name}</li>)}</ul>
    <p data-testid="expensive">Expensive query: {expensive}</p>
    <p data-testid="optional">Optional query: {optional ?? 'not loaded'}</p>
    <p data-testid="always-status">Always phase: {status}</p>
    <button onClick={() => router.reload({ only: ['profile'], data: { phase: 1 } })}>Merge profile delta</button>
    <button onClick={() => router.reload({ except: ['expensive', 'profile', 'status'], data: { phase: 2 } })}>Reload except profile and query</button>
    <button onClick={() => router.reload({ only: ['expensive'] })}>Run expensive query</button>
    <button onClick={() => router.reload({ only: ['optional'] })}>Load optional query</button>
    <button onClick={() => router.reload({ only: ['profile'], reset: ['profile'], data: { phase: 0 } })}>Reset profile</button>
    {toast && <p role="status">{toast}</p>}
    <form aria-label="Profile form" onSubmit={event => { event.preventDefault(); personal.post('/advanced/profile', { errorBag: 'profile' }) }}>
      <h2>Profile form</h2><label htmlFor="profile-name">Profile name</label><input id="profile-name" value={personal.data.name} onChange={event => personal.setData('name', event.target.value)} />
      {personal.errors.name && <p role="alert">{personal.errors.name}</p>}
      <button disabled={personal.processing}>Save profile</button>
    </form>
    <form aria-label="Team form" onSubmit={event => { event.preventDefault(); team.post('/advanced/team', { errorBag: 'team' }) }}>
      <h2>Team form</h2><label htmlFor="team-name">Team name</label><input id="team-name" value={team.data.name} onChange={event => team.setData('name', event.target.value)} />
      {team.errors.name && <p role="alert">{team.errors.name}</p>}
      <button disabled={team.processing}>Save team</button>
    </form>
    <nav><Link href="/about">About this app</Link></nav>
  </main>
}
