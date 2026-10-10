<script setup lang="ts">
import { Deferred, Head, Link, router, useForm, usePage } from '@inertiajs/vue3'
defineProps<{ users: { id: number; name: string }[]; stats?: { total: number }; phase: number; optional?: string }>()
const page = usePage()
const form = useForm({ name: '' })
</script>

<template>
  <main>
    <Head title="Vue users" />
    <h1>Vue users</h1>
    <p>Forms demonstrate validation and flash; they do not write to a database.</p>
    <ul><li v-for="user in users" :key="user.id">{{ user.name }}</li></ul>
    <Deferred data="stats"><template #fallback><p>Loading statistics…</p></template><p data-testid="stats">Total: {{ stats?.total }}</p></Deferred>
    <p data-testid="phase">Phase: {{ phase }}</p>
    <p data-testid="optional">{{ optional ?? 'Not loaded' }}</p>
    <button @click="router.reload({ only: ['phase'], data: { phase: 1 } })">Partial reload</button>
    <button @click="router.reload({ only: ['optional'] })">Load optional value</button>
    <p v-if="page.flash.toast" role="status">{{ page.flash.toast }}</p>
    <form @submit.prevent="form.post('/users')">
      <label for="name">Name</label><input id="name" v-model="form.name" autocomplete="name">
      <p v-if="form.errors.name" role="alert">{{ form.errors.name }}</p>
      <button :disabled="form.processing">Save name</button>
    </form>
    <nav><Link href="/about">About this app</Link><Link href="/feed">Feed</Link></nav>
  </main>
</template>
