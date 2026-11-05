# EXEC-04 执行前预检（v0.4 正式包 WBS #85）

- 定位：**v0.4 执行版补充包（WBS #85，2026-11-05 并入）**。把「先建 run、再在执行期预检判失败」
  提前到请求期，确定性问题直接 400，不落库、不排队。
- 结论：手工触发现在会先校验**套件路径在工作区存在**、**用例编号非空**、**测试名不跨套件重名**。

## 1. 交付内容

| 层 | 文件 | 改动 |
|---|---|---|
| 后端 | `application/execution/ExecutionRequestPrecheck.java`（新增） | `requireSuitesExist`（workspace-root 下文件/目录存在性）；`requireUnambiguousTestNames`（扫描所选套件，`--test` 同名跨套件即 400） |
| 后端 | `application/execution/QuickRunService.java` | 权限校验后、落库前调用两项预检；所有手工入口（`/runs`、`/quick-run`、`/cases/run`、用例集）统一生效 |
| 后端 | `application/execution/TestSetRunService.java` | 用例编号为空改为 400 并列出用例 id（原为静默跳过） |
| 后端测试 | `ExecutionRequestPrecheckTest.java`（新增 5 用例） | 路径存在/缺失、重名拒绝/唯一放行、空清单不扫重名 |
| 后端测试 | `QuickRunServiceTest` / `TestSetRunServiceTest` | 新增「套件不存在 400 不落库不派发」「同名测试 400」「空编号 400」；测试工作区改为 `@TempDir` 真实文件 |

前端无改动。

## 2. 预检口径

| 检查 | 触发条件 | 结果 |
|---|---|---|
| 套件路径存在性 | 任一路径在 `execution.workspace-root` 下不存在 | 400，消息给工作区与缺失清单 |
| 测试名跨套件重名 | 测试级执行时同一测试名出现在 >1 个所选套件文件 | 400，列出名字与套件（`--test` 是全局过滤，无法区分） |
| 用例编号为空 | 用例集内任一条 `case_code` 为空 | 400，列出 `id` |

预检只读探测，不改工作区；目录路径跳过重名扫描（无法在不枚举整棵树的前提下判定）。
执行期原有预检（`ExecutionPrecheck`：venv/pabot/磁盘/DUT）保持不变，作为第二道闸。

## 3. 门禁实测

| 命令 | 结果 | 日志 |
|---|---|---|
| `cd backend && mvn test` | **506 tests, 0 failures**（+8） | `backend-test.log` |
| 加载 `~/.config/robot-autotest/env` 后 `DB_NAME=robot_it mvn -Pit test` | **28/28, BUILD SUCCESS** | `backend-it.log` |
| `python3 tools/check_wbs_consistency.py` | `[PASS] WBS 自洽：85 个包 / 待验 71 / 已完成 13` | `wbs-gate.log` |
| `python3 tools/check_release_docs.py` | 7 个版本 / **FAIL 0 / WARN 0** | `release-docs-gate.log` |
| `bash scripts/lint.sh` | **[lint] PASS** | `lint.log` |

## 4. 新增测试

| 用例 | 断言 |
|---|---|
| `路径存在放行` | 文件与目录都存在 → 不抛 |
| `路径缺失拒绝` | 消息含工作区、缺失路径 |
| `测试名重名拒绝` | 两个套件同名 → 400，消息含两路径 |
| `测试名唯一放行` / `空清单直接放行` | 唯一或套件级执行不误报 |
| `套件不存在被拒`（QuickRunService） | 400 + `insertCount=0` + 无派发 |
| `同名测试被拒`（QuickRunService） | 400 + 未落库 |
| `空编号被拒`（TestSetRunService） | 400 + 消息含 `id=9` + 未触发 |

## 5. 偏差与残余

1. **工作区必须已同步脚本**：默认 `execution.workspace-root=~/robot-test/infra-monitor`，本地未建该目录时
   手工触发会直接 400。这符合预检目的，但首次联调需先把套件同步到该路径或改配置。
2. **目录路径不查重名**：用例集/勾选用例解析出的都是具体文件，不受影响；手工传目录时跳过。
3. **`--test` 仍未实跑**：ADR-0013 禁跑令，真实过滤效果留给验收人现场。
4. **绑定读 DB、执行读磁盘**：EXEC-04 只在请求期确认磁盘有文件，数据库内容与磁盘内容仍可能不同步；
   彻底消除需要 EXEC-05 的「执行工作区落盘」。
