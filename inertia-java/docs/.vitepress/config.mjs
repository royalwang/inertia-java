import { defineConfig } from 'vitepress'
import { withMermaid } from 'vitepress-plugin-mermaid'
import { siteContext } from '../scripts/versions.mjs'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('..', import.meta.url))
const catalog = JSON.parse(readFileSync(resolve(root, '../../docs/inertia-java/open-source-docs-catalog.json'), 'utf8'))
const available = catalog.pages.filter(page => existsSync(resolve(root, page.path)))
const link = page => '/' + page.path.replace(/\.md$/, '')
const sidebar = catalog.sections.map(section => ({
  text: section.title,
  collapsed: section.id !== 'getting-started',
  items: available.filter(page => page.section === section.id && page.id !== 'home').map(page => ({ text: page.title, link: link(page) })),
})).filter(section => section.items.length)
const translations = catalog.translations ?? []
const sectionLabels = { 'getting-started': '开始使用', concepts: '核心概念', guide: '应用指南', props: 'Props', ssr: '服务端渲染', integrations: '集成', deployment: '部署', reference: '参考', testing: '测试', troubleshooting: '故障排查', community: '社区' }
const chineseSidebar = catalog.sections.map(section => ({
  text: sectionLabels[section.id] ?? section.title,
  collapsed: section.id !== 'getting-started',
  items: available.filter(page => page.section === section.id && page.id !== 'home').map(page => {
    const translated = translations.find(entry => entry.id === page.id)
    return translated ? { text: translated.title, link: link(translated) } : { text: page.title + '（英文）', link: link(page) }
  }),
})).filter(section => section.items.length)
const sitemapRoutes = new Set([...available, ...translations].map(page => page.path.replace(/(?:^|\/)index\.md$/, '/').replace(/\.md$/, '').replace(/^\//, '')))
const context = siteContext(root, catalog)
const { base, version, sourceRef, sourceLabel, snapshot, versionMenu } = context
const sourceRoot = 'https://github.com/royalwang/inertia-java'
const editPattern = snapshot ? `${sourceRoot}/blob/${sourceRef}/inertia-java/docs/:path` : `${sourceRoot}/edit/main/inertia-java/docs/:path`

export default withMermaid(defineConfig({
  mermaid: { securityLevel: 'strict' },
  title: 'Inertia Java',
  description: 'Build Inertia applications with Java 21 and Spring MVC.',
  lang: 'en-US',
  locales: {
    root: { label: 'English', lang: 'en-US' },
    zh: {
      label: '简体中文', lang: 'zh-CN', description: '使用 Java 21 和 Spring MVC 构建 Inertia 应用。',
      themeConfig: {
        nav: [
          { text: '开始使用', link: '/zh/getting-started/overview' },
          { text: '配置参考', link: '/zh/reference/configuration' },
          { text: 'API 指南', link: '/zh/api-guide' },
        ],
        sidebar: chineseSidebar,
        outline: { label: '本页目录', level: [2, 3] },
        docFooter: { prev: '上一页', next: '下一页' },
        sidebarMenuLabel: '目录', returnToTopLabel: '返回顶部', darkModeSwitchLabel: '外观',
        langMenuLabel: '选择语言', lastUpdated: { text: '最近更新' },
        editLink: { pattern: editPattern, text: snapshot ? '查看此版本源码' : '在 GitHub 编辑此页' },
        footer: { message: `Apache-2.0 · ${version} 文档${snapshot ? ' · ' + sourceLabel : ''}`, copyright: 'Copyright (c) 2026 royalwang' },
      },
    },
  },
  head: [['link', { rel: 'icon', href: 'data:,' }]],
  base,
  sitemap: {
    hostname: 'https://royalwang.github.io' + base,
    transformItems: items => items.filter(item => sitemapRoutes.has(item.url)),
  },
  srcExclude: ['README.md', 'node_modules/**', 'scripts/**', 'examples/**'],
  cleanUrls: true,
  lastUpdated: true,
  ignoreDeadLinks: false,
  themeConfig: {
    i18nRouting: false,
    sourceRef,
    versionMenu,
    translationPages: translations.map(entry => ({ id: entry.id, path: entry.path, source: catalog.pages.find(page => page.id === entry.id).path })),
    nav: [
      { text: 'Get started', link: '/getting-started/overview' },
      { text: 'API guide', link: '/api-guide' },
      { text: 'Source', link: `${sourceRoot}/tree/${sourceRef}/inertia-java` },
    ],
    sidebar,
    search: { provider: 'local', options: {
      miniSearch: { options: { tokenize: text => Array.from(new Intl.Segmenter('zh-CN', { granularity: 'word' }).segment(text)).filter(part => part.isWordLike).map(part => part.segment) } },
      locales: { zh: { translations: {
      button: { buttonText: '搜索文档', buttonAriaLabel: '搜索文档' },
      modal: { noResultsText: '没有找到结果', resetButtonTitle: '清除搜索', footer: { selectText: '选择', navigateText: '切换', closeText: '关闭' } },
    } } } } },
    editLink: {
      pattern: editPattern,
      text: snapshot ? 'View tagged source' : 'Edit this page on GitHub',
    },
    footer: {
      message: `Apache-2.0 · ${version} documentation${snapshot ? ' · ' + sourceLabel : ''}`,
      copyright: 'Copyright (c) 2026 royalwang',
    },
    outline: [2, 3],
  },
  markdown: {
    config(md) {
      // Canonical examples remain source files; their download copies live in public/.
      md.core.ruler.after('inline', 'java-downloads', state => {
        for (const token of state.tokens) for (const child of token.children ?? []) {
          if (child.type !== 'link_open') continue
          let href = child.attrGet('href')
          if (snapshot && href?.startsWith(sourceRoot + '/')) {
            href = href.replace(/\/(blob|tree)\/main\//, '/$1/' + sourceRef + '/')
            child.attrSet('href', href)
          }
          if (['CoreApiExample.java', 'SpringApiExample.java'].some(name => href === 'examples/' + name || href?.endsWith('/examples/' + name))) {
            child.attrSet('href', base + 'examples/' + href.split('/').at(-1))
            child.attrSet('download', '')
          }
        }
      })
    },
  },
}))
