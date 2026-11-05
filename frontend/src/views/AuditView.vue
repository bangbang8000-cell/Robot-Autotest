<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDatePicker,
  NPagination,
  NSelect,
  type DataTableColumns,
} from 'naive-ui'
import { auditApi, type AuditEntry } from '@/api/audit'
import { useAuthStore } from '@/stores/auth'
import DataTable from '@/components/DataTable.vue'
import ErrorLineView from '@/components/ErrorLine.vue'
import AppIcon from '@/components/AppIcon.vue'
import { localizeError, type ErrorLine } from '@/utils/errors'
import { formatDateTime } from '@/utils/format'

const { t } = useI18n()
const auth = useAuthStore()

// 与后端一致的关键动作清单；后端按 LIKE %action% 过滤，留空即全部
const ACTIONS = [
  'user:create',
  'user:delete',
  'role:update',
  'project:create',
  'project:delete',
  'member:update',
  'credential:upsert',
  'case:delete',
  'set:delete',
  'schedule:update',
  'schedule:delete',
  'lease:force-release',
] as const

const size = 20
const rows = ref<AuditEntry[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const error = ref<ErrorLine | null>(null)
const message = ref('')

// 空串代表「全部项目」：管理员可查全局，项目所有者只查本项目
const filter = reactive({
  projectId: '' as string,
  action: '',
  from: '',
  to: '',
})

const projectOptions = computed(() => [
  { label: t('audit.allProjects'), value: '' },
  ...auth.projects.map((p) => ({ label: `${p.code} · ${p.name}`, value: String(p.id) })),
])

const actionOptions = computed(() => [
  { label: t('audit.allActions'), value: '' },
  ...ACTIONS.map((a) => ({ label: a, value: a })),
])

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)))

/**
 * 列定义（UX-04b 逐页迁移）：表格改由 `NDataTable` 渲染，宽表靠 `scroll-x` 横滚，
 * 与原先 `.table-scroll` 的行为一致。空值统一显示 `—`，避免审计表里出现成片空白格子。
 */
const columns = computed<DataTableColumns<AuditEntry>>(() => [
  {
    title: t('audit.time'),
    key: 'occurredAt',
    width: 170,
    render: (row) => formatDateTime(row.occurredAt),
  },
  { title: t('audit.operator'), key: 'operator', width: 120, render: (row) => row.operator ?? '—' },
  { title: t('audit.action'), key: 'action', width: 160 },
  { title: t('audit.resourceType'), key: 'resourceType', width: 120 },
  {
    title: t('audit.resourceId'),
    key: 'resourceId',
    width: 110,
    render: (row) => row.resourceId ?? '—',
  },
  {
    title: t('audit.summary'),
    key: 'summary',
    minWidth: 280,
    // 摘要可能很长：单行省略 + 悬停看全文，避免撑高整行
    ellipsis: { tooltip: true },
    render: (row) => row.summary || '—',
  },
  { title: t('audit.ip'), key: 'ip', width: 130, render: (row) => row.ip ?? '—' },
])

function params(nextPage = page.value) {
  return {
    projectId: filter.projectId ? Number(filter.projectId) : null,
    action: filter.action,
    from: filter.from,
    to: filter.to,
    page: nextPage,
    size,
  }
}

async function load(nextPage = page.value): Promise<void> {
  error.value = null
  loading.value = true
  try {
    const result = await auditApi.page(params(nextPage))
    rows.value = result.items
    total.value = result.total
    page.value = result.page
  } catch (e) {
    error.value = localizeError(e)
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search(): void {
  message.value = ''
  void load(1)
}

function turnPage(next: number): void {
  if (next < 1 || next > pageCount.value) return
  void load(next)
}

async function exportCsv(): Promise<void> {
  error.value = null
  message.value = ''
  try {
    const blob = await auditApi.exportCsv(params(1))
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `audit-${new Date().toISOString().slice(0, 10)}.csv`
    anchor.click()
    URL.revokeObjectURL(url)
    message.value = t('audit.exported')
  } catch (e) {
    error.value = localizeError(e)
  }
}

onMounted(() => {
  void load(1)
})
</script>

<template>
  <div class="page">
    <h1 class="page-title">{{ t('audit.title') }}</h1>
    <p class="muted">{{ t('audit.hint') }}</p>

    <ErrorLineView v-if="error" :error="error" />
    <p v-if="message" class="text-ok">{{ message }}</p>

    <section class="card wide-card">
      <div class="toolbar">
        <NSelect
          v-model:value="filter.projectId"
          :options="projectOptions"
          style="width: 200px"
          :aria-label="t('audit.allProjects')"
        />
        <NSelect
          v-model:value="filter.action"
          :options="actionOptions"
          style="width: 180px"
          :aria-label="t('audit.action')"
        />
        <NDatePicker
          v-model:formatted-value="filter.from"
          type="date"
          value-format="yyyy-MM-dd"
          clearable
          style="width: 180px"
          :placeholder="t('audit.from')"
        />
        <NDatePicker
          v-model:formatted-value="filter.to"
          type="date"
          value-format="yyyy-MM-dd"
          clearable
          style="width: 180px"
          :placeholder="t('audit.to')"
        />
        <NButton type="primary" :loading="loading" @click="search">{{ t('audit.query') }}</NButton>
        <NButton quaternary @click="exportCsv">
          <template #icon><AppIcon name="download" :size="16" /></template>
          {{ t('audit.export') }}
        </NButton>
      </div>

      <DataTable
        :columns="columns"
        :data="rows"
        :loading="loading"
        :row-key="(row: AuditEntry) => row.id"
        class="audit-table"
        :empty-title="t('audit.empty')"
        empty-icon="audit"
        :error="Boolean(error)"
      />

      <NPagination
        v-if="total > 0"
        class="pager"
        :page="page"
        :page-count="pageCount"
        :page-slot="7"
        @update:page="turnPage"
      />
    </section>
  </div>
</template>
