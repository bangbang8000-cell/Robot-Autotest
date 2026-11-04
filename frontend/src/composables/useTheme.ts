import { computed, readonly, ref } from 'vue'

/**
 * 主题三态（UX-01 / FR-v0.2-31~33）。
 *
 * `system` 不是"第三种颜色"，而是"把决定权交给操作系统"：它必须**实时跟随** OS 外观变化
 * （晚上系统自动切暗色时页面要跟着变，不能等刷新）。所以这里挂了 `matchMedia` 监听，
 * 而不是启动时读一次 `prefers-color-scheme`。
 *
 * 三处状态各有归属，别混：
 *   - 本地 `localStorage.theme`：未登录也能记住选择，首帧由 index.html 内联脚本读取（防闪）；
 *   - 服务端 `user_preference`：登录后覆盖本地，作用是**换设备后仍是同一个主题**；
 *   - `<html data-theme>`：最终生效值，CSS 只认这个属性。
 *
 * 模块级单例：App.vue 的切换按钮、设置页、后续任何地方都读同一份状态。
 */

export type ThemeMode = 'system' | 'light' | 'dark'
export type ResolvedTheme = 'light' | 'dark'

const STORAGE_KEY = 'theme'
const MODES: ThemeMode[] = ['system', 'light', 'dark']

/** 服务端偏好键名，与后端 `PreferenceKeys.THEME` 保持一致 */
export const THEME_PREF_KEY = 'theme'

function readStoredMode(): ThemeMode {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'light' || saved === 'dark' || saved === 'system') {
      return saved
    }
  } catch {
    // 隐私模式/禁用存储：退回跟随系统即可，不阻断启动
  }
  return 'system'
}

const media =
  typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-color-scheme: dark)')
    : null

const mode = ref<ThemeMode>(readStoredMode())
const systemDark = ref(media ? media.matches : true)

/** 把最终值写到 <html>：CSS 的 `:root[data-theme=...]` 认这个属性 */
function paint(next: ResolvedTheme): void {
  const root = document.documentElement
  root.setAttribute('data-theme', next)
  // color-scheme 让浏览器自带的滚动条、表单控件、光标也跟上，否则亮色下是黑滚动条
  root.style.colorScheme = next
}

function resolve(current: ThemeMode, isDark: boolean): ResolvedTheme {
  if (current === 'system') {
    return isDark ? 'dark' : 'light'
  }
  return current
}

const resolved = computed<ResolvedTheme>(() => resolve(mode.value, systemDark.value))

/** 只在 system 模式下把 OS 变化同步到页面；手动选定的主题不受 OS 影响 */
function onSystemChange(event: MediaQueryListEvent): void {
  systemDark.value = event.matches
  if (mode.value === 'system') {
    paint(event.matches ? 'dark' : 'light')
  }
}

if (media) {
  if (typeof media.addEventListener === 'function') {
    media.addEventListener('change', onSystemChange)
  } else if (typeof media.addListener === 'function') {
    // Safari < 14 只支持旧 API
    media.addListener(onSystemChange)
  }
}

/** 切换：立即生效 + 落本地。是否同步服务端由调用方决定（登录态才有意义） */
function setMode(next: ThemeMode): void {
  if (!MODES.includes(next)) {
    return
  }
  mode.value = next
  try {
    localStorage.setItem(STORAGE_KEY, next)
  } catch {
    // 存不进去也不影响当前会话的显示
  }
  paint(resolve(next, systemDark.value))
}

/** 用服务端偏好覆盖本地（登录后调用）。传 null/非法值表示服务端没存过，保持本地。 */
function applyRemote(remote: string | null | undefined): void {
  if (remote === 'light' || remote === 'dark' || remote === 'system') {
    mode.value = remote
    try {
      localStorage.setItem(STORAGE_KEY, remote)
    } catch {
      // 同上
    }
    paint(resolve(remote, systemDark.value))
  }
}

/** 三态循环：system → light → dark → system */
function cycle(): ThemeMode {
  const order: ThemeMode[] = ['system', 'light', 'dark']
  const next = order[(order.indexOf(mode.value) + 1) % order.length]
  setMode(next)
  return next
}

/**
 * 首帧兜底：`index.html` 的内联脚本已经在挂载前设好 data-theme。
 * 这里再 paint 一次是为了覆盖「内联脚本被 CSP 拦掉」或「本地值与当前 mode 不一致」的情况。
 */
function init(): void {
  paint(resolved.value)
}

export function useTheme() {
  return {
    mode: readonly(mode),
    resolved: readonly(resolved),
    systemPrefersDark: readonly(systemDark),
    setMode,
    applyRemote,
    cycle,
    init,
  }
}
