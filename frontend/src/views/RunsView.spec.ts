/**
 * `RunsView` 视图级回归（UX-04b 逐页迁移）。
 *
 * 关注三件在迁移中重新接线、且 `vue-tsc` 与构建都看不出问题的事：
 * ① 「查看日志」按钮 → 选中行 → 日志面板这条联动；
 * ② 选中行的底色（`row-active`）必须落在**正确的那一行**，否则用户会对着 A 行读 B 行日志；
 * ③ 状态列从纯文字换成 `StatusTag` 后，仍然是「图标 + 文字」双编码，不是只剩颜色。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, h, nextTick, type App, type Slots } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { i18n } from '@/locales'
import { useAuthStore } from '@/stores/auth'
import { runStatusLabel } from '@/utils/statusLabels'
import RunsView from '@/views/RunsView.vue'

vi.mock('@/api/execution', () => ({
  runLogSocketUrl: (runId: string) => `ws://test/runs/${runId}/log`,
  executionApi: {
    page: vi.fn(async () => ({
      items: [
        {
          id: 1,
          runId: 'RUN-AAA',
          projectId: 1,
          state: 'FINISHED',
          passedTests: 3,
          totalTests: 3,
          failureReason: null,
          startedAt: '2026-11-05T01:00:00Z',
          finishedAt: '2026-11-05T01:05:00Z',
        },
        {
          id: 2,
          runId: 'RUN-BBB',
          projectId: 1,
          state: 'FAILED',
          passedTests: 1,
          totalTests: 3,
          failureReason: 'assertion failed',
          startedAt: '2026-11-05T02:00:00Z',
          finishedAt: null,
        },
      ],
      total: 2,
      page: 1,
      size: 10,
    })),
    recentLog: vi.fn(async () => [
      { seq: 1, tsGen: '01:00:01', source: 'robot', level: 'info', text: 'started' },
      { seq: 2, tsGen: '01:00:02', source: 'robot', level: 'error', text: 'FAIL boom' },
    ]),
    cancel: vi.fn(),
    get: vi.fn(),
  },
}))

vi.mock('@/components/ProjectPicker.vue', () => ({
  default: { name: 'ProjectPicker', template: '<div />' },
}))

/** 组件会 `new WebSocket(...)`；happy-dom 无实现，给一个不自动回声的桩即可。 */
class FakeSocket {
  onopen: (() => void) | null = null
  onmessage: ((event: { data: string }) => void) | null = null
  onerror: (() => void) | null = null
  onclose: (() => void) | null = null
  close(): void {}
}

/**
 * 组件用 `<RouterLink v-slot="{ navigate }" custom>` 包了一个按钮。不装整个 vue-router
 * 只为了这条链接不划算，给一个同契约的桩：把 navigate 交给默认插槽即可。
 */
const RouterLinkStub = {
  name: 'RouterLink',
  props: { to: { type: [String, Object], default: '' } },
  setup(_props: unknown, { slots }: { slots: Slots }) {
    return () => h('a', slots.default?.({ navigate: () => {} }))
  },
}

let app: App | null = null
let host: HTMLElement | null = null
let pinia: ReturnType<typeof createPinia> | null = null

async function mountView(): Promise<HTMLElement> {
  const root = document.createElement('div')
  document.body.appendChild(root)
  host = root
  app = createApp(RunsView)
  app.use(pinia!)
  app.use(i18n)
  app.component('RouterLink', RouterLinkStub)
  app.mount(root)
  await new Promise((resolve) => setTimeout(resolve, 0))
  await nextTick()
  return root
}

beforeEach(() => {
  vi.stubGlobal('WebSocket', FakeSocket)
  pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().projectId = 1
})

afterEach(() => {
  app?.unmount()
  host?.remove()
  vi.unstubAllGlobals()
  app = null
  host = null
  pinia = null
})

describe('RunsView 列表与日志联动', () => {
  it('渲染两行运行记录', async () => {
    const root = await mountView()
    expect(root.textContent).toContain('RUN-AAA')
    expect(root.textContent).toContain('RUN-BBB')
  })

  it('点「查看日志」后打开对应行的面板，且只有那一行被标为选中', async () => {
    const root = await mountView()
    expect(root.textContent).not.toContain('started')

    // 第二行（FAILED）的「查看日志」——注意按文案取到的是全部按钮里的第一个，
    // 而第一行也有同名按钮，所以这里按行定位：先取第二条数据行的按钮。
    const rows = [...root.querySelectorAll<HTMLElement>('.n-data-table-tbody .n-data-table-tr')]
    const secondRow = rows[1]
    const viewLog = [...secondRow.querySelectorAll<HTMLButtonElement>('button')].find(
      (btn) => btn.textContent?.trim() === i18n.global.t('execution.viewLog'),
    )
    expect(viewLog).toBeTruthy()
    viewLog!.click()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()

    // 面板打开，且显示的是第二行的 runId 与日志内容
    expect(root.textContent).toContain('RUN-BBB')
    expect(root.textContent).toContain('started')

    // 高亮只落在第二行
    const activeRows = root.querySelectorAll('.n-data-table-tr.row-active')
    expect(activeRows).toHaveLength(1)
    expect(activeRows[0].textContent).toContain('RUN-BBB')
  })

  it('状态列是图标 + 文字双编码，不是只靠颜色', async () => {
    const root = await mountView()
    const tags = [...root.querySelectorAll<HTMLElement>('.status-tag')]
    expect(tags.length).toBeGreaterThanOrEqual(2)
    const texts = tags.map((tag) => tag.textContent?.trim())
    expect(texts).toContain(runStatusLabel('FINISHED'))
    expect(texts).toContain(runStatusLabel('FAILED'))
    // 每个徽标都带一个 svg 图标，色觉障碍下仍可辨
    expect(tags.every((tag) => tag.querySelector('svg') !== null)).toBe(true)
  })

  it('已终止的行不再显示「取消」', async () => {
    const root = await mountView()
    const rows = [...root.querySelectorAll<HTMLElement>('.n-data-table-tbody .n-data-table-tr')]
    const cancelText = i18n.global.t('execution.cancelRun')
    for (const row of rows) {
      const texts = [...row.querySelectorAll('button')].map((btn) => btn.textContent?.trim())
      expect(texts).not.toContain(cancelText)
    }
  })
})
