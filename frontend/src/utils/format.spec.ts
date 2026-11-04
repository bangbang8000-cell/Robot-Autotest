/**
 * format.ts 正式单测（UX-02 引入 vitest 后补正式化，TEST-PLAN TC-I18N-06 备注）。
 *
 * 此前在 UX-03 用 esbuild 打包 + node 直跑做临时验证（ux03-format.log），
 * 本文件是它的正式替代：断言相同口径，纳入 `npm run test` 常规执行。
 */
import { beforeAll, describe, expect, it } from 'vitest'
import { i18n } from '@/locales'
import {
  EMPTY,
  formatBytes,
  formatDateTime,
  formatDuration,
  formatNumber,
  formatPercent,
} from './format'

describe('formatDuration', () => {
  beforeAll(() => {
    i18n.global.locale.value = 'zh-CN'
  })

  it('空值 / 负数一律归 —，绝不显示 0 ms', () => {
    expect(formatDuration(null)).toBe(EMPTY)
    expect(formatDuration(undefined)).toBe(EMPTY)
    expect(formatDuration(-1)).toBe(EMPTY)
  })

  it('<1s 保留整毫秒（narrow 中文毫秒作 ms）；<60s 保留一位小数秒', () => {
    expect(formatDuration(900)).toBe('900ms')
    expect(formatDuration(1500)).toBe('1.5秒')
    expect(formatDuration(45_000)).toBe('45秒')
  })

  it('超过 1 分钟拆「分 + 秒」两段', () => {
    const out = formatDuration(90_000)
    expect(out).toContain('分')
    expect(out).toContain('秒')
  })
})

describe('formatDateTime / formatDate 跟随语言（TC-I18N-06）', () => {
  const iso = '2026-11-04T10:30:00Z'

  it('en-US 输出月/日/年序，zh-CN 输出年/月/日序', () => {
    i18n.global.locale.value = 'en-US'
    const en = formatDateTime(iso)
    i18n.global.locale.value = 'zh-CN'
    const zh = formatDateTime(iso)
    expect(en).not.toBe(zh)
    // 同一时区下两语言都含同一组数字片段，只是顺序/分隔不同
    expect(zh).toMatch(/2026/)
    expect(en).toMatch(/2026/)
  })

  it('空值归 —；脏值原样返回便于排查', () => {
    expect(formatDateTime(null)).toBe(EMPTY)
    expect(formatDateTime('')).toBe(EMPTY)
    expect(formatDateTime('not-a-date')).toBe('not-a-date')
  })
})

describe('formatBytes / formatPercent / formatNumber', () => {
  it('字节单位阶梯与空值', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(1024)).toBe('1.0 KB')
    expect(formatBytes(1536)).toBe('1.5 KB')
    expect(formatBytes(1024 ** 3)).toBe('1.00 GB')
    expect(formatBytes(null)).toBe(EMPTY)
    expect(formatBytes(-5)).toBe(EMPTY)
  })

  it('基点转百分比；null 归 — 不显示 0%', () => {
    expect(formatPercent(9950)).toBe('99.5%')
    expect(formatPercent(0)).toBe('0.0%')
    expect(formatPercent(null)).toBe(EMPTY)
  })

  it('千分位跟区域；空值归 —', () => {
    expect(formatNumber(1234567)).toContain('1,234,567')
    expect(formatNumber(null)).toBe(EMPTY)
  })
})
