<script setup lang="ts">
/**
 * 计划依赖图（ORCH-02，v2.1）：**只读 SVG**，不引图形库。
 *
 * 为什么不用 DAG 编辑器 / 拖拽画布：
 * ① 拖一条边会绕过保存时的环检测（`PlanDependencyGraph.assertNoCycle`）——编辑器能画出的图，
 *    后端不一定收得下，用户会在一张「看起来合法」的图上反复被拒；
 * ② 本版真正的缺口是「看不清现在的依赖长什么样」，不是「画不出想要的依赖」。
 *
 * 布局口径：列 = 层级（无前置在第 0 列），行 = 同层内按 id 排序后的位置。
 * 五处必须看得出来的差异（N8 补了 ③⑤）：
 * ① **关键路径**实线加粗 + 主色，非关键边灰色细线；
 * ② **孤立点**虚线框（这类计划不会被任何依赖触发或联动，最容易被忽略）；
 * ③ **组推边**（计划组内链式依赖）画虚线：与手工配的依赖区分，改顺序时不会误以为要手改；
 * ④ **暂停**状态用次要色，避免和图本身的「不可达」混淆；
 * ⑤ **预计时长**优先显示历史 P50，没有历史才显示超时值（两者含义完全不同）。
 * 节点超过一屏时右下角出现**缩略导航**：大图滚动位置一目了然，点一下就能跳过去。
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { PlanGraphView } from '@/api/planGraph'

const props = defineProps<{ view: PlanGraphView }>()
const { t } = useI18n()

/** 列宽 / 行高 / 节点尺寸：固定值而不是自适应，保证 50 节点时也不会重叠。 */
const COLUMN_WIDTH = 190
const ROW_HEIGHT = 64
const NODE_WIDTH = 140
const NODE_HEIGHT = 40
const PADDING = 24

/** 缩略图上限：再大就会盖住内容，所以按比例缩放而不是固定尺寸。 */
const MINI_MAX_WIDTH = 180
const MINI_MAX_HEIGHT = 110

interface Positioned {
  id: number
  name: string
  status: string
  timeoutSeconds: number
  estimatedSeconds: number | null
  layer: number
  isolated: boolean
  onCriticalPath: boolean
  x: number
  y: number
}

/** 同层内按 id 升序排，保证两次渲染位置一致。 */
const positioned = computed<Positioned[]>(() => {
  const byLayer = new Map<number, PlanGraphView['nodes']>()
  for (const node of [...props.view.nodes].sort((left, right) => left.id - right.id)) {
    const bucket = byLayer.get(node.layer) ?? []
    bucket.push(node)
    byLayer.set(node.layer, bucket)
  }
  const result: Positioned[] = []
  for (const [layer, bucket] of [...byLayer.entries()].sort((a, b) => a[0] - b[0])) {
    bucket.forEach((node, index) => {
      result.push({
        ...node,
        x: PADDING + layer * COLUMN_WIDTH,
        y: PADDING + index * ROW_HEIGHT,
      })
    })
  }
  return result
})

const byId = computed(() => new Map(positioned.value.map((node) => [node.id, node])))

const width = computed(() => PADDING * 2 + (props.view.maxLayer + 1) * COLUMN_WIDTH - COLUMN_WIDTH + NODE_WIDTH)
const height = computed(() => {
  const maxRow = Math.max(1, ...countPerLayer())
  return PADDING * 2 + maxRow * ROW_HEIGHT
})

function countPerLayer(): number[] {
  const counts = new Map<number, number>()
  for (const node of props.view.nodes) {
    counts.set(node.layer, (counts.get(node.layer) ?? 0) + 1)
  }
  return [...counts.values()]
}

interface RenderedEdge {
  from: number
  to: number
  source: string
  x1: number
  y1: number
  x2: number
  y2: number
  critical: boolean
  group: boolean
}

/** 边从右边界画到左边界，中间走三次贝塞尔，避免互相穿模。 */
const renderedEdges = computed<RenderedEdge[]>(() => {
  const criticalPairs = new Set<string>()
  const path = props.view.criticalPath
  for (let i = 1; i < path.length; i++) {
    criticalPairs.add(`${path[i - 1]}->${path[i]}`)
  }
  const edges: RenderedEdge[] = []
  for (const edge of props.view.edges) {
    const from = byId.value.get(edge.from)
    const to = byId.value.get(edge.to)
    if (!from || !to) continue
    edges.push({
      from: edge.from,
      to: edge.to,
      source: edge.source,
      x1: from.x + NODE_WIDTH,
      y1: from.y + NODE_HEIGHT / 2,
      x2: to.x,
      y2: to.y + NODE_HEIGHT / 2,
      critical: criticalPairs.has(`${edge.from}->${edge.to}`),
      group: edge.source === 'GROUP',
    })
  }
  return edges
})

/** 秒数 → 便于一眼比较的短文本：90 → `1m30s`。 */
function formatSeconds(seconds: number): string {
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return rest === 0 ? `${minutes}m` : `${minutes}m${rest}s`
}

/** 节点第二行：优先历史 P50，没有历史才显示超时值。 */
function nodeMeta(node: Positioned): string {
  const status = node.status === 'paused' ? t('planGraph.paused') : t('planGraph.enabled')
  const duration = node.estimatedSeconds !== null
    ? t('planGraph.estimateSeconds', { seconds: formatSeconds(node.estimatedSeconds) })
    : t('planGraph.timeoutSeconds', { seconds: formatSeconds(node.timeoutSeconds) })
  return `${status} · ${duration}`
}

// ---- 缩略导航（只在大图上出现） ---------------------------------------------

const scroller = ref<HTMLElement | null>(null)
const viewport = reactive({ left: 0, top: 0, width: 0, height: 0 })

function syncViewport(): void {
  const element = scroller.value
  if (!element) return
  viewport.left = element.scrollLeft
  viewport.top = element.scrollTop
  viewport.width = element.clientWidth
  viewport.height = element.clientHeight
}

let observer: ResizeObserver | null = null

onMounted(() => {
  syncViewport()
  scroller.value?.addEventListener('scroll', syncViewport, { passive: true })
  if (typeof ResizeObserver !== 'undefined' && scroller.value) {
    observer = new ResizeObserver(syncViewport)
    observer.observe(scroller.value)
  }
})

onBeforeUnmount(() => {
  scroller.value?.removeEventListener('scroll', syncViewport)
  observer?.disconnect()
  observer = null
})

/** 一屏放不下才显示缩略图；小图加了反而挡内容。 */
const showMinimap = computed(() => width.value > 900 || height.value > 400)

const miniScale = computed(() => Math.min(MINI_MAX_WIDTH / width.value, MINI_MAX_HEIGHT / height.value))
const miniWidth = computed(() => Math.round(width.value * miniScale.value))
const miniHeight = computed(() => Math.round(height.value * miniScale.value))

/** 当前视口在缩略图坐标系里的框；宽高为 0（尚未挂载/隐藏）时框不画。 */
const viewportBox = computed(() => ({
  x: viewport.left * miniScale.value,
  y: viewport.top * miniScale.value,
  width: Math.min(viewport.width, width.value) * miniScale.value,
  height: Math.min(viewport.height, height.value) * miniScale.value,
  visible: viewport.width > 0 && viewport.height > 0,
}))

/** 点缩略图任意位置：把该点对应的图坐标滚到视口中心。 */
function jumpTo(event: MouseEvent): void {
  const element = scroller.value
  if (!element) return
  const target = event.currentTarget as HTMLElement
  const rect = target.getBoundingClientRect()
  if (rect.width === 0 || rect.height === 0) return
  const graphX = ((event.clientX - rect.left) / rect.width) * width.value
  const graphY = ((event.clientY - rect.top) / rect.height) * height.value
  element.scrollTo({
    left: Math.max(0, graphX - element.clientWidth / 2),
    top: Math.max(0, graphY - element.clientHeight / 2),
    behavior: 'smooth',
  })
}
</script>

<template>
  <div class="graph-shell">
    <div ref="scroller" class="graph" data-testid="plan-graph-scroller">
      <svg
        :width="width"
        :height="height"
        :viewBox="`0 0 ${width} ${height}`"
        data-testid="plan-graph"
        role="img"
        :aria-label="t('planGraph.graphAriaLabel', { nodes: view.nodes.length, edges: view.edges.length })"
      >
        <defs>
          <marker
            id="arrow-critical"
            viewBox="0 0 10 10"
            refX="9"
            refY="5"
            markerWidth="7"
            markerHeight="7"
            orient="auto"
          >
            <path d="M 0 0 L 10 5 L 0 10 z" class="marker-critical" />
          </marker>
          <marker
            id="arrow-normal"
            viewBox="0 0 10 10"
            refX="9"
            refY="5"
            markerWidth="6"
            markerHeight="6"
            orient="auto"
          >
            <path d="M 0 0 L 10 5 L 0 10 z" class="marker-normal" />
          </marker>
        </defs>

        <g data-testid="edges">
          <path
            v-for="edge in renderedEdges"
            :key="`${edge.from}-${edge.to}`"
            :d="`M ${edge.x1} ${edge.y1} C ${edge.x1 + 40} ${edge.y1}, ${edge.x2 - 40} ${edge.y2}, ${edge.x2} ${edge.y2}`"
            :class="{
              edge: true,
              'edge-critical': edge.critical,
              'edge-normal': !edge.critical,
              'edge-group': edge.group,
            }"
            :stroke-width="edge.critical ? 2.5 : 1"
            fill="none"
            :marker-end="edge.critical ? 'url(#arrow-critical)' : 'url(#arrow-normal)'"
            :data-critical="edge.critical ? 'true' : 'false'"
            :data-source="edge.source"
            data-testid="edge"
          />
        </g>

        <g data-testid="nodes">
          <g
            v-for="node in positioned"
            :key="node.id"
            :transform="`translate(${node.x}, ${node.y})`"
            data-testid="node"
            :data-status="node.status"
            :data-isolated="node.isolated ? 'true' : 'false'"
            :data-critical="node.onCriticalPath ? 'true' : 'false'"
            :data-estimated="node.estimatedSeconds === null ? 'none' : 'p50'"
          >
            <rect
              :width="NODE_WIDTH"
              :height="NODE_HEIGHT"
              rx="6"
              :class="{
                'node-rect': true,
                'is-critical': node.onCriticalPath,
                'is-isolated': node.isolated,
              }"
              :stroke-width="node.onCriticalPath ? 2 : 1"
            />
            <text x="8" y="17" class="name">{{ node.name }}</text>
            <text x="8" y="32" class="meta">{{ nodeMeta(node) }}</text>
          </g>
        </g>
      </svg>
    </div>

    <div
      v-if="showMinimap"
      class="minimap"
      :style="{ width: `${miniWidth}px`, height: `${miniHeight}px` }"
      data-testid="plan-graph-minimap"
      :aria-label="t('planGraph.minimap')"
      @click="jumpTo"
    >
      <svg :width="miniWidth" :height="miniHeight" :viewBox="`0 0 ${miniWidth} ${miniHeight}`">
        <rect
          v-for="node in positioned"
          :key="node.id"
          :x="node.x * miniScale"
          :y="node.y * miniScale"
          :width="NODE_WIDTH * miniScale"
          :height="NODE_HEIGHT * miniScale"
          :class="{ 'mini-node': true, 'is-critical': node.onCriticalPath }"
          rx="1.5"
        />
        <rect
          v-if="viewportBox.visible"
          :x="viewportBox.x"
          :y="viewportBox.y"
          :width="viewportBox.width"
          :height="viewportBox.height"
          class="mini-viewport"
        />
      </svg>
    </div>

    <p class="legend">
      <span class="dot critical" /> {{ t('planGraph.legendCritical', { seconds: view.criticalSeconds }) }}
      <span class="dot isolated" /> {{ t('planGraph.legendIsolated') }}
      <span class="line group" /> {{ t('planGraph.legendGroup') }}
    </p>
  </div>
</template>

<style scoped>
.graph-shell {
  position: relative;
}
.graph {
  overflow: auto;
  max-height: 420px;
}
/*
 * 颜色一律走 CSS 类，**不能**写成 SVG 表现属性（`fill="var(--panel)"` 在表现属性里是无效值）。
 * 另外变量名必须是 `styles/theme.css` 里真实定义过的：写错名字不会报错，声明在计算期失效，
 * `fill` 直接回落成初始值**黑色**——节点名是深色字，叠在黑色方块上完全看不见。
 * 两件事 jsdom 都发现不了（不算样式），只能靠真实浏览器量 computed style（见 capture.mjs）。
 */
.edge-critical {
  stroke: var(--accent);
}
.edge-normal {
  stroke: var(--border-strong);
}
/* 组推边：虚线与手工边区分；关键路径上的组边仍加粗（优先级更高） */
.edge-group {
  stroke-dasharray: 6 4;
}
.marker-critical {
  fill: var(--accent);
}
.marker-normal {
  fill: var(--border-strong);
}
.node-rect {
  fill: var(--surface-2);
  stroke: var(--border);
}
.node-rect.is-critical {
  fill: var(--panel);
  stroke: var(--accent);
}
.node-rect.is-isolated {
  stroke-dasharray: 4 3;
}
.name {
  font-size: 12px;
  fill: var(--text);
}
.meta {
  font-size: 10px;
  fill: var(--text-muted);
}
.minimap {
  position: absolute;
  right: 10px;
  bottom: 44px;
  border: 1px solid var(--border);
  background: var(--panel);
  border-radius: 4px;
  cursor: pointer;
  overflow: hidden;
}
.mini-node {
  fill: var(--surface-2);
  stroke: var(--border);
}
.mini-node.is-critical {
  stroke: var(--accent);
}
.mini-viewport {
  fill: none;
  stroke: var(--accent);
  stroke-width: 1.5;
}
.legend {
  font-size: 12px;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}
.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  margin-left: 10px;
}
.dot.critical {
  background: var(--accent);
}
.dot.isolated {
  border: 1px dashed var(--border-strong);
}
.line {
  display: inline-block;
  width: 16px;
  height: 0;
  border-top: 1px dashed var(--border-strong);
  margin-left: 10px;
}
</style>
