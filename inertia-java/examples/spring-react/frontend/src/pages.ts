import Advanced from './pages/Advanced'
import Login from './pages/Login'
import Account from './pages/Account'
import Feed from './pages/Feed'
import History from './pages/History'
import Users from './pages/Users'
import About from './pages/About'
import ErrorPage from './pages/Error'
const pages = { Advanced, 'Auth/Login': Login, 'Auth/Account': Account, 'Users/Index': Users, About, Feed, History, Error: ErrorPage }
export function resolve(name: string) {
  if (!(name in pages)) throw new Error(`Unregistered page ${name}`)
  return pages[name as keyof typeof pages]
}
