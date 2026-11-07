/**
 * `PlanGraph` 组件回归（ORCH-02）。
 *
 * 三个「图上看不出来但一定错」的点：
 * ① **节点漏画**：只从边集推节点时，孤立计划（一条边都没有）会整条消失，
 *    图看起来正常，实际少画了计划；
 * ② **关键边高亮错**：把「所有边」或「所有指向关键节点的边」标粗，用户会以为整张图都是关键路径；
 * ③ **层号没用上**：所有点画在同一列也能渲染出来，只是完全失去「谁依赖谁」的信息。
 */
import { describe, expect, it } from 'vitest'
import { createApp, nextTick } from 'vue'
import { i18n } from '@/locales'
import PlanGraph from '@/components/plan/PlanGraph.vue'
import type { PlanGraphView } from '@/api/planGraph'

const view: PlanGraphView = {
  projectId: 1,
  nodes: [
    { id: 1, name: 'A', status: 'enabled', timeoutSeconds: 100, estimatedSeconds: 600, layer: 0, isolated: false, onCriticalPath: true },
    { id: 2, name: 'B', status: 'enabled', timeoutSeconds: 200, estimatedSeconds: null, layer: 1, isolated: false, onCriticalPath: true },
    { id: 3, name: 'C', status: 'paused', timeoutSeconds: 900, estimatedSeconds: 900, layer: 1, isolated: false, onCriticalPath: false },
    { id: 4, name: 'D', status: 'enabled', timeoutSeconds: 50, estimatedSeconds: null, layer: 0, isolated: true, onCriticalPath: false },
  ],
  edges: [
    { from: 1, to: 2, source: 'GROUP' },
    { from: 1, to: 3, source: 'MANUAL' },
  ],
  criticalPath: [1, 2],
  criticalSeconds: 300,
  maxLayer: 1,
}

async function mount(componentView: PlanGraphView = view): Promise<HTMLElement> {
  const root = document.createElement('div')
  document.body.appendChild(root)
  const app = createApp(PlanGraph, { view: componentView })
  app.use(i18n)
  app.mount(root)
  await nextTick()
  return root
}

describe('PlanGraph', () => {
  it('孤立节点也画出来（只从边集推节点会漏掉它）', async () => {
    const root = await mount()
    const nodes = root.querySelectorAll('[data-testid="node"]')

    expect(nodes.length).toBe(4)
    const isolated = [...nodes].filter((node) => node.getAttribute('data-isolated') === 'true')
    expect(isolated.length).toBe(1)
    expect(isolated[0].textContent).toContain('D')
  })

  it('只有关键路径上的边被标为 critical', async () => {
    const root = await mount()
    const edges = [...root.querySelectorAll('[data-testid="edge"]')]

    const critical = edges.filter((edge) => edge.getAttribute('data-critical') === 'true')
    expect(edges.length).toBe(2)
    expect(critical.length).toBe(1)
  })

  it('按层分列：不同 layer 的节点 x 坐标不同', async () => {
    const root = await mount()
    const nodes = [...root.querySelectorAll('[data-testid="node"]')]

    const xOf = (name: string) =>
      nodes.find((node) => node.textContent?.includes(name))!.getAttribute('transform')
    // A 在第 0 列、B 在第 1 列，transform 里的 x 必须不同
    expect(xOf('A')).not.toBe(xOf('B'))
  })

  it('暂停状态在节点上可见（与 enabled 区分）', async () => {
    const root = await mount()
    const paused = [...root.querySelectorAll('[data-testid="node"]')].find((node) =>
      node.textContent?.includes('C'),
    )
    expect(paused?.getAttribute('data-status')).toBe('paused')
  })

  it('预计时长优先显示历史 P50，无历史才显示超时值（N8）', async () => {
    const root = await mount()
    const nodeOf = (name: string) =>
      [...root.querySelectorAll('[data-testid="node"]')].find((node) =>
        node.querySelector('text.name')?.textContent === name,
      )!
    // A：P50 600s → 10m；超时 100s 不能出现在第二行（P50 优先）
    expect(nodeOf('A').getAttribute('data-estimated')).toBe('p50')
    expect(nodeOf('A').querySelector('text.meta')?.textContent).toContain('10m')
    // B：无历史 → 回落超时 200s → 3m20s
    expect(nodeOf('B').getAttribute('data-estimated')).toBe('none')
    expect(nodeOf('B').querySelector('text.meta')?.textContent).toContain('3m20s')
  })

  it('组推边虚线标识，手工边不标（N8）', async () => {
    const root = await mount()
    const edges = [...root.querySelectorAll('[data-testid="edge"]')]
    const group = edges.find((edge) => edge.getAttribute('data-source') === 'GROUP')!
    const manual = edges.find((edge) => edge.getAttribute('data-source') === 'MANUAL')!

    expect(group.getAttribute('class')).toContain('edge-group')
    expect(manual.getAttribute('class')).not.toContain('edge-group')
  })

  it('大图出现缩略导航，小图不出现（N8）', async () => {
    expect((await mount()).querySelector('[data-testid="plan-graph-minimap"]')).toBeNull()
  })

  it('颜色走 CSS 类，不写 SVG 表现属性（写 var() 会回落成黑块）', async () => {
    const root = await mount()
    const rects = [...root.querySelectorAll('[data-testid="node"] rect')]
    expect(rects.length).toBe(4)
    for (const rect of rects) {
      // `fill="var(--panel)"` 写在 SVG 表现属性里是**无效值**：rect 回落默认黑色、
      // 深色文字叠在上面完全看不见。jsdom 不算样式，只能靠「不许出现这两个属性」守住。
      expect(rect.getAttribute('fill')).toBeNull()
      expect(rect.getAttribute('stroke')).toBeNull()
      expect(rect.getAttribute('class')).toContain('node-rect')
    }

    const isolatedRect = [...root.querySelectorAll('[data-testid="node"]')]
      .find((node) => node.getAttribute('data-isolated') === 'true')!
      .querySelector('rect')!
    expect(isolatedRect.getAttribute('class')).toContain('is-isolated')

    const edges = [...root.querySelectorAll('[data-testid="edge"]')]
    for (const edge of edges) {
      expect(edge.getAttribute('stroke')).toBeNull()
      expect(edge.getAttribute('class')).toContain('edge-')
    }
  })

  it('图例文案走语言包，且带上估算秒数', async () => {
    const root = await mount()
    const legend = root.querySelector('.legend')!
    // 文案不许硬编码（门禁 06-lint 的 i18n 检查会拦），这里只验「取到了词条 + 参数被填进去」
    expect(legend.textContent).not.toContain('planGraph.legend')
    expect(legend.textContent).toContain('300')
  })

  it('50 个节点也能渲染完（无重叠由固定行高保证）', async () => {
    const many: PlanGraphView = {
      projectId: 1,
      nodes: Array.from({ length: 50 }, (_, index) => ({
        id: index + 1,
        name: `plan-${index + 1}`,
        status: 'enabled',
        timeoutSeconds: 60,
        estimatedSeconds: null,
        layer: index % 5,
        isolated: false,
        onCriticalPath: false,
      })),
      edges: [],
      criticalPath: [],
      criticalSeconds: 0,
      maxLayer: 4,
    }
    const root = await mount(many)

    expect(root.querySelectorAll('[data-testid="node"]').length).toBe(50)
    const svg = root.querySelector('svg')!
    // 高度 = 内边距×2 + 最多一层的行数×行高（每层最多 10 个）
    expect(Number(svg.getAttribute('height'))).toBeGreaterThan(10 * 64)
    // 5 列 × 190px 已超出一屏，缩略导航必须出现
    expect(root.querySelector('[data-testid="plan-graph-minimap"]')).not.toBeNull()
  })
})
