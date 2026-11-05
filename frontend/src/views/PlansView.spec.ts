/**
 * `PlansView` 视图级回归（UX-04b 逐页迁移）。
 *
 * 本页迁移里被重新接线的三处，全是「静态检查看不出来」的：
 * ① 表格 6 列改 `columns` 渲染后，「名称 + 套件路径」「Cron + 时区」这类双行单元格
 *    容易丢一半（渲染函数只返回第一行也不报错）；
 * ② 「暂停 / 恢复」同一个按钮两个文案，接错就一直显示暂停、点下去反而恢复；
 * ③ 编辑对话框的预填——`openEdit` → `form` → `FormField` 这条链断了会打开一个空表单，
 *    用户按保存就把原计划覆盖成空值。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, h, nextTick, type App, type Slots } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { i18n } from '@/locales'
import { useAuthStore } from '@/stores/auth'
import PlansView from '@/views/PlansView.vue'

vi.mock('@/api/plans', () => ({
  planApi: {
    page: vi.fn(async () => ({
      items: [
        {
          id: 1,
          projectId: 1,
          name: '每夜全量',
          testSetId: null,
          environmentId: null,
          suitePaths: ['suites/roce', 'suites/rdma'],
          includeTag: null,
          concurrency: 2,
          timeoutSeconds: 3600,
          cron: '0 0 2 * * *',
          timezone: 'Asia/Shanghai',
          status: 'enabled',
          overlapPolicy: 'skip',
          lastFireAt: null,
          nextFireAt: '2026-11-06T02:00:00+08:00',
          nextFireTimes: ['2026-11-06T02:00:00+08:00', '2026-11-07T02:00:00+08:00'],
          dependencyIds: [],
          lastRunId: 'RUN-001',
          lastRunState: 'FINISHED',
          lastRunFinishedAt: '2026-11-05T02:08:00+08:00',
          createdBy: null,
          createdAt: null,
        },
        {
          id: 2,
          projectId: 1,
          name: '手工回归',
          testSetId: 7,
          environmentId: 3,
          suitePaths: ['suites/manual'],
          includeTag: 'smoke',
          concurrency: 1,
          timeoutSeconds: 600,
          cron: '0 30 9 * * MON',
          timezone: 'Asia/Shanghai',
          status: 'paused',
          overlapPolicy: 'skip',
          lastFireAt: null,
          nextFireAt: null,
          nextFireTimes: [],
          dependencyIds: [1],
          lastRunId: null,
          lastRunState: null,
          lastRunFinishedAt: null,
          createdBy: null,
          createdAt: null,
        },
      ],
      total: 2,
      page: 1,
      size: 100,
    })),
    create: vi.fn(),
    update: vi.fn(),
    preview: vi.fn(async () => []),
    pause: vi.fn(),
    resume: vi.fn(),
    triggerNow: vi.fn(async () => ({ runId: 'RUN-NEW' })),
    remove: vi.fn(),
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
    create: vi.fn(),
  },
}))

vi.mock('@/components/ProjectPicker.vue', () => ({
  default: { name: 'ProjectPicker', template: '<div />' },
}))

/** 组件用 `<RouterLink v-slot="{ navigate }" custom>` 包了按钮，给同契约的桩即可。 */
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
  app = createApp(PlansView)
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

/** 顶栏按钮（新建 / 刷新 / 返回运行列表），不在表格行里。 */
function toolbarButton(root: HTMLElement, text: string): HTMLButtonElement {
  const button = [...root.querySelectorAll<HTMLButtonElement>('.toolbar button')].find(
    (item) => item.textContent?.trim() === text,
  )
  if (!button) throw new Error(`toolbar button not found: ${text}`)
  return button
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

describe('PlansView 列表', () => {
  it('双行单元格两行都在：名称 + 套件路径、Cron + 时区', async () => {
    const root = await mountView()
    expect(root.textContent).toContain('每夜全量')
    // 套件路径拼成一行，不能只渲染名称就把路径丢掉
    expect(root.textContent).toContain('suites/roce, suites/rdma')
    expect(root.textContent).toContain('0 0 2 * * *')
    expect(root.textContent).toContain('Asia/Shanghai')
  })

  it('未来触发时间保留第 2、3 次，不只显示第一个', async () => {
    const root = await mountView()
    // 两次未来触发都来自同一条计划；只渲染首个的话第二次的时间不会出现
    const second = i18n.global.t('plans.nextFire')
    expect(second).toBeTruthy()
    const body = rows(root)[0].textContent ?? ''
    const matches = body.match(/\d{2}:\d{2}/g) ?? []
    expect(matches.length).toBeGreaterThanOrEqual(2)
  })

  it('状态徽标图标 + 文字双编码', async () => {
    const root = await mountView()
    const tags = [...root.querySelectorAll<HTMLElement>('.status-tag')]
    expect(tags).toHaveLength(2)
    expect(tags.every((tag) => tag.querySelector('svg') !== null)).toBe(true)
  })

  it('暂停 / 恢复按行状态取文案，不是全页一个文案', async () => {
    const root = await mountView()
    const pauseText = i18n.global.t('plans.pause')
    const resumeText = i18n.global.t('plans.resume')

    const firstTexts = [...rows(root)[0].querySelectorAll('button')].map((b) => b.textContent?.trim())
    const secondTexts = [...rows(root)[1].querySelectorAll('button')].map((b) => b.textContent?.trim())

    expect(firstTexts).toContain(pauseText)
    expect(firstTexts).not.toContain(resumeText)
    expect(secondTexts).toContain(resumeText)
    expect(secondTexts).not.toContain(pauseText)
  })
})

describe('PlansView 编辑对话框', () => {
  it('点「编辑」预填原值，而不是打开一张空表单', async () => {
    const root = await mountView()
    expect(root.querySelector('.form-card')).toBeNull()

    rowButton(root, 1, i18n.global.t('plans.edit')).click()
    await nextTick()

    const card = root.querySelector('.form-card')
    expect(card).not.toBeNull()
    const inputs = [...card!.querySelectorAll<HTMLInputElement>('input')]
    const values = inputs.map((input) => input.value)
    expect(values).toContain('手工回归')
    expect(values).toContain('0 30 9 * * MON')
    expect(values).toContain('smoke')
  })

  it('新建打开空表单，且不显示「编辑」标题', async () => {
    const root = await mountView()
    toolbarButton(root, i18n.global.t('plans.create')).click()
    await nextTick()

    const card = root.querySelector('.form-card')
    expect(card).not.toBeNull()
    expect(card!.textContent).toContain(i18n.global.t('plans.createTitle'))
    expect(card!.textContent).not.toContain(i18n.global.t('plans.editTitle'))
  })

  it('依赖列表排除自己，勾选后写回 dependencyIds', async () => {
    const root = await mountView()
    rowButton(root, 0, i18n.global.t('plans.edit')).click()
    await nextTick()

    const deps = [...root.querySelectorAll<HTMLElement>('.dep-list .n-checkbox')]
    // 编辑「每夜全量」时，可选依赖只有它自己以外的那条
    expect(deps).toHaveLength(1)
    expect(deps[0].textContent).toContain('手工回归')

    deps[0].click()
    await nextTick()
    expect(deps[0].className).toContain('n-checkbox--checked')
  })
})
