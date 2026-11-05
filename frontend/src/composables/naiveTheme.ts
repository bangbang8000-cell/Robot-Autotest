import { nextTick, ref, watch } from 'vue'
import type { GlobalThemeOverrides } from 'naive-ui'
import { useAccent } from './useAccent'
import { useTheme } from './useTheme'

/**
 * UX-04 步骤 4：主题对接层（token 唯一真源 → 组件库主题，单向）。
 *
 * 方向不能反：色值只写在 `styles/theme.css`，这里用 `getComputedStyle` 读**已生效**的
 * semantic token，再派生 naive-ui 的 GlobalThemeOverrides。库组件从不反向写 CSS 变量，
 * 也不自带第二套色板。因此亮/暗 × 六强调色 = 12 套运行时主题自动成立，无需 12 份配置。
 *
 * 为什么用 watch + nextTick 而不是 computed：CSS 变量的值要等 `<html data-theme/data-accent>`
 * 真正更新后才会重算，computed 同步求值时可能读到旧值（表现为「点第二次才变色」）。
 */

const TOKEN_NAMES = {
  accent: '--accent',
  accentHover: '--accent-hover',
  accentActive: '--accent-active',
  accentSoft: '--accent-soft',
  accentContrast: '--accent-contrast',
  bg: '--bg',
  panel: '--panel',
  surface2: '--surface-2',
  surfaceSelected: '--surface-selected',
  text: '--text',
  textMuted: '--text-muted',
  border: '--border',
  borderStrong: '--border-strong',
  ok: '--ok',
  warn: '--warn',
  fail: '--fail',
  fontSans: '--font-sans',
  fontMono: '--font-mono',
  fontSizeBase: '--font-size-base',
  radius: '--radius-lg',
} as const

export type SemanticTokens = { [K in keyof typeof TOKEN_NAMES]: string }

/** styleguide 页展示用的 CSS 变量名清单；`check_styleguide_tokens.py` 会与 theme.css 对账。 */
export const SEMANTIC_CSS_VARS: readonly string[] = Object.values(TOKEN_NAMES)

/** 从当前 DOM 上已生效的语义 token 取值（无主题 CSS 的环境返回空串，单测注入值即可） */
export function readSemanticTokens(el: Element = document.documentElement): SemanticTokens {
  const style = getComputedStyle(el)
  const entries = Object.entries(TOKEN_NAMES).map(([key, cssName]) => [
    key,
    style.getPropertyValue(cssName).trim(),
  ])
  return Object.fromEntries(entries) as SemanticTokens
}

/** 纯函数：给定 token 表派生库主题。不碰 DOM，便于单测与破坏性验证。 */
export function buildThemeOverrides(t: SemanticTokens): GlobalThemeOverrides {
  return {
    common: {
      primaryColor: t.accent,
      primaryColorHover: t.accentHover,
      primaryColorPressed: t.accentActive,
      primaryColorSuppl: t.accentHover,
      infoColor: t.accent,
      successColor: t.ok,
      warningColor: t.warn,
      errorColor: t.fail,
      textColorBase: t.text,
      textColor1: t.text,
      textColor2: t.textMuted,
      textColor3: t.textMuted,
      bodyColor: t.bg,
      cardColor: t.panel,
      modalColor: t.panel,
      popoverColor: t.panel,
      tableColor: t.panel,
      inputColor: t.surface2,
      borderColor: t.border,
      dividerColor: t.border,
      hoverColor: t.surfaceSelected,
      borderRadius: t.radius,
      borderRadiusSmall: t.radius,
      fontFamily: t.fontSans,
      fontFamilyMono: t.fontMono,
      fontSize: t.fontSizeBase,
      fontSizeMedium: t.fontSizeBase,
    },
    /*
     * 页签（UX-04b：资产页的 `.tabs` 原生按钮换成 NTabs 后才需要这段）。
     * 库默认的 line 型页签用 primaryColor 做指示条，换强调色时能跟上，但**未选中文字**
     * 走的是它自己的灰阶，与 `--text-muted` 不是同一个值；选中文字也不会变色。
     * 这里显式接回语义 token，页签才和全站其余文字同一套灰阶。
     */
    Tabs: {
      barColor: t.accent,
      tabTextColorLine: t.textMuted,
      tabTextColorHoverLine: t.text,
      tabTextColorActiveLine: t.accent,
      tabBorderColor: t.border,
      tabFontWeightActiveLine: 500,
    },
  } as GlobalThemeOverrides
}

export function useNaiveTheme() {
  const theme = useTheme()
  const { accent } = useAccent()
  const overrides = ref<GlobalThemeOverrides>(buildThemeOverrides(readSemanticTokens()))

  async function refresh(): Promise<void> {
    await nextTick()
    overrides.value = buildThemeOverrides(readSemanticTokens())
  }

  // 主题或强调色任一变化都重算；immediate 保证首次挂载即与 innerHTML 上的属性一致
  watch([theme.resolved, accent], refresh, { immediate: true })
  return { overrides, refresh }
}
