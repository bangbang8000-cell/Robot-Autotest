<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { healthPing } from '@/api/http'

const { t } = useI18n()
const loading = ref(false)
const result = ref<string>('')
const failed = ref(false)

async function probe(): Promise<void> {
  loading.value = true
  failed.value = false
  result.value = ''
  try {
    const data = await healthPing()
    result.value = `${data.app} · ${data.status} · ${data.version}`
  } catch (error) {
    failed.value = true
    result.value = error instanceof Error ? error.message : String(error)
  } finally {
    loading.value = false
  }
}

onMounted(probe)
</script>

<template>
  <div class="page">
    <h1 class="page-title">{{ t('menu.overview') }}</h1>
    <p class="page-hint">{{ t('common.placeholder') }}</p>

    <section class="card">
      <h2 class="card-title">{{ t('common.backendPing') }}</h2>
      <p v-if="loading" class="muted">{{ t('common.loading') }}</p>
      <p v-else-if="result" :class="failed ? 'text-fail' : 'text-ok'">
        {{ failed ? t('common.failed') : t('common.success') }}：{{ result }}
      </p>
      <button class="btn" type="button" @click="probe">{{ t('common.refresh') }}</button>
    </section>
  </div>
</template>
