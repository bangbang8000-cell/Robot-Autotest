/**
 * `@robot-autotest/editor` 的类型声明（宿主只用到公共出口的一小部分）。
 *
 * 为什么手写：包构建只产运行时产物（`dist/editor.js`），不产 `.d.ts`；
 * 引入 `vite-plugin-dts` 需要联网装依赖。契约变更时同步本文件。
 * 运行期契约以 `packages/editor/index.ts` 为准（EDT-02）。
 */
declare module '@robot-autotest/editor' {
  import type { DefineComponent } from 'vue'

  export interface KeywordRecord {
    name: string
    library: string
    doc: string
    shortdoc?: string
    doc_format?: string
    args?: string[]
  }

  export interface EditorAdapter {
    searchKeywords(query: string, scopeId?: number): Promise<{ results: KeywordRecord[] }>
    [key: string]: unknown
  }

  export interface EditorScope {
    scopeId: number | null
    filePath: string
  }

  export const RobotEditor: DefineComponent<{
    content: string
    readonly?: boolean
    height?: string
  }>

  export function configureEditorAdapter(patch: Partial<EditorAdapter>): void
  export function resetEditorAdapter(): void
  export function setEditorScope(patch: Partial<EditorScope>): void
  export function resetEditorScope(): void
  export function editorScope(): EditorScope
}

/**
 * 编辑器语言包（EDT-07）。`vite.config.ts` 把这个模块别名指向
 * `packages/editor/src/locales/index.ts`，只带入纯对象、不拉入组件树。
 */
declare module '@robot-autotest/editor-locales' {
  export const editorMessages: Record<'zh-CN' | 'en-US', Record<string, unknown>>
}

/** 编辑器样式产物（`packages/editor/dist/editor.css`，由 vite.config 别名指向）。 */
declare module '@robot-autotest/editor-css'
