<script setup lang="ts">
import { Head, Link, useForm, usePage } from '@inertiajs/vue3'
defineProps<{ signedIn: boolean }>()
const form = useForm({ username: '', password: '' })
const page = usePage()
const message = (value: unknown) => Array.isArray(value) ? value[0] : value
const submit = () => form.post('/login', { forceFormData: true, onFinish: () => form.reset('password') })
</script>
<template>
  <main><Head title="Sign in" /><h1>Sign in</h1>
    <p>Local identity demonstration. Use the credentials configured by the operator.</p>
    <p v-if="page.props.errors._csrf" role="alert">{{ message(page.props.errors._csrf) }}</p>
    <Link v-if="signedIn" href="/account">Open account</Link>
    <form v-else @submit.prevent="submit">
      <label for="username">Username</label><input id="username" v-model="form.username" autocomplete="username">
      <label for="password">Password</label><input id="password" v-model="form.password" type="password" autocomplete="current-password">
      <p v-if="page.props.errors.credentials" role="alert">{{ message(page.props.errors.credentials) }}</p>
      <button :disabled="form.processing">Sign in</button>
    </form>
    <nav><Link href="/users">Public users</Link> · <Link href="/account">Open protected account</Link></nav>
  </main>
</template>
