/**
 * `OverviewView` 视图级回归（UX-04b 逐页迁移）。
 *
 * 总览是纯展示页，迁移风险集中在两处：
 * ① 三张表的列渲染是手写 `render`，丢掉一列不会报错，只会让数字悄悄消失；
 * ② 状态列原来只有颜色（`badge verdict-*`），换成 `StatusTag` 后必须真的带上图标，
 *    否则这次迁移等于没解决「六预设与状态色同色相」那个既有问题。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { i18n } from '@/locales'
import { useAuthStore } from '@/stores/auth'
import OverviewView from '@/views/OverviewView.vue'

vi.mock('@/api/overview', () => ({
  overviewApi: {
    load: vi.fn(async () => ({
      projectId: 1,
      generatedAt: '2026-11-05T09:00:00+08:00',
      windowDays: 30,
      cases: { total: 2, byPriority: [], byReviewStatus: [], topDomains: [] },
      assets: { devices: 1, ports: 2, links: 1, credentials: 1, environments: 0, testSets: 1 },
      runs: {
        total: 2,
        inWindow: 2,
        running: 0,
        finished: 1,
        failed: 1,
        canceled: 0,
        casePass: 3,
        caseFail: 1,
        caseSkip: 0,
        passRateBp: 7500,
        avgDurationMs: 120000,
        lastRunAt: '2026-11-05T08:00:00+08:00',
      },
      recentRuns: [
        {
          runId: 'RUN-AAA',
          state: 'finished',
          startedAt: '2026-11-05T07:00:00+08:00',
          finishedAt: '2026-11-05T07:05:00+08:00',
          durationMs: 300000,
          total: 3,
          passed: 3,
          failed: 0,
          dut: 'DUT-A',
          link: 'LINK-AB',
          testSetName: null,
          triggerType: 'manual',
          passRateBp: 10000,
        },
        {
          runId: 'RUN-BBB',
          state: 'failed',
          startedAt: '2026-11-05T08:00:00+08:00',
          finishedAt: '2026-11-05T08:03:00+08:00',
          durationMs: 180000,
          total: 3,
          passed: 1,
          failed: 2,
          dut: null,
          link: null,
          testSetName: null,
          triggerType: 'cron',
          passRateBp: 3333,
        },
      ],
      dailyTrend: [
        { date: '2026-11-04', finished: 1, failed: 0, canceled: 0 },
        { date: '2026-11-05', finished: 0, failed: 1, canceled: 0 },
      ],
    })),
  },
  digestApi: {
    load: vi.fn(async () => ({
      projectId: 1,
      generatedAt: '2026-11-05T09:00:00+08:00',
      windowDays: 30,
      recentReports: [
        {
          runId: 'RUN-AAA',
          state: 'finished',
          finishedAt: '2026-11-05T07:05:00+08:00',
          total: 3,
          pass: 3,
          fail: 0,
          skip: 0,
          passRateBp: 10000,
        },
      ],
      topFailedCases: [
        { caseKey: 'TC-002', failures: 4, lastFailedAt: '2026-11-05T08:03:00+08:00' },
      ],
      coverage: { caseTotal: 2, covered: 1, neverExecuted: 1 },
      passRateTrend: [
        { runId: 'RUN-AAA', finishedAt: '2026-11-05T07:05:00+08:00', passRateBp: 10000, fail: 0 },
      ],
    })),
  },
}))

vi.mock('@/components/ProjectPicker.vue', () => ({
  default: { name: 'ProjectPicker', template: '<div />' },
}))

/**
 * 运行号那一格是 `h(RouterLink, ...)` 渲染的，用的是从模块导入的真组件，
 * 不是 app.component 注册的全局组件 —— 所以必须在模块层换掉，否则会去读
 * router 上下文里的 `currentRoute` 并崩在这里（vitest 报错指向 vue-router 内部）。
 */
vi.mock('vue-router', () => ({
  RouterLink: {
    name: 'RouterLink',
    props: { to: { type: [String, Object], default: '' } },
    template: '<a><slot /></a>',
  },
}))

let app: App | null = null
let host: HTMLElement | null = null
let pinia: ReturnType<typeof createPinia> | null = null

async function mountView(): Promise<HTMLElement> {
  const root = document.createElement('div')
  document.body.appendChild(root)
  host = root
  app = createApp(OverviewView)
  app.use(pinia!)
  app.use(i18n)
  app.mount(root)
  await new Promise((resolve) => setTimeout(resolve, 0))
  await nextTick()
  return root
}

beforeEach(() => {
  pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().projectId = 1
})

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
  pinia = null
})

describe('OverviewView 表格', () => {
  it('近期执行表保留设备与链路两段信息', async () => {
    const root = await mountView()
    expect(root.textContent).toContain('RUN-AAA')
    expect(root.textContent).toContain('DUT-A')
    expect(root.textContent).toContain('LINK-AB')
    // 没有设备的行显示统一占位，不能整格空白
    expect(root.textContent).toContain('—')
  })

  it('近期执行表的通过率与用例数都渲染出来', async () => {
    const root = await mountView()
    // formatPercent 统一保留一位小数（Intl），这里按实际格式断言，不另立一套期望
    expect(root.textContent).toContain('100.0%')
    expect(root.textContent).toContain('33.3%')
  })

  it('报告结果表覆盖失败用例前 N 与通过率', async () => {
    const root = await mountView()
    expect(root.textContent).toContain('TC-002')
    expect(root.textContent).toContain('3 / 0 / 0')
  })

  it('状态列是图标 + 文字双编码', async () => {
    const root = await mountView()
    const tags = [...root.querySelectorAll<HTMLElement>('.status-tag')]
    // 报告结果 1 行 + 近期执行 2 行
    expect(tags.length).toBe(3)
    expect(tags.every((tag) => tag.querySelector('svg') !== null)).toBe(true)
  })
})
