<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { reportsApi, type ReportListItem, type RetentionResult } from '@/api/reports'
import { useAuthStore } from '@/stores/auth'
import ProjectPicker from '@/components/ProjectPicker.vue'
import ErrorLineView from '@/components/ErrorLine.vue'
import { localizeError, type ErrorLine } from '@/utils/errors'
import { formatBytes } from '@/utils/format'
import { runStatusLabel } from '@/utils/statusLabels'

const { t, te } = useI18n()
const auth = useAuthStore()
const router = useRouter()

const items = ref<ReportListItem[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)
const error = ref<ErrorLine | null>(null)

// 产物清理（RPT-06）：先 dry-run 展示清单，二次确认后才执行。
const retentionOpen = ref(false)
const retentionLoading = ref(false)
const retentionError = ref<ErrorLine | null>(null)
const retention = ref<RetentionResult | null>(null)
const retentionDone = ref<RetentionResult | null>(null)

const projectId = computed(() => auth.projectId ?? 0)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)))


async function load(): Promise<void> {
  error.value = null
  if (!projectId.value) {
    items.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const result = await reportsApi.page(projectId.value, page.value, size)
    items.value = result.items
    total.value = result.total
  } catch (e) {
    error.value = localizeError(e)
  } finally {
    loading.value = false
  }
}

function turnPage(next: number): void {
  if (next < 1 || next > pageCount.value) return
  page.value = next
  void load()
}

function openReport(runId: string): void {
  void router.push({ name: 'report-detail', params: { runId } })
}

function reasonLabel(reason: string): string {
  const key = `reports.retention.reasons.${reason}`
  return te(key) ? t(key) : reason
}

async function openRetention(): Promise<void> {
  retentionOpen.value = true
  retentionDone.value = null
  await runRetention(true)
}

async function runRetention(dryRun: boolean): Promise<void> {
  retentionError.value = null
  retentionLoading.value = true
  try {
    retention.value = await reportsApi.retention(projectId.value, dryRun)
    if (!dryRun) retentionDone.value = retention.value
  } catch (e) {
    retentionError.value = localizeError(e)
  } finally {
    retentionLoading.value = false
  }
}

async function executeRetention(): Promise<void> {
  if (!window.confirm(t('reports.retention.confirm'))) return
  await runRetention(false)
  if (retention.value && !retention.value.dryRun) {
    retentionOpen.value = true
    await load()
  }
}

function closeRetention(): void {
  retentionOpen.value = false
}

watch(projectId, () => {
  page.value = 1
  void load()
}, { immediate: true })
</script>

<template>
  <div class="page">
    <div class="page-head">
      <h1 class="page-title">{{ t('menu.reports') }}</h1>
      <ProjectPicker />
    </div>

    <div class="toolbar">
      <button class="btn btn-ghost" type="button" @click="load">{{ t('common.refresh') }}</button>
      <button
        v-if="auth.isAdmin"
        class="btn btn-ghost"
        type="button"
        @click="openRetention"
      >
        {{ t('reports.retention.open') }}
      </button>
    </div>

    <section v-if="retentionOpen && auth.isAdmin" class="card wide-card retention-panel">
      <div class="section-head">
        <h2 class="card-title">{{ t('reports.retention.title') }}</h2>
        <button class="btn btn-ghost" type="button" @click="closeRetention">
          {{ t('reports.retention.close') }}
        </button>
      </div>
      <p class="muted">{{ t('reports.retention.hint') }}</p>

      <ErrorLineView v-if="retentionError" :error="retentionError" />
      <p v-if="retentionLoading" class="muted">{{ t('common.loading') }}</p>

      <template v-else-if="retention">
        <p v-if="retentionDone" class="text-ok">
          {{
            t('reports.retention.done', {
              count: retentionDone.runCount,
              files: retentionDone.deletedFiles,
              size: formatBytes(retentionDone.totalBytes),
            })
          }}
        </p>
        <p class="muted">
          {{ t('reports.retention.keptRecent', { count: retention.keptRecentRuns }) }}
          · {{ t('reports.retention.projectBytes', { size: formatBytes(retention.projectBytes) }) }}
        </p>

        <p v-if="retention.items.length === 0" class="muted">
          {{ t('reports.retention.empty') }}
        </p>
        <template v-else>
          <p class="muted">
            {{
              t('reports.retention.total', {
                count: retention.runCount,
                size: formatBytes(retention.totalBytes),
              })
            }}
          </p>
          <table class="table retention-table">
            <thead>
              <tr>
                <th>{{ t('reports.retention.runId') }}</th>
                <th>{{ t('reports.retention.bytes') }}</th>
                <th>{{ t('reports.retention.reason') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in retention.items" :key="item.runId">
                <td>{{ item.runId }}</td>
                <td>{{ formatBytes(item.bytes) }}</td>
                <td>{{ reasonLabel(item.reason) }}</td>
              </tr>
            </tbody>
          </table>
          <div v-if="retention.dryRun" class="toolbar">
            <button
              class="btn"
              type="button"
              :disabled="retentionLoading"
              @click="executeRetention"
            >
              {{ retentionLoading ? t('reports.retention.executing') : t('reports.retention.execute') }}
            </button>
          </div>
        </template>
      </template>
    </section>

    <ErrorLineView v-if="error" :error="error" />
    <p v-if="loading" class="muted">{{ t('common.loading') }}</p>

    <div v-if="items.length" class="table-scroll">
    <table class="table">
      <thead>
        <tr>
          <th>{{ t('reports.runId') }}</th>
          <th>{{ t('reports.state') }}</th>
          <th>{{ t('reports.counts') }}</th>
          <th>{{ t('reports.startedAt') }}</th>
          <th>{{ t('reports.exportable') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.runId">
          <td>
            <button class="link-btn" type="button" @click="openReport(item.runId)">
              {{ item.runId }}
            </button>
          </td>
          <td>{{ runStatusLabel(item.state) }}</td>
          <td>
            <span class="text-ok">{{ item.passCount }}</span>
            /
            <span class="text-fail">{{ item.failCount }}</span>
            /
            <span class="muted">{{ item.skipCount }}</span>
          </td>
          <td>{{ item.startedAt || '—' }}</td>
          <td>
            <span v-if="item.exportable" class="text-ok">{{ t('reports.exportable') }}</span>
            <span v-else class="text-fail">
              {{ t('reports.notExportable') }}
              <span class="missing-keys">{{ item.metadataMissing.join(', ') }}</span>
            </span>
          </td>
        </tr>
      </tbody>
    </table>
    </div>

    <p v-if="!loading && !error && total === 0" class="muted">{{ t('common.noData') }}</p>

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
  </div>
</template>

<style scoped>
.missing-keys {
  color: var(--text-muted);
  font-size: 12px;
  margin-left: 6px;
}

.retention-panel {
  margin-bottom: 16px;
}
</style>
