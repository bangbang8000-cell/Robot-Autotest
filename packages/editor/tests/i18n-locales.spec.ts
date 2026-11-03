/**
 * EDT-07 · 语言包完整性守护（4 条）
 *
 * 1. 源码里出现的每个 i18n 键都能在 zh-CN 解析到字符串；
 * 2. 同样在 en-US 全部可解析；
 * 3. zh-CN 与 en-US 的叶子键集合完全一致（防止只补一种语言）；
 * 4. zh-CN 的关键命名空间确实是中文（无成句英文残留）。
 */
import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { editorMessages } from '../src/locales'

// vitest 下 import.meta.url 不是 file:// 协议，用包根目录定位源码
const SRC_DIR = join(process.cwd(), 'src')
const KEY_RE = /(?:(?<![\w$])t|tm|\$t)\(\s*(['"])([A-Za-z][A-Za-z0-9_.]*)\1/g

function walk(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) return walk(full)
    return /\.(vue|ts)$/.test(name) ? [full] : []
  })
}

/** 源码中实际被 t()/tm()/$t() 引用的键（去重、排序）。 */
function collectUsedKeys(): string[] {
  const keys = new Set<string>()
  for (const file of walk(SRC_DIR)) {
    const text = readFileSync(file, 'utf8')
    for (const m of text.matchAll(KEY_RE)) keys.add(m[2])
  }
  return [...keys].sort()
}

type Node = { [k: string]: string | Node }

function getPath(node: Node, key: string): string | undefined {
  return key.split('.').reduce<unknown>((acc, part) => (acc as Node | undefined)?.[part], node) as
    | string
    | undefined
}

function leafKeys(node: Node, prefix = ''): string[] {
  return Object.entries(node).flatMap(([k, v]) => {
    const key = prefix ? `${prefix}.${k}` : k
    return typeof v === 'string' ? [key] : leafKeys(v, key)
  })
}

const allQuotedKeys = collectUsedKeys()
/** 以 `.` 结尾的是动态前缀（如 `t('robotEditor.settingType.' + kind)`），不是完整键 */
const usedKeys = allQuotedKeys.filter((k) => !k.endsWith('.'))
const usedPrefixes = allQuotedKeys.filter((k) => k.endsWith('.'))
const zh = editorMessages['zh-CN'] as unknown as Node
const en = editorMessages['en-US'] as unknown as Node

describe('EDT-07 编辑器语言包', () => {
  it('源码引用的键在 zh-CN 全部可解析', () => {
    expect(usedKeys.length).toBeGreaterThan(200)
    const missing = usedKeys.filter((k) => typeof getPath(zh, k) !== 'string')
    expect(missing, `zh-CN 缺键: ${missing.join(', ')}`).toEqual([])
    // 动态前缀必须真实存在，否则运行时拼出来的键同样会缺
    usedPrefixes.forEach((p) => {
      const node = p.split('.').slice(0, -1).reduce<unknown>((acc, part) => (acc as Node)?.[part], zh)
      expect(typeof node, `zh-CN 缺少动态前缀节点: ${p}`).toBe('object')
    })
  })

  it('源码引用的键在 en-US 全部可解析', () => {
    const missing = usedKeys.filter((k) => typeof getPath(en, k) !== 'string')
    expect(missing, `en-US 缺键: ${missing.join(', ')}`).toEqual([])
    usedPrefixes.forEach((p) => {
      const node = p.split('.').slice(0, -1).reduce<unknown>((acc, part) => (acc as Node)?.[part], en)
      expect(typeof node, `en-US 缺少动态前缀节点: ${p}`).toBe('object')
    })
  })

  it('zh-CN 与 en-US 叶子键集合一致', () => {
    const zhKeys = leafKeys(zh).sort()
    const enKeys = leafKeys(en).sort()
    expect(zhKeys.length).toBe(enKeys.length)
    expect(zhKeys).toEqual(enKeys)
  })

  it('zh-CN 核心命名空间不含成句英文', () => {
    // 技术字面量（RF 设置名、变量占位符、定位策略名）允许保留英文
    const allowPrefixes = [
      'robotEditor.settingType.',
      'robotEditor.stepType.',
      'robotEditor.setup',
      'robotEditor.teardown',
      'robotEditor.argTypes.namePlaceholder',
      'robotEditor.returnValuePlaceholder',
      'robotEditor.stepPlaceholder',
      'robotEditor.variableNamePlaceholder',
      'flowEditor.variableNamePlaceholder',
      'flowEditor.settingMeta.',
      'recorder.selector.strategy.',
      'recorder.launcher.targetUrlPlaceholder',
      'recorder.live.pathPlaceholder',
    ]
    const allowed = (k: string) => allowPrefixes.some((p) => k === p || k.startsWith(p))

    const offenders = usedKeys
      .filter((k) => !allowed(k))
      .filter((k) => {
        const value = getPath(zh, k)
        if (!value) return false
        // 纯 ASCII 且有 4 个以上连续英文字母 → 视为未翻译
        return /^[\x00-\x7F]*$/.test(value) && /[a-zA-Z]{4,}/.test(value)
      })

    expect(offenders, `疑似未翻译: ${offenders.slice(0, 10).join(', ')}`).toEqual([])
  })
})
