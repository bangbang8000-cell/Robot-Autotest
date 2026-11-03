/**
 * `@robot-autotest/editor` 公共出口。
 *
 * 只导出「宿主需要的东西」：编辑器组件本体、纯函数解析器、适配器注入点。
 * 内部 store / flow 节点组件不对外暴露，避免宿主反向耦合。
 */
export { default as RobotEditor } from './src/components/editor/RobotEditor.vue'
export { default as FlowEditor } from './src/components/editor/FlowEditor.vue'
export { default as ReadOnlyBanner } from './src/components/editor/ReadOnlyBanner.vue'

export {
  configureEditorAdapter,
  resetEditorAdapter,
  editorAdapter,
  setEditorScope,
  resetEditorScope,
  editorScope,
} from './src/api/context'
export type { EditorAdapter, KeywordRecord } from './src/api/context'

export * from './src/components/editor/robotTextIO'

/** 编辑器语言包（zh-CN / en-US），宿主在 createI18n 时顶层展开合并 */
export { editorMessages } from './src/locales'
export type { EditorMessages } from './src/locales'
