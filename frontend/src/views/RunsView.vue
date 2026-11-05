<script setup lang="ts">
import { computed, h, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NCheckbox,
  NInput,
  NInputNumber,
  NPagination,
  NSelect,
  type DataTableColumns,
} from 'naive-ui'
import { executionApi, runLogSocketUrl, type ExecutionRun, type RunLogLine } from '@/api/execution'
import { caseSetApi, type TestSet } from '@/api/cases'
import { deviceApi, type Device } from '@/api/asset'
import { useAuthStore } from '@/stores/auth'
import DataTable from '@/components/DataTable.vue'
import ProjectPicker from '@/components/ProjectPicker.vue'
import ErrorLineView from '@/components/ErrorLine.vue'
import FormField from '@/components/FormField.vue'
import StatusTag from '@/components/StatusTag.vue'
import { localizeError, type ErrorLine } from '@/utils/errors'
import { EMPTY, formatDateTime } from '@/utils/format'
import { RUN_STATUSES, runStatusLabel, runStatusTone } from '@/utils/statusLabels'

const { t } = useI18n()
const auth = useAuthStore()

const runs = ref<ExecutionRun[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const stateFilter = ref('')
const loading = ref(false)
const error = ref<ErrorLine | null>(null)
const message = ref('')

const selected = ref<ExecutionRun | null>(null)
const logLines = ref<RunLogLine[]>([])
const socketState = ref<'idle' | 'connecting' | 'open' | 'closed'>('idle')
const logPanel = ref<HTMLElement | null>(null)

const projectId = computed(() => auth.projectId ?? 0)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)))

/* ---------- EXEC-01 手动执行表单 ---------- */

const manualOpen = ref(false)
/** 'suite' = 直接填套件路径；'testSet' = 交给后端按用例编号解析套件。 */
const manualMode = ref('suite')
const manualSuites = ref('suites')
const manualTestSetId = ref<number | null>(null)
const manualEnv = ref('')
const manualConcurrency = ref(1)
const manualTimeout = ref(3600)
const manualDeviceKeys = ref<string[]>([])
const manualTag = ref('')
const manualBusy = ref(false)
const testSets = ref<TestSet[]>([])
const devices = ref<Device[]>([])

const manualSourceOptions = computed(() => [
  { label: t('execution.manual.sourceSuite'), value: 'suite' },
  { label: t('execution.manual.sourceTestSet'), value: 'testSet' },
])
const testSetOptions = computed(() =>
  testSets.value.map((set) => ({ label: `${set.name} (${set.setCode})`, value: set.id })))
/**
 * 设备范围选项：默认列本项目设备名，同时 `tag` 允许手填端口键（`DUT-01/mlx5_0`）。
 * 端口级互斥才是实际需求的粒度，只给设备名会让两台机器共用一块网卡时判错。
 */
const deviceOptions = computed(() =>
  devices.value.map((device) => ({ label: device.name, value: device.name })))
const manualPaths = computed(() =>
  manualSuites.value.split('\n').map((path) => path.trim()).filter((path) => path.length > 0))
const canManualSubmit = computed(() => {
  if (!projectId.value || manualBusy.value) return false
  return manualMode.value === 'suite'
    ? manualPaths.value.length > 0
    : manualTestSetId.value !== null
})

function openManual(): void {
  manualOpen.value = true
  void loadTestSets()
  void loadDevices()
}

function closeManual(): void {
  manualOpen.value = false
}

async function loadTestSets(): Promise<void> {
  if (!projectId.value) {
    testSets.value = []
    return
  }
  try {
    const result = await caseSetApi.page(projectId.value, { page: 1, size: 100 })
    testSets.value = result.items
  } catch (e) {
    error.value = localizeError(e)
  }
}

async function loadDevices(): Promise<void> {
  if (!projectId.value) {
    devices.value = []
    return
  }
  try {
    const result = await deviceApi.page(projectId.value, { page: 1, size: 100 })
    devices.value = result.items
  } catch (e) {
    error.value = localizeError(e)
  }
}

function parsedEnv(): number | null {
  const value = Number.parseInt(manualEnv.value.trim(), 10)
  return Number.isNaN(value) ? null : value
}

async function submitManual(): Promise<void> {
  if (!canManualSubmit.value) return
  error.value = null
  message.value = ''
  if (manualMode.value === 'suite' && manualPaths.value.length === 0) {
    error.value = { summary: t('execution.manual.needSuites'), detail: null }
    return
  }
  if (manualMode.value === 'testSet' && manualTestSetId.value === null) {
    error.value = { summary: t('execution.manual.needTestSet'), detail: null }
    return
  }
  manualBusy.value = true
  const testSetId = manualTestSetId.value
  try {
    const common = {
      environmentId: parsedEnv(),
      deviceKeys: manualDeviceKeys.value.length > 0 ? [...manualDeviceKeys.value] : undefined,
      concurrency: Number(manualConcurrency.value) || 1,
      timeoutSeconds: Number(manualTimeout.value) || 3600,
      includeTag: manualTag.value.trim() || null,
    }
    const run =
      manualMode.value === 'suite'
        ? await executionApi.quickRun({
            projectId: projectId.value,
            suitePaths: manualPaths.value,
            ...common,
            source: 'runs-view',
          })
        : await executionApi.runTestSet(projectId.value, testSetId as number, common)
    manualOpen.value = false
    page.value = 1
    await load()
    await selectRun(run)
    // load() 会清空 message，提示必须放在刷新之后
    message.value = t('execution.manual.created', { runId: run.runId })
  } catch (e) {
    error.value = localizeError(e)
  } finally {
    manualBusy.value = false
  }
}

const stateOptions = computed(() => [
  { label: t('execution.allStates'), value: '' },
  ...RUN_STATUSES.map((item) => ({ label: runStatusLabel(item), value: item.toUpperCase() })),
])

/** 终止态：之后再点「取消」没有意义（后端也会拒），按钮直接不渲染。 */
const TERMINAL_STATES = ['FINISHED', 'FAILED', 'CANCELED']

function isTerminal(state: string): boolean {
  return TERMINAL_STATES.includes(state)
}

/**
 * 运行记录列定义（UX-04b 逐页迁移）。
 *
 * 两个刻意的处理：
 * ① 状态列用 `StatusTag` 而不是纯文字 —— 六预设里 crimson/gold 与 fail/warn 同色相，
 *    只靠颜色区分不可靠，标签本身带图标+文字双编码；
 * ② 「查看日志」用 `text` 型按钮而不是 `link` 型 —— 链接样式在本页会被误读成外链，
 *    且 text 型仍保留键盘焦点环与禁用态。
 */
const columns = computed<DataTableColumns<ExecutionRun>>(() => [
  {
    title: t('execution.runId'),
    key: 'runId',
    width: 220,
    // RUN_ID 是三方关联的主键（RF / 看板 / 报告），长且不允许折行打乱对齐
    ellipsis: { tooltip: true },
  },
  {
    title: t('execution.state'),
    key: 'state',
    width: 120,
    render: (row) =>
      h(StatusTag, { label: runStatusLabel(row.state), tone: runStatusTone(row.state) }),
  },
  {
    title: t('execution.tests'),
    key: 'passedTests',
    width: 110,
    render: (row) => `${row.passedTests} / ${row.totalTests}`,
  },
  {
    title: t('execution.failureReason'),
    key: 'failureReason',
    minWidth: 200,
    ellipsis: { tooltip: true },
    render: (row) => row.failureReason || EMPTY,
  },
  {
    title: t('execution.startedAt'),
    key: 'startedAt',
    width: 180,
    render: (row) => formatDateTime(row.startedAt),
  },
  {
    title: t('execution.finishedAt'),
    key: 'finishedAt',
    width: 180,
    render: (row) => formatDateTime(row.finishedAt),
  },
  {
    title: t('common.actions'),
    key: 'actions',
    width: 180,
    render: (row) =>
      h('div', { class: 'row-actions' }, [
        h(
          NButton,
          {
            text: true,
            type: 'primary',
            size: 'small',
            onClick: () => void selectRun(row),
          },
          { default: () => t('execution.viewLog') },
        ),
        !isTerminal(row.state) &&
          h(
            NButton,
            {
              text: true,
              type: 'error',
              size: 'small',
              onClick: () => void cancelRun(row),
            },
            { default: () => t('execution.cancelRun') },
          ),
      ]),
  },
])

/** 当前看日志的那一行加底色，避免「打开了面板却不确定是哪条」的错配。 */
function rowClassName(row: ExecutionRun): string {
  return selected.value?.runId === row.runId ? 'row-active' : ''
}

let socket: WebSocket | null = null
let closedByUser = false
let reconnectTimer: number | undefined


async function load(): Promise<void> {
  error.value = null
  message.value = ''
  if (!projectId.value) {
    runs.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const result = await executionApi.page(projectId.value, {
      state: stateFilter.value,
      page: page.value,
      size,
    })
    runs.value = result.items
    total.value = result.total
  } catch (e) {
    error.value = localizeError(e)
  } finally {
    loading.value = false
  }
}

async function selectRun(run: ExecutionRun): Promise<void> {
  selected.value = run
  logLines.value = []
  await loadRecentLog()
  connect()
}

async function loadRecentLog(): Promise<void> {
  if (!selected.value) return
  try {
    logLines.value = await executionApi.recentLog(projectId.value, selected.value.runId, 200)
    scrollToBottom()
  } catch (e) {
    error.value = localizeError(e)
  }
}

function connect(): void {
  disconnect()
  if (!selected.value) return
  closedByUser = false
  socketState.value = 'connecting'
  try {
    socket = new WebSocket(runLogSocketUrl(selected.value.runId))
  } catch (e) {
    socketState.value = 'closed'
    error.value = localizeError(e)
    return
  }
  socket.onopen = () => {
    socketState.value = 'open'
  }
  socket.onmessage = (event: MessageEvent) => {
    try {
      const payload = JSON.parse(String(event.data)) as { type: string; lines: RunLogLine[] }
      if (payload.type !== 'log' || !Array.isArray(payload.lines)) return
      logLines.value = [...logLines.value, ...payload.lines].slice(-2000)
      scrollToBottom()
    } catch {
      // 非法帧忽略，不影响已展示内容
    }
  }
  socket.onerror = () => {
    socketState.value = 'closed'
  }
  socket.onclose = () => {
    socketState.value = 'closed'
    if (!closedByUser) {
      reconnectTimer = window.setTimeout(() => connect(), 3000)
    }
  }
}

function disconnect(): void {
  closedByUser = true
  if (reconnectTimer) {
    window.clearTimeout(reconnectTimer)
    reconnectTimer = undefined
  }
  if (socket) {
    socket.onclose = null
    socket.close()
    socket = null
  }
  socketState.value = 'idle'
}

/**
 * 日志级别过滤（FR-v0.4-20）：后端只产出 ERROR / WARN / INFO 三档（FAIL 在 RunLogService 里已归到
 * ERROR），所以这里给三个可独立勾选的档位，默认全开。三档全关也允许——面板会明说「当前级别过滤下
 * 没有日志」，而不是假装这份 run 没日志。
 */
type LogLevelFilter = 'error' | 'warn' | 'info'

const enabledLevels = ref<LogLevelFilter[]>(['error', 'warn', 'info'])
const downloadingLog = ref(false)

const levelOptions = computed<{ value: LogLevelFilter; label: string }[]>(() => [
  { value: 'error', label: t('execution.levelError') },
  { value: 'warn', label: t('execution.levelWarn') },
  { value: 'info', label: t('execution.levelInfo') },
])

/** 未知级别按 info 处理：宁可多显示一行，也不要把没见过的 level 藏掉。 */
function lineLevel(level: string): LogLevelFilter {
  const upper = level.toUpperCase()
  if (upper === 'ERROR') return 'error'
  if (upper === 'WARN') return 'warn'
  return 'info'
}

function toggleLevel(level: LogLevelFilter, checked: boolean): void {
  enabledLevels.value = checked
    ? [...enabledLevels.value, level]
    : enabledLevels.value.filter((item) => item !== level)
}

const visibleLines = computed(() =>
  logLines.value.filter((line) => enabledLevels.value.includes(lineLevel(line.level))),
)

async function downloadFullLog(): Promise<void> {
  const run = selected.value
  if (!run) return
  error.value = null
  message.value = ''
  downloadingLog.value = true
  try {
    const blob = await executionApi.downloadLog(projectId.value, run.runId)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `run-${run.runId}.log`
    link.click()
    URL.revokeObjectURL(url)
    message.value = t('execution.logDownloaded')
  } catch (e) {
    error.value = localizeError(e)
  } finally {
    downloadingLog.value = false
  }
}

function scrollToBottom(): void {
  requestAnimationFrame(() => {
    if (logPanel.value) {
      logPanel.value.scrollTop = logPanel.value.scrollHeight
    }
  })
}

async function cancelRun(run: ExecutionRun): Promise<void> {
  error.value = null
  message.value = ''
  try {
    await executionApi.cancel(projectId.value, run.runId, t('execution.cancelReason'))
    message.value = t('execution.canceled', { runId: run.runId })
    if (selected.value?.runId === run.runId) {
      selected.value = await executionApi.get(projectId.value, run.runId)
    }
    await load()
  } catch (e) {
    error.value = localizeError(e)
  }
}

function turnPage(next: number): void {
  if (next < 1 || next > pageCount.value) return
  page.value = next
  void load()
}

// immediate 同 CasesView：projectId 初值即目标值时 watch 不会触发，页面会永远空着
watch([projectId], () => {
  page.value = 1
  selected.value = null
  manualOpen.value = false
  disconnect()
  logLines.value = []
  void load()
}, { immediate: true })

watch([stateFilter], () => {
  page.value = 1
  void load()
})

onBeforeUnmount(() => {
  disconnect()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <h1 class="page-title">{{ t('menu.runs') }}</h1>
      <ProjectPicker />
    </div>

    <div class="toolbar">
      <NButton type="primary" @click="openManual">{{ t('execution.manual.open') }}</NButton>
      <NSelect
        v-model:value="stateFilter"
        :options="stateOptions"
        style="width: 180px"
        :aria-label="t('execution.state')"
      />
      <NButton quaternary :loading="loading" @click="load">{{ t('common.refresh') }}</NButton>
      <RouterLink v-slot="{ navigate }" to="/plans" custom>
        <NButton quaternary @click="navigate">{{ t('plans.title') }}</NButton>
      </RouterLink>
    </div>

    <ErrorLineView v-if="error" :error="error" />
    <p v-if="message" class="text-ok">{{ message }}</p>

    <div v-if="manualOpen" class="card form-card">
      <div class="form-head">
        <h2 class="card-title">{{ t('execution.manual.title') }}</h2>
        <NButton text @click="closeManual">{{ t('common.cancel') }}</NButton>
      </div>

      <div class="form-grid">
        <FormField :label="t('execution.manual.source')">
          <NSelect v-model:value="manualMode" :options="manualSourceOptions" />
        </FormField>

        <FormField
          v-if="manualMode === 'testSet'"
          :label="t('execution.manual.testSet')"
          required
          :hint="t('execution.manual.testSetHint')"
        >
          <NSelect
            v-model:value="manualTestSetId"
            :options="testSetOptions"
            filterable
            clearable
            :placeholder="t('execution.manual.noTestSets')"
          />
        </FormField>

        <FormField :label="t('execution.manual.environment')" :hint="t('execution.manual.environmentHint')">
          <NInput v-model:value="manualEnv" />
        </FormField>

        <FormField :label="t('execution.manual.concurrency')">
          <NInputNumber v-model:value="manualConcurrency" :min="1" :max="16" />
        </FormField>

        <FormField
          :label="t('execution.manual.deviceScope')"
          :hint="t('execution.manual.deviceScopeHint')"
        >
          <NSelect
            v-model:value="manualDeviceKeys"
            :options="deviceOptions"
            multiple
            filterable
            tag
            clearable
            :placeholder="t('execution.allDevices')"
          />
        </FormField>

        <FormField :label="t('execution.manual.timeout')">
          <NInputNumber v-model:value="manualTimeout" :min="1" :max="86400" />
        </FormField>

        <FormField :label="t('execution.manual.includeTag')">
          <NInput v-model:value="manualTag" :maxlength="64" />
        </FormField>
      </div>

      <FormField
        v-if="manualMode === 'suite'"
        :label="t('execution.manual.suitePaths')"
        required
        :hint="t('execution.manual.suitePathsHint')"
      >
        <NInput
          v-model:value="manualSuites"
          type="textarea"
          :autosize="{ minRows: 3, maxRows: 6 }"
        />
      </FormField>

      <div class="form-actions">
        <NButton type="primary" :disabled="!canManualSubmit" :loading="manualBusy" @click="submitManual">
          {{ t('execution.manual.submit') }}
        </NButton>
        <NButton quaternary @click="closeManual">{{ t('common.cancel') }}</NButton>
      </div>
    </div>

    <DataTable
      :columns="columns"
      :data="runs"
      :loading="loading"
      :row-key="(row: ExecutionRun) => row.id"
      :row-class-name="rowClassName"
      :empty-title="t('common.noData')"
      :empty-description="projectId ? undefined : t('overview.noProject')"
      empty-icon="runs"
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

    <div v-if="selected" class="card log-card">
      <div class="log-head">
        <h2 class="card-title">{{ t('execution.logPanel') }} · {{ selected.runId }}</h2>
        <StatusTag
          :label="runStatusLabel(selected.state)"
          :tone="runStatusTone(selected.state)"
        />
        <span class="muted">{{ t(`execution.socket.${socketState}`) }}</span>
        <span class="muted">
          {{ t('execution.deviceScopeLine', { scope: selected.deviceScope || t('execution.allDevices') }) }}
        </span>
        <!-- 重试可追溯（FR-v0.4-29）：首次尝试不渲染，只有重试记录才带「第 N 次尝试」 -->
        <span v-if="selected.attemptNo > 1" class="muted">
          {{ t('execution.attemptLine', { n: selected.attemptNo }) }}
        </span>
        <NCheckbox
          v-for="option in levelOptions"
          :key="option.value"
          :checked="enabledLevels.includes(option.value)"
          class="level-filter"
          @update:checked="(checked: boolean) => toggleLevel(option.value, checked)"
        >
          {{ option.label }}
        </NCheckbox>
        <NButton
          quaternary
          size="tiny"
          :loading="downloadingLog"
          @click="downloadFullLog"
        >
          {{ t('execution.downloadLog') }}
        </NButton>
      </div>
      <pre v-if="visibleLines.length" ref="logPanel" class="log-lines">
<span
  v-for="line in visibleLines"
  :key="`${line.seq}-${line.tsGen}`"
  class="log-line"
  :class="`level-${line.level.toLowerCase()}`"
>{{ line.tsGen }} [{{ line.source }}] {{ line.text }}
</span></pre>
      <p v-else class="muted">
        {{ t(enabledLevels.length < 3 ? 'execution.logFilteredEmpty' : 'execution.emptyLog') }}
      </p>
    </div>
  </div>
</template>

<style scoped>
.form-card {
  margin-bottom: 16px;
}

.form-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.form-head .card-title {
  margin: 0;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--space-3) var(--space-4);
}

.form-actions {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-4);
}

.log-card {
  margin-top: 16px;
}

.log-head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.log-head .card-title {
  margin: 0;
}

.only-fail {
  margin-left: auto;
}

.log-lines {
  max-height: 420px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  border-radius: 8px;
  background: var(--surface-2);
  color: var(--text);
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
  color: var(--warn);
}

.level-error {
  color: var(--fail);
}

/*
 * 当前打开日志的那一行：只加底色，不加边框 —— 边框会和表格自身的分隔线打架。
 * 用 accent-soft 而不是 surface-selected：后者是固定的中性蓝，换成金色/翡翠绿预设时
 * 会和整站强调色对不上；accent-soft 本来就是这个项目「选中」的统一语义色。
 */
:deep(.row-active td) {
  background: var(--accent-soft);
}

/* 操作列两个按钮并排时不能贴在一起；h() 渲染的节点拿不到 scoped 类，靠 :deep 兜 */
:deep(.row-actions) {
  display: flex;
  gap: var(--space-2);
}
</style>
