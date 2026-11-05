/**
 * `EmptyIllustration` 组件级回归（UX-05 三种空态插画）。
 *
 * 插画是**语义**不是装饰：三张图必须能被区分，否则「搜不到」和「加载失败」看起来
 * 一模一样。守门（`check_brand.py`）只管 favicon 的 16px 可辨，这里的 96px 图元
 * 分工只能靠组件测试固定。同时断言「没有 `<text>`」——文字在缩放/翻译下最先坏。
 */
import { afterEach, describe, expect, it } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import EmptyIllustration from './EmptyIllustration.vue'

let app: App | null = null
let host: HTMLElement | null = null

async function mount(props: Record<string, unknown>): Promise<HTMLElement> {
  const root = document.createElement('div')
  document.body.appendChild(root)
  host = root
  app = createApp({ render: () => h(EmptyIllustration, props as never) })
  app.mount(root)
  await nextTick()
  return root
}

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
})

describe('EmptyIllustration', () => {
  it('默认是 no-data，且不带任何文字元素', async () => {
    const root = await mount({})
    const svg = root.querySelector('svg.empty-illustration')!
    expect(svg.classList.contains('empty-illustration--no-data')).toBe(true)
    expect(svg.querySelectorAll('text')).toHaveLength(0)
  })

  it('三种语义画出不同图元（不能是同一张图换个 class）', async () => {
    const noData = await mount({ name: 'no-data' })
    const noDataShapes = noData.querySelectorAll('path').length
    app?.unmount()
    host?.remove()

    const noResult = await mount({ name: 'no-result' })
    expect(noResult.querySelector('circle.empty-illustration__lens')).not.toBeNull()
    app?.unmount()
    host?.remove()

    const error = await mount({ name: 'error' })
    expect(error.querySelector('rect.empty-illustration__plate')).not.toBeNull()
    expect(error.querySelector('path.empty-illustration__bolt')).not.toBeNull()
    // error 是「断开」的方框，与「空托盘」的图元数不同
    expect(error.querySelectorAll('path').length).not.toBe(noDataShapes)
  })

  it('宽度可调（表格内用 64，整块区域用 96）', async () => {
    const root = await mount({ width: 64 })
    expect(root.querySelector<SVGSVGElement>('svg')!.style.width).toBe('64px')
  })

  it('不参与无障碍树（旁边有标题说明，重复朗读同一件事没有意义）', async () => {
    const root = await mount({})
    const svg = root.querySelector('svg')!
    expect(svg.getAttribute('aria-hidden')).toBe('true')
    expect(svg.getAttribute('role')).toBe('presentation')
  })
})
