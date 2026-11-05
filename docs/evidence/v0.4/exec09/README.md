# EXEC-09 证据：租约接入执行链路（WBS #90）

- 日期：2026-11-05
- 范围：把「资源租约」真正接入 `RunExecutionService` 的执行链路，收口 EXEC-08 复核中的首要缺口（FR-04 / 07 / 09 / 10 / 23）
- 状态：**待验**（AI 仅改到待验，不自行置为已完成）
- 约束：开发期未连接真实 DUT / 未触发真实 pabot（ADR-0013）

## 1. 做了什么

| 环节 | 实现 |
|---|---|
| 申请 | 进入 `RUNNING` **之前**按 run 维度 `leaseService.acquireAll(...)`；状态机的 `leaseHeld=true` 前置条件因此真正生效 |
| 资源键 | 请求带 `deviceKeys` → 用「设备+端口」键（如 `DUT-01/mlx5_0`，去重、≤128 字符）；未带 → `run:<runId>` 自持（不做互斥） |
| 外键 | 租约 `job_id` 指向 `execution_job.id`，run 表不存该主键；新增 `RunRepository.findJobId` 按 `job_no = run_id` 反查 |
| 拒绝 | 申请失败（等满 `execution.lease-max-wait-seconds`）→ `FAILED`、`failure_reason=lease_timeout`、`failure_class=INFRA`，**不启动 pabot**，消息含被占用资源键 |
| 释放 | 执行段包在 `try/finally`：成功 → `finished`；失败/异常 → `failed`；并发取消在 `transition` 抛错时同样释放；取消/超时/重启恢复沿用已有 `releaseRun` |
| 续约 | 沿用 `ExecutionSchedulerConfig` 的 40s 全局心跳（`LeaseService.heartbeatAll`），未新增线程 |

改动文件：

- `backend/src/main/java/com/qa/autotest/application/execution/RunExecutionService.java`
- `backend/src/main/java/com/qa/autotest/application/execution/RunService.java`（`leaseJobId`）
- `backend/src/main/java/com/qa/autotest/application/repository/RunRepository.java`（`findJobId` 默认实现）
- `backend/src/main/java/com/qa/autotest/infrastructure/RunRepositoryJdbc.java`（`job_no → execution_job.id`）
- `backend/src/test/java/com/qa/autotest/application/execution/RunExecutionServiceTest.java`（+5 条）
- `backend/src/test/java/com/qa/autotest/application/iam/AuditAspectTest.java`（+1 条：生命周期释放不写强制释放审计）

## 2. 新增用例

1. `执行期持有租约且成功后释放` —— pabot 启动瞬间租约数 = 1，结束后 `resource_lease` 为空、历史含 `released/finished`
2. `失败结束同样释放租约` —— `FAILED` 后同样释放，历史含 `released/failed`
3. `资源被占用时等待超时进入_lease_timeout_且不启动_pabot` —— 预置 `project:7` 被别的 job 占用，`launchCount = 0`
4. `设备范围决定租约键` —— `deviceKeys=[DUT-01/mlx5_0]` 时租约历史出现该键
5. `资源键缺省退化为运行自持` —— `run:RUN-9` / 设备键去重与 trim

## 3. 门禁结果（详见同目录日志）

| 门禁 | 命令 | 结果 |
|---|---|---|
| 后端单测 | `cd backend && mvn -B test` | **527 passed**（基线 521 + 6，含审计口径 1 条） |
| 集成测试 | `DB_NAME=robot_it mvn -B -Pit test` | **28 passed** |
| 前端单测 | `cd frontend && npx vitest run` | **160 passed** |
| 类型检查 | `npx vue-tsc --noEmit` | 0 error |
| 前端构建 | `npx vite build` | 成功 |
| Lint | `bash scripts/lint.sh` | PASS |
| WBS 自洽 | `python3 tools/check_wbs_consistency.py` | 90 包 / 1 / 0 / 76 / 0 / 13 |
| 发布文档 | `python3 tools/check_release_docs.py` | FAIL 0 / WARN 0 |

日志：`backend-test.log`、`backend-it.log`、`frontend-test.log`、`frontend-tsc.log`、`frontend-build.log`、
`lint.log`、`wbs.log`、`release-docs.log`、`git-status.log`。

## 4. 已知残余（未在本包解决）

- 租约按 **run** 聚合（`execution_job` 每 run 一条），未按分片拆 job；FR-04「每片申请」在语义上以「整 run 覆盖设备集合」实现。
- ~~FR-08 等待态 UI（`WAITING` + 等待对象与时长）仍缺；本包只补齐后端拒绝原因与资源键清单。~~
  2026-11-06 已补「等待资源」可见态，见第 5 节。
- ~~`run.lease_wait_ms`（基线列）尚未回写。~~ 2026-11-06 已回写，见第 5 节。
- 全局单线程派发器未改，队列语义（FR-03 / 05）仍为下一 P0。

## 5. 2026-11-06 补记：等待资源可见 + `lease_wait_ms` 回写（FR-08 收口）

**做法**：不新增 `run_status` 枚举值（`WAITING` 会打乱状态机与既有 SQL 口径），改为「运行期登记 + 落库耗时」两层：

| 层 | 位置 | 改动 |
|---|---|---|
| 登记 | 新增 `LeaseWaitRegistry`（`ConcurrentHashMap<runId, Instant>` + 注入 `Clock`） | `begin/end/waiting` 三个方法；只做运行期展示，进程重启即丢，不影响执行 |
| 执行 | `RunExecutionService` 在 `acquireAll` 前后 `begin`/`end` | 成功、`lease_timeout`、`lease_acquire_failed` 三条路径都会 `end` 并回写，不会漏计时 |
| 落库 | `RunRepository.recordLeaseWait`（接口默认空实现 + JDBC 覆盖） | `UPDATE run SET lease_wait_ms=?, updated_at=CURRENT_TIMESTAMP(3) WHERE run_id=? AND project_id>0`，走 `TenantGuard` |
| 接口 | `GET /runs/{runId}/pending-activity` | 等租约时优先返回 `state=waiting`，不被派发器的 `running` 盖住 |
| 界面 | `RunsView.vue` + `execution.ts` | `PREPARING` 也启动 5s 轮询；`waiting` 显示「等待资源 · 设备被其他运行占用，拿到租约后自动开始」；`running`/`terminal` 才停轮询 |

边界：

- `waiting` 只表示「已出队、正在等租约」，**不等同**于排队位次（那是 `queued`）；两者文案分开，避免又混成一团。
- 等待时长只落 `run.lease_wait_ms`，暂不从接口回读（`RunRecord` 有 23 处构造点，单独开接口不值当；需要时直接查库或看 `pending-activity` 状态变化）。
- `execution.lease-max-wait-seconds=0`（不重试）时仍会走登记/回写，值为 0 ms，属于预期。

新增用例：

- 后端 `LeaseWaitRegistryTest`：`begin → waiting → end` 返回 2500ms，重复 `end` 返回 0（用可推进的 `MutableClock`）。
- 后端 `RunControllerTest`：`waiting` 优先于派发器 `running`，响应 `state=waiting`、`maxParallel=4`。
- 后端 `RunExecutionServiceTest`：原「持有租约并释放」补断言 `lease_wait_ms` 必须回写（固定时钟下为 0）。
- 前端 `RunsView.spec`：`PREPARING` 行会调用 `pendingActivity` 并渲染「等待资源」文案。

门禁（2026-11-06）：后端 **557 passed**（555 + 2）；前端 **199 passed**（198 + 1，28 文件）；
`vue-tsc` 0 error、`vite build` 成功、`lint.sh` PASS、`check_release_docs.py` FAIL 0 / WARN 0、
WBS 自洽 PASS。日志：`backend-test-20261106.log`、`frontend-test-20261106.log`、
`frontend-tsc-20261106.log`、`frontend-build-20261106.log`、`font-20261106.log`、
`lint-20261106.log`、`release-docs-20261106.log`、`wbs-20261106.log`。
