import process from 'node:process'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 单测环境（UX-02 引入 vitest）：happy-dom 提供 localStorage / document（locales/index.ts
// 在模块顶层就读 localStorage，node 纯环境会炸）。用例文件放在 src/**/*.spec.ts。
const test = {
  environment: 'happy-dom',
  include: ['src/**/*.spec.ts'],
  setupFiles: ['src/test-setup.ts'],
}

// 开发期把 /api 代理到本地后端（默认 :8081），生产由同域静态服务承载，前端不直连任何外部服务（N13）
//
// 两个值都允许用环境变量覆盖，因为一台机器上跑多个后端实例是常态：
// 8081 被别的系统占用时用 `VITE_API_TARGET=http://127.0.0.1:8082 npm run dev` 即可，
// 不必改这份文件（改了容易误提交，别人 clone 下来就连错后端）。
const API_TARGET = process.env.VITE_API_TARGET ?? 'http://127.0.0.1:8081'
const DEV_PORT = Number(process.env.VITE_DEV_PORT ?? 5173)
// 编辑器包以构建产物接入：宿主只认 `dist/editor.js` 的公共出口，源码里的 `@/` 别名不会串味。
const editorDist = fileURLToPath(new URL('../packages/editor/dist/editor.js', import.meta.url))
const editorCss = fileURLToPath(new URL('../packages/editor/dist/editor.css', import.meta.url))
// 语言包单独走源码入口：只带入两个纯对象，避免把整个编辑器组件树拉进首屏分包（EDT-07）
const editorLocales = fileURLToPath(
  new URL('../packages/editor/src/locales/index.ts', import.meta.url),
)

export default defineConfig({
  test,
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
    port: DEV_PORT,
    proxy: {
      '/api': {
        target: API_TARGET,
        changeOrigin: true,
      },
    },
  },
})
