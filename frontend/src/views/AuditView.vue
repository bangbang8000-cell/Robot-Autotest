<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { auditApi, type AuditEntry } from '@/api/audit'
import { useAuthStore } from '@/stores/auth'
import ErrorLineView from '@/components/ErrorLine.vue'
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

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)))


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
        <select v-model="filter.projectId" class="input">
          <option value="">{{ t('audit.allProjects') }}</option>
          <option v-for="p in auth.projects" :key="p.id" :value="String(p.id)">
            {{ p.code }} · {{ p.name }}
          </option>
        </select>
        <select v-model="filter.action" class="input">
          <option value="">{{ t('audit.allActions') }}</option>
          <option v-for="a in ACTIONS" :key="a" :value="a">{{ a }}</option>
        </select>
        <input v-model="filter.from" class="input" type="date" :aria-label="t('audit.from')" />
        <input v-model="filter.to" class="input" type="date" :aria-label="t('audit.to')" />
        <button class="btn" type="button" :disabled="loading" @click="search">
          {{ t('audit.query') }}
        </button>
        <button class="btn btn-ghost" type="button" @click="exportCsv">
          {{ t('audit.export') }}
        </button>
      </div>

      <p v-if="loading" class="muted">{{ t('common.loading') }}</p>
      <table v-if="rows.length" class="table">
        <thead>
          <tr>
            <th>{{ t('audit.time') }}</th>
            <th>{{ t('audit.operator') }}</th>
            <th>{{ t('audit.action') }}</th>
            <th>{{ t('audit.resourceType') }}</th>
            <th>{{ t('audit.resourceId') }}</th>
            <th>{{ t('audit.summary') }}</th>
            <th>{{ t('audit.ip') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td>{{ formatDateTime(row.occurredAt) }}</td>
            <td>{{ row.operator ?? '—' }}</td>
            <td>{{ row.action }}</td>
            <td>{{ row.resourceType }}</td>
            <td>{{ row.resourceId ?? '—' }}</td>
            <td class="audit-summary">{{ row.summary || '—' }}</td>
            <td>{{ row.ip ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
      <EmptyState v-if="!loading && total === 0" :title="t('audit.empty')" icon="audit" />

      <div v-if="total > 0" class="pager">
        <button class="btn btn-ghost" type="button" :disabled="page <= 1" @click="turnPage(page - 1)">
          {{ t('common.prev') }}
        </button>
        <span class="muted">{{ t('common.pageInfo', { page, total: pageCount }) }}</span>
        <button
          class="btn btn-ghost"
          type="button"
          :disabled="page >= pageCount"
          @click="turnPage(page + 1)"
        >
          {{ t('common.next') }}
        </button>
      </div>
    </section>
  </div>
</template>
