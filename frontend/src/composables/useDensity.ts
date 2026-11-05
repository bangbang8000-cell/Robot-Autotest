import { readonly, ref } from 'vue'

/**
 * 表格密度双档（UX-06）。
 *
 *   comfortable 舒适：行高 44px（默认，与 UX-05 交付时的观感一致）
 *   compact     紧凑：行高 36px（一屏多两行，长列表巡检时用）
 *
 * 与 useTheme / useAccent 同一套三层持久化模型：
 *   - 本地 `localStorage.density`：未登录也记得住，首帧由 index.html 内联脚本读取（防闪）；
 *   - 服务端 `user_preference.density`：登录后覆盖本地，换设备保持同一档；
 *   - `<html data-density>`：最终生效值，theme.css 只认这个属性。
 *
 * 为什么行高不写在组件里：19 处数据网格都走 `DataTable` 壳，但行高是"全局观感"，
 * 写进壳就变成"表格自己的属性"，将来卡片 / 列表想跟随密度还得再抄一遍。
 * 放成 CSS 变量（`--table-row-h`）后，壳只负责把变量用上，档位切换只改一个属性。
 *
 * 服务端同步失败不回滚本地：与主题、强调色同一理由（点了没反应比"换设备没同步"更糟）。
 */

export const DENSITIES = ['comfortable', 'compact'] as const
export type DensityName = (typeof DENSITIES)[number]

export const DENSITY_STORAGE_KEY = 'density'
export const DENSITY_PREF_KEY = 'density'
export const DEFAULT_DENSITY: DensityName = 'comfortable'

export function isDensityName(value: unknown): value is DensityName {
  return typeof value === 'string' && (DENSITIES as readonly string[]).includes(value)
}

function readStored(): DensityName {
  try {
    const saved = localStorage.getItem(DENSITY_STORAGE_KEY)
    if (isDensityName(saved)) return saved
  } catch {
    // 隐私模式 / 禁用存储：用默认档，不阻断启动
  }
  return DEFAULT_DENSITY
}

const density = ref<DensityName>(readStored())

function paint(next: DensityName): void {
  document.documentElement.setAttribute('data-density', next)
}

/** 切换：立即生效 + 落本地。非法值静默拒绝，不写脏数据。 */
function setDensity(next: string): boolean {
  if (!isDensityName(next)) return false
  density.value = next
  try {
    localStorage.setItem(DENSITY_STORAGE_KEY, next)
  } catch {
    // 存不进去不影响当前会话
  }
  paint(next)
  return true
}

/** 服务端偏好覆盖本地。null / 无法识别表示服务端没存过，保持本地。 */
function applyRemote(remote: string | null | undefined): boolean {
  if (!isDensityName(remote)) return false
  return setDensity(remote)
}

/** 首帧兜底：index.html 已设过 data-density，这里纠正内联脚本被拦 / 值不一致的情况。 */
function init(): void {
  paint(density.value)
}

export function useDensity() {
  return {
    density: readonly(density),
    options: DENSITIES,
    setDensity,
    applyRemote,
    init,
  }
}
