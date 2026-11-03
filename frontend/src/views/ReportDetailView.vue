<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ApiError } from '@/api/http'
import {
  REPORT_METADATA_KEYS,
  reportsApi,
  type CompareResult,
  type CompareRow,
  type CompareSide,
  type CaseTrendResult,
  type PassRateResult,
  type ReportDetail,
  type ReportExportFormat,
  type ReportExportHistoryItem,
  type ReportExportMode,
  type ReportExportResult,
  type ReportLogLine,
  type ReportSuiteNode,
} from '@/api/reports'
import { useAuthStore } from '@/stores/auth'
import ProjectPicker from '@/components/ProjectPicker.vue'
import SuiteTree from '@/components/report/SuiteTree.vue'
import TrendChart from '@/components/report/TrendChart.vue'
import type { TrendChartSeries } from '@/components/report/trendChart'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const projectId = computed(() => auth.projectId ?? 0)
const runId = computed(() => String(route.params.runId ?? ''))

const detail = ref<ReportDetail | null>(null)
const tree = ref<ReportSuiteNode | null>(null)
const loading = ref(false)
const error = ref('')

const showCompare = ref(false)
const compare = ref<CompareResult | null>(null)
const compareLoading = ref(false)
const compareError = ref('')

// 趋势区块（RPT-04）：用例维度 + 环境断线 + 自写 SVG 折线。
const trend = ref<CaseTrendResult | null>(null)
const trendLoading = ref(false)
const trendError = ref('')
const selectedCase = ref('')
const granularity = ref<'day' | 'week'>('day')
const passRate = ref<PassRateResult | null>(null)
const passRateLoading = ref(false)
const passRateError = ref('')

const logOpen = ref(false)
const logCaseKey = ref('')
const logLines = ref<ReportLogLine[]>([])
const logTotal = ref(0)
const nextOffset = ref<number | null>(0)
const logLoading = ref(false)
const logError = ref('')

// 导出（RPT-05）：外部版受 exportable 门禁；内部版允许缺项但需二次确认。
const exportBusy = ref('')
const exportError = ref('')
const exportResult = ref<ReportExportResult | null>(null)
const exportHistory = ref<ReportExportHistoryItem[]>([])
const exportHistoryError = ref('')

function errText(e: unknown): string {
  return e instanceof ApiError ? `${e.codeName}：${e.message}` : String(e)
}

function metadataValue(key: string): string {
  const value = detail.value?.metadata?.[key]
  return value ?? ''
}

function metadataMissing(key: string): boolean {
  return metadataValue(key).trim().length === 0
}

function verdictClass(verdict: string): string {
  return `verdict-${(verdict || 'unknown').toLowerCase()}`
}

async function loadAll(): Promise<void> {
  error.value = ''
  if (!projectId.value || !runId.value) {
    detail.value = null
    tree.value = null
    return
  }
  loading.value = true
  try {
    const [d, tr] = await Promise.all([
      reportsApi.detail(projectId.value, runId.value),
      reportsApi.tree(projectId.value, runId.value),
    ])
    detail.value = d
    tree.value = tr
    await loadPassRate()
    await loadExports()
  } catch (e) {
    error.value = errText(e)
  } finally {
    loading.value = false
  }
}

async function loadCompare(): Promise<void> {
  compareError.value = ''
  compareLoading.value = true
  try {
    compare.value = await reportsApi.compare(projectId.value, runId.value)
  } catch (e) {
    compareError.value = errText(e)
  } finally {
    compareLoading.value = false
  }
}

async function toggleCompare(event: Event): Promise<void> {
  showCompare.value = (event.target as HTMLInputElement).checked
  if (showCompare.value && !compare.value) {
    await loadCompare()
  }
}

/** 本次运行里出现的全部 caseKey（趋势选择器的数据源）。 */
const caseKeys = computed(() => {
  const keys: string[] = []
  function walk(node: ReportSuiteNode): void {
    for (const item of node.cases) keys.push(item.caseKey)
    for (const child of node.suites) walk(child)
  }
  if (tree.value) walk(tree.value)
  return Array.from(new Set(keys))
})

const currentTrendPoint = computed(
  () => trend.value?.points.find((point) => point.runId === runId.value) ?? null,
)

/** 全局时间顺序：用 points 的升序下标做横轴，避免各环境各自从 0 开始错位。 */
const pointOrder = computed(() => {
  const order = new Map<string, number>()
  const points = trend.value?.points ?? []
  points.forEach((point, index) => order.set(point.runId, index))
  return order
})

/** 耗时折线：后端已按 envKey 分组，组内按时间升序；环境变化天然断开。 */
const durationSeries = computed<TrendChartSeries[]>(() =>
  (trend.value?.series ?? []).map((series) => ({
    key: series.envKey,
    label: series.envKey,
    points: series.points.map((point, index) => ({
      id: point.runId,
      label: point.runId,
      x: pointOrder.value.get(point.runId) ?? index,
      y: point.durationMs,
      deviated: point.deviated,
    })),
  })),
)

/** 通过率折线：bucket 为横轴，passRate 换算成百分比。 */
const passRateSeries = computed<TrendChartSeries[]>(() => {
  const buckets = passRate.value?.buckets ?? []
  if (buckets.length === 0) return []
  return [
    {
      key: 'pass-rate',
      label: t('reports.trend.passRateTitle'),
      points: buckets.map((bucket, index) => ({
        id: bucket.bucket,
        label: `${bucket.bucket} · ${bucket.pass}/${bucket.total}`,
        x: index,
        y: bucket.passRate * 100,
        deviated: false,
      })),
    },
  ]
})

async function loadTrend(): Promise<void> {
  trendError.value = ''
  if (!selectedCase.value || !projectId.value || !runId.value) {
    trend.value = null
    return
  }
  trendLoading.value = true
  try {
    trend.value = await reportsApi.trend(projectId.value, runId.value, selectedCase.value, 5)
  } catch (e) {
    trendError.value = errText(e)
  } finally {
    trendLoading.value = false
  }
}

async function loadPassRate(): Promise<void> {
  passRateError.value = ''
  if (!projectId.value) {
    passRate.value = null
    return
  }
  passRateLoading.value = true
  try {
    passRate.value = await reportsApi.passRate(projectId.value, granularity.value, 12)
  } catch (e) {
    passRateError.value = errText(e)
  } finally {
    passRateLoading.value = false
  }
}

function openRun(targetRunId: string): void {
  if (!targetRunId || targetRunId === runId.value) return
  void router.push({ name: 'report-detail', params: { runId: targetRunId } })
}

async function openLogs(caseKey: string): Promise<void> {
  if (logOpen.value && logCaseKey.value === caseKey) {
    logOpen.value = false
    return
  }
  logOpen.value = true
  logCaseKey.value = caseKey
  logLines.value = []
  logTotal.value = 0
  nextOffset.value = 0
  logError.value = ''
  await loadMoreLogs()
}

async function loadMoreLogs(): Promise<void> {
  if (nextOffset.value === null) return
  logError.value = ''
  logLoading.value = true
  try {
    const page = await reportsApi.logs(
      projectId.value,
      runId.value,
      logCaseKey.value,
      nextOffset.value,
      200,
    )
    // 增量追加：只把已加载段放进 DOM，未请求的历史行不入内存。
    logLines.value = [...logLines.value, ...page.lines]
    logTotal.value = page.total
    nextOffset.value = page.nextOffset
  } catch (e) {
    logError.value = errText(e)
  } finally {
    logLoading.value = false
  }
}

function historyText(side: CompareSide | null): string {
  return side ? `${side.verdict} · ${side.durationMs} ms` : t('reports.compare.noneBaseline')
}

function currentText(side: CompareSide | null): string {
  return side ? `${side.verdict} · ${side.durationMs} ms` : t('reports.compare.noneThisRun')
}

function changeClass(row: CompareRow): string {
  if (row.history?.verdict === 'FAIL' && row.current?.verdict === 'FAIL') {
    return 'change-both-fail'
  }
  return `change-${row.change.toLowerCase()}`
}

function backToList(): void {
  void router.push({ name: 'reports' })
}

async function loadExports(): Promise<void> {
  exportHistoryError.value = ''
  if (!projectId.value || !runId.value) {
    exportHistory.value = []
    return
  }
  try {
    exportHistory.value = await reportsApi.exports(projectId.value, runId.value)
  } catch (e) {
    exportHistoryError.value = errText(e)
  }
}

/** 外部导出：缺项时只提示并列出缺失键，不发请求；内部导出：二次确认后允许。 */
async function doExport(mode: ReportExportMode, format: ReportExportFormat): Promise<void> {
  if (!detail.value || exportBusy.value) return
  if (mode === 'external' && !detail.value.exportable) {
    return
  }
  if (mode === 'internal') {
    const keys = detail.value.metadataMissing.join(', ')
    const message = keys
      ? t('reports.exportSection.internalConfirm')
      : t('reports.exportSection.internalConfirmNoMissing')
    if (!window.confirm(message)) return
  }

  exportBusy.value = `${mode}-${format}`
  exportError.value = ''
  try {
    exportResult.value = await reportsApi.exportReport(
      projectId.value,
      runId.value,
      mode,
      format,
    )
    await loadExports()
  } catch (e) {
    exportError.value = t('reports.exportSection.failed', { msg: errText(e) })
  } finally {
    exportBusy.value = ''
  }
}

async function saveExport(downloadUrl?: string, fileName?: string): Promise<void> {
  const url = downloadUrl ?? exportResult.value?.downloadUrl
  if (!url) return
  exportError.value = ''
  try {
    const blob = await reportsApi.downloadExport(url)
    const objectUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download =
      fileName ??
      `report_${runId.value}_${exportResult.value?.mode ?? 'external'}.` +
        `${exportResult.value?.format ?? 'pdf'}`
    link.click()
    URL.revokeObjectURL(objectUrl)
  } catch (e) {
    exportError.value = t('reports.exportSection.failed', { msg: errText(e) })
  }
}

function exportDownloadUrl(item: ReportExportHistoryItem): string {
  return `/api/exports/${item.exportId}/download?projectId=${projectId.value}`
}

function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(2)} MB`
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${bytes} B`
}

function formatExportTime(value: string | null): string {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

watch([projectId, runId], () => {
  compare.value = null
  showCompare.value = false
  logOpen.value = false
  trend.value = null
  passRate.value = null
  selectedCase.value = ''
  exportResult.value = null
  exportError.value = ''
  exportHistory.value = []
  void loadAll()
}, { immediate: true })

// 树加载后默认选中第一条用例；换运行/换项目时重新取默认值。
watch(caseKeys, (keys) => {
  if (!keys.includes(selectedCase.value)) {
    selectedCase.value = keys[0] ?? ''
  }
}, { immediate: true })

watch(selectedCase, () => {
  void loadTrend()
})

watch(granularity, () => {
  void loadPassRate()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <button class="link-btn" type="button" @click="backToList">
          ← {{ t('reports.backToList') }}
        </button>
        <h1 class="page-title">{{ runId }}</h1>
      </div>
      <ProjectPicker />
    </div>

    <p v-if="error" class="text-fail">{{ error }}</p>
    <p v-if="loading" class="muted">{{ t('common.loading') }}</p>

    <template v-if="detail">
      <div class="toolbar summary-bar">
        <span class="text-ok">PASS {{ detail.passCount }}</span>
        <span class="text-fail">FAIL {{ detail.failCount }}</span>
        <span class="muted">SKIP {{ detail.skipCount }}</span>
        <span v-if="detail.exportable" class="text-ok">{{ t('reports.exportable') }}</span>
        <span v-else class="text-fail">
          {{ t('reports.notExportable') }}：{{ detail.metadataMissing.join(', ') }}
        </span>
      </div>

      <section class="card wide-card">
        <h2 class="card-title">{{ t('reports.metadataTitle') }}</h2>
        <table class="table meta-table">
          <tbody>
            <tr v-for="key in REPORT_METADATA_KEYS" :key="key">
              <th>{{ key }}</th>
              <td v-if="metadataMissing(key)" class="text-fail">
                {{ t('reports.missingValue') }}
              </td>
              <td v-else>{{ metadataValue(key) }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <div class="toolbar export-bar">
        <span class="muted export-title">{{ t('reports.exportSection.title') }}</span>
        <button
          class="btn"
          type="button"
          :disabled="!detail.exportable || Boolean(exportBusy)"
          :title="
            !detail.exportable
              ? t('reports.exportSection.missingHint', {
                  keys: detail.metadataMissing.join(', '),
                })
              : ''
          "
          @click="doExport('external', 'pdf')"
        >
          {{
            exportBusy === 'external-pdf'
              ? t('reports.exportSection.exporting')
              : t('reports.exportSection.externalPdf')
          }}
        </button>
        <button
          class="btn"
          type="button"
          :disabled="!detail.exportable || Boolean(exportBusy)"
          :title="
            !detail.exportable
              ? t('reports.exportSection.missingHint', {
                  keys: detail.metadataMissing.join(', '),
                })
              : ''
          "
          @click="doExport('external', 'docx')"
        >
          {{
            exportBusy === 'external-docx'
              ? t('reports.exportSection.exporting')
              : t('reports.exportSection.externalWord')
          }}
        </button>
        <button
          class="btn btn-ghost"
          type="button"
          :disabled="Boolean(exportBusy)"
          @click="doExport('internal', 'pdf')"
        >
          {{
            exportBusy === 'internal-pdf'
              ? t('reports.exportSection.exporting')
              : t('reports.exportSection.internalPdf')
          }}
        </button>
        <button
          class="btn btn-ghost"
          type="button"
          :disabled="Boolean(exportBusy)"
          @click="doExport('internal', 'docx')"
        >
          {{
            exportBusy === 'internal-docx'
              ? t('reports.exportSection.exporting')
              : t('reports.exportSection.internalWord')
          }}
        </button>
      </div>
      <p v-if="!detail.exportable" class="text-fail export-hint">
        {{
          t('reports.exportSection.missingHint', {
            keys: detail.metadataMissing.join(', '),
          })
        }}
      </p>
      <p v-if="exportError" class="text-fail">{{ exportError }}</p>
      <p v-if="exportResult" class="text-ok export-result">
        {{
          t('reports.exportSection.done', {
            format: exportResult.format.toUpperCase(),
            size: formatSize(exportResult.bytes),
            sha256: exportResult.sha256,
          })
        }}
        <button class="link-btn" type="button" @click="saveExport()">
          {{ t('reports.exportSection.download') }}
        </button>
      </p>

      <section class="card wide-card">
        <h2 class="card-title">{{ t('reports.exportSection.historyTitle') }}</h2>
        <p v-if="exportHistoryError" class="text-fail">{{ exportHistoryError }}</p>
        <p v-else-if="!exportHistory.length" class="muted">
          {{ t('reports.exportSection.historyEmpty') }}
        </p>
        <table v-else class="table">
          <thead>
            <tr>
              <th>{{ t('reports.exportSection.historyFormat') }}</th>
              <th>{{ t('reports.exportSection.historyMode') }}</th>
              <th>{{ t('reports.exportSection.historySize') }}</th>
              <th>{{ t('reports.exportSection.historyTime') }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in exportHistory" :key="item.exportId">
              <td>{{ item.format.toUpperCase() }}</td>
              <td :class="item.mode === 'internal' ? 'text-fail' : ''">
                {{
                  item.mode === 'internal'
                    ? t('reports.exportSection.internalTag')
                    : t('reports.exportSection.externalTag')
                }}
              </td>
              <td>{{ formatSize(item.bytes) }}</td>
              <td>{{ formatExportTime(item.createdAt) }}</td>
              <td>
                <button
                  class="link-btn"
                  type="button"
                  @click="saveExport(exportDownloadUrl(item), item.fileName)"
                >
                  {{ t('reports.exportSection.historyDownload') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="card wide-card">
        <div class="section-head">
          <h2 class="card-title">{{ t('reports.trend.title') }}</h2>
          <label class="trend-case muted">
            {{ t('reports.trend.case') }}
            <select v-model="selectedCase" class="input trend-select">
              <option v-for="key in caseKeys" :key="key" :value="key">{{ key }}</option>
            </select>
          </label>
        </div>

        <p v-if="trendError" class="text-fail">{{ trendError }}</p>
        <p v-if="trendLoading" class="muted">{{ t('common.loading') }}</p>

        <template v-else-if="trend">
          <div class="toolbar trend-hints">
            <span class="muted">{{ t('reports.trend.envHint') }}</span>
            <span class="muted">{{ t('reports.trend.currentHint') }}</span>
          </div>

          <h3 class="card-title trend-subtitle">{{ t('reports.trend.durationTitle') }}</h3>
          <p
            v-if="trend.baselineMs !== null"
            :class="trend.deviated ? 'trend-deviated' : 'muted'"
          >
            {{
              trend.deviated && currentTrendPoint
                ? t('reports.trend.deviated', {
                    ms: currentTrendPoint.durationMs,
                    base: trend.baselineMs,
                  })
                : t('reports.trend.baseline', { ms: trend.baselineMs })
            }}
          </p>
          <TrendChart
            :series="durationSeries"
            :baseline="trend.baselineMs"
            unit="ms"
            clickable
            :current-id="runId"
            @select="openRun"
          />

          <div class="pass-rate-block">
            <div class="section-head">
              <h3 class="card-title">{{ t('reports.trend.passRateTitle') }}</h3>
              <div class="segmented">
                <button
                  class="btn btn-ghost"
                  :class="{ active: granularity === 'day' }"
                  type="button"
                  @click="granularity = 'day'"
                >
                  {{ t('reports.trend.day') }}
                </button>
                <button
                  class="btn btn-ghost"
                  :class="{ active: granularity === 'week' }"
                  type="button"
                  @click="granularity = 'week'"
                >
                  {{ t('reports.trend.week') }}
                </button>
              </div>
            </div>
            <p v-if="passRateError" class="text-fail">{{ passRateError }}</p>
            <p v-if="passRateLoading" class="muted">{{ t('common.loading') }}</p>
            <TrendChart v-else :series="passRateSeries" unit="%" />
          </div>
        </template>
        <p v-else class="muted">{{ t('reports.trend.empty') }}</p>
      </section>

      <section class="card wide-card">
        <div class="section-head">
          <h2 class="card-title">{{ t('reports.treeTitle') }}</h2>
          <label class="compare-toggle muted">
            <input type="checkbox" :checked="showCompare" @change="toggleCompare" />
            {{ t('reports.compare.toggle') }}
          </label>
        </div>

        <SuiteTree v-if="tree" :node="tree" :depth="0" @open-logs="openLogs" />
        <p v-else class="muted">{{ t('reports.treeEmpty') }}</p>

        <div v-if="logOpen" class="log-panel">
          <div class="section-head">
            <h3 class="card-title">{{ t('reports.logTitle') }} · {{ logCaseKey }}</h3>
            <span class="muted">
              {{ t('reports.logTotal', { total: logTotal, loaded: logLines.length }) }}
            </span>
          </div>
          <p v-if="logError" class="text-fail">{{ logError }}</p>
          <pre v-if="logLines.length" class="log-lines"><span
  v-for="line in logLines"
  :key="line.seq"
  class="log-line"
  :class="`level-${line.level.toLowerCase()}`"
>{{ line.seq }} [{{ line.keyword }}] {{ line.message }}
</span></pre>
          <p v-else class="muted">{{ t('reports.logEmpty') }}</p>
          <div class="toolbar">
            <button
              v-if="nextOffset !== null"
              class="btn btn-ghost"
              type="button"
              :disabled="logLoading"
              @click="loadMoreLogs"
            >
              {{ logLoading ? t('common.loading') : t('reports.loadMore') }}
            </button>
          </div>
        </div>
      </section>

      <section v-if="showCompare" class="card wide-card">
        <div class="section-head">
          <h2 class="card-title">{{ t('reports.compare.title') }}</h2>
          <span v-if="compare?.baseline" class="muted">
            {{ t('reports.compare.baseline', { runId: compare.baseline.runId }) }}
          </span>
          <span v-else-if="compare" class="text-fail">{{ t('reports.compare.noBaseline') }}</span>
        </div>

        <p v-if="compareError" class="text-fail">{{ compareError }}</p>
        <p v-if="compareLoading" class="muted">{{ t('common.loading') }}</p>

        <template v-if="compare">
          <div class="toolbar summary-bar">
            <span class="change-both-fail">
              {{ t('reports.compare.summary.bothFail', { count: compare.summary.bothFail }) }}
            </span>
            <span class="change-pass_to_fail">
              {{ t('reports.compare.summary.newFail', { count: compare.summary.newFail }) }}
            </span>
            <span class="change-fail_to_pass">
              {{ t('reports.compare.summary.newPass', { count: compare.summary.newPass }) }}
            </span>
            <span class="muted">
              {{ t('reports.compare.summary.notRun', { count: compare.summary.notRun }) }}
            </span>
          </div>

          <table class="table compare-table">
            <thead>
              <tr>
                <th>{{ t('reports.case') }}</th>
                <th>{{ t('reports.compare.history') }}</th>
                <th>{{ t('reports.compare.current') }}</th>
                <th>{{ t('reports.compare.change') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in compare.rows" :key="row.caseKey">
                <td>{{ row.caseKey }}</td>
                <td :class="row.history ? verdictClass(row.history.verdict) : 'muted'">
                  {{ historyText(row.history) }}
                </td>
                <td :class="row.current ? verdictClass(row.current.verdict) : 'muted'">
                  {{ currentText(row.current) }}
                </td>
                <td>
                  <span class="badge" :class="changeClass(row)">
                    {{ t(`reports.compare.changes.${row.change}`) }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </template>
      </section>
    </template>
  </div>
</template>

<style scoped>
.summary-bar {
  gap: 16px;
}

.meta-table th {
  width: 140px;
  color: var(--muted);
  font-weight: 500;
}

.compare-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.trend-case {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.trend-select {
  width: auto;
  min-width: 220px;
}

.trend-hints {
  gap: 16px;
}

.trend-subtitle {
  margin: 12px 0 6px;
}

.trend-deviated {
  color: #f0b429;
}

.pass-rate-block {
  margin-top: 18px;
  border-top: 1px solid #22303f;
  padding-top: 10px;
}

.segmented {
  display: inline-flex;
  gap: 6px;
}

.segmented .active {
  color: var(--text);
  border-color: #7cc6fe;
}

.log-panel {
  margin-top: 14px;
  border-top: 1px solid #22303f;
  padding-top: 10px;
}

.log-lines {
  max-height: 420px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  border-radius: 8px;
  background: #11161d;
  color: #d8e1ea;
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.log-line {
  display: block;
}

.level-warn {
  color: #f0b429;
}

.level-error,
.level-fail {
  color: #ef5350;
}

.change-both-fail {
  color: #f0b429;
}

.change-pass_to_fail {
  color: var(--fail);
}

.change-fail_to_pass {
  color: var(--ok);
}

.change-same,
.change-not_run {
  color: var(--muted);
}

/* 窄屏：对比表上下堆叠，避免双列被压成单字符宽度 */
@media (max-width: 760px) {
  .compare-table thead {
    display: none;
  }

  .compare-table,
  .compare-table tbody,
  .compare-table tr,
  .compare-table td {
    display: block;
    width: 100%;
  }

  .compare-table tr {
    border-bottom: 1px solid #22303f;
    padding: 6px 0;
  }

  .compare-table td {
    border-bottom: none;
    padding: 3px 10px;
  }
}
</style>
