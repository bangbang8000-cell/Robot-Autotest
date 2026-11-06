<script setup lang="ts">
/** W3 / KRN-04..05: capability matrix, kernel selection source and command preview. */
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { kernelsApi, type KernelCapabilities, type KernelSelection } from '@/api/kernels'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()
const projectId = computed(() => auth.projectId ?? 0)
const caps = ref<KernelCapabilities | null>(null)
const selection = ref<KernelSelection | null>(null)
const failed = ref(false)

async function load(): Promise<void> {
  failed.value = false
  try {
    caps.value = await kernelsApi.capabilities()
    selection.value = await kernelsApi.select(projectId.value)
  } catch {
    failed.value = true
  }
}
onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-head">
      <h1>{{ t('kernels.title') }}</h1>
      <p class="muted">{{ t('kernels.subtitle') }}</p>
    </header>
    <p v-if="failed" class="notice">{{ t('kernels.failed') }}</p>
    <div v-if="caps" class="facts">
      <p v-for="item in ['robot', 'pabot', 'pytest', 'python3']" :key="item" class="notice">
        {{ item }}: {{ caps[item as 'robot'] ? t('kernels.available') : t('kernels.unavailable') }}
      </p>
      <p class="notice">{{ t('kernels.mixed') }}: {{ caps.sameRunMixedKernels }}</p>
    </div>
    <div v-if="selection" class="facts">
      <p class="notice">{{ t('kernels.selection') }}: {{ selection.kernel }} / {{ selection.source }}</p>
      <p class="notice">{{ t('kernels.overridden') }}: {{ selection.overridden }}</p>
    </div>
  </div>
</template>

<style scoped>
.page { padding: 24px 28px; display: grid; gap: 18px; }
.page-head h1 { margin: 0 0 6px; font-size: 22px; }
.muted { color: var(--text-muted); font-size: 13px; margin: 0; }
.facts { display: grid; gap: 8px; }
.notice { padding: 8px 12px; border: 1px solid var(--border); border-radius: 6px; font-size: 13px; margin: 0; }
</style>
