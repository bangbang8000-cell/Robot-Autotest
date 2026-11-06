# PLN-02 残余收口：下游跳过事件可见 + 联动审计带上项目归属（2026-11-06）

## 1. 缺口

`PlanScheduleService` 早就会在「上游失败/取消」时把下游标成 `skipped_dependency` 并写审计
（`PLAN_DOWNSTREAM_SKIPPED`），但**用户看不到**，而且这条审计实际上写不进任何项目的查询结果：

| 层 | 已有 | 缺什么 |
|---|---|---|
| 调度回调 | `RunDispatcher` 派发线程里调 `afterRun` / `afterExecutionError` | 派发线程**没有 `TenantContext`**，`audit.record` 落库的 `project_id` 为 NULL |
| 审计查询 | `GET /api/audit?projectId=&action=`，按项目查要求所有者/管理员 | 记录本身没有 project_id，按项目永远查不到 |
| 前端 | 计划页只有计划列表 | 没有任何入口能看到「哪些下游被跳过、为什么、上游是哪次运行」 |

## 2. 做法

### 2.1 后端：联动回调补租户上下文

`infrastructure/PlanScheduler.java` 的派发任务里，把 `afterRun` / `afterExecutionError`
包进 `asPlanContext(plan, action)` —— 按计划归属 `TenantContext.runAs(plan.createdBy(), "planner", plan.projectId(), action)`，
执行完自动还原，不污染派发线程后续任务。执行异常分支同样包裹，保证「执行挂了 → 下游跳过」这条路的审计也带项目。

新增 `PlanSchedulerTenantTest`（2 条，Mockito）：

| 用例 | 验证点 |
|---|---|
| `下游联动带计划租户上下文` | 派发前本线程 `projectId` 为 null；`ArgumentCaptor` 捕获提交的 `Runnable` 手动执行，`afterRun` 内读到 `projectId=7` / `userId=1`，结束后恢复为 null |
| `执行异常时也带计划租户上下文` | `executions.execute` 抛 `IllegalStateException("robot 未安装")`，`afterExecutionError` 内同样读到 `projectId=7`，结束为 null |

### 2.2 前端：计划页「依赖跳过事件」按需展开

`views/PlansView.vue` 在计划表格下方加一块折叠面板（`NButton` 切换 + 一句话说明），**展开才拉**：

- 数据源：`auditApi.page({ projectId, action: 'PLAN_DOWNSTREAM_SKIPPED', page: 1, size: 20 })`，
  进页时不多打一次接口，已拉过不重复拉。
- `detail` 是 `k=v, k=v` 行式记录，`parseDetail` 拆字段；`downstream=<id>` 在已加载计划列表里换成计划名，
  查不到则显示 `#<id>`（不显示空白）。
- 原因码取冒号前的码去查 `plans.skippedReasons.*`（`upstream_failed` / `upstream_canceled` / `upstream_error` /
  `plan_missing` / `downstream_paused` / `downstream_overlap`），查不到就显示原文。
- 403 `FORBIDDEN` 单独提示「仅管理员或项目所有者可查看跳过事件」，不渲染空表格 —— 避免把「没权限」
  读成「最近没有跳过」。
- 表格走 `DataTable` 壳（列：时间 / 下游计划 / 原因 / 上游运行，`max-height="240px"` 复用 px 白名单档位），
  空态用 `plans.skippedEmpty`。

新增 vitest 2 条（`PlansView.spec.ts`）：

| 用例 | 验证点 |
|---|---|
| 展开后还原计划名 / 原因文案 / 上游运行号 | 未展开时 `.skipped-table` 不存在且 `auditApi.page` 未调用；展开后调用参数为 `{projectId:1, action, page:1, size:20}`；两行分别含「手工回归」「上游失败」「RUN-FAIL-001」与 `#999`「下游已暂停」 |
| 无权限给提示不给空表 | `page` 抛 `ApiError(403,'FORBIDDEN')` → 无 `.skipped-table`，页面出现 `plans.skippedForbidden` 文案 |

## 3. 门禁读数

| 项 | 结果 |
|---|---|
| `tools/subset_font.py` | PASS（1036 字 / 179758 B，未超预算） |
| 前端 `vitest run` | 28 文件 / **204 用例**（本批 +2） |
| 前端 `vue-tsc --noEmit` | 0 错误 |
| 前端 `vite build` | `built in 10.66s` |
| 后端 `mvn -o -B test` | **614 / 0 / 0**（本批 +2） |
| `scripts/lint.sh` | PASS（含 `check_tables`：33 个 .vue / DataTable 壳 21 处；`check_tokens` px 白名单） |
| `check_release_docs.py` | PASS（7 版本 / FAIL 0 / WARN 0） |
| `check_wbs_consistency.py` | PASS（96 包 / 待验 83 / 已完成 13） |

门禁日志见同目录 `01-subset-font.log` … `08-wbs.log`。

## 4. 残余

1. **失败传导仍未在真实 pabot 链路验证**：本批证明的是「回调有租户上下文 + 审计可查 + 前端可读」，
   上游真正跑挂后下游是否按 DAG 顺序被跳过，仍需一条端到端实跑（需要可用 DUT / 可跑套件）。
2. 跳过事件列表固定取最近 20 条，无分页、无时间范围筛选；审计接口本身支持 `from`/`to`/`page`，需要时再接。
3. 下游计划被删除后，列表里只能显示 `#<id>`；计划名快照若日后需要留档，应在写审计时一并落 detail。
