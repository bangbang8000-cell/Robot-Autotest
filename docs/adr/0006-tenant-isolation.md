# ADR-0006 · 租户隔离：上下文 + 强制守卫 + 元测试

- **状态**：已采纳
- **日期**：2026-11-02
- **决策人**：silence（技术）、大师（评审）
- **约束范围**：v0.2 起所有带 `project_id` 的业务表；新增表默认加入黑名单

## 背景

v0.1 的 schema 已给 38 张表留了 `project_id`，但**没有任何机制强制查询带上它**。
v0.2 DEV-PLAN §2.3 原方案是 MyBatis `Interceptor` 解析 SQL 自动追加 `project_id = ?`。

实际情况：v0.1 的 `backend/pom.xml` 只引入了 `spring-boot-starter-jdbc`（JdbcTemplate），
**没有 MyBatis**；引入 MyBatis 只为一个拦截器，会多一层依赖与 SQL 方言适配面。
同时，「自动改写任意 SQL」对带 JOIN / UNION / 子查询的语句风险高（计划 §7 已列该风险）。

## 选项

| 选项 | 优点 | 代价 | 为什么不选 |
|---|---|---|---|
| ① 引入 MyBatis + Interceptor 自动追加条件 | 覆盖面广、开发者无感 | 新增框架；复杂 SQL 改写易错；联表场景难以验证 | 依赖面与风险不划算 |
| ② 完全靠开发者自觉 + Code Review | 零成本 | 一定会漏；漏了静默跨项目泄露 | 不可接受 |
| ③ **上下文 + 仓储强制守卫 + 兜底元测试**（选它） | 无新框架；守卫失败即抛异常；元测试证明守卫真的在拦 | 需在仓储方法里显式传 `projectId`；守卫只校验不自动改写 | — |

## 决定

三层防护：

1. **上下文**：`TenantContext`（ThreadLocal）保存当前 `userId` 与 `projectId`。
   由认证拦截器从 `Authorization: Bearer <token>` 与 `X-Project-Id` 注入，请求结束必清理。
2. **强制守卫**：所有黑名单表的 SQL 必须经过 `TenantGuard.requireProjectScope(sql, table)`。
   守卫检查 SQL 中是否出现 `project_id`（或 `p.project_id`）条件；缺失即抛 `BusinessException(FORBIDDEN)`，
   并把表名写进消息。仓储层一律从 `TenantContext.requireProjectId()` 取值绑定，不接受前端传入的 projectId。
3. **兜底元测试**：测试里故意写一条不带租户条件的 SQL 调守卫，断言抛异常；
   再写一条带条件的，断言通过。**证明守卫真的在工作**，而不是只存在于文档里。

白名单（登录前 / 全局）：`flyway_schema_history`、`app_user`、`role`、`system_setting`、`audit_log`。
黑名单（必须带租户）：`project`、`project_setting`、`project_label`、`device`、`device_port`、`link`、
`credential`、`env_profile`、`topology_node`、`test_case`、`test_set`、`test_set_item`、`robot_file`、
`robot_file_version`、`case_requirement`、`schedule`、`execution_job`、`run`、`export_record`。

**资源级校验在 service 层再做一次**：同一接口被跨项目调用时，守卫只看「SQL 带了条件」，
不保证条件值等于调用者有权访问的项目。service 必须校验 `projectId` 是否属于当前用户。

## 后果

- 正面：不引入新框架；漏写条件的查询在开发期就失败，不会静默泄露。
- 负面：守卫只做「有没有条件」的语法检查，不判断条件值是否正确——所以 service 层资源级校验不可省。
- 触发复核：若将来引入 MyBatis-Plus 或大量动态 SQL，应重新评估是否改用 AST 级改写；
  但**无论换什么机制，第 3 层元测试必须保留**。

## 关联

- WBS：IAM-04 / IAM-05
- 相关文件：`backend/src/main/java/com/qa/autotest/common/tenant/`、`TenantGuardMetaTest.java`
- 上游依据：v0.2 DEV-PLAN §2.3、PRD FR-v0.2-07
