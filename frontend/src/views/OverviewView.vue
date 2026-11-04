<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ApiError } from '@/api/http'
import { overviewApi, type CountItem, type ProjectOverview } from '@/api/overview'
import { useAuthStore } from '@/stores/auth'
import ProjectPicker from '@/components/ProjectPicker.vue'

/**
 * 项目总览（OVW-01）。
 *
 * 这里只负责「把后端算好的数展示出来」，任何比例/合计都不在前端重算——
 * 前后端各算一套是同一份数据出现两个数的最常见原因。
 *
 * 空态是这个页面的正事：真实新项目就是 0 用例 0 运行，如果照常渲染一排 0 和 0%，
 * 用户会以为坏了。所以每个区块都有「下一步点哪里」的引导。
 */
const { t, te } = useI18n()
const auth = useAuthStore()

const projectId = computed(() => auth.projectId ?? 0)
const data = ref<ProjectOverview | null>(null)
const loading = ref(false)
const error = ref('')

const BAR_HEIGHT = 110

const trendMax = computed(() => {
  if (!data.value) return 1
  const max = Math.max(
    1,
    ...data.value.dailyTrend.map((day) => day.finished + day.failed + day.canceled),
  )
  return max
})

const hasRuns = computed(() => (data.value?.runs.total ?? 0) > 0)
const hasFinishedRuns = computed(() => (data.value?.runs.finished ?? 0) > 0)

async function load(): Promise<void> {
  error.value = ''
  if (!projectId.value) {
    data.value = null
    return
  }
  loading.value = true
  try {
    data.value = await overviewApi.load(projectId.value)
  } catch (e) {
    error.value = e instanceof ApiError ? `${e.codeName}：${e.message}` : String(e)
    data.value = null
  } finally {
    loading.value = false
  }
}

// immediate 不能省：projectId 来自 localStorage，进页面时通常已经是目标值，
// 不加 immediate 时 watch 不触发，load() 永不执行——页面会一直停在「加载中」。
watch(projectId, () => void load(), { immediate: true })

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

function formatTime(iso: string | null): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function formatDuration(ms: number | null): string {
  if (ms === null || ms < 0) return '—'
  if (ms < 1000) return `${ms} ms`
  const seconds = ms / 1000
  if (seconds < 60) return `${seconds.toFixed(1)} s`
  return `${Math.floor(seconds / 60)}m ${Math.round(seconds % 60)}s`
}

/** 基点 → 百分比。null 一律显示「—」，绝不显示 0%——那会被读成「跑过且全挂」。 */
function percent(bp: number | null): string {
  return bp === null ? '—' : `${(bp / 100).toFixed(1)}%`
}

function stateText(state: string): string {
  const key = `overview.state.${state}`
  return te(key) ? t(key) : state
}

function stateClass(state: string): string {
  if (state === 'finished') return 'badge verdict-pass'
  if (state === 'failed') return 'badge verdict-fail'
  if (state === 'canceled') return 'badge verdict-unknown'
  return 'badge verdict-skip'
}

function triggerText(type: string): string {
  const key = `overview.trigger.${type}`
  return te(key) ? t(key) : type
}

function reviewText(key: string): string {
  const path = `overview.reviewStatus.${key}`
  return te(path) ? t(path) : key
}

function chips(items: CountItem[]): string {
  return items.map((item) => `${item.key} ${item.count}`).join(' · ')
}

/** 后端把空的 domain_tag 归一成「未标注」；英文界面要能翻译，其余值原样展示。 */
function domainChips(items: CountItem[]): string {
  return items
    .map((item) => `${item.key === '未标注' ? t('overview.cases.unset') : item.key} ${item.count}`)
    .join(' · ')
}

function segHeight(value: number): string {
  return `${Math.round((value / trendMax.value) * BAR_HEIGHT)}px`
}

function dayLabel(date: string): string {
  return date.slice(5)
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <h1 class="page-title">{{ t('menu.overview') }}</h1>
      <div class="page-head-actions">
        <ProjectPicker />
        <button class="btn" type="button" :disabled="loading || !projectId" @click="load">
          {{ t('common.refresh') }}
        </button>
      </div>
    </div>

    <p class="page-hint">
      {{ t('overview.subtitle') }}
      <span v-if="data"> · {{ t('overview.generatedAt', { time: formatTime(data.generatedAt) }) }}</span>
    </p>

    <p v-if="!projectId" class="muted">{{ t('overview.noProject') }}</p>
    <p v-else-if="loading" class="muted">{{ t('common.loading') }}</p>
    <p v-else-if="error" class="text-fail">{{ error }}</p>

    <template v-else-if="data">
      <section class="stat-grid">
        <article class="stat-card">
          <span class="stat-label">{{ t('overview.cases.title') }}</span>
          <span class="stat-value">{{ data.cases.total }}</span>
          <span class="stat-sub">{{ t('overview.cases.total') }}</span>
          <template v-if="data.cases.total > 0">
            <div class="stat-row">
              <span class="muted">{{ t('overview.cases.priority') }}</span>
              <span>{{ chips(data.cases.byPriority) }}</span>
            </div>
            <div class="stat-row">
              <span class="muted">{{ t('overview.cases.review') }}</span>
              <span>{{ data.cases.byReviewStatus
                .map((item) => `${reviewText(item.key)} ${item.count}`).join(' · ') }}</span>
            </div>
            <div class="stat-row">
              <span class="muted">{{ t('overview.cases.domains') }}</span>
              <span>{{ domainChips(data.cases.topDomains) }}</span>
            </div>
          </template>
          <div v-else class="empty-box">
            <p>{{ t('overview.cases.empty') }}</p>
            <p class="muted">{{ t('overview.cases.emptyHint') }}</p>
          </div>
        </article>

        <article class="stat-card">
          <span class="stat-label">{{ t('overview.assets.title') }}</span>
          <span class="stat-value">{{ data.assets.devices }}</span>
          <span class="stat-sub">{{ t('overview.assets.devices') }}</span>
          <div class="stat-row">
            <span class="muted">{{ t('overview.assets.ports') }}</span>
            <span>{{ data.assets.ports }}</span>
          </div>
          <div class="stat-row">
            <span class="muted">{{ t('overview.assets.links') }}</span>
            <span>{{ data.assets.links }}</span>
          </div>
          <div class="stat-row">
            <span class="muted">{{ t('overview.assets.credentials') }}</span>
            <span>{{ data.assets.credentials }}</span>
          </div>
          <div class="stat-row">
            <span class="muted">{{ t('overview.assets.environments') }} / {{ t('overview.assets.testSets') }}</span>
            <span>{{ data.assets.environments }} / {{ data.assets.testSets }}</span>
          </div>
          <div v-if="data.assets.devices === 0" class="empty-box">
            <p class="muted">{{ t('overview.assets.emptyHint') }}</p>
          </div>
        </article>

        <article class="stat-card">
          <span class="stat-label">{{ t('overview.runs.passRate') }}</span>
          <span class="stat-value" :class="{ muted: data.runs.passRateBp === null }">
            {{ percent(data.runs.passRateBp) }}
          </span>
          <span class="stat-sub">{{ t('overview.runs.passRateHint', { days: data.windowDays }) }}</span>
          <template v-if="hasFinishedRuns">
            <div class="stat-row">
              <span class="muted">PASS / FAIL / SKIP</span>
              <span>{{ data.runs.casePass }} / {{ data.runs.caseFail }} / {{ data.runs.caseSkip }}</span>
            </div>
            <div class="stat-row">
              <span class="muted">{{ t('overview.runs.avgDuration') }}</span>
              <span>{{ formatDuration(data.runs.avgDurationMs) }}</span>
            </div>
            <div class="stat-row">
              <span class="muted">{{ t('overview.runs.lastRun') }}</span>
              <span>{{ formatTime(data.runs.lastRunAt) }}</span>
            </div>
          </template>
          <div v-else class="empty-box">
            <p>{{ t('overview.runs.never') }}</p>
            <p class="muted">{{ t('overview.runs.neverHint') }}</p>
          </div>
        </article>

        <article class="stat-card">
          <span class="stat-label">{{ t('overview.runs.title') }}</span>
          <span class="stat-value">{{ data.runs.total }}</span>
          <span class="stat-sub">{{ t('overview.runs.total') }}</span>
          <div class="stat-row">
            <span class="muted">{{ t('overview.runs.inWindow', { days: data.windowDays }) }}</span>
            <span>{{ data.runs.inWindow }}</span>
          </div>
          <div class="stat-row">
            <span class="muted">{{ t('overview.runs.finished') }}</span>
            <span>{{ data.runs.finished }}</span>
          </div>
          <div class="stat-row">
            <span class="muted">{{ t('overview.runs.failed') }}</span>
            <span>{{ data.runs.failed }}</span>
          </div>
          <div class="stat-row">
            <span class="muted">{{ t('overview.runs.running') }}</span>
            <span>{{ data.runs.running }}</span>
          </div>
        </article>
      </section>

      <section class="card wide-card">
        <div class="section-head">
          <h2 class="card-title">{{ t('overview.trend.title', { days: data.windowDays }) }}</h2>
          <div class="trend-legend">
            <span><i class="trend-dot finished" />{{ t('overview.trend.legendFinished') }}</span>
            <span><i class="trend-dot failed" />{{ t('overview.trend.legendFailed') }}</span>
            <span><i class="trend-dot canceled" />{{ t('overview.trend.legendCanceled') }}</span>
          </div>
        </div>
        <template v-if="hasRuns">
          <div class="trend-bars">
            <div
              v-for="day in data.dailyTrend"
              :key="day.date"
              class="trend-bar"
              :title="`${day.date} · ${t('overview.trend.legendFinished')} ${day.finished} / `
                + `${t('overview.trend.legendFailed')} ${day.failed} / `
                + `${t('overview.trend.legendCanceled')} ${day.canceled}`"
            >
              <span class="trend-seg canceled" :style="{ height: segHeight(day.canceled) }" />
              <span class="trend-seg failed" :style="{ height: segHeight(day.failed) }" />
              <span class="trend-seg finished" :style="{ height: segHeight(day.finished) }" />
            </div>
          </div>
          <div class="trend-axis">
            <span>{{ dayLabel(data.dailyTrend[0]?.date ?? '') }}</span>
            <span>{{ dayLabel(data.dailyTrend[data.dailyTrend.length - 1]?.date ?? '') }}</span>
          </div>
        </template>
        <p v-else class="muted">{{ t('overview.trend.empty') }}</p>
      </section>

      <section class="card wide-card">
        <div class="section-head">
          <h2 class="card-title">{{ t('overview.recent.title') }}</h2>
        </div>
        <table v-if="data.recentRuns.length > 0" class="table">
          <thead>
            <tr>
              <th>{{ t('overview.recent.runId') }}</th>
              <th>{{ t('overview.recent.state') }}</th>
              <th>{{ t('overview.recent.startedAt') }}</th>
              <th>{{ t('overview.recent.duration') }}</th>
              <th>{{ t('overview.recent.cases') }}</th>
              <th>{{ t('overview.recent.passRate') }}</th>
              <th>{{ t('overview.recent.device') }}</th>
              <th>{{ t('overview.recent.trigger') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="run in data.recentRuns" :key="run.runId">
              <td>{{ run.runId }}</td>
              <td><span :class="stateClass(run.state)">{{ stateText(run.state) }}</span></td>
              <td>{{ formatTime(run.startedAt) }}</td>
              <td>{{ formatDuration(run.durationMs) }}</td>
              <td>{{ run.total }}</td>
              <td>{{ percent(run.passRateBp) }}</td>
              <td>{{ run.dut || '—' }}<span v-if="run.link" class="muted"> · {{ run.link }}</span></td>
              <td>{{ triggerText(run.triggerType) }}</td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty-box">
          <p>{{ t('overview.recent.empty') }}</p>
          <p class="muted">{{ t('overview.recent.emptyHint') }}</p>
        </div>
      </section>
    </template>
  </div>
</template>
