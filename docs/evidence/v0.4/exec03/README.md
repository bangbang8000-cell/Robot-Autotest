# EXEC-03 用例集测试级执行（v0.4 正式包 WBS #84）

- 定位：**v0.4 执行版补充包（WBS #84，2026-11-05 并入）**。接在 EXEC-02 之后，关闭 EXEC-02 遗留的
  「用例集仍是套件粒度」偏差。
- 结论：用例集触发现在与勾选台账用例一致，**只跑集合内已绑定的测试**（`--test`），
  不再把同一套件里的其它测试一起跑掉。

## 1. 交付内容

| 层 | 文件 | 改动 |
|---|---|---|
| 后端 | `application/execution/TestSetRunService.java` | `Resolution` 增加 `testNames`；解析时按套件去重、按测试名去重；`QuickRunCommand` 传 `resolution.testNames()` 而不是 `null` |
| 后端测试 | `application/execution/TestSetRunServiceTest.java` | 既有 2 个用例补 `testNames` 断言；新增「只跑所选测试」「测试名去重」2 个用例 |
| 前端 | `api/execution.ts` | 仅注释口径更新（套件路径 + 测试名均由后端解析），无行为改动 |

解析仍复用 `CaseScriptResolver`（ADR-0005 §3）：`[Tags] case:<code>` 优先，测试名与编号一致兜底；
任一条未绑定仍整单 400 并列出编号。

## 2. 执行粒度（EXEC-03 后）

| 触发方式 | 粒度 | 说明 |
|---|---|---|
| 套件路径（`POST /execution/runs`、`/quick-run`） | 套件文件 | 未传 `testNames`，整套件执行 |
| 勾选台账用例（EXEC-02） | 测试 | 命中测试名以 `--test` 透传 |
| 用例集（EXEC-01，**EXEC-03 起**） | 测试 | 集合内绑定的测试名以 `--test` 透传 |

`--test` 的实跑效果**开发期未验证**（ADR-0013 禁跑令），留给验收人现场确认；
同一次执行里 `--test` 与 `--include` 按 RF 语义叠加（AND），当前允许同时传，属预期。

## 3. 门禁实测

| 命令 | 结果 | 日志 |
|---|---|---|
| `cd backend && mvn test` | **498 tests, 0 failures**（+2） | `backend-test.log` |
| 加载 `~/.config/robot-autotest/env` 后 `DB_NAME=robot_it mvn -Pit test` | **28/28, BUILD SUCCESS** | `backend-it.log` |
| `python3 tools/check_wbs_consistency.py` | `[PASS] 84 个包 / 未开始 1 / 进行中 0 / 待验 70 / 待现场 0 / 已完成 13` | `wbs-gate.log` |
| `python3 tools/check_release_docs.py` | 7 个版本 / **FAIL 0 / WARN 0** | `release-docs-gate.log` |
| `bash scripts/lint.sh` | **[lint] PASS** | `lint.log` |

前端无行为改动，未重跑 vitest / tsc / build；`lint.sh` 已含 i18n、WBS 一致性与前端静态检查。

## 4. 新增测试

| 用例 | 断言 |
|---|---|
| `只跑所选测试` | 同套件含 `PERF-005` / `PERF-006`，集合只勾 `PERF-005` → `testNames=["PERF-005"]` |
| `测试名去重` | 同一测试用 `case:PERF-007 case:PERF-008` 绑两条用例 → `testNames` 只出现一次 |

## 5. 偏差与残余

1. **`--test` 仍未实跑**：开发期禁跑 DUT（ADR-0013），pabot 过滤语义与真实报告留给验收人现场。
2. **`--test` 是全局过滤**：pabot 按测试名在全套件范围匹配；若两个套件存在同名测试，命中的测试都会跑。
   当前以「测试名唯一」为隐含前提；**EXEC-04 已在请求期补同名冲突 400**（见 `docs/evidence/v0.4/exec04/`）。
3. **空编号用例原被静默跳过**：`TestSetRunService.resolve` 只收集非空 `caseCode`（沿用 EXEC-01 口径）；
   **EXEC-04 已改为 400 并列出 id**。
4. **绑定读 DB、执行读磁盘**：命中但工作区无文件时仍由预检/pabot 失败，沿用 EXEC-02 记录的既有偏差。
