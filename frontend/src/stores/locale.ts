import { defineStore } from 'pinia'
import { readStoredLocale, setI18nLocale, type AppLocale } from '@/locales'

/** 语言状态：切换后立即改动界面文案，并落 localStorage（FR-v0.1-10） */
export const useLocaleStore = defineStore('locale', {
  state: () => ({
    locale: readStoredLocale() as AppLocale,
  }),
  actions: {
    setLocale(next: AppLocale) {
      this.locale = next
      setI18nLocale(next)
    },
  },
})
