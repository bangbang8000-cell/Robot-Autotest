/**
 * 编辑器语言包出口。
 *
 * 语言包由 `tools/extract-locales.mts` 从上游 v0.16.1 生成，
 * 命名空间为 `robotEditor / flowEditor / debug / recorder / editor`（`common` 已挂到 `editor.common`），
 * 与平台 i18n 的既有键无重名，可由宿主直接顶层展开合并。
 */
import zhCN from './zh-CN'
import enUS from './en-US'

export const editorMessages = {
  'zh-CN': zhCN,
  'en-US': enUS,
} as const

export type EditorMessages = typeof editorMessages
