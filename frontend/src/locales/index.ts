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

export const i18n = createI18n({
  legacy: false,
  locale: readStoredLocale(),
  fallbackLocale: 'zh-CN',
  messages: {
    // 编辑器语言包与平台键无重名，顶层展开即可（EDT-07）
    'zh-CN': { ...zhCN, ...editorMessages['zh-CN'] },
    'en-US': { ...enUS, ...editorMessages['en-US'] },
  },
})

export function setI18nLocale(locale: AppLocale): void {
  i18n.global.locale.value = locale
  localStorage.setItem('locale', locale)
  document.documentElement.lang = locale
}
