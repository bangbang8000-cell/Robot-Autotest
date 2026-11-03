import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import { resolve } from 'node:path'

/**
 * `packages/editor` 独立构建配置。
 * 只做库构建（不产 index.html），用于证明闭包可独立编译，并被前端按源码引用。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    lib: {
      entry: resolve(__dirname, 'index.ts'),
      formats: ['es'],
      fileName: () => 'editor.js',
    },
    rollupOptions: {
      external: [
        'vue',
        'vue-i18n',
        'pinia',
        '@codemirror/commands',
        '@codemirror/language',
        '@codemirror/state',
        '@codemirror/view',
        '@lezer/highlight',
        '@vue-flow/core',
        '@vue-flow/background',
        '@vue-flow/controls',
        '@vue-flow/minimap',
        'dompurify',
      ],
      output: {
        globals: {},
        assetFileNames: 'editor.[ext]',
      },
    },
    sourcemap: true,
    emptyOutDir: true,
  },
})
