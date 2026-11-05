# EXEC-05 执行工作区落盘 — 证据

**包号** WBS #86 ｜ **状态** `待验`（AI 不改 `已完成`）｜ **日期** 2026-11-05
**需求依据** `docs/release/v0.4-execution/PRD.md` FR-v0.4-16（产物结构）与 ADR-0005 §3（`robot_file.content` 为脚本唯一真源）

## 1. 要解决的问题

EXEC-02 / EXEC-03 / EXEC-04 三包都留下同一条残余偏差：

> 用例↔脚本绑定读的是数据库 `robot_file.content`，而 pabot 执行读的是
> `execution.workspace-root` 下的磁盘文件 —— 两者可能不是同一份内容。

后果是 `--test` 用的是 DB 里的测试名，真正跑的却是磁盘上的旧脚本：绑定命中但测试不存在，
或勾选 A 却跑到磁盘版 B。EXEC-05 把执行入口改成「DB 真源 → 本次 run 独立工作区」。

## 2. 实现

| 文件 | 改动 |
|---|---|
| `application/execution/ExecutionWorkspaceMaterializer.java`（新增） | `materialize(projectId, runDir)`：读 `RobotFileRepository#listAll`，按原相对路径写入 `<artifacts>/<runId>/workspace`；拒绝绝对路径与 `..` 段；项目无脚本返回 `Optional.empty()` |
| `application/repository/RobotFileRepository.java` | 新增 `List<RobotFile> listAll(long projectId)` |
| `infrastructure/RobotFileRepositoryJdbc.java` | `SELECT ... FROM robot_file WHERE project_id = ? ORDER BY path`，保留 `TenantGuard.requireProjectScope` |
| `application/execution/PabotCommandBuilder.java` | 新增 `build(spec, props, artifactsRoot, workDir)` 重载；原三参方法委托到「磁盘工作区」，兼容旧调用 |
| `application/execution/RunExecutionService.java` | 建目录后先落盘；成功用 run 工作区，`IOException` → `workspace_materialize_failed`；数据库为空回落 `execution.workspace-root` |
| `application/execution/ExecutionRequestPrecheck.java` | 路径存在性与测试名重名判定改为 **DB 优先、磁盘回落**；目录套件按 `.robot` 前缀扫描；重名按**文件**登记（目录下两文件同名同样会让 `--test` 多跑） |
| `application/execution/QuickRunService.java` | 预检调用传入 `projectId` |

### 工作区布局

```
~/autotest-artifacts/<runId>/
├── workspace/            ← 本次 run 的脚本副本（pabot cwd）
│   └── suites/...        ← 与 robot_file.path 一致
├── shard-0/
│   ├── output.xml
│   └── ...
└── logs/...
```

- 每个 run 独立目录：并发 run 不互相覆盖，也不受上一次执行残留影响。
- 只影响执行入参；报告定位仍走 `shard-0/output.xml`，`workspace/` 不参与报告扫描。

## 3. 门禁实测

| 门禁 | 结果 | 日志 |
|---|---|---|
| 后端全量单测 | **510 tests / 0 failures / 0 errors**，BUILD SUCCESS | `backend-test.log` |
| 后端集成测试 | **28/28**，BUILD SUCCESS（`DB_NAME=robot_it`） | `backend-it.log` |
| `scripts/lint.sh` | `[lint] PASS`（含 WBS 自洽、表格、品牌、i18n、字体子集等六守门） | `lint.log` |
| `tools/check_wbs_consistency.py` | **86 包 / 未开始 1 / 进行中 0 / 待验 72 / 待现场 0 / 已完成 13** | `wbs-gate.log` |
| `tools/check_release_docs.py` | FAIL 0 / WARN 0（7 个版本均 IDLE 不计违规） | `release-docs-gate.log` |
| 改动范围 | 仅工作区留存，**无 git 提交** | `git-status.log` |

## 4. 新增/调整测试（+4 条，506 → 510）

`ExecutionRequestPrecheckTest`（5 → 8 条）：

1. 数据库为空回落磁盘：文件与目录都存在 → 放行；
2. 数据库为空回落磁盘：路径缺失 → 400，消息含工作区与缺失清单；
3. 数据库有脚本：磁盘没有也算存在（以 DB 为准）；
4. 数据库有脚本：路径缺失 → 400，且**不回退磁盘**；
5. 数据库脚本测试名跨套件重名 → 400，列出名字与套件；
6. 选择**目录**套件时扫描目录下全部 `.robot`，两文件重名 → 400；
7. 测试名唯一 → 放行；
8. 空测试名清单（套件级执行）→ 不扫重名。

`RunExecutionServiceTest`（+1 条）：数据库脚本落盘到 `run 工作区`，两个 `.robot` 文件均存在，
且 launcher 收到的 `command.workDir()` 正是该工作区目录。

配套改动：`ExecutionTestSupport#scriptRepo`（`listAll` 桩）、`EditorSaveServiceTest.FakeFiles#listAll`、
`QuickRunServiceTest` / `CaseRunServiceTest` / `TestSetRunServiceTest` 构造参数同步。

## 5. 残余与边界（验收时请重点看）

1. **数据库为空时回落磁盘**：项目在 `robot_file` 没有任何脚本时，仍走
   `execution.workspace-root`。这是为计划 / CI 触发保留的兼容路径，此时「DB/磁盘不同源」的
   旧风险仍在；要彻底消除，需保证建库时脚本先入 `robot_file`。
2. **全量落盘**：每次 run 把项目**全部**脚本写入工作区，而不是只写所选套件 ——
   `Resource` / `Variables` 导入的相对路径才不会断。项目脚本量大时是 O(全部脚本) 的磁盘写，
   当前量级可接受；若后续到数百 MB，再改成「所选套件 + 依赖闭包」。
3. **真实 pabot 在落盘工作区执行**未跑：ADR-0013 开发期禁跑真实 DUT，留给验收人现场
   （与 EXEC-02/03 的 `--test` 实跑同一批）。
4. **`workspace/` 会留在 run 产物目录**：目前不参与报告扫描，也没有清理策略；
   产物保留期策略（RPT-07 / 归档）落地时需把它纳入清理清单。
5. 非 `.robot` 的 `robot_file` 行（如 `requirements.txt`、资源文件）也会原样落盘，这是有意的
   —— 执行可能依赖同目录文件；但不会做可执行权限处理。
