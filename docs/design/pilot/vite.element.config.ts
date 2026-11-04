import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 每个候选独立入口、独立产物，避免两个库互相污染 tree-shaking 与体积测量。
export default defineConfig({
  plugins: [vue()],
  build: {
    outDir: 'dist/element',
    emptyOutDir: true,
    rollupOptions: { input: 'pilot-element.html' },
  },
})
