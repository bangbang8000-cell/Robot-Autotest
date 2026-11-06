/**
 * `ReportTemplatesView` 视图级回归（TPLX-01）。
 *
 * 三个「静态检查看不出来」的点：
 * ① **可选字段只来自 `/fields`**：页面要是内联一份字段名单，白名单收紧后界面还能勾到
 *    已禁用的字段，用户点保存才被 400 打回来；
 * ② **必填字段不可取消**：水印 / 免责声明被取消勾选也「能存」，等于给了一条绕过 C5 的路；
 * ③ **顺序就是提交顺序**：上移只改本地数组而不体现在 payload 里的话，界面看着变了、库里没变。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia, setActivePinia, type Pinia } from 'pinia'
import { i18n } from '@/locales'
import { useAuthStore } from '@/stores/auth'
import { reportTemplateApi, type ReportTemplate } from '@/api/reportTemplates'
import ReportTemplatesView from '@/views/ReportTemplatesView.vue'

vi.mock('@/api/reportTemplates', () => ({
  reportTemplateApi: {
    list: vi.fn(),
    fields: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
    setDefault: vi.fn(),
  },
}))

let app: App | null = null
let host: HTMLElement | null = null
let pinia: Pinia

const REQUIRED = ['reportId', 'title', 'version', 'frozenAt', 'disclaimer', 'watermark']

/** 平台级模板：21 个字段全选，只读。 */
const platformTemplate: ReportTemplate = {
  id: 1,
  projectId: 0,
  name: 'DEFAULT',
  description: '',
  version: 1,
  isDefault: true,
  platform: true,
  fields: [...REQUIRED, 'passRate', 'swBuild'].map((key, index) => ({
    fieldKey: key,
    sortOrder: index,
    titleOverride: null,
  })),
  createdAt: '',
}

/** 项目级模板：7 个字段，可改。 */
const projectTemplate: ReportTemplate = {
  id: 2,
  projectId: 1,
  name: '硬件组视图',
  description: '',
  version: 3,
  isDefault: false,
  platform: false,
  fields: [...REQUIRED, 'passRate'].map((key, index) => ({
    fieldKey: key,
    sortOrder: index,
    titleOverride: null,
  })),
  createdAt: '',
}

/** 接口给的可选字段：刻意比模板字段少一个（swBuild 不在清单里），验证界面不认模板里的野键。 */
const AVAILABLE = [...REQUIRED, 'passRate', 'conclusion']

async function mountView(): Promise<HTMLElement> {
  const root = document.createElement('div')
  document.body.appendChild(root)
  host = root
  app = createApp(ReportTemplatesView)
  app.use(pinia)
  app.use(i18n)
  app.mount(root)
  await new Promise((resolve) => setTimeout(resolve, 0))
  await nextTick()
  return root
}

function tick(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

beforeEach(() => {
  vi.mocked(reportTemplateApi.list).mockResolvedValue([platformTemplate, projectTemplate])
  vi.mocked(reportTemplateApi.fields).mockResolvedValue(AVAILABLE)
  vi.mocked(reportTemplateApi.create).mockResolvedValue(projectTemplate)
  vi.mocked(reportTemplateApi.update).mockResolvedValue(projectTemplate)
  vi.mocked(reportTemplateApi.remove).mockResolvedValue(undefined)
  vi.mocked(reportTemplateApi.setDefault).mockResolvedValue(projectTemplate)
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

describe('ReportTemplatesView', () => {
  it('可选字段只来自 /fields，模板里的野键不出现在勾选项里', async () => {
    const root = await mountView()

    const boxes = [...root.querySelectorAll<HTMLInputElement>('[data-testid="available-fields"] input')]
    expect(boxes.map((box) => box.dataset.field)).toEqual(AVAILABLE)
    // 平台级模板带了 swBuild，但它不在 /fields 清单里 → 不可勾选
    expect(boxes.map((box) => box.dataset.field)).not.toContain('swBuild')
  })

  it('必填字段勾选框禁用，其余可选', async () => {
    const root = await mountView()
    await tick()

    // 首屏默认选中平台级模板（整页只读），先切到项目模板再看勾选框状态
    root.querySelector<HTMLButtonElement>('[data-testid="pick-2"]')!.click()
    await tick()

    const boxes = [...root.querySelectorAll<HTMLInputElement>('[data-testid="available-fields"] input')]
    for (const key of REQUIRED) {
      const box = boxes.find((item) => item.dataset.field === key)!
      expect(box.disabled, `${key} 不该可选`).toBe(true)
      expect(box.checked, `${key} 应默认勾选`).toBe(true)
    }
    const optional = boxes.find((item) => item.dataset.field === 'conclusion')!
    expect(optional.disabled).toBe(false)
    expect(optional.checked).toBe(false)
  })

  it('上移改变顺序，并原样体现在保存请求里', async () => {
    const root = await mountView()
    await tick()

    root.querySelector<HTMLButtonElement>('[data-testid="pick-2"]')!.click()
    await tick()

    const row = root.querySelector<HTMLElement>('[data-testid="selected-fields"] li[data-field="passRate"]')!
    row.querySelector<HTMLButtonElement>('button')!.click() // 第一个按钮是 ↑
    await tick()

    root.querySelector<HTMLButtonElement>('[data-testid="save"]')!.click()
    await tick()

    const calls = vi.mocked(reportTemplateApi.update).mock.calls
    expect(calls.length).toBe(1)
    const keys = calls[0][2].fields.map((field) => field.fieldKey)
    // passRate 从末尾上移一位：应排在 watermark 之前
    expect(keys.indexOf('passRate')).toBe(keys.length - 2)
    expect(keys[keys.length - 1]).toBe('watermark')
  })

  it('平台级模板整页只读：输入禁用、保存禁用', async () => {
    const root = await mountView()
    await tick()

    // 首屏默认选中 isDefault 的平台级模板
    const nameInput = root.querySelector<HTMLInputElement>('[data-testid="template-name"]')!
    expect(nameInput.disabled).toBe(true)
    expect(root.querySelector<HTMLButtonElement>('[data-testid="save"]')!.disabled).toBe(true)
    expect(root.querySelector<HTMLButtonElement>('[data-testid="delete"]')!.disabled).toBe(true)
    expect(root.querySelector('[data-testid="readonly-hint"]')).not.toBeNull()
  })

  it('新建草稿默认带上全部必填字段', async () => {
    const root = await mountView()
    await tick()

    root.querySelector<HTMLButtonElement>('[data-testid="new-template"]')!.click()
    await tick()

    const rows = [...root.querySelectorAll<HTMLElement>('[data-testid="selected-fields"] li')]
    expect(rows.map((row) => row.dataset.field)).toEqual(REQUIRED)
    const nameInput = root.querySelector<HTMLInputElement>('[data-testid="template-name"]')!
    expect(nameInput.disabled).toBe(false)
  })
})
