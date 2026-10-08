import Feed from './pages/Feed'
import History from './pages/History'
import Users from './pages/Users'
import About from './pages/About'
import ErrorPage from './pages/Error'
const pages = { 'Users/Index': Users, About, Feed, History, Error: ErrorPage }
export function resolve(name: string) {
  if (!(name in pages)) throw new Error(`Unregistered page ${name}`)
  return pages[name as keyof typeof pages]
}
