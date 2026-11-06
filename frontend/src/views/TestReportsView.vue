<script setup lang="ts">
/** W2 / RPTX-03 对外测试报告列表。冻结后的报告快照自包含，可脱离源 run 渲染。 */
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DataTableColumns } from 'naive-ui'
import DataTable from '@/components/DataTable.vue'
import { useAuthStore } from '@/stores/auth'
import { testReportsApi, type TestReportItem } from '@/api/testReports'

const { t } = useI18n()
const auth = useAuthStore()
const rows = ref<TestReportItem[]>([])
const loading = ref(false)
const failed = ref(false)
const notice = ref('')
const runId = ref('')
const projectId = computed(() => auth.projectId ?? 0)

const columns = computed<DataTableColumns<TestReportItem>>(() => [
  { title: t('testReports.colTitle'), key: 'title', minWidth: 160 },
  { title: t('testReports.colRun'), key: 'runId', minWidth: 160 },
  { title: t('testReports.colState'), key: 'state', minWidth: 90 },
  { title: t('testReports.colVersion'), key: 'version', minWidth: 80 },
])

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    rows.value = await testReportsApi.list(projectId.value)
  } catch {
    failed.value = true
    notice.value = t('testReports.failed')
  } finally {
    loading.value = false
  }
}

async function create(): Promise<void> {
  if (!runId.value.trim()) return
  try {
    await testReportsApi.create(projectId.value, runId.value.trim(), '')
    runId.value = ''
    notice.value = t('testReports.created')
    await load()
  } catch {
    notice.value = t('testReports.failed')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-head">
      <h1>{{ t('testReports.title') }}</h1>
      <p class="muted">{{ t('testReports.subtitle') }}</p>
    </header>
    <form class="bar" @submit.prevent="create">
      <input
        v-model="runId"
        class="input"
        type="text"
        :placeholder="t('testReports.runPlaceholder')"
      />
      <button class="btn" type="submit">{{ t('testReports.create') }}</button>
      <button class="btn" type="button" @click="load">{{ t('testReports.refresh') }}</button>
    </form>
    <p v-if="notice" class="notice">{{ notice }}</p>
    <DataTable
      :columns="columns"
      :data="rows"
      :loading="loading"
      :error="failed"
      :empty-title="t('testReports.empty')"
    />
  </div>
</template>

<style scoped>
.page { padding: 24px 28px; display: grid; gap: 18px; }
.page-head h1 { margin: 0 0 6px; font-size: 22px; }
.muted { color: color-mix(in srgb, currentColor 60%, transparent); font-size: 13px; }
.bar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.notice { padding: 8px 12px; border-radius: 6px; font-size: 13px; border: 1px solid var(--fail-border); }
</style>
