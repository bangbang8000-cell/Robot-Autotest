/**
 * `PlanGroupsView` 视图级回归（ORCH-01）。
 *
 * 本页三处是「静态检查看不出来」的，所以专门盯它们：
 * ① **顺序提交**：上移/下移必须是「整条顺序数组提交」，如果只把 sortOrder 改一位再发给后端，
 *    后端 reorder 是全量语义，会把它当「只剩这一条」把其它成员删掉；
 * ② **应用结果的两个数**：created 与 skipped 在重复应用时分别是 0 和 n-1，
 *    文案接错会让用户以为「又建了一遍边」；
 * ③ **成环错误原样展示**：后端返回的是带计划名的路径（`检测到循环依赖: A → B → A`），
 *    前端要是吞掉换成「操作失败」，用户就永远不知道是哪两条计划成的环。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia, setActivePinia, type Pinia } from 'pinia'
import { i18n } from '@/locales'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/api/http'
import PlanGroupsView from '@/views/PlanGroupsView.vue'
import { planGroupApi } from '@/api/planGroups'

vi.mock('@/api/planGroups', () => ({
  planGroupApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
    addMembers: vi.fn(),
    removeMember: vi.fn(),
    reorder: vi.fn(),
    apply: vi.fn(),
  },
}))

vi.mock('@/api/plans', () => ({
  planApi: {
    page: vi.fn(async () => ({
      items: [
        { id: 1, name: 'plan-1' },
        { id: 2, name: 'plan-2' },
        { id: 3, name: 'plan-3' },
        { id: 4, name: 'plan-4' },
      ],
      total: 4,
      page: 1,
      size: 100,
    })),
  },
}))

const group = (id: number, name: string, planIds: number[]) => ({
  id,
  projectId: 1,
  name,
  description: null,
  items: planIds.map((planId, index) => ({
    planId,
    planName: `plan-${planId}`,
    sortOrder: index,
  })),
  edgeCount: Math.max(0, planIds.length - 1),
  createdAt: null,
})

let app: App | null = null
let host: HTMLElement | null = null
let pinia: Pinia | null = null

async function mountView(): Promise<HTMLElement> {
  const root = document.createElement('div')
  document.body.appendChild(root)
  host = root
  app = createApp(PlanGroupsView)
  app.use(pinia!)
  app.use(i18n)
  app.mount(root)
  await new Promise((resolve) => setTimeout(resolve, 0))
  await nextTick()
  return root
}

function memberNames(root: HTMLElement): string[] {
  return [...root.querySelectorAll<HTMLElement>('[data-testid="members"] .name')].map(
    (item) => item.textContent?.trim() ?? '',
  )
}

function memberButton(root: HTMLElement, index: number, text: string): HTMLButtonElement {
  const row = root.querySelectorAll<HTMLElement>('[data-testid="members"] li')[index]
  const button = [...row.querySelectorAll<HTMLButtonElement>('button')].find(
    (item) => item.textContent?.trim() === text,
  )
  if (!button) throw new Error(`button not found: ${text}`)
  return button
}

beforeEach(() => {
  vi.mocked(planGroupApi.list).mockResolvedValue([group(11, '每夜链', [1, 2, 3])])
  vi.mocked(planGroupApi.reorder).mockResolvedValue(group(11, '每夜链', [2, 1, 3]))
  vi.mocked(planGroupApi.apply).mockResolvedValue({
    groupId: 11,
    created: 2,
    skipped: 0,
    group: group(11, '每夜链', [1, 2, 3]),
  })
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

describe('PlanGroupsView', () => {
  it('默认选中第一个组，成员按顺序渲染', async () => {
    const root = await mountView()

    expect(root.querySelector('[data-testid="group-detail"]')).not.toBeNull()
    expect(memberNames(root)).toEqual(['plan-1', 'plan-2', 'plan-3'])
  })

  it('上移提交的是整条顺序数组，不是只改一格', async () => {
    const root = await mountView()

    memberButton(root, 1, '上移').click()
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(planGroupApi.reorder).toHaveBeenCalledWith(1, 11, [2, 1, 3])
  })

  it('应用后展示 created 与 skipped 两个数', async () => {
    vi.mocked(planGroupApi.apply).mockResolvedValue({
      groupId: 11,
      created: 0,
      skipped: 2,
      group: group(11, '每夜链', [1, 2, 3]),
    })
    const root = await mountView()

    root.querySelector<HTMLButtonElement>('[data-testid="apply"]')!.click()
    await new Promise((resolve) => setTimeout(resolve, 0))

    const notice = root.querySelector('[data-testid="notice"]')?.textContent ?? ''
    expect(notice).toContain('0')
    expect(notice).toContain('2')
  })

  it('成环错误原样展示后端文案（含计划名路径）', async () => {
    vi.mocked(planGroupApi.apply).mockRejectedValue(
      new ApiError(40001, 'INVALID_ARGUMENT', '组内顺序与既有依赖成环: plan-1 → plan-2 → plan-1'),
    )
    const root = await mountView()

    root.querySelector<HTMLButtonElement>('[data-testid="apply"]')!.click()
    await new Promise((resolve) => setTimeout(resolve, 0))

    const error = root.querySelector('[data-testid="error"]')?.textContent ?? ''
    expect(error).toContain('plan-1')
    expect(error).toContain('plan-2')
  })

  it('添加成员下拉只列不在组内的计划', async () => {
    const root = await mountView()

    const options = [
      ...root.querySelectorAll<HTMLOptionElement>('[data-testid="add-member"] option'),
    ].map((item) => item.textContent?.trim() ?? '')

    expect(options).toContain('plan-4')
    expect(options).not.toContain('plan-1')
  })
})
