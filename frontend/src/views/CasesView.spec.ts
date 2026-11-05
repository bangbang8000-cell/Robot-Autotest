/**
 * `CasesView` 视图级回归（UX-04b 逐页迁移）。
 *
 * 为什么专门测这个：迁移把原生 `<table>` 换成了 `NDataTable`，而**勾选列没有**用
 * 内置的 `type: 'selection'`（内置列只认 checked-row-keys，刷新时会丢掉不在当前页的键，
 * 与 `useSelection` 的「跨页保留」契约冲突，FR-v0.2-35）。自己渲染 `NCheckbox` 意味着
 * 「勾选 → selectedIds → 批量条」这条线完全由本页接线，`useSelection` 的单测覆盖不到。
 *
 * 这里只断言接线，不重复测 useSelection 的内部逻辑。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { i18n } from '@/locales'
import { useAuthStore } from '@/stores/auth'
import CasesView from '@/views/CasesView.vue'
import { executionApi } from '@/api/execution'

// EXEC-02：执行所选只依赖 runCases，绑定解析在后端；这里只断言前端把 id 与参数发出去
vi.mock('@/api/execution', () => ({
  executionApi: {
    runCases: vi.fn(async () => ({ runId: 'RUN-9' })),
  },
}))

// 列表数据放在 hoisted 容器里而不是写死在工厂里：个别用例（L2 禁用执行）需要换一整页数据
const { pageItems } = vi.hoisted(() => ({
  pageItems: [] as Array<Record<string, string | number | null>>,
}))

// 工厂内直接写死数据：vi.mock 会被提升到 import 之前，引用外部 const 会 TDZ 报错
vi.mock('@/api/cases', () => ({
  caseApi: {
    page: vi.fn(async () => ({
      items: pageItems,
      total: pageItems.length,
      page: 1,
      size: 10,
    })),
    capabilities: vi.fn(async () => []),
    create: vi.fn(),
    submit: vi.fn(),
    review: vi.fn(),
    remove: vi.fn(),
    batch: vi.fn(),
    previewImport: vi.fn(),
    commitImport: vi.fn(),
  },
}))

// 项目选择器依赖 auth.projects，与本测试无关；stub 掉避免引入它的下拉逻辑
vi.mock('@/components/ProjectPicker.vue', () => ({
  default: { name: 'ProjectPicker', template: '<div />' },
}))

let app: App | null = null
let host: HTMLElement | null = null
let pinia: ReturnType<typeof createPinia> | null = null

/** 挂载并等首屏请求落定（load() 是 async，需要跨过 microtask）。 */
async function mountView(): Promise<HTMLElement> {
  const root = document.createElement('div')
  document.body.appendChild(root)
  host = root
  app = createApp(CasesView)
  // 必须复用 beforeEach 里那个 pinia：这里再 createPinia() 会得到一套新 state，
  // projectId 回到默认 0，load() 直接短路，页面停在「请先选择项目」
  app.use(pinia!)
  app.use(i18n)
  app.mount(root)
  // 先让 watch(immediate) 发请求，再让 Promise.all 的结果渲染进表格
  await new Promise((resolve) => setTimeout(resolve, 0))
  await nextTick()
  return root
}

/** NDataTable 的勾选框统一是 `.n-checkbox`，第 0 个是表头全选。 */
function checkboxes(root: HTMLElement): HTMLElement[] {
  return [...root.querySelectorAll<HTMLElement>('.n-checkbox')]
}

beforeEach(() => {
  pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().projectId = 1
  pageItems.splice(
    0,
    pageItems.length,
    {
      id: 1,
      caseCode: 'TC-001',
      title: 'first',
      priority: 'P1',
      safetyLevel: 'L1',
      reviewStatus: 'draft',
      domainTag: 'roce',
      capability: 'throughput',
    },
    {
      id: 2,
      caseCode: 'TC-002',
      title: 'second',
      priority: 'P2',
      safetyLevel: 'L1',
      reviewStatus: 'approved',
      domainTag: 'roce',
      capability: 'latency',
    },
  )
})

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
  pinia = null
  vi.clearAllMocks()
})

describe('CasesView 勾选接线', () => {
  it('渲染出接口返回的行', async () => {
    const root = await mountView()
    expect(root.textContent).toContain('TC-001')
    expect(root.textContent).toContain('TC-002')
  })

  it('勾一行出现批量条并计数为 1', async () => {
    const root = await mountView()
    const boxes = checkboxes(root)
    // 1 个表头 + 2 行
    expect(boxes).toHaveLength(3)

    boxes[1].click()
    await nextTick()

    // 批量条的计数文案来自 i18n，按同一套模板取期望值，避免硬编码中文
    expect(root.textContent).toContain(i18n.global.t('cases.batch.selected', { count: 1 }))
  })

  it('表头全选只选本页，再点一次全部取消', async () => {
    const root = await mountView()
    const header = checkboxes(root)[0]

    header.click()
    await nextTick()
    expect(root.textContent).toContain(i18n.global.t('cases.batch.selected', { count: 2 }))

    header.click()
    await nextTick()
    // 全部取消后批量条整体消失
    expect(root.textContent).not.toContain(i18n.global.t('cases.batch.selected', { count: 2 }))
    expect(root.textContent).not.toContain(i18n.global.t('cases.batch.selected', { count: 1 }))
  })

  it('状态列走 StatusTag，图标与文字双编码', async () => {
    const root = await mountView()
    // 每行两个徽标：评审状态 + 执行安全级（L1/L2/LX 不是普通文本，要能一眼分级）
    expect(root.querySelectorAll('.status-tag').length).toBe(4)
  })
})

describe('CasesView 执行所选（EXEC-02）', () => {
  /** 按 i18n 文案找按钮，避免硬编码中文。 */
  function button(root: HTMLElement, label: string): HTMLButtonElement {
    const found = [...root.querySelectorAll('button')].find((b) =>
      b.textContent?.includes(label),
    )
    expect(found).toBeTruthy()
    return found as HTMLButtonElement
  }

  async function openRunForm(root: HTMLElement): Promise<void> {
    checkboxes(root)[1].click()
    await nextTick()
    button(root, i18n.global.t('cases.run.open')).click()
    await nextTick()
    expect(root.textContent).toContain(i18n.global.t('cases.run.title', { count: 1 }))
  }

  it('提交只传所选 id 与执行参数，成功提示运行号', async () => {
    const root = await mountView()
    await openRunForm(root)

    button(root, i18n.global.t('cases.run.submit')).click()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()

    expect(executionApi.runCases).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ caseIds: [1], concurrency: 1, timeoutSeconds: 3600 }),
    )
    expect(root.textContent).toContain(i18n.global.t('cases.run.created', { runId: 'RUN-9' }))
  })

  it('后端未绑定 400：错误原文展示在表单内，勾选不丢', async () => {
    vi.mocked(executionApi.runCases).mockRejectedValueOnce(
      new Error('存在未绑定脚本的用例（1 条）：TC-001'),
    )
    const root = await mountView()
    await openRunForm(root)

    button(root, i18n.global.t('cases.run.submit')).click()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()

    expect(root.textContent).toContain('TC-001')
    expect(root.textContent).toContain(i18n.global.t('cases.batch.selected', { count: 1 }))
  })

  it('选中 L2 用例时禁用执行并给出原因（ADR-0013）', async () => {
    pageItems.splice(0, pageItems.length, {
      id: 7,
      caseCode: 'DESTRUCT-001',
      title: 'wipe disk',
      priority: 'P1',
      safetyLevel: 'L2',
      reviewStatus: 'approved',
      domainTag: 'storage',
      capability: null,
    })
    const root = await mountView()
    checkboxes(root)[1].click()
    await nextTick()

    // 按钮禁用只是提前告知，真正的拦截在后端（整单 403 SAFETY_LEVEL_BLOCKED）
    expect(button(root, i18n.global.t('cases.run.open')).disabled).toBe(true)
    expect(root.textContent).toContain(
      i18n.global.t('cases.run.blocked', { codes: 'DESTRUCT-001' }),
    )
  })
})
