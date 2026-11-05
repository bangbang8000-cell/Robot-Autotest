/**
 * 语言包合并回归（UX-06 走查发现的真实缺陷）。
 *
 * 缺陷：编辑器语言包与平台语言包各有一个顶层 `editor` 段，
 * `{ ...zhCN, ...editorMessages }` 是浅合并，后者把前者的 `editor` 整段替换掉，
 * 运行时编辑器页直接显示 `editor.titleEditor`、`editor.missingRoute` 原始键名。
 *
 * 本文件锁住两件事：
 * ① `editor` 段是字段级合并，两边的键都还在；
 * ② 编辑器语言包的每个顶层命名空间都没有把平台同名命名空间挤掉。
 */
import { describe, expect, it } from 'vitest'
import { editorMessages } from '@robot-autotest/editor-locales'
import { i18n } from '@/locales'
import zhCN from '@/locales/zh-CN'
import enUS from '@/locales/en-US'

const LOCALES = [
  { name: 'zh-CN', platform: zhCN as Record<string, unknown>, editor: editorMessages['zh-CN'] as Record<string, unknown> },
  { name: 'en-US', platform: enUS as Record<string, unknown>, editor: editorMessages['en-US'] as Record<string, unknown> },
] as const

describe('语言包合并', () => {
  for (const { name, platform, editor } of LOCALES) {
    it(`${name}：editor 段同时保留平台文案与编辑器文案`, () => {
      const merged = (i18n.global.messages.value as Record<string, Record<string, Record<string, unknown>>>)[name]
      const platformEditor = platform.editor as Record<string, unknown>
      const editorPackEditor = editor.editor as Record<string, unknown>

      expect(merged.editor.titleEditor).toBe(platformEditor.titleEditor)
      expect(typeof merged.editor.missingRoute).toBe('string')
      expect(merged.editor.common).toEqual(editorPackEditor.common)
    })

    it(`${name}：编辑器语言包的顶层命名空间全部并入`, () => {
      const merged = (i18n.global.messages.value as Record<string, Record<string, unknown>>)[name]
      for (const ns of Object.keys(editor)) {
        expect(merged[ns], `命名空间 ${ns} 被挤掉`).toBeDefined()
      }
    })

    it(`${name}：平台自有的顶层命名空间全部保留`, () => {
      const merged = (i18n.global.messages.value as Record<string, Record<string, unknown>>)[name]
      for (const ns of Object.keys(platform)) {
        expect(merged[ns], `命名空间 ${ns} 被挤掉`).toBeDefined()
      }
    })
  }
})
