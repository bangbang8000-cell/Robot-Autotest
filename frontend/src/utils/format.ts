/**
 * 时间 / 时长 / 字节 / 数字的**唯一**格式化出口（FR-v0.2-35）。
 *
 * <p>三个理由，决定了这些函数必须集中而不是各页面自己写：
 * ① 页面里散落的 `toLocaleString()` 不传 locale，永远跟着浏览器语言走，
 *    界面切到英文后时间仍然是 `2026/11/4 10:30:00` —— 看起来"没生效"。
 * ② 时长、字节的单位在中英文里不同（`13分钟` / `13m`），必须走 `Intl`。
 * ③ 空值统一显示 `—`。显示 `0 ms` 会被读成"跑过且耗时为零"，与"没跑过"混淆。
 *
 * <p>**依赖方向**：本文件只依赖 `@/locales` 的 i18n 单例，不依赖任何 api / view，
 * 因此可以在 `.ts`（拦截器、store）和 `.vue` 里同时使用。
 */
import { i18n } from '@/locales'

/** 空值占位。全应用统一，避免一处 `-` 一处 `N/A`。 */
export const EMPTY = '—'

function locale(): string {
  return i18n.global.locale.value
}

function unit(value: number, name: Intl.NumberFormatOptions['unit']): string {
  // style:'unit' 是唯一能同时给出「13分钟」和「13m」的标准做法。
  // Safari < 14.1 不支持 unit，捕获后退回「数字 + 拉丁单位」，不至于崩。
  try {
    return new Intl.NumberFormat(locale(), {
      style: 'unit',
      unit: name,
      unitDisplay: 'narrow',
      maximumFractionDigits: 1,
    }).format(value)
  } catch {
    return `${value} ${String(name)}`
  }
}

/** ISO 时间串 → 本地日期时间。无法解析时原样返回，便于用户看出后端给了什么脏值。 */
export function formatDateTime(value: string | null | undefined, fallback = EMPTY): string {
  if (!value) return fallback
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  try {
    return new Intl.DateTimeFormat(locale(), {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    }).format(date)
  } catch {
    return date.toISOString()
  }
}

/** ISO 时间串 → 本地日期（不含时刻），供打印页眉等窄位使用。 */
export function formatDate(value: string | null | undefined, fallback = EMPTY): string {
  if (!value) return fallback
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  try {
    return new Intl.DateTimeFormat(locale(), {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(date)
  } catch {
    return date.toISOString().slice(0, 10)
  }
}

/**
 * 毫秒 → 人类可读时长。
 *
 * <p>`null` / 负数一律 `—`（不是 `0 ms`）；`<1s` 保留整毫秒（性能测试的常用量级）；
 * `<60s` 保留一位小数秒；超过 1 分钟拆成「分 + 秒」两段。
 */
export function formatDuration(ms: number | null | undefined, fallback = EMPTY): string {
  if (ms === null || ms === undefined || Number.isNaN(ms) || ms < 0) return fallback
  if (ms < 1000) return unit(Math.round(ms), 'millisecond')
  if (ms < 60_000) return unit(Number((ms / 1000).toFixed(1)), 'second')
  const minutes = Math.floor(ms / 60_000)
  const seconds = Math.round((ms % 60_000) / 1000)
  return `${unit(minutes, 'minute')} ${unit(seconds, 'second')}`
}

/** 字节 → 人类可读体积。B / KB / MB / GB / TB 在中英文里都写作拉丁后缀，无需翻译。 */
export function formatBytes(bytes: number | null | undefined, fallback = EMPTY): string {
  if (bytes === null || bytes === undefined || Number.isNaN(bytes) || bytes < 0) return fallback
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let value = bytes
  let index = 0
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024
    index += 1
  }
  const digits = index === 0 ? 0 : index === 1 ? 1 : 2
  return `${value.toFixed(digits)} ${units[index]}`
}

/** 基点（1/100 百分比）→ 百分比串。`null` 显示 `—`，绝不显示 `0%`。 */
export function formatPercent(basisPoints: number | null | undefined, fallback = EMPTY): string {
  if (basisPoints === null || basisPoints === undefined || Number.isNaN(basisPoints)) {
    return fallback
  }
  return `${(basisPoints / 100).toFixed(1)}%`
}

/** 千分位整数，跟随语言（zh 也是 `1,234,567`，但换个区域就可能变）。 */
export function formatNumber(value: number | null | undefined, fallback = EMPTY): string {
  if (value === null || value === undefined || Number.isNaN(value)) return fallback
  try {
    return new Intl.NumberFormat(locale()).format(value)
  } catch {
    return String(value)
  }
}
