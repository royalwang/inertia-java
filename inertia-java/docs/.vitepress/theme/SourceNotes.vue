<script setup>
import { useData } from 'vitepress'
const { frontmatter, lang, theme } = useData()
const sourceUrl = path => 'https://github.com/royalwang/inertia-java/blob/' + theme.value.sourceRef + '/' + path
</script>

<template>
  <aside v-if="frontmatter.sources?.length" class="source-notes" aria-label="Page sources">
    <details>
      <summary>{{ lang === 'zh-CN' ? '相关源码' : 'Source code' }}</summary>
      <ul>
        <li v-for="path in frontmatter.sources" :key="path"><a :href="sourceUrl(path)">{{ path }}</a></li>
      </ul>
    </details>
  </aside>
</template>

<style scoped>
.source-notes { margin-top: 32px; padding-top: 16px; border-top: 1px solid var(--vp-c-divider); color: var(--vp-c-text-2); font-size: 13px; }
summary { cursor: pointer; }
ul { padding-left: 20px; }
a { color: var(--vp-c-brand-1); overflow-wrap: anywhere; }
</style>
