import { beforeEach, describe, expect, it, vi } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import {
  DENSITIES,
  DEFAULT_DENSITY,
  DENSITY_STORAGE_KEY,
  isDensityName,
  useDensity,
} from './useDensity'

/**
 * 密度档位（UX-06）的三条守门：
 *   - 非法值不能写进 localStorage / html（下次启动直接坏观感）；
 *   - 服务端"没存过"不能覆盖本机选择；
 *   - index.html 首帧脚本的档位表与本模块一致（内联脚本不能 import，靠注释必然漂移）。
 */
describe('useDensity', () => {
  const density = useDensity()

  beforeEach(() => {
    localStorage.removeItem(DENSITY_STORAGE_KEY)
    density.setDensity(DEFAULT_DENSITY)
  })

  it('两档齐全且默认舒适', () => {
    expect(DENSITIES).toEqual(['comfortable', 'compact'])
    expect(DEFAULT_DENSITY).toBe('comfortable')
    expect(isDensityName('compact')).toBe(true)
    expect(isDensityName('')).toBe(false)
    expect(isDensityName('Compact')).toBe(false)
    expect(isDensityName(null)).toBe(false)
  })

  it('切换写本地 + 写 html', () => {
    expect(density.setDensity('compact')).toBe(true)
    expect(density.density.value).toBe('compact')
    expect(localStorage.getItem(DENSITY_STORAGE_KEY)).toBe('compact')
    expect(document.documentElement.getAttribute('data-density')).toBe('compact')

    expect(density.setDensity('comfortable')).toBe(true)
    expect(document.documentElement.getAttribute('data-density')).toBe('comfortable')
  })

  it('非法值被拒绝，且不污染已生效状态', () => {
    density.setDensity('compact')
    expect(density.setDensity('dense')).toBe(false)
    expect(density.setDensity('')).toBe(false)
    expect(density.density.value).toBe('compact')
    expect(localStorage.getItem(DENSITY_STORAGE_KEY)).toBe('compact')
    expect(document.documentElement.getAttribute('data-density')).toBe('compact')
  })

  it('服务端合法值覆盖本地；空值 / 非法值不覆盖', () => {
    density.setDensity('comfortable')
    expect(density.applyRemote('compact')).toBe(true)
    expect(density.density.value).toBe('compact')
    expect(density.applyRemote(null)).toBe(false)
    expect(density.applyRemote(undefined)).toBe(false)
    expect(density.applyRemote('')).toBe(false)
    expect(density.applyRemote('cozy')).toBe(false)
    expect(density.density.value).toBe('compact')
  })

  it('init 把当前状态重刷到 html（覆盖首帧脚本被拦的情况）', () => {
    density.setDensity('compact')
    document.documentElement.removeAttribute('data-density')
    density.init()
    expect(document.documentElement.getAttribute('data-density')).toBe('compact')
  })

  it('index.html 首帧脚本的 DENSITIES 与本模块一致', () => {
    // 与 useAccent.spec 同样的路径处理：vitest 的 cwd 是 frontend/，从仓库根跑时兜一下。
    const candidates = [
      resolve(process.cwd(), 'index.html'),
      resolve(process.cwd(), 'frontend/index.html'),
    ]
    const indexPath = candidates.find((p) => existsSync(p))
    expect(indexPath, `找不到 index.html，试过：${candidates.join(' / ')}`).toBeTruthy()
    const html = readFileSync(indexPath!, 'utf8')

    const raw = html.match(/var DENSITIES = \[([^\]]*)\]/)?.[1]
    expect(raw, 'index.html 里找不到 var DENSITIES = [...]，首帧脚本被改名了？').toBeTruthy()
    const fromHtml = raw!
      .split(',')
      .map((s) => s.trim().replace(/^'|'$/g, ''))
      .filter(Boolean)
    expect(fromHtml).toEqual([...DENSITIES])
  })

  it('localStorage 里的档位在模块初始化时被读取（刷新后保持）', async () => {
    // readStored() 只在模块加载时执行一次，重置模块才能覆盖这条路径。
    // 本用例必须留在文件最后：resetModules 之后顶层 density 仍指向旧模块实例。
    localStorage.setItem(DENSITY_STORAGE_KEY, 'compact')
    vi.resetModules()
    const fresh = await import('./useDensity')
    expect(fresh.useDensity().density.value).toBe('compact')
  })
})
