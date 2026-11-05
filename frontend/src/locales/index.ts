import { createI18n } from 'vue-i18n'
import { editorMessages } from '@robot-autotest/editor-locales'
import zhCN from './zh-CN'
import enUS from './en-US'

export type AppLocale = 'zh-CN' | 'en-US'

/** 语言选择落在 localStorage，刷新后保持（FR-v0.1-10 / TC-v0.1-042） */
export function readStoredLocale(): AppLocale {
  const saved = localStorage.getItem('locale')
  return saved === 'en-US' ? 'en-US' : 'zh-CN'
}

/*
 * 编辑器语言包的命名空间是 `robotEditor / flowEditor / debug / recorder / editor`，
 * 前四个与平台无重名，但 `editor` 两边都写了：编辑器包放的是编辑器内部文案（`editor.common.*`），
 * 平台这边放的是宿主页标题 / 角色 / 保存提示（`editor.titleEditor` 等）。
 * 顶层展开是浅合并，后一份会把前一份整段覆盖掉——UX-06 走查实测表现为编辑器页直接显示
 * `editor.titleEditor`、`editor.missingRoute` 原始键名。这里对 `editor` 段做字段级合并。
 */
// 生成的编辑器语言包整体标注为 `Record<string, unknown>`，取 `.editor` 后是 unknown，不能直接展开
type MessageTree = Record<string, unknown>
const zhEditor = editorMessages['zh-CN'].editor as MessageTree
const enEditor = editorMessages['en-US'].editor as MessageTree

const zhMessages = {
  ...zhCN,
  ...editorMessages['zh-CN'],
  editor: { ...zhCN.editor, ...zhEditor },
}
const enMessages = {
  ...enUS,
  ...editorMessages['en-US'],
  editor: { ...enUS.editor, ...enEditor },
}

export const i18n = createI18n({
  legacy: false,
  locale: readStoredLocale(),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhMessages,
    'en-US': enMessages,
  },
})

export function setI18nLocale(locale: AppLocale): void {
  i18n.global.locale.value = locale
  localStorage.setItem('locale', locale)
  document.documentElement.lang = locale
}

/*
 * 首屏就要把 <html lang> 对齐到实际语言。
 * UX-06 走查实测：localStorage 里存的是 en-US 时，界面文案已经是英文（读 readStoredLocale），
 * 但 index.html 里写死的 lang="zh-CN" 只有在用户手动切一次语言后才被改掉——
 * 屏幕阅读器与浏览器断词会按中文规则处理英文页面。这里补上启动时的一次同步。
 */
document.documentElement.lang = i18n.global.locale.value
