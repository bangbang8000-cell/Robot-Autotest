# W5 / IAMX-01..04 组授权 · 交付证据（2026-11-06）

## 实现

| 包 | 交付 |
|---|---|
| IAMX-01 | `db/migration/V8_3__group_auth.sql` 五张表；`GroupService` 用户组 CRUD / 成员管理；`GroupController`；`GroupsView.vue` |
| IAMX-02 | `EffectiveRole`（NONE/VIEWER/ENGINEER/PM/ADMIN）+ `EffectiveRoleResolver` 单点合并直接角色与组角色取最高；缓存按 `(user, project)`，变更后 `invalidate(projectId)` |
| IAMX-03 | `SuperAdminViewService` 非管理员 403；`GET /api/admin/overview` 跨项目统计 |
| IAMX-04 | `project_template` + `test_scenario`；`ScenarioService` 固定 `parent_scenario_id = NULL`（D5 一层、无 DAG） |

## 实测

- `EffectiveRoleTest`：**82 条**越权矩阵（5 角色 × 4 动作 × 4 项目上下文，要求 ≥ 60）。
- `ScenarioServiceTest`：源码级守门，`createScenario` 必须写 `VALUES (?, ?, ?, NULL)`，且不得出现 `parent_scenario_id = ?`。
- 后端全量 `mvn -B test` 771 条全绿；前端 `GroupsView.vue` 类型检查 / eslint / vitest 全绿。
- 迁移 `V8_3` 实跑并核表。

## 边界

- D4（组授权映射项目角色）/ D5（一层编排、不做 DAG）已按向导裁决实现，无残留产品口径缺口。
- 真实账号 HTTP 全链路属 v0.2 历史包 `IAM-05` / `HRD-01` 的现场缺口，不在本波重复主张。
- G8 不申请。

## C9 新表登记

`user_group`、`user_group_member`、`project_group_role`、`project_template`、`test_scenario`。
