import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发期把 /api 代理到本地后端（:8081），生产由同域静态服务承载，前端不直连任何外部服务（N13）
// 编辑器包以构建产物接入：宿主只认 `dist/editor.js` 的公共出口，源码里的 `@/` 别名不会串味。
const editorDist = fileURLToPath(new URL('../packages/editor/dist/editor.js', import.meta.url))
const editorCss = fileURLToPath(new URL('../packages/editor/dist/editor.css', import.meta.url))
// 语言包单独走源码入口：只带入两个纯对象，避免把整个编辑器组件树拉进首屏分包（EDT-07）
const editorLocales = fileURLToPath(
  new URL('../packages/editor/src/locales/index.ts', import.meta.url),
)

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: [
      { find: /^@robot-autotest\/editor$/, replacement: editorDist },
      { find: '@robot-autotest/editor-css', replacement: editorCss },
      { find: '@robot-autotest/editor-locales', replacement: editorLocales },
      { find: '@', replacement: fileURLToPath(new URL('./src', import.meta.url)) },
    ],
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true,
      },
    },
  },
})
