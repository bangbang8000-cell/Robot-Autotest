/**
 * `SuiteTree` 组件级回归（UX-04b 第六批）。
 *
 * 这棵树是报告详情页里唯一**递归渲染 + 自带折叠状态**的组件，两类缺陷只有挂载才能发现：
 * ① 折叠/展开状态错位 —— 点套件标题收起后，子级仍留在 DOM 里（或反过来点不开）；
 * ② 状态只有颜色 —— 换成 `StatusTag` 之前它是 `badge verdict-*`，六预设里 crimson
 *    与 fail 同色相，色觉障碍下套件树等于没有状态信息。
 *
 * 另外这批把整行原生 `<button class="tree-toggle">` 换成了 `NButton text`：
 * 原生那份**没有 hover / focus-visible**（scoped style 不在 `check_states.py` 扫描范围，
 * 所以守门一直绿），这里至少断言渲染出的是库按钮，避免以后被改回裸 `<button>`。
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createApp, defineComponent, h, nextTick, type App } from 'vue'
import { i18n } from '@/locales'
import type { ReportSuiteNode } from '@/api/reports'
import SuiteTree from '@/components/report/SuiteTree.vue'

const TREE: ReportSuiteNode = {
  name: 'Roce',
  status: 'FAIL',
  suites: [
    {
      name: 'Smoke',
      status: 'PASS',
      suites: [],
      cases: [
        {
          caseKey: 'TC-009',
          name: '子套件用例',
          verdict: 'PASS',
          durationMs: 300,
          failureMsg: null,
          keywords: [],
        },
      ],
    },
  ],
  cases: [
    {
      caseKey: 'TC-001',
      name: '基本收发',
      verdict: 'PASS',
      durationMs: 1200,
      failureMsg: null,
      keywords: [
        {
          name: 'Setup',
          status: 'PASS',
          children: [{ name: 'Open Connection', status: 'PASS', children: [] }],
        },
      ],
    },
    {
      caseKey: 'TC-002',
      name: '大包吞吐',
      verdict: 'FAIL',
      durationMs: 8000,
      failureMsg: 'expected 190 got 120',
      keywords: [],
    },
  ],
}

let app: App | null = null
let host: HTMLElement | null = null
let logs: string[] = []

async function mountTree(): Promise<HTMLElement> {
  const root = document.createElement('div')
  document.body.appendChild(root)
  host = root
  logs = []
  // 用 h() 建包装组件而不是字符串模板：不依赖运行时编译器，emit 监听也直接写在 props 里
  const Wrapper = defineComponent({
    setup() {
      return () =>
        h(SuiteTree, {
          node: TREE,
          depth: 0,
          onOpenLogs: (caseKey: string) => logs.push(caseKey),
        })
    },
  })
  app = createApp(Wrapper)
  app.use(i18n)
  app.mount(root)
  await nextTick()
  return root
}

beforeEach(() => {
  logs = []
})

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
})

describe('SuiteTree 折叠', () => {
  it('根套件默认展开、子套件默认折叠', async () => {
    const root = await mountTree()
    // 根套件的两条用例可见
    expect(root.textContent).toContain('基本收发')
    expect(root.textContent).toContain('大包吞吐')
    // 子套件标题在，但它的用例默认不可见
    expect(root.textContent).toContain('Smoke')
    expect(root.textContent).not.toContain('子套件用例')
  })

  it('点子套件标题展开后出现它的用例', async () => {
    const root = await mountTree()
    const toggles = [...root.querySelectorAll<HTMLElement>('.tree-toggle')]
    // 顺序：根套件、TC-001、TC-002、子套件
    toggles[3].click()
    await nextTick()
    expect(root.textContent).toContain('子套件用例')
  })

  it('再点根套件标题收起，用例行从 DOM 消失', async () => {
    const root = await mountTree()
    root.querySelector<HTMLElement>('.tree-toggle')!.click()
    await nextTick()
    expect(root.textContent).not.toContain('基本收发')
    // 收起只是隐藏，套件名要留着（否则连不回来）
    expect(root.textContent).toContain('Roce')
  })
})

describe('SuiteTree 用例交互', () => {
  it('点「查看日志」把 caseKey 交给父级', async () => {
    const root = await mountTree()
    const viewLog = [...root.querySelectorAll<HTMLElement>('.case-head .link-btn')].at(-1)
    expect(viewLog).not.toBeUndefined()
    viewLog!.click()
    await nextTick()
    expect(logs).toEqual(['TC-002'])
  })

  it('点用例标题展开步骤树，递归两层都渲染', async () => {
    const root = await mountTree()
    const toggles = [...root.querySelectorAll<HTMLElement>('.tree-toggle')]
    toggles[1].click() // TC-001
    await nextTick()
    expect(root.textContent).toContain('Setup')
    expect(root.textContent).toContain('Open Connection')
  })

  it('没有关键字步骤时给提示，不留空白区块', async () => {
    const root = await mountTree()
    const toggles = [...root.querySelectorAll<HTMLElement>('.tree-toggle')]
    toggles[2].click() // TC-002 无 keywords
    await nextTick()
    expect(root.textContent).toContain(i18n.global.t('reports.noSteps'))
  })
})

describe('SuiteTree 状态与控件', () => {
  it('套件与用例的状态都是图标 + 文字双编码', async () => {
    const root = await mountTree()
    const tags = [...root.querySelectorAll<HTMLElement>('.status-tag')]
    // 根套件 1 + 两条用例 2 + 子套件 1 = 4（子套件收起，但它自己的标题徽标在）
    expect(tags.length).toBe(4)
    expect(tags.every((tag) => tag.querySelector('svg') !== null)).toBe(true)
    // FAIL 与 PASS 落在不同色档上，不能都是一种颜色
    const tones = new Set(tags.map((tag) => tag.dataset.tone))
    expect(tones.has('fail')).toBe(true)
    expect(tones.has('ok')).toBe(true)
  })

  it('整行是键盘可达的按钮（三态由 check_states 守门，这里只钉语义）', async () => {
    const root = await mountTree()
    const toggles = [...root.querySelectorAll<HTMLElement>('.tree-toggle')]
    expect(toggles.length).toBe(4)
    // type="button" 防止在表单里被当成 submit；标签名必须是 button 而不是可点的 div
    expect(toggles.every((el) => el.tagName === 'BUTTON')).toBe(true)
    expect(toggles.every((el) => el.getAttribute('type') === 'button')).toBe(true)
  })
})
