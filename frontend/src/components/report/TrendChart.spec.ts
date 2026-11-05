/**
 * `TrendChart` 组件级回归（UX-05 图表薄封装）。
 *
 * 四件事守门与类型检查都看不见：
 * ① 没数据时**不能画空坐标系**（要 `EmptyState`，否则图上是一片空白网格，像加载失败）；
 * ② 图例可点 → 隐藏后折线消失、图例项标记 `is-off`、`aria-pressed` 变 false；
 * ③ 全部隐藏 → 给一行提示，不是空白；
 * ④ 悬停出读数浮层，离开消失（原来只有原生 `<title>`，1 秒延迟且样式不可控）。
 */
import { afterEach, describe, expect, it } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import { i18n } from '@/locales'
import TrendChart from './TrendChart.vue'
import type { TrendChartSeries } from './trendChart'

const series: TrendChartSeries[] = [
  {
    key: 'env-a',
    label: 'ENV-A',
    points: [
      { id: 'RUN-1', label: 'RUN-1', x: 0, y: 1200 },
      { id: 'RUN-2', label: 'RUN-2', x: 1, y: 1500, deviated: true },
    ],
  },
  {
    key: 'env-b',
    label: 'ENV-B',
    points: [
      { id: 'RUN-3', label: 'RUN-3', x: 0, y: 800 },
      { id: 'RUN-4', label: 'RUN-4', x: 1, y: 900 },
    ],
  },
]

let app: App | null = null
let host: HTMLElement | null = null

async function mount(props: Record<string, unknown>): Promise<HTMLElement> {
  const root = document.createElement('div')
  document.body.appendChild(root)
  host = root
  app = createApp({ render: () => h(TrendChart, props as never) })
  app.use(i18n)
  app.mount(root)
  await nextTick()
  return root
}

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
})

describe('TrendChart', () => {
  it('没有数据时给 EmptyState，不画空坐标系', async () => {
    const root = await mount({ series: [] })
    expect(root.querySelector('.empty-state')).not.toBeNull()
    expect(root.querySelector('svg.trend-chart')).toBeNull()
  })

  it('有数据时画折线，y 轴刻度带单位', async () => {
    const root = await mount({ series, unit: '%' })
    expect(root.querySelectorAll('polyline').length).toBe(2)
    // 只看 y 轴刻度（`.axis-label--y`）；x 轴时间标签与基线标签同为 .axis-label，
    // 但一个是时间、一个是阈值，都不能要求带单位
    const labels = [...root.querySelectorAll('text.axis-label--y')].map((el) =>
      el.textContent?.trim(),
    )
    expect(labels.length).toBe(4)
    expect(labels.every((text) => text?.endsWith('%'))).toBe(true)
  })

  it('点图例可隐藏该系列，再点恢复', async () => {
    const root = await mount({ series, unit: 'ms' })
    const legend = [...root.querySelectorAll<HTMLButtonElement>('button.legend-item')]
    expect(legend.length).toBe(2)

    legend[0].click()
    await nextTick()
    expect(root.querySelectorAll('polyline').length).toBe(1)
    expect(legend[0].getAttribute('aria-pressed')).toBe('false')
    expect(legend[0].classList.contains('is-off')).toBe(true)

    legend[0].click()
    await nextTick()
    expect(root.querySelectorAll('polyline').length).toBe(2)
    expect(legend[0].getAttribute('aria-pressed')).toBe('true')
  })

  it('隐藏全部系列后给提示，而不是留一张空图', async () => {
    const root = await mount({ series, unit: 'ms' })
    for (const button of root.querySelectorAll<HTMLButtonElement>('button.legend-item')) {
      button.click()
      await nextTick()
    }
    expect(root.querySelector('.trend-all-hidden')).not.toBeNull()
    expect(root.querySelector('svg.trend-chart')).toBeNull()
  })

  it('悬停命中区出读数浮层，离开即消失', async () => {
    const root = await mount({ series, unit: 'ms' })
    const hit = root.querySelector('circle.hit')
    expect(hit).not.toBeNull()

    hit!.dispatchEvent(new MouseEvent('mouseenter'))
    await nextTick()
    const tip = root.querySelector('.chart-tip')
    expect(tip).not.toBeNull()
    expect(tip!.textContent).toContain('RUN-1')
    expect(tip!.textContent).toContain('1200 ms')

    hit!.dispatchEvent(new MouseEvent('mouseleave'))
    await nextTick()
    expect(root.querySelector('.chart-tip')).toBeNull()
  })

  it('clickable 时点数据点发出 select，非 clickable 不发', async () => {
    const picked: string[] = []
    const root = await mount({
      series,
      unit: 'ms',
      clickable: true,
      onSelect: (id: string) => picked.push(id),
    })
    root.querySelector<SVGElement>('circle.hit')!.dispatchEvent(new MouseEvent('click'))
    await nextTick()
    expect(picked).toEqual(['RUN-1'])
  })

  it('非 clickable 时点数据点不发事件', async () => {
    const picked: string[] = []
    const root = await mount({
      series,
      unit: 'ms',
      onSelect: (id: string) => picked.push(id),
    })
    root.querySelector<SVGElement>('circle.hit')!.dispatchEvent(new MouseEvent('click'))
    await nextTick()
    expect(picked).toEqual([])
  })
})
