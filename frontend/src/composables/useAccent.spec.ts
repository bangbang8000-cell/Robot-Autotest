import { beforeEach, describe, expect, it } from 'vitest'
import {
  ACCENTS,
  ACCENT_STORAGE_KEY,
  isAccentName,
  useAccent,
} from './useAccent'

/**
 * 三层持久化里最容易漏的两件事：非法值写进 localStorage（下次启动直接坏主题）、
 * 服务端"没存过"被当成"存了默认值"覆盖掉用户本机选择。这里各给一条守门用例。
 */
describe('useAccent', () => {
  const accent = useAccent()

  beforeEach(() => {
    localStorage.removeItem(ACCENT_STORAGE_KEY)
    accent.setAccent('blue')
  })

  it('六预设齐全且默认 blue', () => {
    expect(ACCENTS).toEqual(['blue', 'cyan', 'indigo', 'violet', 'teal', 'fuchsia'])
    expect(isAccentName('teal')).toBe(true)
    expect(isAccentName('')).toBe(false)
    expect(isAccentName('BLUE')).toBe(false)
    expect(isAccentName(null)).toBe(false)
  })

  it('切换写本地 + 写 html，且六个预设都能切', () => {
    for (const name of ACCENTS) {
      expect(accent.setAccent(name)).toBe(true)
      expect(accent.accent.value).toBe(name)
      expect(localStorage.getItem(ACCENT_STORAGE_KEY)).toBe(name)
      expect(document.documentElement.getAttribute('data-accent')).toBe(name)
    }
  })

  it('非法值被拒绝，且不污染已生效状态', () => {
    accent.setAccent('violet')
    expect(accent.setAccent('chartreuse')).toBe(false)
    expect(accent.setAccent('')).toBe(false)
    expect(accent.accent.value).toBe('violet')
    expect(localStorage.getItem(ACCENT_STORAGE_KEY)).toBe('violet')
    expect(document.documentElement.getAttribute('data-accent')).toBe('violet')
  })

  it('服务端合法值覆盖本地；空值 / 非法值不覆盖', () => {
    accent.setAccent('cyan')
    expect(accent.applyRemote('fuchsia')).toBe(true)
    expect(accent.accent.value).toBe('fuchsia')
    expect(accent.applyRemote(null)).toBe(false)
    expect(accent.applyRemote(undefined)).toBe(false)
    expect(accent.applyRemote('')).toBe(false)
    expect(accent.applyRemote('unknown')).toBe(false)
    expect(accent.accent.value).toBe('fuchsia')
    expect(localStorage.getItem(ACCENT_STORAGE_KEY)).toBe('fuchsia')
  })

  it('init 把当前状态重刷到 html（覆盖首帧脚本被拦的情况）', () => {
    accent.setAccent('indigo')
    document.documentElement.removeAttribute('data-accent')
    accent.init()
    expect(document.documentElement.getAttribute('data-accent')).toBe('indigo')
  })
})
