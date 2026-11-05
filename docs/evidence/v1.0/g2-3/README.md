# G2-3 / EXEC-15 执行安全级闸门 —— 证据（2026-11-05）

对应 ADR-0013《执行安全分级与开发期禁跑令》、WBS #96 `EXEC-15`、G2 现场验收第 3 项。

## 1. 做了什么（一句话）

给 `test_case` 加 `safety_level`（缺省 `LX`），在执行入口**两处**逐条判级，
`L2` 与 `LX`（LX 按 L2 对待）**整单拒绝**（403 `SAFETY_LEVEL_BLOCKED`），
并补一个批量 `setSafetyLevel` 作为人工补判通道。

## 2. 复现命令与读数

| 门禁 | 命令 | 读数 | 日志 |
|---|---|---|---|
| 后端单测 | `cd backend && mvn -B test` | `Tests run: 549, Failures: 0, Errors: 0, Skipped: 0` · BUILD SUCCESS 43.3s | `backend-test.log` |
| 后端集成 | `bash artifacts/run_it.sh` | `Tests run: 30, Failures: 0, Errors: 0, Skipped: 0` | `backend-it.log` |
| 前端单测 | `cd frontend && npm run test -- --run` | `Test Files 26 passed (26)` / `Tests 185 passed (185)` | `frontend-test.log` |
| 前端构建 | `cd frontend && npm run build` | `✓ built in 10.87s`，仅 chunk 体积告警 | `frontend-build.log` |
| 仓库门禁 | `bash scripts/lint.sh` | EXIT=0（含 `check_wbs_consistency` / secret-scan / 字体子集 1021 字 · 177826 B / check_i18n） | `lint.log` |
| 发版文档 | `python3 tools/check_release_docs.py` | 0 FAIL | — |

## 3. 新增/改动的断言（可点名的）

- 后端 `CaseRunServiceTest`
  - `L2 被拦截`：`SAFETY_LEVEL_BLOCKED` + 消息含 `DESTRUCT-001(L2)` + 派发器未被调用
  - `LX 被拦截补判后放行`：LX 先拒，改 `L1` 后成功跑出 `List.of("PERF-001")`
- 后端 `TestSetRunServiceTest`
  - `集合含 L2 / LX 用例：整单 403，一条都不跑`（消息含 `PERF-SET`、`DESTRUCT-001(L2)`、`UNKNOWN-001(LX)`）
- 后端 `CaseBatchIT`
  - `setSafetyLevel` 落库为 `L2`；非法值 `L9` → 400 `INVALID_ARGUMENT` 且消息含「安全级」
- 后端 `CaseImportIT`
  - 台账无安全级列时，preview 全部行 `safetyLevel == "LX"`
- 后端 `MigrationFileTest`
  - `V7_5` 列 + `DEFAULT 'LX'` + down 的 `DROP INDEX` / `DROP COLUMN`
- 前端 `statusLabels.spec`
  - 色档 `L2=fail / LX=warn / L1=ok / L0=neutral`
  - 拦截口径 `L2/LX=true`、`L0/L1=false`
  - 空值 / 未定义按 `LX`（`blocksExecution(null|undefined|'') === true`，不允许静默放行）
- 前端 `CasesView.spec`
  - 选中 `L2` 行 → 「执行所选」`disabled` 且页面出现 `cases.run.blocked` 原因（含 `DESTRUCT-001`）

## 4. 口径留痕

- 拦截集合是 `{L2, LX}`，不是只有 `L2`：ADR-0013 明确「LX 在人工补判前一律按 L2 对待」。
- 台账 226 条没有安全级列 → 导入后全是 `LX`（不可执行），必须人工用 `setSafetyLevel` 补判；
  这是 ADR 想要的默认态，不是导入 bug。
- 整单拒绝而非跳过坏用例：部分执行会让人误以为整套都跑过了。
- 前端禁用只覆盖「当前页可见」的选中项；跨页选中的级差由后端兜底。

## 5. 复跑注意

`V7_5` 是新迁移，跑 IT 前必须先迁 dev/it 两库，否则 `CaseBatchIT` / `CaseImportIT` 会因
`Unknown column 'safety_level'` 报红：

```bash
set -a; . ~/.config/robot-autotest/env; set +a
bash scripts/db-migrate.sh
bash artifacts/run_it.sh
```

## 6. 残余（未做，不得据此点「已完成」）

1. 现场真实触发一次 `L2` 用例并留存 403 拒绝响应（本轮只有单测 + IT 级证据）。
2. 跨页选中的安全级前端不可见（需新增预检接口才能做准）。
3. 台账 226 条的安全级补判仍需人工逐条完成。
