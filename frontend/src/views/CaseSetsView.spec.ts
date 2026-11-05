/**
 * `CaseSetsView` 视图级回归（CAS-02 前端补齐）。
 *
 * 这一页把「用例集」从只能被计划间接触达，变成可维护的实体。三处容易在静态检查下
 * 蒙混过关、上线才暴露的接线，是本组用例的重点：
 * ① 列表三列（编号 / 名称 / 描述）必须都渲染，描述为空要显示占位而不是空白；
 * ② 「管理用例」要真的拉成员并按后端顺序编号，打开面板却没数据等于功能不可用；
 * ③ 「上移 / 下移」提交的必须是**完整成员列表的新顺序**，只提交被移动的一条会被后端拒绝。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, h, nextTick, type App, type Slots } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { i18n } from '@/locales'
import { useAuthStore } from '@/stores/auth'
import { caseSetApi } from '@/api/cases'
import CaseSetsView from '@/views/CaseSetsView.vue'

const sets = [
  {
    id: 7,
    projectId: 1,
    setCode: 'SMOKE-001',
    name: '冒烟集',
    description: '每日冒烟',
  },
  {
    id: 8,
    projectId: 1,
    setCode: 'REG-002',
    name: '回归集',
    description: null,
  },
]

const items = [
  { id: 101, projectId: 1, caseCode: 'TC-001', title: '登录成功' },
  { id: 102, projectId: 1, caseCode: 'TC-002', title: '错误口令' },
]

vi.mock('@/api/cases', () => ({
  caseSetApi: {
    page: vi.fn(async () => ({ items: sets, total: 2, page: 1, size: 100 })),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
    items: vi.fn(async () => items),
    addItem: vi.fn(),
    removeItem: vi.fn(),
    reorder: vi.fn(async () => undefined),
  },
  caseApi: {
    page: vi.fn(async () => ({ items, total: 2, page: 1, size: 50 })),
  },
}))

vi.mock('@/api/execution', () => ({
  executionApi: {
    runTestSet: vi.fn(async () => ({ runId: 'RUN-001' })),
  },
}))

vi.mock('@/components/ProjectPicker.vue', () => ({
  default: { name: 'ProjectPicker', template: '<div />' },
}))

/** 页面用 `<RouterLink v-slot="{ navigate }" custom>` 包按钮，给同契约的桩即可。 */
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
  app = createApp(CaseSetsView)
  app.use(pinia!)
  app.use(i18n)
  app.component('RouterLink', RouterLinkStub)
  app.mount(root)
  await new Promise((resolve) => setTimeout(resolve, 0))
  await nextTick()
  return root
}

function rows(root: HTMLElement): HTMLElement[] {
  return [...root.querySelectorAll<HTMLElement>('.n-data-table-tbody .n-data-table-tr')]
}

function rowButton(root: HTMLElement, rowIndex: number, text: string): HTMLButtonElement {
  const button = [...rows(root)[rowIndex].querySelectorAll<HTMLButtonElement>('button')].find(
    (item) => item.textContent?.trim() === text,
  )
  if (!button) throw new Error(`button not found in row ${rowIndex}: ${text}`)
  return button
}

/** 面板里的操作按钮（上移 / 下移 / 移除），按顺序取。 */
function itemButtons(root: HTMLElement, index: number): HTMLButtonElement[] {
  const list = [...root.querySelectorAll<HTMLElement>('.set-item')]
  if (!list[index]) throw new Error(`set item ${index} not found`)
  return [...list[index].querySelectorAll<HTMLButtonElement>('button')]
}

beforeEach(() => {
  pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().projectId = 1
  vi.mocked(caseSetApi.reorder).mockClear()
})

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
  pinia = null
})

describe('CaseSetsView 列表', () => {
  it('编号 / 名称 / 描述三列都渲染，空描述显示占位符', async () => {
    const root = await mountView()
    const text = root.textContent ?? ''
    expect(text).toContain('SMOKE-001')
    expect(text).toContain('冒烟集')
    expect(text).toContain('每日冒烟')
    expect(text).toContain('REG-002')
    // 第二条 description 为 null，不能渲染成空白单元格
    expect(text).toContain('—')
  })

  it('点「编辑」预填原值，且集合编号不可改', async () => {
    const root = await mountView()
    rowButton(root, 1, i18n.global.t('caseSets.edit')).click()
    await nextTick()

    const card = root.querySelector<HTMLElement>('.form-card')
    expect(card).not.toBeNull()
    expect(card!.textContent).toContain(i18n.global.t('caseSets.editTitle'))
    const inputs = [...card!.querySelectorAll<HTMLInputElement>('input')]
    expect(inputs.map((input) => input.value)).toContain('回归集')
    const codeInput = inputs.find((input) => input.value === 'REG-002')
    expect(codeInput?.disabled).toBe(true)
  })
})

describe('CaseSetsView 成员管理', () => {
  it('打开面板后按后端顺序列出成员并编号', async () => {
    const root = await mountView()
    rowButton(root, 0, i18n.global.t('caseSets.manage')).click()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()

    const nodes = [...root.querySelectorAll<HTMLElement>('.set-item')]
    expect(nodes).toHaveLength(2)
    expect(nodes[0].textContent).toContain('TC-001')
    expect(nodes[0].querySelector('.set-item-order')?.textContent?.trim()).toBe('1')
    expect(nodes[1].textContent).toContain('TC-002')
    expect(nodes[1].querySelector('.set-item-order')?.textContent?.trim()).toBe('2')
  })

  it('上移提交完整成员列表的新顺序，而不是只提交被移动的一条', async () => {
    const root = await mountView()
    rowButton(root, 0, i18n.global.t('caseSets.manage')).click()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()

    // 第二行点「上移」→ 顺序应变成 [102, 101]
    const buttons = itemButtons(root, 1)
    const moveUp = buttons.find((b) => b.textContent?.trim() === i18n.global.t('caseSets.moveUp'))
    expect(moveUp).toBeTruthy()
    moveUp!.click()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()

    expect(caseSetApi.reorder).toHaveBeenCalledTimes(1)
    expect(caseSetApi.reorder).toHaveBeenCalledWith(1, 7, [102, 101])
    const nodes = [...root.querySelectorAll<HTMLElement>('.set-item')]
    expect(nodes[0].textContent).toContain('TC-002')
  })
})
