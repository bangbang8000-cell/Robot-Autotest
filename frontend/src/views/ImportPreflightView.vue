<script setup lang="ts">
/** W4 / IMP-01..05: upload a zip into the sandbox and show the preflight report. */
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DataTableColumns } from 'naive-ui'
import DataTable from '@/components/DataTable.vue'
import { importsApi, looksLikeZip, type CaseSource, type PreflightReport } from '@/api/imports'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()
const projectId = computed(() => auth.projectId ?? 0)
const sourceType = ref('pytest')
const file = ref<File | null>(null)
const report = ref<PreflightReport | null>(null)
const loading = ref(false)
const failed = ref(false)
const fileName = ref('')

function pick(event: Event): void {
  const input = event.target as HTMLInputElement
  file.value = input.files?.[0] ?? null
  fileName.value = file.value?.name ?? ''
}

const columns = computed<DataTableColumns<CaseSource>>(() => [
  { title: t('imports.relPath'), key: 'relPath', minWidth: 200 },
  { title: t('imports.kind'), key: 'kind', minWidth: 90 },
  { title: t('imports.nodeId'), key: 'nodeId', minWidth: 180 },
])

async function preflight(): Promise<void> {
  if (!file.value || !looksLikeZip(file.value.name)) return
  loading.value = true
  failed.value = false
  try {
    report.value = await importsApi.preflight(projectId.value, sourceType.value, file.value)
  } catch {
    failed.value = true
    report.value = null
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <header class="page-head">
      <h1>{{ t('imports.title') }}</h1>
      <p class="muted">{{ t('imports.subtitle') }}</p>
    </header>

    <form class="bar" @submit.prevent="preflight">
      <select v-model="sourceType" class="input" :aria-label="t('imports.sourceType')">
        <option value="pytest">pytest</option>
        <option value="robot">Robot Framework</option>
      </select>
      <input class="input" type="file" accept=".zip" :aria-label="t('imports.file')" @change="pick" />
      <button class="btn" type="submit" :disabled="!file || loading">
        {{ t('imports.preflight') }}
      </button>
    </form>
    <p class="muted">{{ fileName }}</p>
    <p v-if="failed" class="notice">{{ t('imports.failed') }}</p>

    <div v-if="report" class="report">
      <p class="muted">
        {{ t('imports.fileCount') }}: {{ report.fileCount }} /
        {{ t('imports.checksum') }}: {{ report.checksum }}
      </p>
      <p class="muted">{{ t('imports.executable') }}: {{ report.executable }}</p>
      <p v-for="(cmd, i) in report.commands" :key="i" class="notice">{{ cmd }}</p>
      <p v-for="(item, i) in report.sensitiveFindings" :key="i" class="notice">{{ item }}</p>
      <p v-if="report.sensitiveFindings.length === 0" class="muted">
        {{ t('imports.noFindings') }}
      </p>
      <DataTable
        :columns="columns"
        :data="report.cases"
        :loading="loading"
        :error="failed"
        :empty-title="t('imports.empty')"
      />
    </div>
  </div>
</template>

<style scoped>
.page { padding: 24px 28px; display: grid; gap: 18px; }
.page-head h1 { margin: 0 0 6px; font-size: 22px; }
.muted { color: var(--text-muted); font-size: 13px; margin: 0; }
.bar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.report { display: grid; gap: 8px; }
.notice { padding: 8px 12px; border: 1px solid var(--border); border-radius: 6px; font-size: 13px; margin: 0; }
</style>
