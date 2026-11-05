# frontend

Vue 3 + Vite + TypeScript + Pinia + i18n 的平台自身页面。

```
src/
├── router/     一级路由与子路由
├── stores/     平台状态
├── views/      页面
├── components/ 页面模式组件与通用组件
├── api/        平台 API client
├── locales/    中文 / English 语言包
└── styles/     全局样式与设计 token 消费
```

- 归属：**WS-03**（骨架 + 路由 + i18n）；v1.0 页面已铺满，后续按验收反馈迭代。
- 构建产物最终打进 Spring Boot 静态资源，单 jar 部署。

```bash
npm ci
npm run dev       # :5173，/api 代理到 http://127.0.0.1:8081
npm run lint      # eslint（flat config）
npm run test       # vitest 单次
npm run test:watch # vitest（watch）
npm run build     # vue-tsc --noEmit && vite build
```

- 单测用 Vitest，组件测试与纯函数测试同仓；`npm run build` 的 `vue-tsc --noEmit` 是类型门禁。
- 统一响应体解析与超时在 `src/api/http.ts`，新增接口沿用它，别另起一套 fetch。
- 主题与密度走 `composables/{useTheme,useDensity}.ts`，naive-ui 主题由 `composables/naiveTheme.ts` 从语义 token 派生，禁止另起色板。
