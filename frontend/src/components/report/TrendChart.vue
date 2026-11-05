<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import EmptyState from '@/components/EmptyState.vue'
import type { TrendChartPoint, TrendChartSeries } from './trendChart'

/**
 * 自写 SVG 折线（RPT-04）：不引入图表库。
 *
 * <p>每条 series 一条折线：后端按 envKey 分组，环境变化处天然断开；
 * deviated 点标黄，clickable 时点击发 select（跳转对应 run）。
 *
 * <p>UX-05 补四件事（图表薄封装的验收面）：
 * ① **空图不画坐标系** —— 没数据时给 `EmptyState`，原来是一行灰字，视觉上仍会留一块空白；
 * ② **图例可点** —— 3 条环境线叠在一起时，用户只想看其中一条；隐藏后 y 轴按**可见**点重算，
 *    否则一条大值线被藏起来后其余点会被压成贴着底边的直线；
 * ③ **悬停读数** —— 原生 `<title>` 有 1 秒延迟且无法控制样式、在暗色下是浏览器白框；
 *    改为自绘浮层，位置按 viewBox 比例换算（SVG 等比缩放，百分比定位与点在视觉上对齐）；
 * ④ 全部隐藏时给一行提示，而不是一片空白图。
 *
 * <p>取舍：浮层只走鼠标悬停，不给每个点加 `tabindex`。一张趋势图几十个点，让键盘用户
 * Tab 穿过 30 个数据点比拿不到读数更糟；点的键盘替代路径是报告列表里的同名行（本来就可点）。
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

// 系列色走语义 token：亮/暗两套主题的图表色必须各自够读（浅蓝浅绿在白底上几乎看不见）。
// SVG 的 stroke/fill 是 presentation attribute，var() 在那里能不能解析取决于浏览器，
// 所以模板统一用 :style 绑定（style 里的 var() 一定生效）。
const COLORS = ['var(--chart-1)', 'var(--chart-2)', 'var(--chart-3)', 'var(--chart-4)']
const DEVIATED_COLOR = 'var(--warn)'

/** 被点掉的系列（空数组 = 全部显示） */
const hiddenKeys = ref<string[]>([])

function isHidden(key: string): boolean {
  return hiddenKeys.value.includes(key)
}

function toggleSeries(key: string): void {
  hiddenKeys.value = isHidden(key)
    ? hiddenKeys.value.filter((item) => item !== key)
    : [...hiddenKeys.value, key]
}

const visibleSeries = computed(() => props.series.filter((item) => !isHidden(item.key)))

const visiblePoints = computed(() => visibleSeries.value.flatMap((item) => item.points))

const xRange = computed(() => {
  const values = visiblePoints.value.map((point) => point.x)
  if (values.length === 0) return { min: 0, max: 1 }
  const min = Math.min(...values)
  const max = Math.max(...values)
  return { min, max: max === min ? min + 1 : max }
})

const yRange = computed(() => {
  const values = visiblePoints.value.map((point) => point.y)
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

// 颜色按**原始** series 顺序取：隐藏一条后其余系列的颜色不能跟着左移，
// 否则同一条环境线在勾选前后换了颜色，读图的人会以为是两条线。
const plotted = computed(() =>
  props.series.map((item, index) => ({
    key: item.key,
    label: item.label,
    color: COLORS[index % COLORS.length],
    hidden: isHidden(item.key),
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
  const points = visiblePoints.value
  if (points.length === 0) return []
  const first = points.reduce((a, b) => (a.x <= b.x ? a : b))
  const last = points.reduce((a, b) => (a.x >= b.x ? a : b))
  if (first.x === last.x) {
    return [{ x: scaleX(first.x), label: first.label, anchor: 'middle' }]
  }
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

/** 悬停读数：坐标存 viewBox 单位，模板换算成百分比（SVG 等比缩放，百分比定位才跟得上） */
interface HoverTip {
  key: string
  label: string
  value: number
  px: number
  py: number
  color: string
  deviated: boolean
}

const tip = ref<HoverTip | null>(null)

function showTip(seriesKey: string, point: TrendChartPoint & { px: number; py: number }, color: string): void {
  tip.value = {
    key: `${seriesKey}-${point.id}`,
    label: point.label,
    value: point.y,
    px: point.px,
    py: point.py,
    color,
    deviated: Boolean(point.deviated),
  }
}

function hideTip(): void {
  tip.value = null
}

const tipStyle = computed(() => {
  if (!tip.value) return {}
  // 点靠右/靠上时把浮层翻到另一侧，避免被图表边界裁掉
  const flipX = tip.value.px > WIDTH * 0.7
  const flipY = tip.value.py < HEIGHT * 0.3
  return {
    left: `${(tip.value.px / WIDTH) * 100}%`,
    top: `${(tip.value.py / HEIGHT) * 100}%`,
    transform: `translate(${flipX ? '-100%' : '0'}, ${flipY ? '12px' : 'calc(-100% - 12px)'})`,
  }
})
</script>

<template>
  <EmptyState v-if="series.length === 0" :title="t('reports.trend.empty')" icon="reports" />
  <div v-else class="trend-chart-wrap">
    <svg
      v-if="visiblePoints.length > 0"
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
        class="axis-label axis-label--y"
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
        <template v-if="!line.hidden">
          <polyline
            v-if="line.points.length > 1"
            :points="line.points.map((point) => `${point.px},${point.py}`).join(' ')"
            :style="{ stroke: line.color }"
            stroke-width="2"
            fill="none"
          />
          <g v-for="point in line.points" :key="`${line.key}-${point.id}`">
            <!-- 透明命中区：可见圆点只有 4.5px，直接拿它当悬停目标会非常难对准。
                 悬停与点击必须落在**同一个**圆上 —— 两个同位置圆叠加时，上层会吃掉
                 mouseenter，下层永远收不到事件。 -->
            <circle
              class="hit"
              :class="{ clickable }"
              :cx="point.px"
              :cy="point.py"
              r="11"
              @mouseenter="showTip(line.key, point, line.color)"
              @mouseleave="hideTip"
              @click="clickPoint(point.id)"
            />
            <circle
              :cx="point.px"
              :cy="point.py"
              :r="point.id === currentId ? 6 : 4.5"
              :style="{ fill: point.deviated ? DEVIATED_COLOR : line.color }"
              :class="{ deviated: point.deviated, current: point.id === currentId }"
              pointer-events="none"
            >
              <title>{{ point.label }} · {{ formatY(point.y) }}</title>
            </circle>
          </g>
        </template>
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

    <p v-else class="muted trend-all-hidden">{{ t('reports.trend.allHidden') }}</p>

    <div class="legend">
      <button
        v-for="line in plotted"
        :key="`legend-${line.key}`"
        type="button"
        class="legend-item"
        :class="{ 'is-off': line.hidden }"
        :aria-pressed="!line.hidden"
        :title="line.hidden ? t('reports.trend.showSeries') : t('reports.trend.hideSeries')"
        @click="toggleSeries(line.key)"
      >
        <span class="legend-dot" :style="{ background: line.color }" />
        {{ line.label }}
      </button>
      <span class="legend-item legend-item--static deviated">
        <span class="legend-dot legend-dot-warn" />
        {{ t('reports.trend.deviatedLegend') }}
      </span>
    </div>

    <div v-if="tip" class="chart-tip" :style="tipStyle" role="status">
      <span class="chart-tip__label">{{ tip.label }}</span>
      <span class="chart-tip__value" :style="{ color: tip.deviated ? 'var(--warn)' : 'var(--text)' }">
        {{ formatY(tip.value) }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.trend-chart-wrap {
  position: relative;
  width: 100%;
}

.trend-chart {
  width: 100%;
  height: auto;
}

.grid-line {
  stroke: var(--border);
  stroke-width: 1;
}

.axis-label {
  fill: var(--text-muted);
  font-size: 11px;
}

.baseline-line {
  stroke: var(--text-muted);
  stroke-width: 1;
  stroke-dasharray: 5 4;
}

.baseline-label {
  fill: var(--text-muted);
  font-size: 11px;
}

.hit {
  fill: transparent;
}

.clickable {
  cursor: pointer;
}

.deviated {
  stroke: var(--warn);
  stroke-width: 1.5;
}

.current {
  stroke: var(--text);
  stroke-width: 2;
}

.trend-all-hidden {
  margin: 0;
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-muted);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 4px;
  background: transparent;
  border: none;
  border-radius: 6px;
  color: inherit;
  font: inherit;
  cursor: pointer;
  transition:
    color 0.15s ease,
    background-color 0.15s ease,
    opacity 0.15s ease;
}

.legend-item:hover {
  background: var(--surface-2);
  color: var(--text);
}

.legend-item:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 1px;
}

/* 关掉的系列同时降透明度 + 划掉圆点，不只靠颜色变淡（色觉障碍下一样要能分辨） */
.legend-item.is-off {
  opacity: 0.45;
  text-decoration: line-through;
}

.legend-item--static {
  cursor: default;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.legend-dot-warn {
  background: var(--warn);
}

.chart-tip {
  position: absolute;
  z-index: 2;
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 4px 8px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--panel);
  box-shadow: var(--shadow-sm);
  color: var(--text);
  font-size: 12px;
  white-space: nowrap;
  pointer-events: none;
}

.chart-tip__label {
  color: var(--text-muted);
  font-family: var(--font-mono);
}

.chart-tip__value {
  font-weight: 500;
}
</style>
