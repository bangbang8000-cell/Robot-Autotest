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
import { executionApi } from '@/api/execution'

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
          deviceScope: 'DUT-01/mlx5_0,DUT-02/mlx5_1',
          attemptNo: 2,
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
          attemptNo: 1,
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
    downloadLog: vi.fn(async () => new Blob(['line1'])),
    recentLog: vi.fn(async () => [
      { seq: 1, tsGen: '01:00:01', source: 'robot', level: 'info', text: 'started' },
      { seq: 2, tsGen: '01:00:02', source: 'robot', level: 'error', text: 'FAIL boom' },
      { seq: 3, tsGen: '01:00:03', source: 'robot', level: 'warn', text: 'slow step' },
    ]),
    cancel: vi.fn(),
    get: vi.fn(),
    pendingActivity: vi.fn(async (_projectId: number, runId: string) => ({
      runId,
      state: 'queued' as const,
      position: 2,
      waiting: 3,
      running: 1,
      maxParallel: 2,
    })),
    quickRun: vi.fn(async () => ({ runId: 'RUN-MANUAL-1', state: 'QUEUED' })),
    runTestSet: vi.fn(async () => ({ runId: 'RUN-SET-1', state: 'QUEUED' })),
  },
}))

vi.mock('@/api/asset', () => ({
  deviceApi: {
    page: vi.fn(async () => ({
      items: [{ id: 1, projectId: 1, name: 'DUT-01' }],
      total: 1,
      page: 1,
      size: 100,
    })),
  },
}))

vi.mock('@/api/cases', () => ({
  caseSetApi: {
    page: vi.fn(async () => ({
      items: [{ id: 7, projectId: 1, setCode: 'SMOKE', name: '冒烟集', description: null }],
      total: 1,
      page: 1,
      size: 100,
    })),
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
  vi.clearAllMocks()
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

  it('列表页直接显示设备范围，未声明时写「全部设备」而不是空白（EXEC-11）', async () => {
    const root = await mountView()
    const rows = [...root.querySelectorAll<HTMLElement>('.n-data-table-tbody .n-data-table-tr')]

    // RUN-AAA 声明了两条设备，RUN-BBB 未声明
    expect(rows[0].textContent).toContain('DUT-01/mlx5_0,DUT-02/mlx5_1')
    expect(rows[1].textContent).toContain(i18n.global.t('execution.allDevices'))
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

  it('日志面板可下载完整日志（EXEC-06）', async () => {
    const root = await mountView()
    const rows = [...root.querySelectorAll<HTMLElement>('.n-data-table-tbody .n-data-table-tr')]
    const viewLog = [...rows[1].querySelectorAll<HTMLButtonElement>('button')].find(
      (btn) => btn.textContent?.trim() === i18n.global.t('execution.viewLog'),
    )
    viewLog!.click()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()

    Object.defineProperty(URL, 'createObjectURL', {
      value: vi.fn(() => 'blob:test'),
      configurable: true,
    })
    Object.defineProperty(URL, 'revokeObjectURL', {
      value: vi.fn(),
      configurable: true,
    })

    const download = [...root.querySelectorAll<HTMLButtonElement>('button')].find(
      (btn) => btn.textContent?.trim() === i18n.global.t('execution.downloadLog'),
    )
    expect(download).toBeTruthy()
    download!.click()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()

    expect(executionApi.downloadLog).toHaveBeenCalledWith(1, 'RUN-BBB')
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

  it('排队中的运行在日志面板显示位次，位次未知时明说未知（EXEC-10）', async () => {
    const queued = {
      id: 3,
      runId: 'RUN-QUEUED',
      projectId: 1,
      state: 'QUEUED',
      attemptNo: 1,
      passedTests: 0,
      totalTests: 0,
      failureReason: null,
      startedAt: null,
      finishedAt: null,
    }
    vi.mocked(executionApi.page).mockResolvedValueOnce({
      items: [queued],
      total: 1,
      page: 1,
      size: 10,
    } as never)
    const root = await mountView()

    const viewLog = [...root.querySelectorAll<HTMLButtonElement>('button')].find(
      (btn) => btn.textContent?.trim() === i18n.global.t('execution.viewLog'),
    )
    viewLog!.click()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()

    expect(executionApi.pendingActivity).toHaveBeenCalledWith(1, 'RUN-QUEUED')
    expect(root.textContent).toContain(
      i18n.global.t('execution.queuePosition', { position: 2, waiting: 3 }),
    )

    // 后端重启后派发器不知道位次（position=0）：不假装知道，也不显示「第 0 位」
    vi.mocked(executionApi.pendingActivity).mockResolvedValueOnce({
      runId: 'RUN-QUEUED',
      state: 'queued',
      position: 0,
      waiting: 0,
      running: 0,
      maxParallel: 2,
    })
    viewLog!.click()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()
    expect(root.textContent).toContain(i18n.global.t('execution.queueUnknown'))
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

/**
 * EXEC-01 手动执行表单。
 *
 * 后端接口另有单测；这里守前端接线：按钮开表单、套件路径按行解析后原样提交、
 * 空路径时提交按钮禁用（不能把空列表打到后端再吃 400）。
 */
describe('RunsView 手动执行', () => {
  function manualButton(root: HTMLElement): HTMLButtonElement {
    const button = [...root.querySelectorAll<HTMLButtonElement>('.toolbar button')].find(
      (item) => item.textContent?.trim() === i18n.global.t('execution.manual.open'),
    )
    if (!button) throw new Error('toolbar manual button not found')
    return button
  }

  function submitButton(root: HTMLElement): HTMLButtonElement {
    const button = [...root.querySelectorAll<HTMLButtonElement>('.form-actions button')].find(
      (item) => item.textContent?.trim() === i18n.global.t('execution.manual.submit'),
    )
    if (!button) throw new Error('submit button not found')
    return button
  }

  it('点「手动执行」打开表单，默认带一套可改的套件路径', async () => {
    const root = await mountView()
    expect(root.querySelector('.form-card')).toBeNull()

    manualButton(root).click()
    await nextTick()

    const card = root.querySelector('.form-card')
    expect(card).not.toBeNull()
    const textarea = card!.querySelector<HTMLTextAreaElement>('textarea')
    expect(textarea?.value).toBe('suites')
    expect(submitButton(root).disabled).toBe(false)
  })

  it('套件路径按行解析并原样提交给 quick-run', async () => {
    const root = await mountView()
    manualButton(root).click()
    await nextTick()

    const textarea = root.querySelector<HTMLTextAreaElement>('.form-card textarea')!
    textarea.value = 'suites/perf\n\n suites/rdma '
    textarea.dispatchEvent(new Event('input'))
    await nextTick()

    submitButton(root).click()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()

    expect(executionApi.quickRun).toHaveBeenCalledTimes(1)
    expect(executionApi.quickRun).toHaveBeenCalledWith(
      expect.objectContaining({
        projectId: 1,
        suitePaths: ['suites/perf', 'suites/rdma'],
        source: 'runs-view',
        testTimeoutSeconds: 0,
      }),
    )
    expect(root.textContent).toContain(
      i18n.global.t('execution.manual.created', { runId: 'RUN-MANUAL-1' }),
    )
  })

  it('套件路径清空后提交按钮禁用，不会把空列表打到后端', async () => {
    const root = await mountView()
    manualButton(root).click()
    await nextTick()

    const textarea = root.querySelector<HTMLTextAreaElement>('.form-card textarea')!
    textarea.value = '   \n\n '
    textarea.dispatchEvent(new Event('input'))
    await nextTick()

    expect(submitButton(root).disabled).toBe(true)
    expect(executionApi.quickRun).not.toHaveBeenCalled()
  })

  it('手动执行表单可声明设备范围，并随 quick-run 提交（FR-v0.4-01）', async () => {
    const root = await mountView()
    manualButton(root).click()
    await nextTick()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()

    expect(root.textContent).toContain(i18n.global.t('execution.manual.deviceScope'))

    // 设备范围支持手填端口键（tag 模式），不能只局限于设备名下拉
    const inputs = [...root.querySelectorAll<HTMLInputElement>('.form-card .n-select input')]
    const scopeInput = inputs[inputs.length - 1]
    scopeInput.focus()
    scopeInput.value = 'DUT-03/mlx5_2'
    scopeInput.dispatchEvent(new Event('input', { bubbles: true }))
    await nextTick()
    scopeInput.dispatchEvent(new KeyboardEvent('keydown', {
      key: 'Enter',
      code: 'Enter',
      keyCode: 13,
      bubbles: true,
    }))
    await nextTick()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()

    submitButton(root).click()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()

    expect(executionApi.quickRun).toHaveBeenCalledWith(
      expect.objectContaining({ deviceKeys: ['DUT-03/mlx5_2'] }),
    )
  })

  it('日志面板显示运行的设备范围，未声明时明确写「全部设备」', async () => {
    const root = await mountView()
    const viewButtons = [...root.querySelectorAll<HTMLButtonElement>('button')].filter(
      (item) => item.textContent?.trim() === i18n.global.t('execution.viewLog'),
    )
    viewButtons[0].click()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()

    expect(root.textContent).toContain(
      i18n.global.t('execution.deviceScopeLine', { scope: 'DUT-01/mlx5_0,DUT-02/mlx5_1' }),
    )
  })

  it('日志面板显示「第 N 次尝试」，首次尝试不渲染该标记（FR-v0.4-29）', async () => {
    const root = await mountView()
    const viewButtons = [...root.querySelectorAll<HTMLButtonElement>('button')].filter(
      (item) => item.textContent?.trim() === i18n.global.t('execution.viewLog'),
    )
    viewButtons[0].click()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()

    // RUN-AAA 是第 2 次尝试，重试可追溯
    expect(root.textContent).toContain(i18n.global.t('execution.attemptLine', { n: 2 }))

    viewButtons[1].click()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()

    // RUN-BBB 是首次尝试，标记不应出现——否则正常记录会被同一句提示占满
    expect(root.textContent).not.toContain(i18n.global.t('execution.attemptLine', { n: 1 }))
  })

  it('日志按级别过滤：取消「信息」后 INFO 行隐藏，ERROR / WARN 保留（FR-v0.4-20）', async () => {
    const root = await mountView()
    const viewButtons = [...root.querySelectorAll<HTMLButtonElement>('button')].filter(
      (item) => item.textContent?.trim() === i18n.global.t('execution.viewLog'),
    )
    viewButtons[0].click()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()

    expect(root.textContent).toContain('started')
    expect(root.textContent).toContain('FAIL boom')
    expect(root.textContent).toContain('slow step')

    const boxes = [...root.querySelectorAll<HTMLElement>('.level-filter')]
    expect(boxes).toHaveLength(3)
    const boxOf = (key: string) =>
      boxes.find((box) => box.textContent?.includes(i18n.global.t(key))) as HTMLElement

    boxOf('execution.levelInfo').click()
    await nextTick()
    expect(root.textContent).not.toContain('started')
    expect(root.textContent).toContain('FAIL boom')
    expect(root.textContent).toContain('slow step')

    // 三档全关不能说成「这份 run 没日志」，要明说是被过滤掉了
    boxOf('execution.levelError').click()
    boxOf('execution.levelWarn').click()
    await nextTick()
    expect(root.textContent).not.toContain('FAIL boom')
    expect(root.textContent).toContain(i18n.global.t('execution.logFilteredEmpty'))
  })
})
