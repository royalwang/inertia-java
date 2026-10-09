<script setup>
import { computed } from 'vue'
import { useData, withBase } from 'vitepress'
const { page, frontmatter, theme, lang } = useData()
const chinese = computed(() => lang.value === 'zh-CN')
const translation = computed(() => (theme.value.translationPages ?? []).find(entry => entry.source === page.value.relativePath))
const route = path => withBase('/' + path.replace(/(?:^|\/)index\.md$/, '/').replace(/\.md$/, '').replace(/^\//, ''))
</script>

<template>
  <aside class="translation-notice" aria-label="Translation status">
    <template v-if="chinese">
      <p>中文译文 · 英文为规范正文 · <a :href="route(frontmatter.translation.source)">查看对应英文</a></p>
    </template>
    <p v-else-if="translation"><a :href="route(translation.path)">阅读本页中文译文</a> · English is the canonical source.</p>
    <p v-else><a :href="withBase('/zh/')">简体中文文档</a></p>
  </aside>
</template>

<style scoped>
.translation-notice { margin-bottom: 24px; padding: 12px 16px; border: 1px solid var(--vp-c-divider); border-radius: 8px; font-size: 13px; color: var(--vp-c-text-2); }
p { margin: 0; }
a { color: var(--vp-c-brand-1); }
</style>
