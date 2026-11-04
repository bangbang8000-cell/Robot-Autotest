import { readonly, ref } from 'vue'

/**
 * 强调色六预设（UX-04 / FR-v0.3-* 步骤 6）。
 *
 *   新年红 crimson ｜ 科技蓝 blue ｜ 土豪金 gold ｜ 活力橙 orange ｜ 魅力紫 violet ｜ 翡翠绿 jade
 *
 * 与 useTheme 同一套三层持久化模型，别混：
 *   - 本地 `localStorage.accent`：未登录也能记住，首帧由 index.html 内联脚本读取（防闪）；
 *   - 服务端 `user_preference.accent`：登录后覆盖本地，保证换设备仍是同一套强调色；
 *   - `<html data-accent>`：最终生效值，theme.css 的 12 个变体块只认这个属性，
 *     naive-ui 适配层也从这个属性切换后重算（见 composables/naiveTheme.ts）。
 *
 * 服务端同步失败不回滚本地：用户点了没反应比"换设备没同步"严重得多。
 *
 * 键名是**语义名**不是色相名（crimson 而不是 red）：`red` 已经是状态色 --red-* 的
 * primitives 前缀，同名会让"这是强调红还是失败红"在代码里失去区分。
 */

export const ACCENTS = ['crimson', 'blue', 'gold', 'orange', 'violet', 'jade'] as const
export type AccentName = (typeof ACCENTS)[number]

export const ACCENT_STORAGE_KEY = 'accent'
export const ACCENT_PREF_KEY = 'accent'

export function isAccentName(value: unknown): value is AccentName {
  return typeof value === 'string' && (ACCENTS as readonly string[]).includes(value)
}

/**
 * 旧键名 → 新键名，2026-11-05 色板改名的迁移映射。
 *
 * 首版六预设是 blue/cyan/indigo/violet/teal/fuchsia，已上线过一段时间，
 * 用户的 localStorage 与服务端 user_preference 里可能还留着旧键。
 * 不映射的后果不是报错，而是 `isAccentName` 判否 → 静默回到默认蓝 ——
 * 与「换设备发现强调色丢了」是同一类缺陷，只是触发点换了。
 *
 * **index.html 的首帧内联脚本持有同一份映射**（它不能 import 本模块），
 * 两处必须同步；`useAccent.spec.ts` 有一条用例直接读 index.html 解析比对，
 * 改一边不改另一边会被测出来。
 */
export const LEGACY_ALIAS = {
  cyan: 'blue',
  indigo: 'blue',
  teal: 'jade',
  fuchsia: 'violet',
} as const satisfies Record<string, AccentName>

/** 把任意存储值归一成合法预设名；无法识别返回 null（调用方决定兜底值）。 */
export function normalizeAccent(value: unknown): AccentName | null {
  if (isAccentName(value)) return value
  if (typeof value === 'string' && value in LEGACY_ALIAS) {
    return LEGACY_ALIAS[value as keyof typeof LEGACY_ALIAS]
  }
  return null
}

function readStored(): AccentName {
  try {
    const saved = localStorage.getItem(ACCENT_STORAGE_KEY)
    const normalized = normalizeAccent(saved)
    if (normalized) return normalized
  } catch {
    // 隐私模式 / 禁用存储：用默认蓝，不阻断启动
  }
  return 'blue'
}

const accent = ref<AccentName>(readStored())

function paint(next: AccentName): void {
  document.documentElement.setAttribute('data-accent', next)
}

/** 切换：立即生效 + 落本地。返回是否真的切了（非法值静默拒绝，不写脏数据）。 */
function setAccent(next: string): boolean {
  if (!isAccentName(next)) return false
  accent.value = next
  try {
    localStorage.setItem(ACCENT_STORAGE_KEY, next)
  } catch {
    // 存不进去不影响当前会话
  }
  paint(next)
  return true
}

/** 服务端偏好覆盖本地。null / 无法识别表示服务端没存过，保持本地。旧键名会被归一。 */
function applyRemote(remote: string | null | undefined): boolean {
  const normalized = normalizeAccent(remote)
  if (!normalized) return false
  return setAccent(normalized)
}

/** 首帧兜底：index.html 已设过 data-accent，这里纠正 CSP 被拦 / 值不一致的情况。 */
function init(): void {
  paint(accent.value)
}

export function useAccent() {
  return {
    accent: readonly(accent),
    options: ACCENTS,
    setAccent,
    applyRemote,
    init,
  }
}
