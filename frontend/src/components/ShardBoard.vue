<script setup lang="ts">
/**
 * 分片看板（v2.2 W2）。
 *
 * 分片不是前端自己算的：后端在 `execution_shard` 表里维护「带租约的状态行」，
 * 这里只做两件事——读回状态、给管理员/工程师提供「重新规划 / 手动聚合」两个动作。
 * 轮询 4 秒一轮，全终态（complete=true）自动停表，避免空转。
 *
 * 为什么不用表格：分片是 1~4 条，且每条要同时展示节点、租约、attempt、fencing、套件清单，
 * 表格列宽会把套件路径挤成省略号；卡片式一屏看完。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton, NInput, NInputNumber } from 'naive-ui'
import { shardApi, type RunShards, type ShardState } from '@/api/shards'
import StatusTag from '@/components/StatusTag.vue'
import { localizeError, type ErrorLine } from '@/utils/errors'
import type { StatusTone } from '@/utils/statusLabels'

const props = defineProps<{ runId: string; projectId: number }>()
const emit = defineEmits<{ (e: 'changed'): void }>()

const { t } = useI18n()

const data = ref<RunShards | null>(null)
const loading = ref(false)
const error = ref<ErrorLine | null>(null)
const busy = ref(false)

const planOpen = ref(false)
const planPaths = ref('')
const planNodes = ref(2)

const planList = computed(() =>
  planPaths.value
    .split('\n')
    .map((path) => path.trim())
    .filter((path) => path.length > 0),
)
const canPlan = computed(() => planList.value.length > 0 && !busy.value)

const TERMINAL: ShardState[] = ['DONE', 'FAILED', 'LOST']

const STATE_KEYS: Record<string, string> = {
  PENDING: 'execution.shard.statePending',
  LEASED: 'execution.shard.stateLeased',
  DONE: 'execution.shard.stateDone',
  FAILED: 'execution.shard.stateFailed',
  LOST: 'execution.shard.stateLost',
}

const TONES: Record<string, StatusTone> = {
  PENDING: 'neutral',
  LEASED: 'info',
  DONE: 'ok',
  FAILED: 'fail',
  LOST: 'warn',
}

const AGGREGATE_KEYS: Record<string, string> = {
  IN_PROGRESS: 'execution.shard.aggregateInProgress',
  RETRYING: 'execution.shard.aggregateRetrying',
  DONE: 'execution.shard.aggregateDone',
  FAILED: 'execution.shard.aggregateFailed',
}

function stateLabel(state: string): string {
  return t(STATE_KEYS[state] ?? 'execution.shard.statePending')
}

function stateTone(state: string): StatusTone {
  return TONES[state] ?? 'neutral'
}

const aggregateLabel = computed(() =>
  t(AGGREGATE_KEYS[data.value?.aggregateState ?? 'IN_PROGRESS'] ?? 'execution.shard.aggregateInProgress'),
)

const aggregateTone = computed<StatusTone>(() => {
  const state = data.value?.aggregateState
  if (state === 'DONE') return 'ok'
  if (state === 'FAILED') return 'fail'
  if (state === 'RETRYING') return 'warn'
  return 'info'
})

let timer: number | undefined

async function load(): Promise<void> {
  if (!props.projectId || !props.runId) {
    data.value = null
    return
  }
  loading.value = true
  error.value = null
  try {
    data.value = await shardApi.view(props.projectId, props.runId)
    if (data.value.complete) stopPoll()
  } catch (e) {
    error.value = localizeError(e)
  } finally {
    loading.value = false
  }
}

function startPoll(): void {
  stopPoll()
  // 全终态就不再轮询；有分片但还没跑完才需要动态刷新
  if (!data.value || data.value.complete) return
  timer = window.setInterval(() => void load(), 4000)
}

function stopPoll(): void {
  if (timer) {
    window.clearInterval(timer)
    timer = undefined
  }
}

async function reloadAndPoll(): Promise<void> {
  await load()
  startPoll()
}

async function submitPlan(): Promise<void> {
  if (!canPlan.value) return
  busy.value = true
  error.value = null
  try {
    data.value = await shardApi.plan(
      props.projectId,
      props.runId,
      planList.value,
      Number(planNodes.value) || null,
    )
    planOpen.value = false
    emit('changed')
    startPoll()
  } catch (e) {
    error.value = localizeError(e)
  } finally {
    busy.value = false
  }
}

async function aggregate(): Promise<void> {
  busy.value = true
  error.value = null
  try {
    data.value = await shardApi.aggregate(props.projectId, props.runId)
    emit('changed')
    startPoll()
  } catch (e) {
    error.value = localizeError(e)
  } finally {
    busy.value = false
  }
}

watch(
  () => [props.runId, props.projectId],
  () => {
    data.value = null
    planOpen.value = false
    stopPoll()
    void reloadAndPoll()
  },
  { immediate: true },
)

onBeforeUnmount(stopPoll)
</script>

<template>
  <section class="shard-board" data-testid="shard-board">
    <div class="shard-head">
      <h3 class="shard-title">{{ t('execution.shard.title') }}</h3>
      <StatusTag
        v-if="data"
        :label="aggregateLabel"
        :tone="aggregateTone"
      />
      <span v-if="data" class="muted">
        {{
          t('execution.shard.summary', {
            terminal: data.terminalShards,
            total: data.totalShards,
          })
        }}
      </span>
      <span v-if="data" class="muted">
        {{ t('execution.shard.counts', { tests: data.testCount, fails: data.failCount }) }}
      </span>
      <span class="spacer" />
      <NButton quaternary size="tiny" :loading="loading" @click="load">
        {{ t('common.refresh') }}
      </NButton>
      <NButton quaternary size="tiny" @click="planOpen = !planOpen">
        {{ planOpen ? t('common.cancel') : t('execution.shard.planOpen') }}
      </NButton>
      <NButton
        quaternary
        size="tiny"
        :disabled="busy || !data"
        @click="aggregate"
      >
        {{ t('execution.shard.aggregate') }}
      </NButton>
    </div>

    <p v-if="error" class="shard-error">{{ error.summary }}</p>

    <div v-if="planOpen" class="plan-box">
      <p class="plan-hint">{{ t('execution.shard.planHint') }}</p>
      <NInput
        v-model:value="planPaths"
        type="textarea"
        :autosize="{ minRows: 3, maxRows: 6 }"
        :placeholder="t('execution.shard.planPlaceholder')"
      />
      <div class="plan-actions">
        <span class="muted">{{ t('execution.shard.nodeCount') }}</span>
        <NInputNumber v-model:value="planNodes" :min="1" :max="4" />
        <NButton type="primary" size="small" :disabled="!canPlan" :loading="busy" @click="submitPlan">
          {{ t('execution.shard.planSubmit') }}
        </NButton>
      </div>
    </div>

    <div v-if="data && data.shards.length" class="shard-grid">
      <article
        v-for="shard in data.shards"
        :key="shard.shardIndex"
        class="shard-card"
        :data-state="shard.state"
      >
        <div class="shard-card-head">
          <strong>#{{ shard.shardIndex }}</strong>
          <StatusTag :label="stateLabel(shard.state)" :tone="stateTone(shard.state)" />
          <span v-if="TERMINAL.includes(shard.state)" class="muted">
            {{ t('execution.shard.attempt', { n: shard.attempt }) }}
          </span>
        </div>
        <p class="muted shard-meta">
          <template v-if="shard.nodeId !== null">
            {{ t('execution.shard.owner', { node: shard.nodeId, lease: shard.leaseOwner ?? '-' }) }}
          </template>
          <template v-else>{{ t('execution.shard.unclaimed') }}</template>
        </p>
        <p v-if="shard.leaseUntil" class="muted shard-meta">
          {{ t('execution.shard.leaseUntil', { time: shard.leaseUntil }) }}
        </p>
        <p class="muted shard-meta">
          {{
            t('execution.shard.countsPerShard', {
              tests: shard.testCount,
              fails: shard.failCount,
              token: shard.fencingToken,
            })
          }}
        </p>
        <ul class="suite-list">
          <li v-for="path in shard.suitePaths" :key="path">{{ path }}</li>
        </ul>
      </article>
    </div>

    <p v-else-if="!loading && !error" class="muted shard-empty">
      {{ t('execution.shard.empty') }}
    </p>
  </section>
</template>

<style scoped>
.shard-board {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

.shard-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.shard-title {
  margin: 0;
  font-size: var(--font-size-base);
  font-weight: 600;
}

.spacer {
  flex: 1;
}

.shard-error {
  margin: 8px 0 0;
  color: var(--fail);
  font-size: var(--font-size-xs);
}

.plan-box {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface-2);
}

.plan-hint {
  margin: 0 0 8px;
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}

.plan-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: 8px;
}

.shard-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--space-4);
  margin-top: 12px;
}

.shard-card {
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--panel);
}

.shard-card-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.shard-meta {
  margin: 6px 0 0;
  font-size: var(--font-size-xs);
  line-height: 1.5;
}

.suite-list {
  margin: 8px 0 0;
  padding-left: 16px;
  color: var(--text);
  font-size: var(--font-size-xs);
  line-height: 1.6;
  word-break: break-all;
}

.shard-empty {
  margin: 12px 0 0;
  font-size: var(--font-size-sm);
}
</style>
