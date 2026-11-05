# EXEC-12 证据：单用例超时（WBS #93 / FR-v0.4-24）

- 日期：2026-11-05
- 范围：单 run 总超时之外的「单用例超时」，超时后 run 原因可区分（`test_timeout`），按取消归类
- 状态：**待验**
- 约束：开发期未连接真实 DUT（ADR-0013），注入与识别均以本地套件 + 构造产物验证

## 1. 结论先行

Robot Framework **没有全局单用例超时命令行开关**（7.5.1 实测 `robot --help` 无任何 timeout 选项）。
唯一可用的机制是套件级 `*** Settings ***` 的 `Test Timeout`。因此本包的做法是：

1. 执行前把 `Test Timeout    <N>s` 注入**本 run 的工作区副本**（不碰数据库脚本与用户原脚本）；
2. run 结束后从 `output.xml` 识别 RF 的超时文案，把 run 原因记 `test_timeout`。

## 2. 改动

| 项 | 之前 | 现在 |
|---|---|---|
| 配置 | 只有 `runTimeoutSeconds`（run 总超时） | 新增 `execution.test-timeout-seconds`，默认 `1800`，`<=0` 关闭 |
| 注入 | 无 | `TestTimeoutInjector.inject(workDir, suitePaths, seconds)`：`.robot` 套件按需注入，目录递归；已有 `Test Timeout` 的套件保持用户值 |
| 注入时机 | — | `RunExecutionService` 在 `workspaces.materialize` **成功后**才注入；回落到真实 workspace-root 时跳过（那是用户原脚本） |
| 产物识别 | 只校验结构与分片 | `OutputXmlProbe.timedOutTests` 数 `msg` 里的 `Test timeout ... exceeded`；`ArtifactVerifier.testTimedOut` 对外暴露 |
| run 原因 | 总超时 `timeout` | 新增 `test_timeout`（`FailureClassifier` 命中 `timeout` → CANCELED，符合 PRD「超时按取消处理」），并写一行 `[system]` 日志 |
| 成功判定 | `exitCode == 0 && 产物 ok && 分片 ok` | 追加 `&& !testTimedOut` |

改动文件：

- `backend/src/main/java/com/qa/autotest/application/execution/TestTimeoutInjector.java`（新建）
- `.../ExecutionProperties.java`、`.../RunExecutionService.java`、`.../OutputXmlProbe.java`、`.../ArtifactVerifier.java`、`.../PabotOrchestrator.java`
- `backend/src/test/java/com/qa/autotest/application/execution/TestTimeoutInjectorTest.java`（新建，5 条）
- `.../ArtifactVerifierTest.java`（+1 条）

## 3. 用例

`TestTimeoutInjectorTest`：

1. `已有_Settings_段时紧随其后注入` —— 注入行落在 `*** Settings ***` 下一行
2. `没有_Settings_段时新建在文件头` —— 自动补 `*** Settings ***` + `Test Timeout`
3. `用户已写_Test_Timeout_时不覆盖` —— 原 `120s` 保留，返回改写数 0
4. `目录递归只改_robot_文件` —— 嵌套 `.robot` 被改，`notes.txt` 不动
5. `秒数非正时不注入` —— `0` 时静默跳过

`ArtifactVerifierTest`：

6. `识别单用例超时的失败消息` —— 构造 `test > status > msg` 含 `Test timeout 30 seconds exceeded.` 的产物，识别为 1；正常产物 0；文件不存在 false（不反向误判）

## 4. 门禁（日志见同目录）

| 门禁 | 命令 | 结果 | 日志 |
|---|---|---|---|
| 后端单测 | `cd backend && mvn -B test` | **539** 通过（EXEC-11 为 533，+6） | `backend-test.log` |
| 后端集成 | `DB_NAME=robot_it mvn -B -Pit test` | **28** 通过 | `backend-it.log` |
| 前端单测 | `npx vitest run` | **162** 通过（未改动前端） | `frontend-test.log` |
| 类型检查 | `npx vue-tsc --noEmit` | exit 0 | `frontend-tsc.log` |
| 前端构建 | `npx vite build` | exit 0 | `frontend-build.log` |
| 规范 | `bash scripts/lint.sh` | PASS | `lint.log` |
| WBS 自洽 | `python3 tools/check_wbs_consistency.py` | PASS `93 个包 / 1 / 0 / 79 / 0 / 13` | `wbs.log` |
| 发布文档机检 | `python3 tools/check_release_docs.py` | FAIL 0 / WARN 0 | `release-docs.log` |
| 工作区 | `git status --short` | 仅工作区改动，**无提交** | `git-status.log` |

## 5. 已知缺口

1. **`.resource` 资源文件不注入**：RF 的 `Test Timeout` 只对套件生效，资源文件里没有该设置；若某用例把长步骤封装在 resource 关键字里，超时仍按整个 test 计时，行为正确。
2. **按用例单独配置超时未做**：当前是全局值（`execution.test-timeout-seconds`），PRD 未要求逐用例配置。
3. **UI 不可调**：只能改配置或环境变量；如需在页面上按 run 指定，要再走一遍 `CreateRunCommand` 扩字段（同 EXEC-11 的 `deviceScope` 路径）。
4. **未做真实长跑验证**：按 ADR-0013 开发期不连 DUT，实机验证留到 v1.0 验收。
