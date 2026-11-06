# G2-3 / EXEC-15 执行安全级闸门 —— 证据（2026-11-05）

对应 ADR-0013《执行安全分级与开发期禁跑令》、WBS #96 `EXEC-15`、G2 现场验收第 3 项。

## 1. 做了什么（一句话）

给 `test_case` 加 `safety_level`（缺省 `LX`），在执行入口**两处**逐条判级，
`L2` 与 `LX`（LX 按 L2 对待）**整单拒绝**（403 `SAFETY_LEVEL_BLOCKED`），
并补一个批量 `setSafetyLevel` 作为人工补判通道；前端再用只读预检接口
（`POST /execution/cases/safety-precheck`）把**跨页选中**的 L2/LX 提前列出来（§7）。

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
  - 选中 `L2` 行 → 「执行所选」`disabled` 且页面出现 `cases.run.blocked` 原因（含 `DESTRUCT-001(L2)`）

## 3.1 本机 HTTP 实测（补单测/IT 之上的接口级证据）

单测与 IT 只证明服务层会抛错，不证明**接口真的回 403**。2026-11-05 在本机 dev 后端
（`java -jar` :8081，新 jar 构建于 23:52:04，库 `robot_dev` 已迁到 `V7_5`）用真实 HTTP 请求复验，
原始记录见 [`http-verify-20261105.md`](./http-verify-20261105.md)，复跑脚本同目录 [`http-verify.sh`](./http-verify.sh)。

| 项 | 期望 | 实测 |
|---|---|---|
| `L2` 执行所选 | 403 `SAFETY_LEVEL_BLOCKED` | 403，消息含 `TN-IMP-226(L2)` |
| `LX` 执行所选 | 403（LX 按 L2 对待） | 403，消息含 `TN-IMP-226(LX)` |
| 非法级 `L9` | 400 `INVALID_ARGUMENT` | 400「非法安全级: L9（只允许 L0/L1/L2/LX）」 |
| 批量 `setSafetyLevel` | 200 且落库 | 200，`succeeded=1`，查库确认 `L2` → 还原 `LX` |

这批请求在派发前即被拒，**不需要真实 DUT**，所以能在本机闭环；但它是接口级而非现场级证据，
见 §6 残余第 1 条。

## 4. 口径留痕

- 拦截集合是 `{L2, LX}`，不是只有 `L2`：ADR-0013 明确「LX 在人工补判前一律按 L2 对待」。
- 台账 226 条没有安全级列 → 导入后全是 `LX`（不可执行），必须人工用 `setSafetyLevel` 补判；
  这是 ADR 想要的默认态，不是导入 bug。
- 整单拒绝而非跳过坏用例：部分执行会让人误以为整套都跑过了。
- 前端禁用按**跨页全集**判断：选中一变就调后端预检 `POST /execution/cases/safety-precheck`，
  不在当前页的 L2/LX 也会被列出（2026-11-06 补齐，见 §7）。

## 5. 复跑注意

`V7_5` 是新迁移，跑 IT 前必须先迁 dev/it 两库，否则 `CaseBatchIT` / `CaseImportIT` 会因
`Unknown column 'safety_level'` 报红：

```bash
set -a; . ~/.config/robot-autotest/env; set +a
bash scripts/db-migrate.sh
bash artifacts/run_it.sh
```

## 6. 残余（未做，不得据此点「已完成」）

1. **现场**真实触发一次 `L2` 用例并留存 403 拒绝响应。本机接口级已验（§3.1，请求在派发前即被拒），
   但现场环境的后端版本、库与账号口径仍需各留一份读数。
2. ~~跨页选中的安全级前端不可见（需新增预检接口才能做准）~~ —— 2026-11-06 已补预检接口，见 §7。
3. 台账 226 条的安全级补判仍需人工逐条完成。

## 7. 2026-11-06 补记：跨页选中的安全级可见（残余 2 收口）

**问题**：勾选是跨页的，`CasesView` 的「执行所选」只看得到 `cases.value`（当前页）的安全级，
别的页选了 L2/LX 时按钮仍是可点的，点下去才吃整单 403——能拦住，但用户不知道为什么。

**做法**：不动执行链路，只加一个**只读预检**接口，判级逻辑与执行入口共用同一份代码。

| 层 | 改动 |
|---|---|
| 后端 `CaseRunService` | 把原来内联的判级循环抽成 `collect(projectId, ids)`，`run()` 与新增的 `precheck()` 共用；`precheck` 只取 `EXECUTION:READ`（执行仍是 `EXECUTE`），返回 `SafetyPrecheck(total, allowed, blocked[{caseId, caseCode, safetyLevel}])` |
| 后端 `RunController` | 新增 `POST /api/execution/cases/safety-precheck`（`projectId` 查询参数 + `{caseIds}` 请求体） |
| 前端 `api/execution.ts` | 新增 `SafetyPrecheck` / `BlockedCase` 类型与 `safetyPrecheck(projectId, caseIds)` |
| 前端 `CasesView.vue` | 选中集合变化后 200ms 防抖调预检，用自增序号丢弃迟到响应；`blockedSelected` 以预检结果为准，预检未回/失败时退回「只看当前页」的本地判断；离开页面时清掉定时器 |

**四条边界**：

1. **预检失败不阻塞操作**：请求出错就退回本地判断，真正兜底仍是执行入口的 403，不因预检挂了而让按钮永远禁用。
2. **预检与选中集合必须对得上**：用 `precheckedKey`（选中 id 排序拼接）比对，避免「旧选中集合的预检结论」被套到新集合上。
3. **不看页、只看 id**：请求只带 id 列表，不受分页/筛选影响，跨页选中的 L2/LX 一样能被列出。
4. **预检不写库、不触发执行**：`precheck` 全程只读，单测里断言派发器未被调用。

**门禁（2026-11-06，日志同目录 `*-20261106.log`）**：

| 门禁 | 读数 |
|---|---|
| 后端 `mvn -o test` | `Tests run: 562, Failures: 0, Errors: 0, Skipped: 0` · BUILD SUCCESS |
| 前端 `npx vitest run` | `Tests 200 passed (200)` |
| 前端 `vue-tsc --noEmit` | EXIT=0（0 行输出） |
| 前端 `vite build` | `✓ built in 9.51s` |
| 字体子集 | `1029 字 / 178798 B gzip`，未超 800 KiB |
| `bash scripts/lint.sh` | `[lint] PASS` |
| `check_release_docs.py` | `FAIL 0 项 / WARN 0 项` |
| `check_wbs_consistency.py` | `[PASS] 96 个包 / 待验 83 / 已完成 13` |

**新增断言**：

- 后端 `CaseRunServiceTest`：「预检：跨页选中的 L2 / LX 逐条列出，allowed=false，不触发执行」、
  「预检：全 L1 放行 allowed=true；空选 400」。
- 后端 `RunControllerTest`：「安全级预检：透传 caseIds，返回被拦编号与安全级；空选 400」。
- 前端 `CasesView.spec`：「跨页选中的 L2 不在当前页时，预检结果同样能提前禁用」（当前页只有 L1，
  预检返回 `DESTRUCT-999(L2)`，断言按钮禁用且页面写出该编号）。
