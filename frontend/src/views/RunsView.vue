<script setup lang="ts">
import { computed, h, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NCheckbox,
  NPagination,
  NSelect,
  type DataTableColumns,
} from 'naive-ui'
import { executionApi, runLogSocketUrl, type ExecutionRun, type RunLogLine } from '@/api/execution'
import { useAuthStore } from '@/stores/auth'
import DataTable from '@/components/DataTable.vue'
import ProjectPicker from '@/components/ProjectPicker.vue'
import ErrorLineView from '@/components/ErrorLine.vue'
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

const onlyFail = ref(false)

/** 只看失败：匹配文本含 FAIL/FAILED，或服务端判定的 error 级别。 */
const visibleLines = computed(() =>
  onlyFail.value
    ? logLines.value.filter((line) => /fail/i.test(line.text) || line.level === 'error')
    : logLines.value,
)

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
        <NCheckbox v-model:checked="onlyFail" class="only-fail">
          {{ t('execution.onlyFail') }}
        </NCheckbox>
      </div>
      <pre v-if="visibleLines.length" ref="logPanel" class="log-lines">
<span
  v-for="line in visibleLines"
  :key="`${line.seq}-${line.tsGen}`"
  class="log-line"
  :class="`level-${line.level.toLowerCase()}`"
>{{ line.tsGen }} [{{ line.source }}] {{ line.text }}
</span></pre>
      <p v-else class="muted">{{ t('execution.emptyLog') }}</p>
    </div>
  </div>
</template>

<style scoped>
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
