<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TrendChartSeries } from './trendChart'

/**
 * 自写 SVG 折线（RPT-04）：不引入图表库。
 *
 * <p>每条 series 一条折线：后端按 envKey 分组，环境变化处天然断开；
 * deviated 点标黄，clickable 时点击发 select（跳转对应 run）。
 */
const props = withDefaults(
  defineProps<{
    series: TrendChartSeries[]
    baseline?: number | null
    unit?: string
    clickable?: boolean
    currentId?: string
  }>(),
  { baseline: null, unit: '', clickable: false, currentId: '' },
)

const emit = defineEmits<{ (event: 'select', id: string): void }>()

const { t } = useI18n()

const WIDTH = 720
const HEIGHT = 250
const PAD_LEFT = 64
const PAD_RIGHT = 20
const PAD_TOP = 20
const PAD_BOTTOM = 40

const COLORS = ['#7cc6fe', '#b9a6ff', '#7ee2b8', '#ffb86b']

const allPoints = computed(() => props.series.flatMap((item) => item.points))

const xRange = computed(() => {
  const values = allPoints.value.map((point) => point.x)
  if (values.length === 0) return { min: 0, max: 1 }
  const min = Math.min(...values)
  const max = Math.max(...values)
  return { min, max: max === min ? min + 1 : max }
})

const yRange = computed(() => {
  const values = allPoints.value.map((point) => point.y)
  if (props.baseline !== null) values.push(props.baseline)
  if (values.length === 0) return { min: 0, max: 1 }
  let min = Math.min(...values)
  let max = Math.max(...values)
  if (max === min) {
    min = min - 1
    max = max + 1
  }
  const pad = (max - min) * 0.1
  return { min: min - pad, max: max + pad }
})

function scaleX(value: number): number {
  const span = xRange.value.max - xRange.value.min
  if (span === 0) return (WIDTH + PAD_LEFT - PAD_RIGHT) / 2
  return PAD_LEFT + ((value - xRange.value.min) / span) * (WIDTH - PAD_LEFT - PAD_RIGHT)
}

function scaleY(value: number): number {
  const span = yRange.value.max - yRange.value.min
  if (span === 0) return (HEIGHT - PAD_TOP - PAD_BOTTOM) / 2 + PAD_TOP
  return PAD_TOP + ((yRange.value.max - value) / span) * (HEIGHT - PAD_TOP - PAD_BOTTOM)
}

const plotted = computed(() =>
  props.series.map((item, index) => ({
    key: item.key,
    label: item.label,
    color: COLORS[index % COLORS.length],
    points: item.points.map((point) => ({
      ...point,
      px: scaleX(point.x),
      py: scaleY(point.y),
    })),
  })),
)

const yTicks = computed(() => {
  const { min, max } = yRange.value
  return [0, 1, 2, 3].map((step) => {
    const value = max - ((max - min) * step) / 3
    return { value, y: scaleY(value) }
  })
})

const xTicks = computed(() => {
  const points = allPoints.value
  if (points.length === 0) return []
  const first = points.reduce((a, b) => (a.x <= b.x ? a : b))
  const last = points.reduce((a, b) => (a.x >= b.x ? a : b))
  return [
    { x: scaleX(first.x), label: first.label, anchor: 'start' },
    { x: scaleX(last.x), label: last.label, anchor: 'end' },
  ]
})

function formatY(value: number): string {
  if (props.unit === '%') return `${Math.round(value)}%`
  return `${Math.round(value)}${props.unit ? ` ${props.unit}` : ''}`
}

function clickPoint(id: string): void {
  if (props.clickable) emit('select', id)
}
</script>

<template>
  <p v-if="allPoints.length === 0" class="muted">{{ t('reports.trend.empty') }}</p>
  <div v-else class="trend-chart-wrap">
    <svg
      class="trend-chart"
      :viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
      role="img"
      :aria-label="t('reports.trend.title')"
    >
      <line
        v-for="tick in yTicks"
        :key="`grid-${tick.y}`"
        class="grid-line"
        :x1="PAD_LEFT"
        :x2="WIDTH - PAD_RIGHT"
        :y1="tick.y"
        :y2="tick.y"
      />
      <text
        v-for="tick in yTicks"
        :key="`ylabel-${tick.y}`"
        class="axis-label"
        :x="PAD_LEFT - 8"
        :y="tick.y + 4"
        text-anchor="end"
      >
        {{ formatY(tick.value) }}
      </text>

      <line
        v-if="baseline !== null"
        class="baseline-line"
        :x1="PAD_LEFT"
        :x2="WIDTH - PAD_RIGHT"
        :y1="scaleY(baseline)"
        :y2="scaleY(baseline)"
      />
      <text
        v-if="baseline !== null"
        class="baseline-label"
        :x="WIDTH - PAD_RIGHT"
        :y="scaleY(baseline) - 4"
        text-anchor="end"
      >
        {{ t('reports.trend.baselineLine', { ms: baseline }) }}
      </text>

      <g v-for="line in plotted" :key="line.key">
        <polyline
          v-if="line.points.length > 1"
          :points="line.points.map((point) => `${point.px},${point.py}`).join(' ')"
          :stroke="line.color"
          stroke-width="2"
          fill="none"
        />
        <circle
          v-for="point in line.points"
          :key="`${line.key}-${point.id}`"
          :cx="point.px"
          :cy="point.py"
          :r="point.id === currentId ? 6 : 4.5"
          :fill="point.deviated ? '#f0b429' : line.color"
          :class="{ clickable, deviated: point.deviated, current: point.id === currentId }"
          @click="clickPoint(point.id)"
        >
          <title>{{ point.label }} · {{ formatY(point.y) }}</title>
        </circle>
      </g>

      <text
        v-for="tick in xTicks"
        :key="`xlabel-${tick.anchor}`"
        class="axis-label"
        :x="tick.x"
        :y="HEIGHT - 12"
        :text-anchor="tick.anchor"
      >
        {{ tick.label }}
      </text>
    </svg>

    <div class="legend">
      <span v-for="line in plotted" :key="`legend-${line.key}`" class="legend-item">
        <span class="legend-dot" :style="{ background: line.color }" />
        {{ line.label }}
      </span>
      <span class="legend-item deviated">
        <span class="legend-dot legend-dot-warn" />
        {{ t('reports.trend.deviatedLegend') }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.trend-chart-wrap {
  width: 100%;
}

.trend-chart {
  width: 100%;
  height: auto;
}

.grid-line {
  stroke: #22303f;
  stroke-width: 1;
}

.axis-label {
  fill: var(--muted);
  font-size: 11px;
}

.baseline-line {
  stroke: var(--muted);
  stroke-width: 1;
  stroke-dasharray: 5 4;
}

.baseline-label {
  fill: var(--muted);
  font-size: 11px;
}

.clickable {
  cursor: pointer;
}

.deviated {
  stroke: #f0b429;
  stroke-width: 1.5;
}

.current {
  stroke: var(--text);
  stroke-width: 2;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--muted);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.legend-dot-warn {
  background: #f0b429;
}
</style>
