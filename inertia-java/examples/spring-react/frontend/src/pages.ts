import type { Page } from '@inertiajs/core'
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
  if (!Object.hasOwn(pages, name)) throw new Error(`Unregistered page ${name}`)
  return pages[name as keyof typeof pages]
}

// Validate the decoded Page envelope before invoking any React component.
export function isSsrPage(value: unknown): value is Page {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const page = value as Record<string, unknown>
  const object = (item: unknown) => item !== null && typeof item === 'object' && !Array.isArray(item)
  return typeof page.component === 'string' && Object.hasOwn(pages, page.component)
    && object(page.props) && typeof page.url === 'string' && page.url.length <= 8192
    && (page.version === null || typeof page.version === 'string')
    && (page.flash === undefined || object(page.flash))
    && ['preserveBigIntegers', 'encryptHistory', 'clearHistory', 'preserveFragment'].every(
      key => page[key] === undefined || typeof page[key] === 'boolean')
}
