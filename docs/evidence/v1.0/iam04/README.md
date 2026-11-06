# IAM-04 残余：租户隔离的真实 SQL 覆盖

日期：2026-11-06　状态：待验（`docs/plan/WBS-STATUS.md` 第 49 行）

## 1. 缺口是什么

ADR-0006 的租户隔离分三层：

1. 控制器/服务统一 `access.bindProject(projectId)`（调用者是否属于该项目）；
2. 仓储 SQL 必须带 `project_id`，由 `TenantGuard.requireProjectScope` 在**执行前**拦；
3. 元测试 `TenantGuardMetaTest`（5 条）故意写危险 SQL，断言被拦。

第 2、3 层此前只有**字符串级**验证——「守卫会不会拦 `SELECT * FROM run` 这种没带租户条件的 SQL」。
没有人证明过「真实仓储跑在真实 MariaDB 上时，A 项目的会话确实读不到 B 项目的行」。
这就是本批补的集成测试。

## 2. 做法

新增 `backend/src/test/java/com/qa/autotest/db/TenantIsolationIT.java`，`@Tag("it")`：

- 连真实 MariaDB（`DB_NAME` 必须以 `_it` 结尾，断言兜底防误清开发库），
  Flyway `clean() + migrate()` 从空库起 schema，**不用 H2**（ADR-0002：方言差异会掩盖问题）；
- 每个用例前重置种子数据：两个项目 `IT-A` / `IT-B`，各一条 run（`IT-RUN-A` / `IT-RUN-B`）；
- 用**真实实现** `RunRepositoryJdbc`、`RunMetadataRepositoryJdbc`，不 mock，不走服务层。

## 3. 用例与它证明了什么

| 用例 | 断言的结论 |
|---|---|
| 补录项按项目隔离 | 用 B 的项目 id 查 A 的 run，返回空集合而不是串数据；反向亦然 |
| 运行查询按项目隔离 | `findByRunId` 的 `project_id` 条件真的生效，同 runId 在别的项目下查不到 |
| 同键重复写入不新增行 | `INSERT ... ON DUPLICATE KEY UPDATE` 命中唯一键 `(run_id, meta_key)`，只更新值 |
| 删除不跨项目 | 删除 A 的 `DUT` 不影响 B 的同名键——删除语句同样带 `project_id` |
| 删除运行级联清理补录项 | 外键 `run(run_id) ON DELETE CASCADE` 在真实库上确实生效 |
| 守卫拦截未带租户条件的 SQL | `run_metadata` 已登记进黑名单，未带 `project_id` 的 SQL 抛 `FORBIDDEN` |

## 4. 读数

| 项 | 命令 | 结果 | 日志 |
|---|---|---|---|
| 隔离集成测试 | `DB_NAME=robot_it mvn -o -Pit test -Dtest=TenantIsolationIT` | **Tests run: 6, Failures: 0, Errors: 0**（8.7 s） | `tenant-isolation-it.log` |
| 后端全量（默认不含 it） | `mvn -o -B test` | **590 / 0 / 0**（与上批一致，本批只新增 IT） | `backend-test-summary.txt` |

## 5. 仍未验

1. **只覆盖了 `run` 与 `run_metadata` 两条链路**。其余 20 余个租户表的仓储是同一写法
   （SQL 常量里带 `project_id` + 守卫），但未逐条集成覆盖；逐个铺开成本高，
   建议改为「新增租户表时必须同时加一条隔离用例」的约定。
2. **DB 层不阻止跨项目写**：`run_metadata` 的外键只约束 `run_id`，若有人绕过服务层的
   `requireRun(projectId, runId)` 直接调仓储，可以用 B 的项目 id 给 A 的 run 写补录项。
   当前依赖服务层前置校验；若要 DB 级兜底，需要给 `run` 加 `(run_id, project_id)` 复合唯一键
   并把外键改成复合外键——本批未做。
3. **第 1 层（`access.bindProject`）的越权拒绝**由 HRD-01 的授权矩阵覆盖，不在本文件重复。
