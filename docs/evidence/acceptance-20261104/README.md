# 按七版 DEV-PLAN「完成判定」的逐项实跑（2026-11-04）

**对应**：`WBS #79 / WS-06`（验收守门接线与测试环境整改，状态 `待验`）
**触发**：大师「参考 PRD / 开发计划 / 测试计划 继续开发」。
**边界**：只跑静态 / 单测 / IT / 解析 / 构建 / 守门类；**不触发真实 DUT 执行**
（ADR-0013 禁跑令）、不 `POST /api/runs`、不跑 `bench-4way.sh` / `smoke-2node.sh` /
`stability-24h.sh`、不改任何 `待验 → 已完成` 状态。

---

## 1. 最终结果：19 项全绿

逐项退出码见 `SUMMARY.tsv`（三列：任务名 / exit / 日志路径）。原始日志全部同目录留存。

| 层 | 项目 | 结果 |
|---|---|---|
| 后端 | `mvn -B test` | **476/476**，Failures 0（`be-unit.log`） |
| 后端 | `DB_NAME=robot_it mvn -B -Pit test` | **25/25**（`CaseBatchIT` 3、`CaseImportIT` 1、`SchemaIT` 7 等） |
| 前端 | `npm run test` / `lint` / `build` | 21/21、eslint 0、vue-tsc + build 0 |
| 编辑器包 | `npm run test` / `build` | **215/215**（修复前 209/215）、build 0 |
| 静态守门 | `check_colors` / `check_contrast` / `check_i18n` / `check_states` / `check_tokens` | 5 项全 0 |
| 静态守门 | `check_import_header.py <226 条台账.xlsx>` | 0；列命中 **6/14**，与 WBS「台账 4 列命令类信息确认丢失」口径一致 |
| 闭包 | `closure.mjs packages/editor/index.ts --root packages/editor/src --json` | 0 |
| 仓库级 | `lint.sh` / `check_release_docs.py` / `brand-scan.sh` | 0 / **FAIL 0 WARN 0（7 版 IDLE）** / 0 |

## 2. 首次实跑的 4 项红灯及修复（这才是本轮真正的产出）

| 红灯 | 根因 | 修复 | 验证 |
|---|---|---|---|
| `editor-test` exit 1（6/215 失败） | Node 26 + vitest 3.2.7(jsdom) 下 `globalThis.localStorage` 未注入，`ui.store.ts` 在 store 初始化即读它 | `packages/editor/tests/setup.ts` **仅在缺失时**补内存版 `Storage`；`vitest.config.ts` 挂 `setupFiles`。**不动上游 19 条断言** | 215/215 |
| `check_colors` exit 1（3 处） | `.btn.danger` 的 hover/active 用 `color-mix(in srgb, var(--fail) 88%, #000)`，守门把中性混合基色当硬编码色值 | 提升为 semantic token `--fail-hover/--fail-active`，`theme.css` 亮暗两套各一份，组件侧只引用 var | `check_colors` / `check_tokens` / `check_contrast` 全 0 |
| `check_import_header` exit 1 | 环境缺 `openpyxl`，且原报错只有裸 `ModuleNotFoundError` | 补 `tools/requirements.txt`；脚本缺依赖时明确打印安装命令并 **exit 2，不以降级放行** | 带参 0；无参 `import-header-noarg.log` 预期非零且明确报错 |
| `tool-closure` exit 2 | **验收脚本调用漏了入口参数**，不是项目缺陷 | 传 `packages/editor/index.ts --root packages/editor/src --json` | 0 |

## 3. 比红灯更重要的发现：守门写了却没接线

原 `scripts/lint.sh` 只做「密文扫描 + 前端 eslint + 后端编译」，**从未调用**
`tools/check_*.py` 六个守门。也就是说此前多次汇报的「`lint.sh` PASS = CI 三绿」，
并不包含颜色 / 对比度 / i18n / 交互态 / token / 导入表头任一判据 —— 属于**假绿**。

本轮把六个守门统一接进 `lint.sh`（`set -euo pipefail` 下逐项执行，任一 FAIL 即中断），
并把接线后暴露的三处问题一并修掉，最终 `bash scripts/lint.sh` exit 0。
证据：`lint-all.log`（含六守门逐行输出）。

## 4. 两处「守门自咬」也留了痕

- 试点 `check_pilot.py` 初版把 `--el-*` 这些**输出变量**当成「输入 token」去 `theme.css` 找定义，
  误报 15 处；收窄为只检查输入侧后 PASS。详见 `docs/design/UX-04-两页试点对比报告-20261104.md` §2.2。
- 本轮 WBS 文档补丁用**行号**定位 `#74 / #78`，但前面插入一行后行号漂移，
  误把 `EXEC-01` 换成 UX-04 内容、`#79` 插到 `#77` 后。已用 `git show HEAD:` 取原文修回，
  并加「编号 1~79 连续且无重复」断言校验通过。**教训：多步替换必须按内容定位，不能用行号。**

## 5. 未纳入本批（说清楚别当成已验）

- 真实环境实跑（EXE-07 / EXE-08 / 打印链路 / 回滚 RTO）、F2 八个 `待现场` 包、F1-2 需人裁决项。
- G6 的 7 天墙上时钟仍按「G5 通过当天起算」，dev 调度器只提前挂观测，不计成功率。
- **七版 ①/②/⑥ 流程产物仍缺**（机检 7 版 `IDLE`）：补齐会激活「每条 TC 要有结果」判据，
  而 413 条 TC 尚无逐条实测台账；且事后补 ①/② 按流程 §9 不能追溯洗白既有 PRD。
  理由已写进 `docs/plan/WBS-STATUS.md` 23:00 补记，留待大师裁决后单开一轮。
