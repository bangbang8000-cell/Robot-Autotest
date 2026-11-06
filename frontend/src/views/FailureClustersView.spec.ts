/**
 * `FailureClustersView` 视图级回归（ORCH-03）。
 *
 * 三个「静态检查看不出来」的点：
 * ① **占比是小数**：后端给 0.6667，直接拼 `%` 会显示「0.6667%」，必须 ×100；
 * ② **空 caseIds 要禁用重跑**：导入的失败行没有关联台账，按钮可点就会发出空请求，
 *    后端返回 400，用户以为是平台坏了；
 * ③ **空 runId 不发请求**：`if (!runId) return` 漏了会在页面刚打开时打一次 400。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia, setActivePinia, type Pinia } from 'pinia'
import { i18n } from '@/locales'
import { useAuthStore } from '@/stores/auth'
import { failureClusterApi } from '@/api/failureClusters'
import FailureClustersView from '@/views/FailureClustersView.vue'

vi.mock('@/api/failureClusters', () => ({
  failureClusterApi: {
    load: vi.fn(),
    rerun: vi.fn(),
  },
}))

let app: App | null = null
let host: HTMLElement | null = null
let pinia: Pinia

const view = {
  runId: 'RUN-1',
  total: 3,
  runStatus: 'FAILED',
  runFailureClass: 'ASSERT',
  classes: [
    {
      failureClass: 'ASSERT',
      count: 2,
      ratio: 0.6667,
      clusters: [
        {
          key: 'AssertionError: <n> != <n>',
          count: 2,
          ratio: 0.6667,
          samples: [{ caseId: 1, caseCode: 'CASE-1', caseTitle: 't1' }],
          caseIds: [1, 2],
        },
      ],
    },
    {
      failureClass: 'ENV',
      count: 1,
      ratio: 0.3333,
      clusters: [
        {
          key: 'Connection refused <ip>',
          count: 1,
          ratio: 0.3333,
          samples: [{ caseId: null, caseCode: null, caseTitle: 'imported-row' }],
          caseIds: [],
        },
      ],
    },
  ],
}

async function mountView(): Promise<HTMLElement> {
  const root = document.createElement('div')
  document.body.appendChild(root)
  host = root
  app = createApp(FailureClustersView)
  app.use(pinia)
  app.use(i18n)
  app.mount(root)
  await new Promise((resolve) => setTimeout(resolve, 0))
  await nextTick()
  return root
}

async function query(root: HTMLElement): Promise<void> {
  const input = root.querySelector<HTMLInputElement>('[data-testid="query"] input')!
  input.value = 'RUN-1'
  input.dispatchEvent(new Event('input'))
  root.querySelector<HTMLFormElement>('[data-testid="query"]')!.dispatchEvent(new Event('submit'))
  await new Promise((resolve) => setTimeout(resolve, 0))
  await nextTick()
}

beforeEach(() => {
  vi.mocked(failureClusterApi.load).mockResolvedValue(view as never)
  vi.mocked(failureClusterApi.rerun).mockResolvedValue({ runId: 'RUN-2' } as never)
  pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().projectId = 1
})

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
  document.body.innerHTML = ''
  vi.clearAllMocks()
})

describe('FailureClustersView', () => {
  it('空 runId 不发请求', async () => {
    const root = await mountView()
    root.querySelector<HTMLFormElement>('[data-testid="query"]')!.dispatchEvent(new Event('submit'))
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(failureClusterApi.load).not.toHaveBeenCalled()
  })

  it('渲染两级：两个类别各带自己的簇', async () => {
    const root = await mountView()
    await query(root)

    const groups = root.querySelectorAll('[data-testid="class-group"]')
    expect(groups.length).toBe(2)
    expect(groups[0].textContent).toContain('ASSERT')
    expect(groups[1].textContent).toContain('ENV')
    expect(groups[0].querySelectorAll('[data-testid="cluster-row"]').length).toBe(1)
  })

  it('占比按百分比展示（0.6667 → 66.7%）', async () => {
    const root = await mountView()
    await query(root)

    const firstRow = root.querySelectorAll<HTMLElement>('[data-testid="cluster-row"]')[0]
    expect(firstRow.textContent).toContain('66.7%')
    expect(firstRow.textContent).not.toContain('0.6667%')
  })

  it('caseIds 为空的簇禁用重跑', async () => {
    const root = await mountView()
    await query(root)

    const buttons = root.querySelectorAll<HTMLButtonElement>('[data-testid="rerun"]')
    expect(buttons[0].disabled).toBe(false)
    expect(buttons[1].disabled).toBe(true)
  })

  it('重跑带上项目、运行与簇键', async () => {
    const root = await mountView()
    await query(root)

    root.querySelectorAll<HTMLButtonElement>('[data-testid="rerun"]')[0].click()
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(failureClusterApi.rerun).toHaveBeenCalledWith(1, 'RUN-1', 'AssertionError: <n> != <n>')
    expect(root.querySelector('[data-testid="notice"]')?.textContent).toContain('RUN-2')
  })
})
