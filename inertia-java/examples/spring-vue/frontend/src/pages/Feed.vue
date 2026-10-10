<script setup lang="ts">
import { Head, InfiniteScroll, Link, router } from '@inertiajs/vue3'
defineProps<{ items: { data: { id: number; name: string }[] }; catalog: { load: number } }>()
</script>
<template>
  <main><Head title="Vue feed" /><h1>Vue feed</h1>
    <p data-testid="catalog">Catalog load: {{ catalog.load }}</p>
    <button @click="router.reload({ only: ['catalog'] })">Refresh catalog</button>
    <InfiniteScroll data="items" manual preserve-url>
      <template #previous="{ fetch, hasPrevious }"><button v-if="hasPrevious" @click="fetch">Load previous</button></template>
      <ul data-testid="feed-items"><li v-for="item in items.data" :key="item.id">{{ item.name }}</li></ul>
      <template #next="{ fetch, hasNext }"><button v-if="hasNext" @click="fetch">Load next</button></template>
    </InfiniteScroll>
    <button @click="router.reload({ only: ['items'], data: { page: 1 }, reset: ['items'] })">Reset feed</button>
    <Link href="/about">About this app</Link><Link href="/users">Back to users</Link>
  </main>
</template>
