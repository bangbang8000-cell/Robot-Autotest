import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { i18n } from './locales'
// fonts.css 由 tools/subset_font.py 生成：三片 woff2 + unicode-range 分包 + font-display: swap。
// 放在 theme.css 之前，保证首帧解析 @font-face 时语义变量也已就位。
import './styles/fonts.css'
import './styles/theme.css'
import './styles/main.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(i18n)
app.mount('#app')
