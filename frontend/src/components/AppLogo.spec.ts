/**
 * `AppLogo` 组件级回归（UX-05 品牌）。
 *
 * 标记只有 4 条线，退化时**页面完全正常**：少一条线、多一个 `<text>`、把几何形换成
 * 图片都构建通过、类型通过、肉眼不盯着看也发现不了。这里把「几何、无文字、彩色走 token」
 * 三件事固定住——尤其「无 `<text>`」：favicon 是同一形状的固定色版本，16px 下文字必糊。
 */
import { afterEach, describe, expect, it } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import { i18n } from '@/locales'
import AppLogo from './AppLogo.vue'

let app: App | null = null
let host: HTMLElement | null = null

async function mount(props: Record<string, unknown>): Promise<HTMLElement> {
  const root = document.createElement('div')
  document.body.appendChild(root)
  host = root
  app = createApp({ render: () => h(AppLogo, props) })
  app.use(i18n)
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

describe('AppLogo', () => {
  it('几何标记：一条底板 + 四条线（无文字）', async () => {
    const root = await mount({})
    const svg = root.querySelector('svg.app-logo__mark')
    expect(svg).not.toBeNull()
    expect(svg!.querySelectorAll('rect')).toHaveLength(1)
    expect(svg!.querySelectorAll('line')).toHaveLength(4)
    expect(svg!.querySelectorAll('text')).toHaveLength(0)
  })

  it('viewBox 与 favicon 同坐标系（32×32），换形时两边才对照得上', async () => {
    const root = await mount({})
    expect(root.querySelector('svg')!.getAttribute('viewBox')).toBe('0 0 32 32')
  })

  it('size 落到盒子尺寸上，不靠外层 padding 撑', async () => {
    const root = await mount({ size: 40 })
    const svg = root.querySelector<SVGSVGElement>('svg.app-logo__mark')!
    expect(svg.style.width).toBe('40px')
    expect(svg.style.height).toBe('40px')
  })

  it('withWordmark 才渲染字标', async () => {
    const without = await mount({})
    expect(without.querySelector('.app-logo__word')).toBeNull()
    app?.unmount()
    host?.remove()

    const withWord = await mount({ withWordmark: true })
    expect(withWord.querySelector('.app-logo__word')?.textContent).toContain(i18n.global.t('app.title'))
  })

  it('颜色走 token 而不是硬编码（换强调色时标记必须跟着变）', async () => {
    const root = await mount({})
    const html = root.innerHTML
    expect(html).not.toMatch(/#[0-9a-f]{3,6}/i)
    // 颜色在 scoped style 里以 var() 出现，SVG 元素自身不带 fill/stroke 字面量
    const plate = root.querySelector('.app-logo__plate')!
    expect(plate.getAttribute('fill')).toBeNull()
  })
})
