# EXEC-02 勾选台账用例直接执行（v0.4 正式包 WBS #83）

- 日期：2026-11-05
- 权威工作副本：WSL `~/robot-autotest`（基线 `a996a54`，本轮改动**未提交**）
- 定位：**v0.4 执行版补充包（WBS #83，2026-11-05 正式并入）**。原为候选包外提前实现，现已登记进
  `docs/release/v0.4-execution/DEV-PLAN.md` §1.3 与 `docs/plan/WBS-STATUS.md` #83。

## 1. 交付内容

| 层 | 文件 | 改动 |
|---|---|---|
| 绑定解析 | `application/execution/CaseScriptResolver.java` | 新增：扫 `robot_file.content`，按 ADR-0005 §3 的 `[Tags] case:<case_code>` 解析，测试名等于编号兜底；支持中英文节区名、注释、缩进 |
| 执行服务 | `application/execution/CaseRunService.java` | 新增：按用例 id 取编号 → 解析绑定 → 套件去重 + 测试名去重 → 未绑整单 400 列编号 → 走 `QuickRunService` 派发，`source=cases` |
| 执行器 | `application/execution/RunService.java`、`RunExecutionService.java`、`PabotCommandBuilder.java` | `CreateRunCommand` / `ShardSpec` 增 `testNames`，非空时逐条追加 `--test <name>`；计划/CI 路径传 `null` 不受影响 |
| API | `api/execution/RunController.java` | 新增 `POST /api/execution/cases/run?projectId=`，只收用例 id 与执行参数 |
| 用例集复用 | `application/execution/TestSetRunService.java` | 改为复用同一解析器（标签 + 同名兜底），删除原有「测试名 == 编号」私有解析，执行粒度仍是套件（EXEC-01 口径不变） |
| 前端 | `frontend/src/api/execution.ts`、`views/CasesView.vue`、`locales/{zh-CN,en-US}.ts` | `runCases` API；批量条新增「执行所选」，展开内联表单（环境 / 并发 / 超时 / 附加标签）→ 提交 → 成功提示运行号并清空勾选，失败原文展示；i18n 中英双份 |

## 2. 绑定规则（ADR-0005 §3）

1. 脚本测试的 `[Tags]` 中出现 `case:<case_code>` → **优先命中**（同一测试可绑多个编号，一条编号也可绑多个测试）；
2. 测试名与用例编号完全一致 → **兜底**（沿用 EXEC-01 起的报告覆盖口径，保证已有脚本零改动可跑）；
3. 所选用例中**任何一条**解析不到 → 整单 400，消息列出全部未绑定编号与补救指引，不静默跳过；
4. 绑定只读 `robot_file.content`，**不读磁盘工作区**；与执行器的 `execution.workspace-root` 可能不一致，
   命中但执行期找不到文件时会由预检/pabot 明确失败（与本包无关的既有偏差）。

## 3. 执行粒度（与 EXEC-01 的差异要看清）

| 触发方式 | 粒度 | 说明 |
|---|---|---|
| 套件路径（EXEC-01） | 套件文件 | 同一套件内所有测试都会被跑 |
| 用例集（EXEC-01 起；**EXEC-03 起改为测试级**） | 测试 | 集合内绑定的测试名以 `--test` 透传 |
| 勾选台账用例（EXEC-02） | 测试 | 命中测试名以 `--test` 透传给 pabot |

`--test` 的实跑效果**开发期未验证**（ADR-0013 禁跑令），留给验收人现场确认；
pabot 与 `--include` 同时存在时按 RF 的过滤语义叠加（AND），当前允许同时传，属预期。

## 4. 门禁实测

| 命令 | 结果 | 日志 |
|---|---|---|
| `cd backend && mvn test` | **496 tests, 0 failures**（+8） | `backend-test.log` |
| `DB_NAME=robot_it mvn -Pit test` | **28/28, BUILD SUCCESS** | `backend-it.log` |
| `cd frontend && npx vitest run` | **23 files / 155 tests passed**（+2） | `frontend-test.log` |
| `npx vue-tsc --noEmit` | 退出 0 | `frontend-tsc.log` |
| `npm run build` | 退出 0（dist 9.36s） | `frontend-build.log` |
| `bash scripts/lint.sh` | `[lint] PASS`（i18n 对等 + WBS 一致 + 架构层规则） | `lint.log` |
| `python3 tools/check_release_docs.py` | 7 个版本 / FAIL 0 / WARN 0 | `release-docs-gate.log` |

## 5. 新增/修改测试

| 测试 | 覆盖 |
|---|---|
| `CaseScriptResolverTest`（新增 2） | 中英节区/注释/缩进/`[Tags]` 大小写；标签命中、测试名兜底、未命中不返回 |
| `CaseRunServiceTest`（新增 4） | 全部绑定→套件去重 + `--test` 名单 + `source=cases`；未绑整单 400 列编号且不触发；用例不存在 404、空选 400；访客 403 |
| `PabotCommandBuilderTest`（+1） | `testNames` 非空时逐条 `--test`，空白项跳过 |
| `RunControllerTest`（+1） | `/cases/run` 参数透传、未绑定 400、越权 403 |
| `TestSetRunServiceTest`（改） | 构造函数改为注入解析器；删私有解析单测，补「标签绑定」用例 |
| `CasesView.spec.ts`（+2） | 勾选→展开表单→提交只传 id 与参数并提示运行号；后端 400 原文展示、勾选不丢 |

## 6. 偏差与风险（需在 v0.4 立项时对齐）

1. **`--test` 未实跑**：开发期无 DUT，按 RF/pabot 约定透传，实跑验证留验收人现场。
2. **DB 绑定与磁盘工作区不同源**：`robot_file` 存 DB（ADR-0005），执行器走磁盘 `execution.workspace-root`；
   绑定命中不代表执行期文件存在。彻底统一需要「执行工作区配置化 + 落盘」另开包。
3. **用例集执行仍是套件粒度**：本包只把解析器统一，没有把用例集执行也改成 `--test`，
   避免一次改两条执行路径的语义；如需用例集也按测试级执行，下一小步只需在 `TestSetRunService` 一并传 `testNames`。
4. **已并入 v0.4 包表（2026-11-05）**：正式登记 WBS **#83**，汇总 82→83、待验 68→69，v0.4 分箱包数 9→10；原「只记补记、不改汇总」口径作废。
