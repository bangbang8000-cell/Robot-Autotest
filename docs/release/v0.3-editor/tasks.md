# v0.3 · EDT-07 / EDT-08 任务清单（tasks）

**对应规格** `spec.md` ｜ **执行约定** 一次最多并行 2 个任务包；不得 git commit；状态只改到 `待验`

---

## 阶段 A · EDT-07

| # | 任务 | 产出文件 | 验证 | 状态 |
|---:|---|---|---|---|
| A1 | 品牌扫描器 + 白名单 | `scripts/brand-scan.sh`、`brand-allow.txt` | `bash scripts/brand-scan.sh` 可跑、能分别报产品面/归属面 | ✔ |
| A2 | 清理 4 处真残留 + 2 个 localStorage 键 | `paletteView.ts`、`recorder.types.ts`、`robotLanguage.ts`、`RobotEditor.vue` | §1.2 表格逐条对照；`grep` 不再命中 | ✔ |
| A3 | 语言包生成器 | `packages/editor/tools/extract-locales.mts` | `npx vite-node tools/extract-locales.mts` 打印键数且键集合断言通过 | ✔ |
| A4 | 生成 zh-CN / en-US 语言包 | `packages/editor/src/locales/{zh-CN,en-US,index}.ts` | 两文件键集合相等；`common` 已落为 `editor.common` | ✔ |
| A5 | 15 个 `common.*` 调用点改名 `editor.common.*` | `SelectorPicker.vue`、`FlowEditor.vue`、`RobotEditor.vue` | `grep "t('common\."` 在 `packages/editor/src` 为 0 | ✔ |
| A6 | 导出消息并接宿主 i18n | `packages/editor/index.ts`、`frontend/src/locales/index.ts` | 宿主 zh-CN 下编辑器显示中文 | ✔ |
| A7 | 新增 i18n 用例 4 条 | `packages/editor/tests/i18n-locales.spec.ts` | `npx vitest run` 全绿 | ✔ |
| A8 | 重建产物并落证据 | `edt07-*.log` ×5 | 产品面含 `dist` 扫描 0 命中 | ✔ |
| A9 | WBS #27 置 `待验` | `docs/plan/WBS-STATUS.md` | 证据列可贴命令 | ✔ |

**A2 与 A3 可并行**（互不依赖）；A4 依赖 A3；A5 依赖 A4（键名已定）。

## 阶段 B · EDT-08

| # | 任务 | 产出文件 | 验证 | 状态 |
|---:|---|---|---|---|
| B1 | 根 `LICENSE`（Apache-2.0，复制原文） | `LICENSE` | sha256 与 `packages/editor/LICENSE-roboscope` 相等 | ✔ |
| B2 | 根 `LICENSE-roboscope` | `LICENSE-roboscope` | 与包内文件字节一致 | ✔ |
| B3 | 根 `NOTICE` | `NOTICE` | 含四要素（来源/版本、Apache-2.0、修改清单、商标） | ✔ |
| B4 | 更新包内 NOTICE 检查单 | `packages/editor/NOTICE.md` | 两项未完成项改为已完成并注证据 | ✔ |
| B5 | 发布检查单（留人签栏） | `docs/release/v0.3-editor/EDT-08-发布检查单.md` | 四件事 + 命令 + 结果 + 签署栏 | ✔ |
| B6 | 文件头枚举 + 校验和证据 | `docs/evidence/v0.3/edt08-license-check.log` | 20 个文件可枚举 | ✔ |
| B7 | WBS #28 置 `待验（待人工签署）` | `docs/plan/WBS-STATUS.md` | 汇总数字同步 | ✔ |

B1~B4 可并行；B6 依赖 B1~B4；B7 最后。

## 收尾

| # | 任务 | 验证 | 状态 |
|---:|---|---|---|
| C1 | 全量回归：`mvn test`（64）+ `vitest run`（215）+ `frontend build/lint` | 三处 exit 0 | ✔ |
| C2 | WBS 汇总行更新（未开始 26→24 / 待验 29→31） | 数字自洽 | ✔ |
| C3 | 汇报：交付物、命令、偏差、待人工签署项 | 中文 | ✔ |

---

## 执行偏差记录（相对 `spec.md`）

| # | 偏差 | 原因 | 影响 |
|---|------|------|------|
| D1 | 宿主不再从 `@robot-autotest/editor` 导入语言包，改为新别名 `@robot-autotest/editor-locales` → `packages/editor/src/locales/index.ts` | 首版直接把 `editorMessages` 挂在主入口，实测把整个编辑器组件树拉进首屏分包（`index` 1,070 kB）；改后回到 `index` 216.67 kB / `EditorView` 857.76 kB | 无，构建与类型均通过 |
| D2 | 上游 `zh.ts` 有 21 条成句英文缺译，生成器内置 `ZH_GAP_FIXES` 补译 | 上游中文包不完整，不补则 FR-v0.3-24「无英文硬编码文案残留」不成立 | 译文由本项目撰写，需人眼校一遍 |
| D3 | 语言包另加了 `BRAND_REWRITES`（3 条文案把上游产品名当主语） | 这些文案在上游中文包里已翻译，但仍含 `RoboScope`，属真残留 | 文案含义不变 |
| D4 | 品牌扫描把 `docs/**` 计入「归属面」而非产品面 | 内部规划/证据文档必须如实引用上游来源，逐行白名单不可维护 | 报告口径：产品面 0 命中，归属面 166 处逐类登记 |
