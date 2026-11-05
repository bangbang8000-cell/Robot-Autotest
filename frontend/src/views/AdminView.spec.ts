/**
 * `AdminView` 视图级回归（UX-04b 逐页迁移）。
 *
 * 这一页有 4 张表、4 个数据源，迁移中最容易坏的不是样式而是**权限与只读语义**：
 * ① 所有者那一行不能给可编辑的角色下拉（后端会拒，界面上却像个能改的控件）；
 * ② 停用用户不是「否」，是「已停用」——两种说法在成员/候选人两处都要一致；
 * ③ 用户表的勾选仍要接线到批量条（与资产页同一套 useSelection）。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { i18n } from '@/locales'
import { useAuthStore } from '@/stores/auth'
import AdminView from '@/views/AdminView.vue'

vi.mock('@/api/iam', () => ({
  roleApi: {
    list: vi.fn(async () => [
      { id: 9, code: 'admin', name: '管理员', description: null, builtIn: true, permissionsJson: null },
    ]),
  },
  userApi: {
    page: vi.fn(async () => ({
      items: [
        {
          id: 1,
          username: 'alice',
          displayName: '爱丽丝',
          email: 'alice@example.com',
          orgId: null,
          enabled: true,
          passwordExpiresAt: null,
          lastLoginAt: null,
          roles: ['admin'],
        },
        {
          id: 2,
          username: 'bob',
          displayName: '鲍勃',
          email: null,
          orgId: null,
          enabled: false,
          passwordExpiresAt: null,
          lastLoginAt: null,
          roles: [],
        },
      ],
      total: 2,
      page: 1,
      size: 10,
    })),
    create: vi.fn(),
    update: vi.fn(),
  },
  projectApi: {
    create: vi.fn(),
    members: vi.fn(async () => [
      {
        id: 1,
        projectId: 1,
        userId: 1,
        username: 'alice',
        roleCode: 'admin',
        displayName: '爱丽丝',
        enabled: true,
      },
      {
        id: 2,
        projectId: 1,
        userId: 2,
        username: 'bob',
        roleCode: 'engineer',
        displayName: '鲍勃',
        enabled: false,
      },
    ]),
    addMember: vi.fn(),
    updateMember: vi.fn(),
    removeMember: vi.fn(),
  },
}))

vi.mock('@/api/batch', () => ({
  batchApi: { run: vi.fn(async () => ({ items: [] })) },
}))

let app: App | null = null
let host: HTMLElement | null = null
let pinia: ReturnType<typeof createPinia> | null = null

async function mountView(): Promise<HTMLElement> {
  const root = document.createElement('div')
  document.body.appendChild(root)
  host = root
  app = createApp(AdminView)
  app.use(pinia!)
  app.use(i18n)
  app.mount(root)
  await flush()
  return root
}

/** onMounted 里有 4 段串行 await，一次 setTimeout 不足以全部落定。 */
async function flush(): Promise<void> {
  for (let i = 0; i < 4; i += 1) {
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()
  }
}

/** 四张卡片顺序固定：用户 / 项目 / 项目成员 / 角色。 */
function section(root: HTMLElement, index: number): HTMLElement {
  const cards = root.querySelectorAll<HTMLElement>('.card.wide-card')
  const card = cards[index]
  if (!card) throw new Error(`section not found: ${index}`)
  return card
}

function sectionRows(root: HTMLElement, index: number): HTMLElement[] {
  return [...section(root, index).querySelectorAll<HTMLElement>('.n-data-table-tbody .n-data-table-tr')]
}

beforeEach(() => {
  pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.projectId = 1
  // 预置项目列表：让 onMounted 走「已有项目」分支，避免再打一次 projects 接口
  auth.projects = [
    { id: 1, code: 'QA', name: '质量项目', description: null, enabled: true, ownerId: 1, visibility: null },
  ]
})

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
  pinia = null
})

describe('AdminView 用户列表', () => {
  it('渲染用户行，空邮箱显示统一占位', async () => {
    const root = await mountView()
    const rows = sectionRows(root, 0)
    expect(rows).toHaveLength(2)
    expect(rows[0].textContent).toContain('alice')
    expect(rows[1].textContent).toContain('bob')
    expect(rows[1].textContent).toContain('—')
  })

  it('启用状态走 StatusTag，图标 + 文字双编码', async () => {
    const root = await mountView()
    const tags = [...section(root, 0).querySelectorAll<HTMLElement>('.status-tag')]
    expect(tags).toHaveLength(2)
    expect(tags.every((tag) => tag.querySelector('svg') !== null)).toBe(true)
    expect(tags[0].textContent).toContain(i18n.global.t('common.yes'))
    expect(tags[1].textContent).toContain(i18n.global.t('common.no'))
  })

  it('勾一行出现批量条并计数为 1', async () => {
    const root = await mountView()
    const boxes = [...section(root, 0).querySelectorAll<HTMLElement>('.n-checkbox')]
    // 1 个表头 + 2 行
    expect(boxes).toHaveLength(3)

    boxes[1].click()
    await nextTick()
    expect(root.textContent).toContain(i18n.global.t('cases.batch.selected', { count: 1 }))
  })
})

describe('AdminView 项目成员', () => {
  it('所有者行是只读文本，不给可编辑的角色下拉', async () => {
    const root = await mountView()
    const rows = sectionRows(root, 2)
    expect(rows).toHaveLength(2)

    const ownerRow = rows[0]
    expect(ownerRow.textContent).toContain(i18n.global.t('admin.owner'))
    // 有下拉就意味着「看起来能改」，但后端会拒 —— 这里必须是纯文本
    expect(ownerRow.querySelector('.n-select')).toBeNull()

    const memberRow = rows[1]
    expect(memberRow.querySelector('.n-select')).not.toBeNull()
  })

  it('停用成员显示「已停用」，不是笼统的「否」', async () => {
    const root = await mountView()
    const memberRow = sectionRows(root, 2)[1]
    expect(memberRow.textContent).toContain(i18n.global.t('admin.disabledUser'))
  })

  it('所有者不可移除，非所有者有移除按钮', async () => {
    const root = await mountView()
    const [ownerRow, memberRow] = sectionRows(root, 2)
    const removeText = i18n.global.t('admin.removeMember')

    expect([...ownerRow.querySelectorAll('button')].map((b) => b.textContent?.trim()))
      .not.toContain(removeText)
    expect([...memberRow.querySelectorAll('button')].map((b) => b.textContent?.trim()))
      .toContain(removeText)
  })
})

describe('AdminView 项目与角色', () => {
  it('项目表渲染项目编码与启用状态', async () => {
    const root = await mountView()
    const rows = sectionRows(root, 1)
    expect(rows).toHaveLength(1)
    expect(rows[0].textContent).toContain('QA')
    expect(rows[0].textContent).toContain('质量项目')
  })

  it('角色表渲染角色码与内置标记', async () => {
    const root = await mountView()
    const rows = sectionRows(root, 3)
    expect(rows).toHaveLength(1)
    expect(rows[0].textContent).toContain('admin')
    expect(rows[0].querySelector('.status-tag')).not.toBeNull()
  })
})
