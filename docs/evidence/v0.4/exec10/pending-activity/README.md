# EXEC-10 残余：QUEUED 队列位次（pending-activity）+ `execution_queue.queue_state` 回写

- 日期：2026-11-06
- 对应 WBS：第 130 行 #91 EXEC-10（本次只补残余，不改原「按项目分道 + MAX_PARALLEL」结论）
- 状态：**待验**（按纪律不自行升「已完成」）

## 1. 原缺口

WBS 备注原文两条：

1. 「未回写 `execution_job.queue_state`」——实际库里字段在 **`execution_queue.queue_state`**
   （`V1__baseline.sql` 建了表，但全仓库没有任何写入方）；
2. 「`QUEUED` 队列位次接口（pending-activity 语义）未做」——用户在排队时看不到「第几位」。

## 2. 本次实现

### 后端

| 文件 | 改动 |
|---|---|
| `application/execution/RunDispatcher.java` | 新增只读快照 `record QueueStatus(runId, state, position, waiting, running, maxParallel)` 与 `default Optional<QueueStatus> status(String runId)`（默认空，既有内存实现不受影响） |
| `application/execution/RunQueueRepository.java` | **新建**：`enqueue / markRunning / markDone` 三个回写口，全部默认空实现 |
| `infrastructure/ProjectRunDispatcher.java` | 维护 `runId → projectId`（`projectOfRun`）；`status()` 用线程池真实队列 `getQueue()` 计算位次：同项目队列中 `compareTo` 排在自己前面的任务数 + 1，与出队顺序同源；提交时 `enqueue`，线程内 `markRunning`，`finally` 里 `markDone` |
| `infrastructure/RunQueueRepositoryJdbc.java` | **新建**：按 `execution_job.job_no = runId` 反查 `job_id` 写 `execution_queue`；`queue_state` 走 `waiting → running → done`；同 run 重复提交命中唯一键时改回 `waiting` 并清 `started_at`（幂等重放） |
| `api/execution/RunController.java` | 新增 `GET /api/execution/runs/{runId}/pending-activity?projectId=`：先 `runs.get(projectId, runId)` 校验归属，再问派发器；查不到（重启/已结束）按 DB 状态兜底为 `queued / running / terminal`，`position = 0` |

位次口径：同项目、同队列、按 `priority` 降序再按提交序号升序——即**线程池下一次真正会出队的顺序**，
不是另起一套估算。跨项目的 run 不在同一 lane，互不影响位次。

### 前端

| 文件 | 改动 |
|---|---|
| `api/execution.ts` | 新增 `PendingActivity` 类型与 `executionApi.pendingActivity(projectId, runId)` |
| `views/RunsView.vue` | 选中行 `state === 'QUEUED'` 时拉取位次并**每 5 秒轮询**（位次会随前面的 run 结束而前进）；离开选中行 / 切项目 / 卸载时清定时器；`state !== 'queued'` 自动停轮询 |
| `locales/zh-CN.ts`、`en-US.ts` | `execution.queuePosition`、`execution.queueUnknown` |

展示规则：`position > 0` 显示「排队中 · 第 N 位 / 共 M 个」；`position = 0`（后端重启后派发器不知道位次）
显示「排队中 · 位次暂不可用」，**不显示「第 0 位」**。

## 3. 验证

| 门禁 | 命令 | 结果 |
|---|---|---|
| 后端单测 | `cd backend && mvn -q -o -Dtest=ProjectRunDispatcherTest,RunControllerTest -DfailIfNoTests=false test` | PASS（日志 `pending-activity/backend-test.log`） |
| 前端视图测试 | `cd frontend && npx vitest run src/views/RunsView.spec.ts` | 14 passed（`pending-activity/frontend-vitest.log`） |
| 前端全量 | `npm run test` | 28 文件 / **197 用例** 全通过 |
| 类型 + 构建 | `npm run build`（`vue-tsc --noEmit && vite build`） | 0 错误，`built in 9.26s` |
| 字体子集 | `python3 tools/subset_font.py` | PASS，1028 字 / 178634 B gzip（未超 800 KiB） |
| lint | `bash scripts/lint.sh` | `[lint] PASS` |
| 发布文档 | `python3 tools/check_release_docs.py` | FAIL 0 |
| WBS 自洽 | `python3 tools/check_wbs_consistency.py` | PASS（96 包 / 待验 83 / 已完成 13） |

### 新增用例

- 后端 `ProjectRunDispatcherTest#队列位次与状态回写`：maxParallel=1，seed 占住线程后提交
  priority 2/9/1 三个任务 → `high` 位次 1、`low` 2、`lowest` 3；`waiting=3`、`running=1`；
  `seed` 报 `running`；不在派发器的 runId 返回空；`queue_state` 依次 `waiting → running → done`。
- 后端 `RunControllerTest#pendingActivity_位次与兜底`：派发器有位次时原样返回；派发器为空时按 DB
  `QUEUED` 兜底成 `queued / position=0`。
- 前端 `RunsView.spec.ts`：QUEUED 行打开日志面板 → 调用 `pendingActivity(1, 'RUN-QUEUED')` 并
  渲染「第 2 位 / 共 3 个」；位次 0 时渲染「位次暂不可用」。

## 4. 已知边界

- 派发器位次只在**当前进程**内准确；后端重启后队列为空，接口退化为 DB 兜底（`position=0`）。
  这是「不假装知道」的选择，不是遗漏——跨进程精确位次需要持久化队列快照，超出本项范围。
- `execution_queue` 不在 `TenantGuard.TENANT_TABLES` 内，故回写 SQL 通过 `JOIN execution_job` 带
  `project_id` 条件，避免跨项目改行；该方法未走 `TenantGuard.requireProjectScope`（会因表未登记直接抛错）。
