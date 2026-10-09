import DefaultTheme from 'vitepress/theme'
import { h } from 'vue'
import VersionMenu from './VersionMenu.vue'
import TranslationNotice from './TranslationNotice.vue'
import SearchFocus from './SearchFocus.vue'
export default {
  extends: DefaultTheme,
  Layout: () => h(DefaultTheme.Layout, null, { 'nav-bar-content-after': () => [h(VersionMenu), h(SearchFocus)], 'doc-before': () => h(TranslationNotice) }),
}
