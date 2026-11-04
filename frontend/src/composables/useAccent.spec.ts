import { beforeEach, describe, expect, it, vi } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import {
  ACCENTS,
  ACCENT_STORAGE_KEY,
  isAccentName,
  LEGACY_ALIAS,
  normalizeAccent,
  useAccent,
} from './useAccent'

/**
 * 三层持久化里最容易漏的两件事：非法值写进 localStorage（下次启动直接坏主题）、
 * 服务端"没存过"被当成"存了默认值"覆盖掉用户本机选择。这里各给一条守门用例。
 *
 * 2026-11-05 换色板（新年红/科技蓝/土豪金/活力橙/魅力紫/翡翠绿）后新增两类用例：
 *   - 旧键名归一：升级用户不能被打回默认蓝；
 *   - index.html 内联脚本与 useAccent 的两张表一致：内联脚本不能 import，
 *     靠注释提醒必然漂移，这里直接解析源码比对。
 */
describe('useAccent', () => {
  const accent = useAccent()

  beforeEach(() => {
    localStorage.removeItem(ACCENT_STORAGE_KEY)
    accent.setAccent('blue')
  })

  it('六预设齐全且默认 blue', () => {
    expect(ACCENTS).toEqual(['crimson', 'blue', 'gold', 'orange', 'violet', 'jade'])
    expect(isAccentName('jade')).toBe(true)
    expect(isAccentName('')).toBe(false)
    expect(isAccentName('BLUE')).toBe(false)
    expect(isAccentName(null)).toBe(false)
    // 首版键名不能再算合法，否则「六预设」在前后端两侧定义会分叉
    expect(isAccentName('fuchsia')).toBe(false)
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
    accent.setAccent('crimson')
    expect(accent.applyRemote('jade')).toBe(true)
    expect(accent.accent.value).toBe('jade')
    expect(accent.applyRemote(null)).toBe(false)
    expect(accent.applyRemote(undefined)).toBe(false)
    expect(accent.applyRemote('')).toBe(false)
    expect(accent.applyRemote('unknown')).toBe(false)
    expect(accent.accent.value).toBe('jade')
    expect(localStorage.getItem(ACCENT_STORAGE_KEY)).toBe('jade')
  })

  it('init 把当前状态重刷到 html（覆盖首帧脚本被拦的情况）', () => {
    accent.setAccent('gold')
    document.documentElement.removeAttribute('data-accent')
    accent.init()
    expect(document.documentElement.getAttribute('data-accent')).toBe('gold')
  })

  it('旧键名归一：cyan/indigo → blue、teal → jade、fuchsia → violet', () => {
    expect(normalizeAccent('cyan')).toBe('blue')
    expect(normalizeAccent('indigo')).toBe('blue')
    expect(normalizeAccent('teal')).toBe('jade')
    expect(normalizeAccent('fuchsia')).toBe('violet')
    expect(normalizeAccent('jade')).toBe('jade')
    expect(normalizeAccent('purple')).toBeNull()
    expect(normalizeAccent(null)).toBeNull()

    // 服务端还存着旧值时，登录后不能被打回默认蓝，而应归一到最近的同色相预设
    expect(accent.applyRemote('fuchsia')).toBe(true)
    expect(accent.accent.value).toBe('violet')
    expect(localStorage.getItem(ACCENT_STORAGE_KEY)).toBe('violet')
  })

  it('index.html 首帧脚本的 ACCENTS / LEGACY 与本模块一致', () => {
    // 不能用 import.meta.url 拼路径：jsdom/happy-dom 环境下它是 http://localhost/，
    // new URL(..., import.meta.url) 出来的不是 file: 协议，readFileSync 直接报
    // "The URL must be of scheme file"。vitest 的 cwd 是 frontend/（npm run 的工作目录），
    // 所以直接按 cwd 找；再从仓库根跑的情况兜一下。
    const candidates = [resolve(process.cwd(), 'index.html'), resolve(process.cwd(), 'frontend/index.html')]
    const indexPath = candidates.find((p) => existsSync(p))
    expect(indexPath, `找不到 index.html，试过：${candidates.join(' / ')}`).toBeTruthy()
    const html = readFileSync(indexPath!, 'utf8')

    const accentsRaw = html.match(/var ACCENTS = \[([^\]]*)\]/)?.[1]
    expect(accentsRaw, 'index.html 里找不到 var ACCENTS = [...]，首帧脚本被改名了？').toBeTruthy()
    const fromHtml = accentsRaw!
      .split(',')
      .map((s) => s.trim().replace(/^'|'$/g, ''))
      .filter(Boolean)
    expect(fromHtml).toEqual([...ACCENTS])

    const legacyRaw = html.match(/var LEGACY = \{([^}]*)\}/)?.[1]
    expect(legacyRaw, 'index.html 里找不到 var LEGACY = {...}，首帧脚本被改名了？').toBeTruthy()
    const fromHtmlLegacy = Object.fromEntries(
      [...legacyRaw!.matchAll(/(\w+)\s*:\s*'(\w+)'/g)].map((m) => [m[1], m[2]]),
    )
    expect(fromHtmlLegacy).toEqual({ ...LEGACY_ALIAS })
  })

  it('localStorage 里的旧键名在模块初始化时被归一（升级首屏不掉色）', async () => {
    // readStored() 在模块加载时执行一次，所以要重置模块重新加载才能覆盖这条路径。
    // 本用例必须留在文件最后：resetModules 之后本文件顶层的 accent 仍指向旧模块实例。
    localStorage.setItem(ACCENT_STORAGE_KEY, 'teal')
    vi.resetModules()
    const fresh = await import('./useAccent')
    expect(fresh.useAccent().accent.value).toBe('jade')
    expect(fresh.useAccent().accent.value).not.toBe('blue')
  })
})
