# v0.4 执行 · ⑥ 测试报告

**文档编号** RA-REL-2026-v0.4-TR ｜ **版本** v0.4 ｜ **日期** 2026-11-06
**状态** 机器门复跑通过；TC 级沿用历史证据；门禁 G4 真值见 §4

> 本文件是 2026-11-06 W1 收口时补齐的版本流程第 ⑥ 道产物。**本轮重跑的是机器门**，
> 不是逐条 TC 重新执行；凡未重跑的 TC 一律标 `EVIDENCE-ONLY`，不得读作「本轮通过」。
> 真机 / 外部凭据 / 时间类条件见 §5，一律 `待现场`。

## 1. 结论

| 项 | 值 |
|---|---|
| 版本 / 批次 | v0.4 执行 / B4/B5 |
| TEST-PLAN 中识别到 TC | 49 条 |
| 本轮机器门 | 8 项全绿（见 §2） |
| 本轮逐条重跑 TC | 0（口径见上文） |
| 历史证据文件 | 171 份（§3 列前 18 份） |
| 门禁 G4 | 无书面核验纪要，W3 因此不得开工 |
| 当前结论 | **机器门通过、TC 证据齐备；门禁未判过项与真机项不冒充通过** |

## 2. 本轮机器门实测（2026-11-06，W1 收口批次）

| 门 | 命令 | 实测读数 | 结果 |
|---|---|---|---|
| 后端单测 | `mvn -B test` | 644 / 644 通过，0 失败 0 错误 | PASS |
| 前端单测 | `cd frontend && npm run test` | 30 文件 / 221 用例通过 | PASS |
| 前端类型与构建 | `npm run build（vue-tsc --noEmit）` | 0 类型错误，dist 产出成功 | PASS |
| 后端静态检查 | `mvn -B checkstyle:check` | 0 violations / BUILD SUCCESS | PASS |
| 数据库体检 | `bash scripts/db-audit.sh` | 8 项 PASS / 47 业务表 | PASS |
| 仓库守门 | `bash scripts/lint.sh` | PASS（含 Checkstyle 接线） | PASS |
| 统一入口实测 | `bash scripts/verify-entry.sh` | 12 / 12 PASS | PASS |
| WBS 自洽 | `python3 tools/check_wbs_consistency.py` | 130 包 / 87 已完成，五数自洽 | PASS |

> 原始日志由 `docs/evidence/v1.0/policy-20261106/gates.sh` 复跑重建（logs/ 被 .gitignore 忽略）。
> **2026-11-06 二轮复跑（本节读数为最终值）**：门禁从 10 步扩到 **11 步**（新增
> `11-editor_test`：`packages/editor` 有自己的 vitest 工程，frontend 的 vitest 扫不到它，
> 而 EDT-03/EDT-05 的回归用例正住在那里）。11 步 `GATES_RESULT FAILS=0`，实测读数：
> 后端 **Tests run: 780, Failures: 0**、frontend **37 文件 / 239 用例**、editor 包 **24 文件 / 219 用例**、
> `vue-tsc` 0 错误、`vite build` 通过（`EditorView` 809.56 kB）、`scripts/lint.sh` PASS、
> `check_release_docs` / `check_wbs_consistency`（130 包 / 124 已完成 / 待验 0）/ `check_md_tables` / `git diff --check` 均 PASS。


## 3. 历史证据清单

共 171 份，以下列前 18 份（相对仓库根）：

- `docs/evidence/v0.4/bench-20rounds.txt`
- `docs/evidence/v0.4/bench-2process.log`
- `docs/evidence/v0.4/cancel-ps.txt`
- `docs/evidence/v0.4/dut-real.md`
- `docs/evidence/v0.4/exe03-fault-injection.log`
- `docs/evidence/v0.4/exe03-fault-injection.md`
- `docs/evidence/v0.4/exe03-pabot-2proc.log`
- `docs/evidence/v0.4/exec02/README.md`
- `docs/evidence/v0.4/exec02/backend-it.log`
- `docs/evidence/v0.4/exec02/backend-test.log`
- `docs/evidence/v0.4/exec02/frontend-build.log`
- `docs/evidence/v0.4/exec02/frontend-test.log`
- `docs/evidence/v0.4/exec02/frontend-tsc.log`
- `docs/evidence/v0.4/exec02/lint.log`
- `docs/evidence/v0.4/exec02/release-docs-gate.log`
- `docs/evidence/v0.4/exec03/README.md`
- `docs/evidence/v0.4/exec03/backend-it.log`
- `docs/evidence/v0.4/exec03/backend-test.log`
- … 其余 153 份见 `docs/evidence/v0.4/`

## 4. TC 台账（口径：EVIDENCE-ONLY）

| TC | 本轮结果 | 判定依据 |
|---|---|---|
| TC-v0.4-101 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-102 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-103 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-104 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-105 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-106 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-107 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-108 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-109 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-201 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-202 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-203 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-204 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-205 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-206 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-301 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-302 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-303 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-304 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-305 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-306 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-307 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-308 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-401 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-402 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-403 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-404 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-405 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-406 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-501 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-502 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-503 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-504 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-505 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-506 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-507 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-508 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-601 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-602 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-603 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-604 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-605 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-606 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-701 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-702 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-703 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-704 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-705 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.4-706 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |

## 5. 遗留缺口与未验证项（不自评通过）

- 门禁 G4：无书面核验纪要，W3 因此不得开工。
- 真机 / 外部凭据类（DUT、交换机、GitHub runner、pabot）一律 `待现场`；ADR-0013 禁跑令有效。
- 条件类（4 并发、打印链路）与时间类（连续运行）不具备复跑条件，见 `docs/plan/门禁核验纪要-G2-G5-20261106.md`。
- v2.0 的 W2/W3 受 G5/G4 前置约束，本报告不将其计入任何「通过」结论。

## 6. 修订记录

| 日期 | 版本 | 变更 |
|---|---|---|
| 2026-11-06 | v1.2 | 机器门读数按最终门禁日志订正：后端 642→644、前端 220→221，WBS 补 87/130；不沿用估算值 |
| 2026-11-06 | v1.3 | 二轮真实链路复跑：`TC-v0.4-101`（真库 100 连接恰 1 成功）、`TC-v0.4-404`（真实 Tomcat WS 补发 200 行）由 EVIDENCE-ONLY 转实测；门禁 10→11 步（补 editor 包 219 条），读数 780 / 239 / 219；订正「MariaDB」为 **MySQL 8.0.46**（MariaDB 仍未验）；新增 §7 记录两个真缺陷 |
| 2026-11-06 | v1.1 | TC 台账改为复用 `tools/check_release_docs.py::extract_ids()`，补齐区间展开与两种编号形态（v1.0 生成器漏 30 条被机检判 FAIL） |
| 2026-11-06 | v1.0 | 首次补齐；按「机器门实测 + 历史证据 + 门禁真值」三层写实，不用通过模板 |

---

## 7. 二轮回写：真实链路复跑（2026-11-06 晚）

> §4 里 `EVIDENCE-ONLY` 的两组（租约并发、实时日志）在本轮**真的跑了**。原表保留。

### 7.1 从 EVIDENCE-ONLY 转为实测的 TC

| TC | 原口径 | 本轮实测 | 证据 |
|---|---|---|---|
| `TC-v0.4-101` 100 线程抢同一 `resource_key` | EVIDENCE-ONLY（`putIfAbsent` 内存语义） | **真跑 PASS**：真实 MySQL 8.0.46，100 条独立连接屏障同时 INSERT → 成功 **1** / 冲突 **99** / 其他错误 **0**，落库 1 行 | `docs/evidence/v0.4/lease-concurrency/`（`run.py --workers 100` + `run.log`） |
| `TC-v0.4-404` 断线重连收到最近 200 行 | EVIDENCE-ONLY | **真跑 PASS**：真实 Tomcat + JDK `WebSocket` 客户端，断线期间写入后重连补发最近 200 行 | `docs/evidence/v0.4/exe04-ws-e2e.md`（4/4） |
| `TC-v0.4-401/402` 日志延迟 P95 | 只有服务端 flush 样本 | **部分推进**：新增单帧 ≤50 行断言与增量推送时序断言，但仍未做网络侧 P95 采样 | 同上 |
| `TC-v0.4-403` 10,000 行/10 s 疯狂打日志 | EVIDENCE-ONLY | **未跑**（属 HRD-03 试点范围） | — |

### 7.2 本轮修掉的两个真缺陷

**缺陷 1：日志 flush 寄生在执行调度上。**
`RunLogService.flush()` 标了 `@Scheduled`，但所在配置类没有 `@EnableScheduling` ——
只有「执行调度」那套配置被加载时才生效。后果：没开执行调度时**一条日志都不推**，
前端「跑起来了但日志不动」，而单测直接调 `flush()` 所以全绿。
修法：新增 `infrastructure/RunLogFlushConfig` 独立 `@EnableScheduling`。

**缺陷 2：补发与增量重叠，同一行推两遍。**
订阅时先补发环形缓冲，同时 batcher 里压着的旧行会在下一轮 flush 再推一次。
修法：订阅者水位 `lastSeq`，只推 `seq > lastSeq`。
**连带发现旧断言在固化错误行为**：`RunLogServiceTest` 原来断言 `received.contains("line-1")`，
而 line-1 已在补发窗口之外，它要求的正是「重复推」这个 bug。改成三条：新行必达 / 不得重复 / 窗口外不得回流。

### 7.3 一处事实订正：库是 MySQL，不是 MariaDB

本版证据多处写「真实 MariaDB」，实测 `select version()` = **8.0.46-0ubuntu0.24.04.4**。
ADR-0002 里 MariaDB 一直是「待验」，把 MySQL 的并发结论安到 MariaDB 头上属于张冠李戴。
已改：脚本运行时取 `VERSION()` 写进日志抬头，WBS 与本节同步改成 MySQL；
**MariaDB 的并发结论仍为空（未验）**，不随本行外推。

### 7.4 仍未收口

- `TC-v0.4-102`（100 线程抢 10 个 key、总成功 10）仍只有单测覆盖，未上真库。
- 取消/超时/重启恢复（`TC-v0.4-501..508`）需真实 pabot 子进程树，仍 `待现场`（ADR-0013 禁跑令）。
- 4 并发压测 20 轮（`TC-v0.4-201..206`）需真实 RF 套件跑满 20 轮，仍 `待现场`。
- 门禁 G4 **不自评通过**（结论同 §1）。
