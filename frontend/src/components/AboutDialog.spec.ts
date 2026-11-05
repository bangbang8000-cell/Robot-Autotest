/**
 * `AboutDialog` 组件级回归（UX-05：版本号移入「关于」）。
 *
 * 三件事单看都对、合起来容易坏：
 * ① 版本号必须**从 `/_ping` 读**（手写常量会与服务端实际版本脱节，正是本次要修的问题）；
 * ② 后端不可达时面板仍要能打开并说明「未知」—— 一个关于面板把界面卡住是荒谬的；
 * ③ 每次打开都重新读，升级后端后不用刷新整页。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import { i18n } from '@/locales'

const apiState = vi.hoisted(() => ({
  version: '0.5.0',
  app: 'robot-autotest',
  fail: false,
}))

vi.mock('@/api/http', () => ({
  healthPing: vi.fn(async () => {
    if (apiState.fail) throw new Error('backend down')
    return { status: 'UP', app: apiState.app, version: apiState.version }
  }),
}))

// import 放在 vi.mock 之后是刻意的：读起来"先声明假数据再引入被测组件"，`vi.mock` 本来就会被提升
import AboutDialog from './AboutDialog.vue'

let app: App | null = null
let host: HTMLElement | null = null

async function mount(open: boolean): Promise<HTMLElement> {
  const root = document.createElement('div')
  document.body.appendChild(root)
  host = root
  app = createApp({ render: () => h(AboutDialog, { open }) })
  app.use(i18n)
  app.mount(root)
  await new Promise((resolve) => setTimeout(resolve, 0))
  await nextTick()
  return root
}

beforeEach(() => {
  apiState.fail = false
})

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
})

describe('AboutDialog', () => {
  it('打开后显示从 /_ping 读到的版本与服务名', async () => {
    const root = await mount(true)
    expect(root.textContent).toContain('0.5.0')
    expect(root.textContent).toContain('robot-autotest')
    expect(root.textContent).toContain(i18n.global.t('app.subtitle'))
  })

  it('后端不可达时仍能打开，版本显示「未知」而不是空白或崩溃', async () => {
    apiState.fail = true
    const root = await mount(true)
    expect(root.querySelector('[role="dialog"]')).not.toBeNull()
    expect(root.textContent).toContain(i18n.global.t('about.versionUnknown'))
  })

  it('关闭状态不渲染面板', async () => {
    const root = await mount(false)
    expect(root.querySelector('[role="dialog"]')).toBeNull()
  })

  it('关闭时发出 update:open=false', async () => {
    const seen: boolean[] = []
    const root = document.createElement('div')
    document.body.appendChild(root)
    host = root
    app = createApp({
      render: () => h(AboutDialog, { open: true, 'onUpdate:open': (v: boolean) => seen.push(v) }),
    })
    app.use(i18n)
    app.mount(root)
    await new Promise((resolve) => setTimeout(resolve, 0))
    root.querySelector<HTMLButtonElement>('button.btn')!.click()
    await nextTick()
    expect(seen).toEqual([false])
  })
})
