import { createInertiaApp } from '@inertiajs/vue3'
import { createApp, createSSRApp, h } from 'vue'
import { resolve } from './pages'
import './style.css'

void createInertiaApp({ resolve, setup({ el, App, props, plugin }) {
  if (!el) throw new Error('Missing browser root')
  const factory = el.hasAttribute('data-server-rendered') ? createSSRApp : createApp
  factory({ render: () => h(App, props) }).use(plugin).mount(el)
} })
