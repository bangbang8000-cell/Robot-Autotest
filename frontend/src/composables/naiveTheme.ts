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
    /*
     * 按钮（UX-06 暗色走查）。库的 `textColorPrimary` 是 light 主题里的写死白色，
     * 而按钮底色由我们接成了 `--accent`。暗色档的强调色是浅蓝，白字压上去只有 2.57:1，
     * 实心按钮几乎糊成一片。`--accent-contrast` 本来就是给「强调色底上的文字」准备的
     * （亮色白字 / 暗色近黑），这里四个态统一接回它。
     */
    Button: {
      textColorPrimary: t.accentContrast,
      textColorHoverPrimary: t.accentContrast,
      textColorPressedPrimary: t.accentContrast,
      textColorFocusPrimary: t.accentContrast,
      // 禁用态同样取库里的写死白色，暗色下白字压在浅蓝底上完全读不出（报告页 Export PDF 未就绪时）
      textColorDisabledPrimary: t.accentContrast,
    },
    /*
     * 单选按钮组（UX-06 暗色走查，报告页的 Daily/Weekly 切换）。
     * 库默认 `buttonColor` / `buttonColorActive` 都取 light 主题的 `baseColor`（纯白），
     * 于是暗色页面上会出现一块白底 + 浅蓝文字的选中项（2.57:1）。
     * 底色接 panel / surface-2，文字接 text-muted / accent，亮色档取值与库默认一致，视觉不变。
     */
    Radio: {
      buttonColor: t.panel,
      buttonColorActive: t.surface2,
      buttonTextColor: t.textMuted,
      buttonTextColorActive: t.accent,
      buttonTextColorHover: t.accent,
      buttonBorderColor: t.border,
      buttonBorderColorActive: t.accent,
      buttonBorderColorHover: t.border,
    },
    /*
     * 表格（UX-05）。这两条是**修缺陷**，不是调样式：
     *
     * ① 表头底色。库默认 `thColor = composite(cardColor, tableHeaderColor)`，而
     *    `tableHeaderColor` 是 light 主题里的亮白常量。我们只覆盖了 semantic token，
     *    暗色页面上表头就被叠成一片浅灰白，浅色表头文字压在上面几乎看不见。
     * ② 表头字色。默认 `thTextColor` 接的是 `textColor1`（正文色），于是表头与正文同色，
     *    失去了「辅助信息」该有的弱化层级 —— UX-01b 的判据（表头为灰阶弱化色）
     *    在迁库之后静默失效，脚本查不到，只有肉眼看暗色截图才发现。
     */
    DataTable: {
      thColor: t.surface2,
      thColorHover: t.surfaceSelected,
      thColorSorting: t.surfaceSelected,
      thTextColor: t.textMuted,
      tdColor: t.panel,
      tdTextColor: t.text,
      borderColor: t.border,
      thFontWeight: '500',
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
