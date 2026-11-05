# EXEC-10 证据：队列语义（WBS #91）

- 日期：2026-11-05
- 范围：收口 FR-v0.4-03（同项目优先级 + 创建顺序、跨项目不互阻）与 FR-v0.4-05（MAX_PARALLEL、达上限排队不拒绝）
- 状态：**待验**
- 约束：开发期未连接真实 DUT / 未触发真实 pabot（ADR-0013）

## 1. 改动

| 项 | 之前 | 现在 |
|---|---|---|
| 派发器 | `SerialRunDispatcher`：全局单线程（core=max=1，队列容量 64） | `ProjectRunDispatcher`：按项目分道，每项目独立线程池，核心=上限 |
| 并发上限 | 全局 1 | 单项目 `execution.max-parallel`（默认 4），跨项目不共享线程池 |
| 排序 | `LinkedBlockingQueue`，仅入队先后 | `PriorityBlockingQueue` + `Comparable`：`priority` 降序 → 提交序号升序 |
| 满队列 | 容量 64，超限抛拒绝 | 无界优先队列，达并发上限只排队不拒绝 |
| 提交签名 | `submit(projectId, planId, runId, task)` | 增 `priority`，三个提交点取 `command.priority()`（0~9） |
| 在途去重 | 全局 `inFlight` set | 保留，重复 runId 丢弃并告警 |

提交点：`QuickRunService.dispatch`、`PlanScheduleService.dispatch`、`PlanScheduler.tick`。

改动文件：

- `backend/src/main/java/com/qa/autotest/infrastructure/ProjectRunDispatcher.java`（取代 `SerialRunDispatcher.java`）
- `backend/src/main/java/com/qa/autotest/application/execution/RunDispatcher.java`（签名与语义约定）
- `backend/src/main/java/com/qa/autotest/application/execution/QuickRunService.java`
- `backend/src/main/java/com/qa/autotest/application/plan/PlanScheduleService.java`
- `backend/src/main/java/com/qa/autotest/infrastructure/PlanScheduler.java`
- 测试桩：`PlanTestSupport.RecordingDispatcher`、`QuickRunServiceTest.RecordingDispatcher`
- `backend/src/test/java/com/qa/autotest/infrastructure/ProjectRunDispatcherTest.java`（+4 条，取代旧的 2 条串行用例）

## 2. 用例

1. `同项目按优先级再按提交顺序` —— max=1，顺序断言 `seed → high → low → low-later`
2. `达到并发上限排队不拒绝` —— max=2，前两条开跑、第三条排队，释放后补位；峰值并发 = 2
3. `跨项目互不阻塞` —— A 项目占满唯一线程时，B 项目任务仍立即开跑（`laneCount = 2`）
4. `同一 runId 在途重复提交只执行一次`

## 3. 门禁（日志见同目录）

| 门禁 | 结果 |
|---|---|
| `cd backend && mvn -B test` | **529 passed**（527 + 4 新 − 2 旧串行用例） |
| `DB_NAME=robot_it mvn -B -Pit test` | **28 passed** |
| `cd frontend && npx vitest run` | **160 passed** |
| `npx vue-tsc --noEmit` / `npx vite build` | 0 error / 成功 |
| `bash scripts/lint.sh` | PASS |
| `python3 tools/check_wbs_consistency.py` | 91 包 / 1 / 0 / 77 / 0 / 13 |
| `python3 tools/check_release_docs.py` | FAIL 0 / WARN 0 |

## 4. 残余

- `execution_job.queue_state` 未回写，排队位次/`pending-activity` 类接口未做（归入 v0.5 报告与后续包）。
- 优先级只在内存派发队列内生效；进程重启后 QUEUED run 的恢复顺序仍由重启恢复逻辑决定。
