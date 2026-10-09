<script setup>
import { onMounted, onUnmounted } from 'vue'

let cleanup
onMounted(() => {
  let previous, restore = false
  const dialog = () => document.querySelector('.VPLocalSearchBox')
  const remember = event => {
    if (dialog()) {
      if (event.type === 'keydown' && event.key === 'Escape') restore = true
      return
    }
    const target = event.target
    const editing = target instanceof HTMLElement && (target.isContentEditable || /^(INPUT|SELECT|TEXTAREA)$/.test(target.tagName))
    const shortcut = event.type === 'keydown' && ((event.key === 'k' && (event.ctrlKey || event.metaKey)) || (event.key === '/' && !editing))
    if (shortcut || (event.type === 'click' && target instanceof Element && target.closest('#local-search button'))) {
      previous = document.activeElement
      restore = false
    }
  }
  // VitePress 1.6.4 activates its trap after focusing the input. That removed
  // input cannot receive focus on Escape; retain the original trigger instead.
  const observer = new MutationObserver(() => {
    if (!restore || dialog()) return
    restore = false
    if (previous instanceof HTMLElement && previous.isConnected) previous.focus()
    previous = undefined
  })
  observer.observe(document.body, { childList: true, subtree: true })
  document.addEventListener('keydown', remember, true)
  document.addEventListener('click', remember, true)
  cleanup = () => {
    observer.disconnect()
    document.removeEventListener('keydown', remember, true)
    document.removeEventListener('click', remember, true)
  }
})
onUnmounted(() => cleanup?.())
</script>

<template><span hidden aria-hidden="true" /></template>
