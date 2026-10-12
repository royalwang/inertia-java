<script setup lang="ts">
import { Head, Link, router, usePage } from '@inertiajs/vue3'
import { notifyDemoLogout } from '../auth'
defineProps<{ username: string }>()
const page = usePage()
const message = (value: unknown) => Array.isArray(value) ? value[0] : value
const logout = () => router.post('/logout', {}, { onSuccess: notifyDemoLogout })
</script>
<template>
  <main><Head title="Account" /><h1>Your account</h1><p data-testid="identity">Signed in as {{ username }}</p>
    <p v-if="page.props.errors._csrf" role="alert">{{ message(page.props.errors._csrf) }}</p>
    <button @click="logout">Sign out</button>
    <nav><Link href="/users">Public users</Link></nav>
  </main>
</template>
