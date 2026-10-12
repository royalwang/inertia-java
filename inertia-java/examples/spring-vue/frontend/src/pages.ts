import type { DefineComponent } from 'vue'
import Users from './pages/Users.vue'
import About from './pages/About.vue'
import Feed from './pages/Feed.vue'
import Login from './pages/Login.vue'
import Account from './pages/Account.vue'
const pages = { Users, About, Feed, 'Auth/Login': Login, 'Auth/Account': Account }
export function resolve(name: string) {
  if (!Object.hasOwn(pages, name)) throw new Error('Unregistered page ' + name)
  // Inertia resolves heterogeneous page props through one erased component boundary.
  return pages[name as keyof typeof pages] as unknown as DefineComponent
}
