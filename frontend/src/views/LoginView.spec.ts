/**
 * `LoginView` 视图级回归（UX-04b 第七批）。
 *
 * 登录页只有 60 行，但它是**唯一一个"提交即改路由"**的页面，三类缺陷只有挂载才发现：
 * ① 必填校验被绕过（空用户名/空密码仍发请求）→ 白跑一次 401；
 * ② 提交按钮换了控件后**回车提交失效**（`NButton` 的 `type` 是语义色档，HTML 提交行为
 *    要用 `attr-type="submit"`；写错了页面看起来完全正常，只是回车没反应）；
 * ③ 失败后错误信息没落到界面上（只 console 里看得到）。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { i18n } from '@/locales'
import LoginView from '@/views/LoginView.vue'

const authState = vi.hoisted(() => ({
  // 用泛型签名声明参数而不是写实现：既不触发 ESLint 的 no-unused-vars（连 `_` 前缀也拦），
  // 又能让 `toHaveBeenCalledWith(username, password)` 保持类型检查
  login: vi.fn<(username: string, password: string) => Promise<void>>(),
  failWith: null as null | Error,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    login: async (username: string, password: string) => {
      if (authState.failWith) throw authState.failWith
      return authState.login(username, password)
    },
  }),
}))

const pushState = vi.hoisted(() => ({ replaced: [] as string[] }))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: { redirect: '/reports' } }),
  useRouter: () => ({
    replace: async (target: string) => {
      pushState.replaced.push(target)
    },
  }),
}))

let app: App | null = null
let host: HTMLElement | null = null
let pinia: ReturnType<typeof createPinia> | null = null

async function flush(): Promise<void> {
  for (let i = 0; i < 3; i += 1) {
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()
  }
}

async function mountView(): Promise<HTMLElement> {
  const root = document.createElement('div')
  document.body.appendChild(root)
  host = root
  app = createApp(LoginView)
  app.use(pinia!)
  app.use(i18n)
  app.mount(root)
  await nextTick()
  return root
}

/** 往 `NInput` 里打字：它是受控组件，必须派发原生 input 事件才能走到 setter。 */
async function typeInto(root: HTMLElement, index: number, value: string): Promise<void> {
  const input = [...root.querySelectorAll<HTMLInputElement>('input')][index]
  expect(input).not.toBeUndefined()
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flush()
}

function submitButton(root: HTMLElement): HTMLButtonElement {
  const button = root.querySelector<HTMLButtonElement>('form button')
  expect(button).not.toBeNull()
  return button!
}

beforeEach(() => {
  authState.failWith = null
  vi.clearAllMocks()
  pushState.replaced = []
  pinia = createPinia()
  setActivePinia(pinia)
})

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
  pinia = null
})

describe('LoginView 表单', () => {
  it('两个字段都渲染成输入框，密码框类型正确', async () => {
    const root = await mountView()
    const inputs = [...root.querySelectorAll<HTMLInputElement>('input')]
    expect(inputs.length).toBe(2)
    expect(inputs[1].type).toBe('password')
    expect(inputs[0].getAttribute('autocomplete')).toBe('username')
  })

  it('提交按钮必须是原生 submit（否则回车提交静默失效）', async () => {
    const root = await mountView()
    const button = submitButton(root)
    // NButton 的 type 走语义色档，HTML 提交行为只能在 attr-type 上
    expect(button.getAttribute('type')).toBe('submit')
  })

  it('用户名或密码为空时不发请求，并给必填提示', async () => {
    const root = await mountView()
    await typeInto(root, 0, 'evergreen')
    submitButton(root).click()
    await flush()

    expect(authState.login).not.toHaveBeenCalled()
    expect(root.textContent).toContain(i18n.global.t('login.required'))
  })

  it('填齐后提交走 auth.login，并按 redirect 参数跳转', async () => {
    const root = await mountView()
    await typeInto(root, 0, '  evergreen  ')
    await typeInto(root, 1, 'secret')
    submitButton(root).click()
    await flush()

    // 用户名两侧空白要去掉：后端按精确匹配，带空格会直接 401
    expect(authState.login).toHaveBeenCalledWith('evergreen', 'secret')
    expect(pushState.replaced).toEqual(['/reports'])
  })

  it('登录失败把错误渲染到界面上，而不是只留在控制台', async () => {
    authState.failWith = new Error('bad credentials')
    const root = await mountView()
    await typeInto(root, 0, 'evergreen')
    await typeInto(root, 1, 'wrong')
    submitButton(root).click()
    await flush()

    // 跳转不该发生
    expect(pushState.replaced).toEqual([])
    // ErrorLineView 会渲染错误摘要；用「非空且有内容」判定，不依赖具体文案
    const text = root.textContent ?? ''
    expect(text.length).toBeGreaterThan(0)
    expect(text).not.toContain(i18n.global.t('login.required'))
    expect(root.querySelector('.error-line, [role="alert"]')).not.toBeNull()
  })

  it('品牌区：登录页有标记 + 产品名 + 定位语，且不含版本号（版本在关于里）', async () => {
    const root = await mountView()
    const brand = root.querySelector('.login-brand')
    expect(brand).not.toBeNull()
    // 标记是几何 SVG（无 <text>），与 favicon 同形
    expect(brand!.querySelector('svg.app-logo__mark')).not.toBeNull()
    expect(brand!.querySelectorAll('text')).toHaveLength(0)
    expect(brand!.textContent).toContain(i18n.global.t('app.title'))
    expect(brand!.textContent).toContain(i18n.global.t('app.subtitle'))
    // 版本号不该在登录页出现：它只在「关于」里，且从 /_ping 读
    expect(root.textContent).not.toMatch(/v\d+\.\d+/)
  })
})
