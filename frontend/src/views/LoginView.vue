<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import ErrorLineView from '@/components/ErrorLine.vue'
import { localizeError, type ErrorLine } from '@/utils/errors'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const submitting = ref(false)
const error = ref<ErrorLine | null>(null)

async function submit(): Promise<void> {
  error.value = null
  if (!username.value.trim() || !password.value) {
    error.value = { summary: t('login.required'), detail: null }
    return
  }
  submitting.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/overview'
    await router.replace(redirect)
  } catch (e) {
    error.value = localizeError(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
    <form class="login-card" @submit.prevent="submit">
      <h1 class="login-title">{{ t('app.title') }}</h1>
      <p class="muted">{{ t('login.subtitle') }}</p>

      <label class="field">
        <span class="field-label">{{ t('login.username') }}</span>
        <input v-model="username" class="input" autocomplete="username" />
      </label>
      <label class="field">
        <span class="field-label">{{ t('login.password') }}</span>
        <input v-model="password" class="input" type="password" autocomplete="current-password" />
      </label>

      <ErrorLineView v-if="error" :error="error" />
      <button class="btn login-btn" type="submit" :disabled="submitting">
        {{ submitting ? t('login.submitting') : t('login.submit') }}
      </button>
      <p class="muted">{{ t('login.hint') }}</p>
    </form>
  </div>
</template>
