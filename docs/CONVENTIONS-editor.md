# 编辑器包编码约定（CONVENTIONS-editor）

**文档编号** RA-CONV-2026-003 ｜ **适用** `packages/editor/` ｜ **版本** v0.3

> 本文只写「编辑器包内部代码怎么写」。宿主如何接入见 `docs/CONVENTIONS-frontend.md`，
> 三视图的需求边界见 `docs/release/v0.3-editor/`。
> 本包衍生自 `viadee/roboscope` v0.16.1（Apache-2.0），分发时须保留 `LICENSE` 并标注修改。

## 1. 目录职责

```
packages/editor/
├── index.ts              # 唯一公共出口，宿主只从这里 import
├── src/
│   ├── api/              # 宿主适配器与编辑器自身的 HTTP 封装
│   ├── components/
│   │   ├── editor/       # 文本视图、流程视图及其节点
│   │   ├── debug/        # 调试面板、前置条件弹窗
│   │   ├── recorder/     # 录制选择器
│   │   └── ui/           # 包内基础控件（BaseButton / BaseModal）
│   ├── composables/      # 组合式逻辑（特性开关、关键词签名、录制旁路）
│   ├── stores/           # 包内 Pinia store，不对外导出
│   ├── types/            # 领域类型与 API 类型
│   ├── utils/            # 纯函数（选择器质量、环境变量、错误处理等）
│   └── locales/          # zh-CN / en-US 语言包
```

- 宿主**只能**从 `index.ts` 导入；禁止宿主深链 `src/` 内部文件。
- 包内不得 import 宿主 `frontend/src/**`，避免反向耦合。
- 新增对外能力先改 `index.ts`，并在 `docs/release/v0.3-editor/` 记录契约。

## 2. 三视图单一真源

- 文本视图（`RobotEditor.vue`）、流程视图（`FlowEditor.vue`）、录制器共用
  `components/editor/flow/flowConverter.ts` 做「文本 ⇄ 流程」转换。
- **禁止**在任一视图里另写一套解析/生成逻辑；转换缺陷只改 `flowConverter.ts` 并补单测。
- 纯函数（`robotTextIO.ts`、`keywordArgs.ts`、`resourcePath.ts`）不依赖组件实例，可直接单测。

## 3. 宿主适配（依赖注入）

- 编辑器不直连后端：HTTP 能力经 `configureEditorAdapter(...)` 注入，见 `src/api/context.ts`。
- 新增后端调用时：先在 `EditorAdapter` 接口加方法，再在宿主实现里接线；包内不留 `fetch` 直调。
- 作用域（项目 / 用户）经 `setEditorScope(...)` 注入，不在包内读取 localStorage。
- 适配器与作用域都必须在宿主卸载时 `resetEditorAdapter()` / `resetEditorScope()`。

## 4. TypeScript

- 严格模式；不用 `any`，未知结构先 `unknown` 再收窄。
- 跨组件共享的类型放 `src/types/`；API 请求/响应类型放 `src/types/api.types.ts`。
- 后端字段名即前端字段名，不另造映射层；可选字段用 `?` 或 `| null`，与后端可空语义一致。

## 5. 组件

- 单文件组件顺序 `<script setup lang="ts">` → `<template>` → `<style scoped>`；不写 Options API。
- props / emits 显式类型；组件不读宿主 store。
- 节点类组件（`KeywordNode.vue` / `ControlNode.vue` / `StartEndNode.vue`）只负责渲染与事件，
  增删改排序逻辑放 `flowConverter.ts` 或 `reorderDrag.ts`。
- 破坏性操作先二次确认；提交中禁用按钮防重复。

## 6. 状态与特性开关

- 包内状态只进 `src/stores/`（debug / explorer / repos / environments / auth / ui），宿主不得直接读写。
- 能力开关统一走 `useFeatureFlags.ts`；不得用 `import.meta.env` 在组件里散点判断。
- 只读态由 `ReadOnlyBanner.vue` 统一提示，禁止各视图各写一套文案。

## 7. i18n

- 语言包经 `editorMessages` 导出，宿主在 `createI18n` 时展开合并。
- `zh-CN.ts` 与 `en-US.ts` **键结构必须完全一致**，新增键两边同时加。
- 键按功能区前缀：`editor.*` / `debug.*` / `recorder.*`；带参数用命名插值 `{name}`，不拼字符串。
- 面向用户的文案禁止硬编码中文或英文。

## 8. 样式

- 组件样式默认 `scoped`；颜色/圆角/间距优先用宿主 `:root` 变量
  （`--bg` / `--panel` / `--text` / `--muted` / `--accent` / `--ok` / `--fail`），并为独立运行给回退值。
- 不写全局选择器；确需覆盖第三方（CodeMirror / Vue Flow）时限定在组件根节点下。
- 不写内联 `style` 做主题；布局用 flex / grid。

## 9. 安全

- 渲染用户内容（报告消息、关键词文档）必须经 `dompurify` 清洗，禁止 `v-html` 直出原始串。
- 不在控制台打印令牌、请求头、凭据；调试面板展示 `configured` 布尔值，不显示密文。
- 录制器生成的选择器必须经 `selectorQuality.ts` 评估后再落库。

## 10. 自检

```bash
cd packages/editor
npm run type-check   # vue-tsc --noEmit
npm run build        # vue-tsc + vite build（产物 dist/editor.js、dist/editor.css）
npm run test         # vitest run
```

- 三者必须 exit 0 才能开 PR；日志落 `docs/evidence/<版本>/`。
- 改动 `index.ts` 的导出面时，必须同步跑一次宿主 `frontend` 的 `npm run build`，确认别名接入未断。
