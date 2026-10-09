<script setup>
import { useData } from 'vitepress'
const { theme, lang } = useData()
const navigate = event => { window.location.assign(event.target.value) }
</script>

<template>
  <label class="version-menu">
    <span class="sr-only">{{ lang === 'zh-CN' ? '文档版本' : 'Documentation version' }}</span>
    <select :aria-label="lang === 'zh-CN' ? '文档版本' : 'Documentation version'" :value="theme.versionMenu.current" @change="navigate">
      <option v-for="entry in theme.versionMenu.entries" :key="entry.path" :value="entry.path">{{ entry.path === theme.versionMenu.current ? theme.versionMenu.label : entry.label }}</option>
    </select>
  </label>
</template>

<style scoped>
.version-menu { display: flex; align-items: center; margin-left: 12px; }
select { max-width: 145px; border: 1px solid var(--vp-c-divider); border-radius: 6px; padding: 3px 6px; background: var(--vp-c-bg); color: var(--vp-c-text-1); font-size: 12px; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }
@media (max-width: 640px) { .version-menu { margin-left: 6px; } select { max-width: 112px; } }
</style>
