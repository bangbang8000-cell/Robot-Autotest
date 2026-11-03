# frontend

Vue 3 + Vite + TypeScript + Pinia + i18n 的平台自身页面。

```
src/
├── router/     6 个一级路由（总览/资产/用例/执行/报告/管理）
├── stores/     平台状态
├── views/      页面
├── components/ 5 种页面模式组件
├── api/        平台 API client
├── locales/    中文包
└── styles/
```

- 归属：**WS-03**（骨架 + 路由 + i18n），后续由各版本页面填充。
- 构建产物最终打进 Spring Boot 静态资源，单 jar 部署。

```bash
npm ci
npm run dev     # :5173，/api 代理到 http://127.0.0.1:8081
npm run lint    # eslint（flat config）
npm run build   # vue-tsc --noEmit && vite build
```

- v0.1 无单测框架，`npm run build` 的 `vue-tsc --noEmit` 是唯一的类型检查。
- 统一响应体解析与超时在 `src/api/http.ts`，新增接口沿用它，别另起一套 fetch。
