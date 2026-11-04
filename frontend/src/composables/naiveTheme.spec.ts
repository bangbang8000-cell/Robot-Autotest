import { describe, expect, it } from 'vitest'
import {
  SEMANTIC_CSS_VARS,
  buildThemeOverrides,
  type SemanticTokens,
} from './naiveTheme'

/**
 * 对接层的核心判据是「单向派生」：色值只从 theme.css 读出来，库主题不得自带第二套色板。
 * 所以这里不测 DOM（happy-dom 不加载真实 CSS），只测纯函数对输入的响应。
 *
 * 注意：样例值故意**不用真实 hex**。`check_colors.py` 把 frontend/src 里除 theme.css
 * 之外的 hex 一律视为硬编码色值（包括测试文件），而这里要验证的是「值有没有被透传」，
 * 用任意唯一字符串同样能证明，且不会给自己造一条违规。
 */
const sample: SemanticTokens = {
  accent: 'accent-default',
  accentHover: 'accent-hover',
  accentActive: 'accent-active',
  accentSoft: 'accent-soft',
  accentContrast: 'on-accent',
  bg: 'bg-token',
  panel: 'panel-token',
  surface2: 'surface2-token',
  surfaceSelected: 'selected-token',
  text: 'text-token',
  textMuted: 'muted-token',
  border: 'border-token',
  borderStrong: 'borderstrong-token',
  ok: 'ok-token',
  warn: 'warn-token',
  fail: 'fail-token',
  fontSans: 'sans',
  fontMono: 'mono',
  fontSizeBase: '14px',
  radius: '6px',
}

describe('naiveTheme 对接层', () => {
  it('库主题字段全部由 semantic token 派生（无第二套色板）', () => {
    const overrides = buildThemeOverrides(sample)
    expect(overrides.common?.primaryColor).toBe('accent-default')
    expect(overrides.common?.primaryColorHover).toBe('accent-hover')
    expect(overrides.common?.primaryColorPressed).toBe('accent-active')
    expect(overrides.common?.successColor).toBe('ok-token')
    expect(overrides.common?.errorColor).toBe('fail-token')
    expect(overrides.common?.bodyColor).toBe('bg-token')
    expect(overrides.common?.cardColor).toBe('panel-token')
    expect(overrides.common?.fontFamily).toBe('sans')
    const allowed = new Set(Object.values(sample))
    for (const value of Object.values(overrides.common ?? {})) {
      if (typeof value === 'string' && value !== '') {
        expect(allowed.has(value)).toBe(true)
      }
    }
  })

  it('换强调色 → 库主题同步换色（12 套运行时主题的基础）', () => {
    const changed = buildThemeOverrides({ ...sample, accent: 'crimson-token' })
    expect(changed.common?.primaryColor).toBe('crimson-token')
    expect(changed.common?.infoColor).toBe('crimson-token')
    expect(changed.common?.bodyColor).toBe('bg-token')
  })

  it('展示清单覆盖全部语义名，且都是 CSS 变量名', () => {
    expect(SEMANTIC_CSS_VARS.length).toBeGreaterThanOrEqual(18)
    expect(new Set(SEMANTIC_CSS_VARS).size).toBe(SEMANTIC_CSS_VARS.length)
    for (const name of SEMANTIC_CSS_VARS) {
      expect(name.startsWith('--')).toBe(true)
    }
  })
})
