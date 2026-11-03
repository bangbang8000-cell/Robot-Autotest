<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ApiError } from '@/api/http'
import { executionApi, runLogSocketUrl, type ExecutionRun, type RunLogLine } from '@/api/execution'
import { useAuthStore } from '@/stores/auth'
import ProjectPicker from '@/components/ProjectPicker.vue'

const { t, te } = useI18n()
const auth = useAuthStore()

const runs = ref<ExecutionRun[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const stateFilter = ref('')
const loading = ref(false)
const error = ref('')
const message = ref('')

const selected = ref<ExecutionRun | null>(null)
const logLines = ref<RunLogLine[]>([])
const socketState = ref<'idle' | 'connecting' | 'open' | 'closed'>('idle')
const logPanel = ref<HTMLElement | null>(null)

const projectId = computed(() => auth.projectId ?? 0)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)))

let socket: WebSocket | null = null
let closedByUser = false
let reconnectTimer: number | undefined

function errText(e: unknown): string {
  return e instanceof ApiError ? `${e.codeName}：${e.message}` : String(e)
}

function stateLabel(state: string): string {
  const key = `execution.states.${state.toLowerCase()}`
  return te(key) ? t(key) : state
}

async function load(): Promise<void> {
  error.value = ''
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
    error.value = errText(e)
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
    error.value = errText(e)
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
    error.value = errText(e)
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
  error.value = ''
  message.value = ''
  try {
    await executionApi.cancel(projectId.value, run.runId, t('execution.cancelReason'))
    message.value = t('execution.canceled', { runId: run.runId })
    if (selected.value?.runId === run.runId) {
      selected.value = await executionApi.get(projectId.value, run.runId)
    }
    await load()
  } catch (e) {
    error.value = errText(e)
  }
}

function turnPage(next: number): void {
  if (next < 1 || next > pageCount.value) return
  page.value = next
  void load()
}

watch([projectId], () => {
  page.value = 1
  selected.value = null
  disconnect()
  logLines.value = []
  void load()
})

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
      <select v-model="stateFilter" class="input">
        <option value="">{{ t('execution.allStates') }}</option>
        <option value="QUEUED">{{ t('execution.states.queued') }}</option>
        <option value="PREPARING">{{ t('execution.states.preparing') }}</option>
        <option value="RUNNING">{{ t('execution.states.running') }}</option>
        <option value="FINISHED">{{ t('execution.states.finished') }}</option>
        <option value="FAILED">{{ t('execution.states.failed') }}</option>
        <option value="CANCELED">{{ t('execution.states.canceled') }}</option>
      </select>
      <button class="btn btn-ghost" type="button" @click="load">{{ t('common.refresh') }}</button>
      <RouterLink class="btn btn-ghost" to="/plans">{{ t('plans.title') }}</RouterLink>
    </div>

    <p v-if="error" class="text-fail">{{ error }}</p>
    <p v-if="message" class="text-ok">{{ message }}</p>
    <p v-if="loading" class="muted">{{ t('common.loading') }}</p>

    <table v-if="runs.length" class="table">
      <thead>
        <tr>
          <th>{{ t('execution.runId') }}</th>
          <th>{{ t('execution.state') }}</th>
          <th>{{ t('execution.tests') }}</th>
          <th>{{ t('execution.failureReason') }}</th>
          <th>{{ t('execution.startedAt') }}</th>
          <th>{{ t('execution.finishedAt') }}</th>
          <th>{{ t('common.actions') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="run in runs" :key="run.id">
          <td>{{ run.runId }}</td>
          <td>{{ stateLabel(run.state) }}</td>
          <td>{{ run.passedTests }} / {{ run.totalTests }}</td>
          <td>{{ run.failureReason || '—' }}</td>
          <td>{{ run.startedAt || '—' }}</td>
          <td>{{ run.finishedAt || '—' }}</td>
          <td>
            <button class="link-btn" type="button" @click="selectRun(run)">
              {{ t('execution.viewLog') }}
            </button>
            <button
              v-if="run.state !== 'FINISHED' && run.state !== 'FAILED' && run.state !== 'CANCELED'"
              class="link-btn danger"
              type="button"
              @click="cancelRun(run)"
            >
              {{ t('execution.cancelRun') }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>

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

    <div v-if="selected" class="card log-card">
      <div class="log-head">
        <h2 class="card-title">{{ t('execution.logPanel') }} · {{ selected.runId }}</h2>
        <span class="muted">
          {{ stateLabel(selected.state) }} · {{ t(`execution.socket.${socketState}`) }}
        </span>
        <label class="muted only-fail">
          <input v-model="onlyFail" type="checkbox" />
          {{ t('execution.onlyFail') }}
        </label>
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
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.only-fail {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
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

.level-error {
  color: #ef5350;
}
</style>
