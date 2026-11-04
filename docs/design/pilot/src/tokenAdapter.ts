/**
 * 组件库主题适配层（试点版）。
 *
 * 单向：semantic token（theme.css） -> 库主题变量。试点内禁止硬编码色值，
 * 也不允许反向把库主题写回 token。切换 data-theme 后重新读一次即可换肤。
 */

// 试点需要用到的 semantic token 键。全部必须在 theme.css 有定义（check_pilot.py 机检）。
export const TOKEN_KEYS = [
  '--bg', '--side', '--panel', '--surface-2', '--border', '--border-strong',
  '--text', '--text-muted', '--accent', '--accent-hover', '--accent-active',
  '--accent-soft', '--accent-contrast', '--focus-ring', '--surface-hover',
  '--surface-selected', '--ok', '--ok-soft', '--ok-border',
  '--fail', '--fail-soft', '--fail-border', '--warn', '--warn-soft',
] as const

export type TokenKey = typeof TOKEN_KEYS[number]
export type Tokens = Record<TokenKey, string>

export function readTokens(): Tokens {
  const cs = getComputedStyle(document.documentElement)
  const out = {} as Tokens
  for (const k of TOKEN_KEYS) {
    // var() 嵌套会被浏览器解析成最终值；空值视为缺失，交给机检拦截。
    out[k] = cs.getPropertyValue(k).trim()
  }
  return out
}

/** Naive UI：themeOverrides 全部由 token 派生，无一处字面量。 */
export function naiveOverrides(t: Tokens) {
  return {
    common: {
      primaryColor: t['--accent'],
      primaryColorHover: t['--accent-hover'],
      primaryColorPressed: t['--accent-active'],
      primaryColorSuppl: t['--accent-hover'],
      bodyColor: t['--bg'],
      cardColor: t['--panel'],
      popoverColor: t['--surface-2'],
      modalColor: t['--panel'],
      tableColor: t['--panel'],
      textColorBase: t['--text'],
      textColor3: t['--text-muted'],
      borderColor: t['--border'],
      dividerColor: t['--border'],
      hoverColor: t['--surface-hover'],
      errorColor: t['--fail'],
      successColor: t['--ok'],
      warningColor: t['--warn'],
    },
    Button: { textColorPrimary: t['--accent-contrast'] },
    Input: { borderFocus: t['--focus-ring'], borderHover: t['--border-strong'] },
    DataTable: {
      tdColorHover: t['--surface-hover'],
      thColor: t['--panel'],
      borderColor: t['--border'],
    },
  }
}

/** Element Plus：把 token 写成 --el-* 变量，组件样式自然跟随。 */
export const ELEMENT_MAP: Record<string, TokenKey> = {
  '--el-color-primary': '--accent',
  '--el-color-primary-light-3': '--accent-hover',
  '--el-color-primary-dark-2': '--accent-active',
  '--el-color-success': '--ok',
  '--el-color-danger': '--fail',
  '--el-color-warning': '--warn',
  '--el-bg-color': '--panel',
  '--el-bg-color-page': '--bg',
  '--el-bg-color-overlay': '--surface-2',
  '--el-text-color-primary': '--text',
  '--el-text-color-secondary': '--text-muted',
  '--el-border-color': '--border',
  '--el-border-color-hover': '--border-strong',
  '--el-fill-color-blank': '--panel',
  '--el-fill-color-light': '--surface-hover',
}

export function applyElementTokens(t: Tokens): void {
  const root = document.documentElement
  for (const [elVar, tokenKey] of Object.entries(ELEMENT_MAP)) {
    root.style.setProperty(elVar, t[tokenKey])
  }
}
