import { readonly, ref } from 'vue'

/**
 * 强调色六预设（UX-04 / FR-v0.3-* 步骤 6）。
 *
 * 与 useTheme 同一套三层持久化模型，别混：
 *   - 本地 `localStorage.accent`：未登录也能记住，首帧由 index.html 内联脚本读取（防闪）；
 *   - 服务端 `user_preference.accent`：登录后覆盖本地，保证换设备仍是同一套强调色；
 *   - `<html data-accent>`：最终生效值，theme.css 的 12 个变体块只认这个属性，
 *     naive-ui 适配层也从这个属性切换后重算（见 composables/naiveTheme.ts）。
 *
 * 服务端同步失败不回滚本地：用户点了没反应比"换设备没同步"严重得多。
 */

export const ACCENTS = ['blue', 'cyan', 'indigo', 'violet', 'teal', 'fuchsia'] as const
export type AccentName = (typeof ACCENTS)[number]

export const ACCENT_STORAGE_KEY = 'accent'
export const ACCENT_PREF_KEY = 'accent'

export function isAccentName(value: unknown): value is AccentName {
  return typeof value === 'string' && (ACCENTS as readonly string[]).includes(value)
}

function readStored(): AccentName {
  try {
    const saved = localStorage.getItem(ACCENT_STORAGE_KEY)
    if (isAccentName(saved)) return saved
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

/** 服务端偏好覆盖本地。null / 非法值表示服务端没存过，保持本地。 */
function applyRemote(remote: string | null | undefined): boolean {
  if (!isAccentName(remote)) return false
  return setAccent(remote)
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
