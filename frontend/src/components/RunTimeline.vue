<script setup lang="ts">
/**
 * run 时间线（W4/v3.0）。
 *
 * 后端把 `run.enqueued_at/started_at/finished_at` 与每个分片的
 * `started_at/finished_at` 合并成一条按时间排序的事件流；这里只做展示：
 * 事件类型 i18n、时间本地化、分片号/节点/attempt/fencing 与用例计数。
 *
 * 轮询 4 秒，run 进入终态即停表——已完成的历史 run 不该继续打接口。
 * 空事件流要明说「还没有事件」，不能渲染成加载失败。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton } from 'naive-ui'
import { timelineApi, type RunTimeline, type TimelineEvent } from '@/api/observability'
import ErrorLineView from '@/components/ErrorLine.vue'
import { localizeError, type ErrorLine } from '@/utils/errors'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{ runId: string; projectId: number }>()

const { t } = useI18n()

const data = ref<RunTimeline | null>(null)
const loading = ref(false)
const error = ref<ErrorLine | null>(null)

const EVENT_KEYS: Record<string, string> = {
  RUN_ENQUEUED: 'execution.timeline.enqueued',
  RUN_STARTED: 'execution.timeline.started',
  SHARD_LEASED: 'execution.timeline.shardLeased',
  SHARD_DONE: 'execution.timeline.shardDone',
  SHARD_FAILED: 'execution.timeline.shardFailed',
  SHARD_LOST: 'execution.timeline.shardLost',
  SHARD_UPDATED: 'execution.timeline.shardUpdated',
  RUN_FINISHED: 'execution.timeline.finished',
}

const TONES: Record<string, string> = {
  RUN_ENQUEUED: 'is-info',
  RUN_STARTED: 'is-info',
  SHARD_LEASED: 'is-info',
  SHARD_DONE: 'is-ok',
  SHARD_FAILED: 'is-fail',
  SHARD_LOST: 'is-warn',
  RUN_FINISHED: 'is-ok',
}

const TERMINAL = ['FINISHED', 'FAILED', 'CANCELED']

const events = computed(() => data.value?.events ?? [])
const terminal = computed(() => !!data.value && TERMINAL.includes(data.value.state))

function eventLabel(type: string): string {
  return t(EVENT_KEYS[type] ?? 'execution.timeline.event')
}

function eventTone(event: TimelineEvent): string {
  if (event.type === 'RUN_FINISHED' && event.failCount > 0) return 'is-fail'
  return TONES[event.type] ?? 'is-info'
}

/** 分片事件才显示分片号；run 级事件的 shardIndex = -1。 */
function shardLabel(event: TimelineEvent): string {
  return event.shardIndex >= 0 ? `#${event.shardIndex}` : ''
}

let timer: number | undefined

async function load(): Promise<void> {
  if (!props.projectId || !props.runId) {
    data.value = null
    return
  }
  loading.value = true
  error.value = null
  try {
    data.value = await timelineApi.run(props.projectId, props.runId)
    if (terminal.value) stopPoll()
  } catch (e) {
    error.value = localizeError(e)
  } finally {
    loading.value = false
  }
}

function startPoll(): void {
  stopPoll()
  if (terminal.value) return
  timer = window.setInterval(() => void load(), 4000)
}

function stopPoll(): void {
  if (timer) {
    window.clearInterval(timer)
    timer = undefined
  }
}

watch(
  () => [props.runId, props.projectId],
  () => {
    data.value = null
    stopPoll()
    void load().then(startPoll)
  },
  { immediate: true },
)

onBeforeUnmount(stopPoll)
</script>

<template>
  <section class="run-timeline" data-testid="run-timeline">
    <div class="timeline-head">
      <h3 class="timeline-title">{{ t('execution.timeline.title') }}</h3>
      <span v-if="data" class="muted">
        {{ t('execution.timeline.count', { n: data.events.length }) }}
      </span>
      <span class="spacer" />
      <NButton quaternary size="tiny" :loading="loading" @click="load">
        {{ t('common.refresh') }}
      </NButton>
    </div>

    <ErrorLineView v-if="error" :error="error" />

    <ol v-else-if="events.length" class="timeline-list">
      <li v-for="(event, index) in events" :key="`${event.type}-${event.at}-${index}`" class="timeline-item">
        <span class="timeline-dot" :class="eventTone(event)" aria-hidden="true" />
        <div class="timeline-body">
          <div class="timeline-row">
            <strong class="timeline-label">{{ eventLabel(event.type) }}</strong>
            <span v-if="shardLabel(event)" class="timeline-shard">{{ shardLabel(event) }}</span>
            <span class="muted">{{ formatDateTime(event.at) }}</span>
            <span v-if="event.nodeId !== null" class="muted">
              {{ t('execution.timeline.node', { node: event.nodeId }) }}
            </span>
          </div>
          <p v-if="event.shardIndex >= 0" class="muted timeline-meta">
            {{
              t('execution.timeline.meta', {
                attempt: event.attempt,
                token: event.fencingToken,
                tests: event.testCount,
                fails: event.failCount,
              })
            }}
          </p>
        </div>
      </li>
    </ol>

    <p v-else-if="!loading" class="muted timeline-empty">
      {{ t('execution.timeline.empty') }}
    </p>
  </section>
</template>

<style scoped>
.run-timeline {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

.timeline-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.timeline-title {
  margin: 0;
  font-size: var(--font-size-base);
  font-weight: 600;
}

.spacer {
  flex: 1;
}

.timeline-list {
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.timeline-item {
  display: flex;
  gap: var(--space-3);
  padding: 6px 0;
}

.timeline-dot {
  flex: 0 0 auto;
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--accent);
}

.timeline-dot.is-ok {
  background: var(--ok);
}

.timeline-dot.is-fail {
  background: var(--fail);
}

.timeline-dot.is-warn {
  background: var(--warn);
}

.timeline-body {
  min-width: 0;
}

.timeline-row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2);
  font-size: var(--font-size-sm);
}

.timeline-label {
  font-weight: 600;
}

.timeline-shard {
  padding: 0 6px;
  border-radius: 4px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

.timeline-meta {
  margin: 2px 0 0;
  font-size: var(--font-size-xs);
}

.timeline-empty {
  margin: 12px 0 0;
  font-size: var(--font-size-sm);
}
</style>
