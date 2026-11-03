# PLN-01 / PLN-02 交付证据（v0.6 自动化）

> 代码不改 git；本文所有数字均来自本机真实执行输出，未做估算。
> 生成日期：2026-11-03；环境：Windows + JDK 25.0.2 + Maven 3.9.16 + MySQL 8.0（127.0.0.1/robot_dev）。

## 0. 改动清单

**迁移**
`db/migration/V6_1__execution_plan.sql`、`V6_2__plan_dependency.sql`；
`db/down/V6_1__execution_plan.down.sql`、`V6_2__plan_dependency.down.sql`。

**后端新增**
`domain/plan/{PlanStatus,OverlapPolicy,PlanRecord,PlanLastRun,PlanCycleException,PlanDependencyGraph}.java`、
`domain/execution/RunOrigin.java`；
`application/plan/{CronSchedule,PlanCommand,PlanView,PlanProperties,RunDispatcher,PlanService,PlanScheduleService}.java`；
`application/repository/PlanRepository.java`、`infrastructure/PlanRepositoryJdbc.java`；
`infrastructure/{PlanScheduler,PlanRunDispatcher}.java`、`api/plan/PlanController.java`。

**后端修改（最小改动）**
`RunService`（新增 `create(cmd, origin)` / `createForScheduler`，原 `create(cmd)` 委托）、
`RunExecutionService`（拆出 `execute(created, cmd)`）、
`RunRepository` / `RunRepositoryJdbc`（`insert(..., RunOrigin)`、`findOrigin`，均用 default 方法保持既有测试桩不变）、
`ReportIngestService`（入库时合并 `PLAN_ID / TRIGGER_TYPE`）、
`common/tenant/TenantGuard`（登记两张新表）。

**后端测试**
`application/plan/{CronNextFireTest,PlanScheduleServiceTest,PlanDependencyGraphTest,PlanTestSupport}.java`。

**前端**
`api/plans.ts`、`views/PlansView.vue`、`router/index.ts`（`/plans` 路由）、
`views/RunsView.vue`（执行菜单入口链接）、`locales/{zh-CN,en-US}.ts`（`plans` 命名空间）。

## 1. 迁移与表结构

| 项 | 值 |
|---|---|
| 版本 | `V6_1__execution_plan.sql` → 6.1；`V6_2__plan_dependency.sql` → 6.2 |
| 回滚 | `db/down/V6_1__execution_plan.down.sql`、`db/down/V6_2__plan_dependency.down.sql` |
| 应用命令 | `mvn -B flyway:migrate '-Dflyway.placeholderReplacement=false'`（env：DB_HOST/PORT/NAME/USERNAME/PASSWORD） |
| 结果 | `Successfully applied 2 migrations ... now at version v6.2`（`docs/evidence/v0.6/flyway-migrate.log`） |
| 历史表 | 15 条全部 `success=true`，6.1 / 6.2 在列（`docs/evidence/v0.6/flyway-history.log`） |

`flyway_schema_history` 实测结果（末尾两行）：

```
version | success
5.5 | true
6.1 | true
6.2 | true
```

结构核对（`flyway-schema-check.log`）：`execution_plan` 20 列（timezone 默认 `Asia/Shanghai`、status 默认
`enabled`、overlap_policy 默认 `SKIP`，含 created/updated 审计四列）；`run` 新增
`plan_id / triggered_by_plan_at`，`trigger_type` 收窄为 `VARCHAR(16)`、默认 `manual`；
索引 `idx_execution_plan_project_status`（execution_plan）与 `idx_run_plan`（run）均已存在。

**表名说明**：`V1__baseline.sql` 中用例集的实际表名是 `test_set`（本库没有 `case_set`），
`execution_plan.test_set_id` 的外键按 `test_set` 引用，迁移文件头部已注明。

## 2. 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/plans?projectId=&page=&size=` | 分页列表，含 `nextFireTimes`（未来 3 个）与最近一次运行结果 |
| POST | `/api/plans` | 创建（body 含 `projectId`） |
| PUT | `/api/plans/{id}` | 修改（body 含 `projectId`） |
| DELETE | `/api/plans/{id}?projectId=` | 删除，同时清依赖 |
| POST | `/api/plans/{id}/pause?projectId=` | 暂停，`next_fire_at` 置空 |
| POST | `/api/plans/{id}/resume?projectId=` | 恢复，从下一次触发点重算，不补跑 |
| POST | `/api/plans/{id}/trigger-now?projectId=` | 立即触发，`trigger_type=manual` |
| POST | `/api/plans/preview` | cron 预览未来 3 次（表单用）；非法表达式 400 |

权限（现有 `PermissionAction` **没有 MANAGE**，因此按实际动作枚举选最接近的）：

- 读：`EXECUTION:READ`
- 写（创建/修改/删除/暂停/恢复）：`EXECUTION:UPDATE`（矩阵中 PM/ENGINEER/ADMIN 拥有，VIEWER 无）。
  不用 `DELETE` 是因为 ENGINEER 没有 `EXECUTION:DELETE`，会把日常维护者挡在外面。
- 立即触发：`EXECUTION:EXECUTE`。
- 依赖成环：保存前 `PlanDependencyGraph.assertNoCycle` 拒绝，抛 `BusinessException(INVALID_ARGUMENT)`
  → 400 中文提示（含环路径），**不写库**。

## 3. 调度语义

| 主题 | 实现 | 证据 |
|---|---|---|
| cron | Spring `CronExpression`（6 段：秒 分 时 日 月 周，**不支持年字段**），时区按计划的 `timezone` 计算，默认 `Asia/Shanghai`，不读 systemDefault | `CronNextFireTest` |
| 到点领取 | `SELECT ... FOR UPDATE SKIP LOCKED`（`PlanRepositoryJdbc.lockDue`），在 `PlanScheduleService.tick()` 短事务内；`last_fire_at/next_fire_at` 同事务推进 | `PlanScheduleServiceTest.领取只消费一次` |
| 重叠 `SKIP` | 该计划存在 `queued/preparing/running` 的 run → 跳过本次，`next_fire_at` 推进到 `now` 之后，审计 `PLAN_SKIPPED_OVERLAP` | `PlanScheduleServiceTest.重叠跳过` |
| 错过（停机） | `countFirePoints(last_fire_at, now)` ≥ 2 判为 missed：**只建 1 个 run**，随后 `next_fire_at = cron.next(now)`，不连续补历史；审计 `PLAN_MISSED_CATCHUP` | `PlanScheduleServiceTest.错过只补一次`、`CronNextFireTest.错过触发点计数` |
| 暂停/恢复 | 暂停：status=paused + `next_fire_at=null`；恢复：status=enabled + `last_fire_at=now`、`next_fire_at=next(now)`，暂停期间不补跑 | `PlanScheduleServiceTest.暂停不触发` |
| 并发上限 | `planning.max-concurrent-per-project`（默认 2），统计项目内 `trigger_type='schedule'` 且 `queued/preparing/running` 的 run；超限**不建 run**，保持到点并审计 `PLAN_DEFERRED`（每个触发点只记一次），下一 tick 重试 | `PlanScheduleServiceTest.并发上限_deferred` |
| 串行执行 | `PlanRunDispatcher` 单线程 `ThreadPoolTaskExecutor`（core=max=1），调度器在短事务提交后派发 | 代码 + 构建 |
| 调度开关 | `PlanProperties` 默认 `planning.scheduler-enabled=false`，`PlanScheduler` 加 `@ConditionalOnProperty` 与 `@Profile("!test")`；tick 间隔 `${planning.tick-seconds:30}` | 代码 |

### metadata 注入方案（PLN-01）

现有链路无法把任意键写进 pabot 的 output.xml metadata，因此采用「落库 + 入库时合并」：

1. 计划触发时 `RunOrigin.scheduled(planId, fireAt)` 写入 `run.plan_id / run.trigger_type='schedule' / run.triggered_by_plan_at`；
2. 报告入库时 `ReportIngestService.mergePlanMetadata` 从 `run` 表读来源，把 `PLAN_ID`、`TRIGGER_TYPE`
   合并进 RPT-01 解析出的 metadata（`putIfAbsent`，不覆盖用例自身写入的值）；
3. 仅当 `plan_id` 非空时注入，手工运行保持原十项门禁不变。

单测 `PlanScheduleServiceTest.metadata_注入来源` 校验传给 `RunService` 的 origin 含 `planId/triggerType/触发点`；
DB 层合并逻辑未新增独立集成测试（见 §6 未覆盖项）。

## 4. 依赖 DAG 语义（PLN-02）

- 保存：`PlanDependencyGraph`（纯领域类）把「计划 → 前置集合」代入整图 DFS，含自环、两节点环、
  三节点环、替换旧边后的环；成环 400，不写库；`UNIQUE(plan_id, depends_on_plan_id)` 兜底重复。
- 成功后联动：上游 `state=FINISHED`（本平台「全部通过」的终态，对应任务书 `passed`）→ 只触发**直接下游**；
  下游仍需检查：暂停（`downstream_paused`）、自身重叠（`downstream_overlap`）、项目并发上限（`PLAN_DEFERRED`）。
- 失败/取消：下游不触发，审计 `PLAN_DOWNSTREAM_SKIPPED` 且 detail 含 `mark=skipped_dependency` 与
  `reason=upstream_failed/upstream_canceled`；执行链路抛异常走 `afterExecutionError`，同样阻断下游。
- 手动 `trigger-now`：忽略依赖与并发上限，但环在保存时已拒绝，不可能通过手动触发绕过；仍写
  `trigger_type=manual` 并保留 `plan_id`。

## 5. 测试与构建真实计数

### 后端 `mvn -B test`（`docs/evidence/v0.6/mvn-test-pln01-pln02.log`）

```
Tests run: 292, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
[exit code] 0
```

> 该日志为**最终代码**（含权限校验顺序调整、未用导入清理）复跑结果，运行时间 2026-11-03 17:35，
> 晚于本轮源码最后修改，日志末尾已显式追加 `[exit code] 0`。

新增用例（日志逐类）：

| 测试类 | 用例数 | 覆盖 |
|---|---|---|
| `CronNextFireTest` | 6 | 正常表达式、跨天、Asia/Shanghai 与 UTC 差异、非法 5/7 段拒绝、非法时区、错过计数 |
| `PlanScheduleServiceTest` | 10 | 到点触发+next_fire、重叠跳过、并发 deferred、只领取一次、暂停不触发、手动触发、metadata 来源、错过只补一次、空套件、项目归属 |
| `PlanDependencyGraphTest` | 10 | 无环拓扑、自环、两节点环、三节点环、直接/间接下游、替换后环、成功触发直接下游、失败阻断、暂停下游阻断 |

ArchUnit `LayerRulesTest`：`Tests run: 6, Failures: 0, Errors: 0`（domain 无 Spring 注解、api 不依赖
infrastructure 等规则保持绿）。

### 前端（`docs/evidence/v0.6/frontend-pln01-pln02-{build,lint}.log`）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `npm run build` | **0** | vue-tsc + vite build 通过 |
| `npm run lint` | **0** | 无告警/错误 |

## 6. 未覆盖项与风险

1. **真实定时触发未做长时连续实跑**：单测用固定时钟覆盖 cron/重叠/错过/并发逻辑，但没有让 `PlanScheduler`
   在本地 WSL 或本机连续运行较长时段（随试点迭代观察，不作为验收项）；后端 8081 正在跑旧代码且按任务要求未重启，因此新接口也未做
   浏览器端到端验证。
2. **多实例 `SKIP LOCKED` 未压测**：SQL 与事务边界按 MySQL 8 的语义实现，单测用内存仓储模拟
   「同一行只被消费一次」，未起两个实例并发抢同一批计划做压力测试。
3. **迁移 down 未实际执行**：down 脚本已写且与 V6_1/V6_2 对称，但未在 dev 库上演练回滚。
4. **审计代替计划事件表**：`skipped_overlap / deferred / skipped_dependency` 落在 `audit_log`
   （`resource_type=execution_plan`），未新建 `execution_plan_event` 表；满足「可解释原因」，
   但按计划查询事件需要走审计检索。
5. **并发上限只统计计划触发的 run**：手工运行不计入 `planning.max-concurrent-per-project`，
   与「计划并发」口径一致；若后续要求全项目调度共享配额，需要改统计条件。
6. **计划编辑不改变状态**：状态只能走 pause/resume 接口，避免表单误把暂停计划改回启用。
