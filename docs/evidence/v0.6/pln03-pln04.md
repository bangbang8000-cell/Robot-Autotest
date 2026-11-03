# PLN-03 / PLN-04 交付证据（v0.6 自动化）

> 代码不 git commit；本文所有数字均来自本机真实执行输出，未做估算。
> 生成日期：2026-11-03；环境：Windows + JDK 25.0.2 + Maven 3.9.16 + MySQL 8.0（127.0.0.1/robot_dev）。

## 0. 改动清单

**迁移**
`db/migration/V6_3__ci_trigger.sql`、`db/down/V6_3__ci_trigger.down.sql`。

**后端新增**
`domain/{ci/{CiTriggerRecord,CiWritebackStatus},iam/ProjectApiToken}.java`；
`application/ci/{CiProperties,CiStatusClient,CiTokenService,CiTriggerCommand,CiTriggerResult,CiTriggerService,CiWritebackService,CiWritebackSupport,GitHubTokenSource,TokenRateLimiter}.java`；
`application/repository/{CiTriggerRepository,ProjectApiTokenRepository}.java`；
`application/execution/RunCompletionListener.java`；
`infrastructure/{CiTriggerRepositoryJdbc,ProjectApiTokenRepositoryJdbc,GitHubCiStatusClient,EnvironmentGitHubTokenSource}.java`；
`api/ci/CiTriggerController.java`。

**后端修改（最小改动）**
`RunService`（新增 7 参构造 + `List<RunCompletionListener>`，终态提交后通知，异常吞掉；保留 6 参构造兼容既有测试）、
`PlanScheduleService`（新增 `triggerCi`，`trigger_type='ci'`）、
`RunOrigin`（新增 `CI` 与 `ci(planId)`）、
`ReportIngestService`（CI run 入库时补 `COMMIT_SHA / BUILD / CI_REF`，40 字符截断；新增可选 `CiTriggerRepository` 构造，保留 5 参构造）、
`common/ErrorCode` + `api/GlobalExceptionHandler`（新增 `TOO_MANY_REQUESTS` → 429）、
`api/WebConfig`（`/api/ci/**` 从用户会话拦截中放行，由 API token 自认证）、
`common/tenant/TenantGuard`（登记 `ci_trigger`，`api_token` 进白名单——令牌按哈希在租户上下文之前查询）。

**后端测试新增**
`application/ci/{CiTestSupport,CiTriggerServiceTest,CiWritebackTest}.java`。

**文档新增/修改**
`docs/adr/ADR-0011-双仓与Gitea决策.md`、`docs/CI接入指南.md`、`docs/adr/README.md`（索引加 0011）。

**前端**
本包**未改前端**（无需构建/ lint）。原因：PLN-03/04 的交付面是接口、回写与文档；
「内网镜像仓（NAS）不支持回写」已在 `ci_trigger.writeback_status`、触发响应 `writeback` 字段与文档中体现，
前端暂无 CI 触发记录页，故不改 UI。

## 1. 迁移与表结构

| 项 | 值 |
|---|---|
| 版本 | `V6_3__ci_trigger.sql` → **6.3** |
| 回滚 | `db/down/V6_3__ci_trigger.down.sql`（先删 `ci_trigger`，再删 `api_token` 两列） |
| 应用命令 | `mvn -B flyway:migrate '-Dflyway.placeholderReplacement=false'`（env：DB_HOST/PORT/NAME/USERNAME/PASSWORD） |
| 结果 | `Successfully applied 1 migration to schema 'robot_dev', now at version v6.3`；`mvn flyway:info` 的 6.3 行 `State=Success`（`flyway-migrate-pln03.log`、`flyway-history-pln03.log`） |
| 历史表 | 16 条迁移全部 `Success`，末行为 `6.3 ci trigger` |

`flyway_schema_history` 实测（`mvn flyway:info` 输出末尾）：

```
| Versioned | 6.1     | execution plan  | SQL  | 2026-11-03 17:26:52 | Success | No       |
| Versioned | 6.2     | plan dependency | SQL  | 2026-11-03 17:26:52 | Success | No       |
| Versioned | 6.3     | ci trigger      | SQL  | 2026-11-03 18:12:53 | Success | No       |
Schema version: 6.3
```

> `flyway:info` 有一条 `1142 SELECT denied ... user_variables_by_thread` 警告，是 MySQL 8 对
> `robot` 账号的元数据权限限制，不影响迁移状态与结果（14.10 版本已知行为）。

### 1.1 存量兼容策略（显式）

- 新增 `api_token.project_id BIGINT NULL`、`api_token.scopes VARCHAR(128) NOT NULL DEFAULT ''`。
- 存量行 `token_kind='session'`（V2_1 默认值），两列取默认值后**行为完全不变**：
  `SessionTokenRepository` 只按 `token_kind='session' + token_hash` 查询，不读这两列。
- 项目 API token 必须是 `token_kind='api' AND project_id IS NOT NULL` 且 `scopes` 含 `ci:trigger`；
  `project_id` 为空的 api token 一律 403，**不会退化成「可访问全部项目」**。
- `scopes=''` 表示「无 scope」，不是「全部 scope」。

### 1.2 `ci_trigger`

> DEV-PLAN §4 规划期把这张表写作 `trigger_log`；本包按 WBS 任务口径落地为 **`ci_trigger`**，
> 语义一致（幂等键 + 回写状态），文档与代码统一用 `ci_trigger`。

`UNIQUE(plan_id, commit_sha)` 是幂等键；`run_id` 建 run 后回填；
`writeback_status` 取值 `pending/success/failed/skipped/not_supported`；
`writeback_error` 只存失败原因，**永不存令牌**。

## 2. 接口契约

### 2.1 请求

```http
POST /api/ci/trigger
Authorization: Bearer <项目 API token>
Content-Type: application/json

{"planId":12,"commitSha":"<40 hex>","ref":"refs/heads/main","build":"SW-2026.11-rc1","repo":"owner/repo"}
```

- `build` 缺省取 `commitSha` 前 8 位。
- `planId` 必须是令牌绑定项目的计划；否则 **403**（不泄露计划是否存在）。
- `commitSha` 必须 1~64 位十六进制，统一小写后作为幂等键；否则 **400**。

### 2.2 响应（新建与重复均为 **202 Accepted**）

```json
{"code":200,"data":{"runId":"RUN-20261103-000123","duplicated":false,"writeback":"pending"}}
```

```json
{"code":200,"data":{"runId":"RUN-20261103-000123","duplicated":true,"writeback":"pending"}}
```

`writeback`：`pending` / `disabled`（`ci.writeback.enabled=false`）/ `not_supported`（内网镜像仓（NAS）仓库）。

### 2.3 状态码一览

| 码 | 场景 |
|---|---|
| **202** | 新建成功；或幂等命中（`duplicated=true`，不新建 run） |
| **400** | `planId` 非法 / `commitSha` 非十六进制或为空 / `build`/`ref`/`repo` 超长 |
| **401** | 缺 `Bearer`、令牌哈希不匹配、已吊销或已过期 |
| **403** | 令牌未绑定项目；缺 `ci:trigger` scope；计划不属于该项目 |
| **429** | 同令牌每分钟超过 10 次 |

### 2.4 审计

每次触发都写 `audit_log`：

- `CI_TRIGGER`：`token=<令牌名>, token_id=<id>, plan=<planId>, commit=<sha>, build=..., run=<runId>`；
- `CI_TRIGGER_DUPLICATED`：同上（`run=` 为已有 runId）；
- `CI_WRITEBACK`：`run=..., state=..., result=success|failed|skipped|not_supported, target=<报告页 URL>`。

**不记录令牌明文**（服务层从未接触明文；认证失败信息也不回显明文，有单测）。

## 3. 语义逐条

| 规则 | 实现 | 证据 |
|---|---|---|
| 不走用户会话 | `WebConfig` 放行 `/api/ci/**`；`CiTokenService` 读 `Authorization` 并按 `token_hash` 查 `token_kind='api'` | `CiTriggerServiceTest.无效令牌不回显明文` |
| 令牌必须绑定项目 | `ProjectApiToken.projectId` 为 null → 403 | 代码 `CiTokenService` |
| scope 必须含 `ci:trigger` | 认证与触发服务双重校验；空串不是全量 scope | `CiTriggerServiceTest.scope缺失403` |
| plan 必须属于项目 | `plans.find(token.projectId(), planId)` 为空 → 403 | `CiTriggerServiceTest.令牌项目不匹配403` |
| 幂等 | 先查 `(project_id, plan_id, commit_sha)`；命中返回已有 runId；插入唯一键冲突被捕获后回读 | `CiTriggerServiceTest.重复同commit返回duplicated` |
| 并发兜底 | `DataIntegrityViolationException` → 回读胜者 → `duplicated=true`，不 500 | 代码 `CiTriggerService` catch 分支 |
| 限流 | 令牌级滑动窗口，默认 10 次/60 秒；超限 429 | `CiTriggerServiceTest.限流第11次429` |
| 异步执行 | run 落库后立即返回；`PlanRunDispatcher` 异步派发，接口不等结果 | 代码 `PlanScheduleService.triggerCi` |
| `trigger_type='ci'` | `RunOrigin.ci(planId)` | 代码 `RunOrigin`；报告 metadata `TRIGGER_TYPE=ci` |
| metadata 注入 | 入库时补 `COMMIT_SHA`（40 字符）、`BUILD`、`CI_REF`（后两者截断到 40） | 代码 `ReportIngestService.fit` |
| 完成钩子 | `application/execution/RunCompletionListener`；`RunService` 在有事务时 `afterCommit` 执行，异常吞掉并 warn | `CiWritebackTest.终态钩子触发且异常被吞`、`中间态不触发` |

## 4. 回写语义

| 场景 | `writeback_status` | 是否发 HTTP | `writeback_error` |
|---|---|---|---|
| FINISHED（passed） | `success` | 是，`state=success` | 空 |
| FAILED | `success`（回写调用成功；GitHub 侧 `state=failure`） | 是，`state=failure` | 空 |
| 回写调用异常 / 非 2xx | `failed` | 是 | `github_error: ...` 或 `github_http_<code>` |
| `ci.writeback.enabled=false`（默认） | `skipped` | **否** | `disabled` |
| 未配置 `GITHUB_STATUS_TOKEN` | `skipped` | **否** | `token_missing` |
| `repo` 为内网镜像仓（NAS）（`ssh://` / `git@` 或运维配置的仓库标识） | `not_supported` | **否** | 内网镜像仓（NAS）无 HTTP status API |
| 非 CI 触发的 run | 无记录，静默跳过 | 否 | — |
| CANCELED 等非 passed/failed 终态 | 不更新，仍为 `pending` | 否 | — |

- `target_url = ci.writeback.target-base-url + /reports/{runId}`。
- 回写失败**只更新 `ci_trigger`**，不写 run 表，异常在监听器边界被吞掉 → run 结论不变。
- 令牌只从环境变量 `GITHUB_STATUS_TOKEN` 读取，不落库、不落日志、不进 `writeback_error`。

## 5. 测试真实计数

命令：`mvn -B test`（cwd=`backend`，JDK 25.0.2，Maven 3.9.16）

```
[INFO] Tests run: 311, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

新增两类：

```
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 -- in com.qa.autotest.application.ci.CiTriggerServiceTest
Tests run:  9, Failures: 0, Errors: 0, Skipped: 0 -- in com.qa.autotest.application.ci.CiWritebackTest
```

全量中 `ArchUnit` 分层规则（api 不 import infrastructure、common 不依赖 domain、domain 无 Spring 注解等）
一并通过。完整日志：`docs/evidence/v0.6/mvn-test-pln03.log`。

> 日志里可见 JaCoCo 对 JDK 25 类文件的 `Unsupported class file major version 69` 报错，
> 这是既有 `jacoco` 版本与 JDK 25 的兼容问题；测试仍全部执行并通过，与本次改动无关。

## 6. 未验证事项（必须保留）

| 项 | 状态 | 说明 |
|---|---|---|
| **GitHub 真实 commit status 回写** | **未验证（无 `GITHUB_STATUS_TOKEN`）** | 只有伪 HTTP 客户端的单测；真实 GitHub API 未调用过 |
| GitHub Actions 端到端 | **未验证** | 指南中的 workflow 示例按接口契约编写，未在真实 GitHub 上跑过 |
| 8081 运行实例上的接口实测 | **未验证** | 本包要求不重启 8081；新接口需重启后端后才生效 |
| 内网镜像仓（NAS）状态回写 | **不支持** | 代码固定返回 `not_supported` 并写审计，不发起请求 |
| 多实例限流 | 未验证 | 当前为单实例内存计数，扩多实例需共享存储 |

## 7. 证据文件

| 文件 | 内容 |
|---|---|
| `docs/evidence/v0.6/mvn-test-pln03.log` | 全量 `mvn -B test` 原始输出（311 通过） |
| `docs/evidence/v0.6/flyway-migrate-pln03.log` | V6_3 迁移应用输出 |
| `docs/evidence/v0.6/flyway-history-pln03.log` | `flyway_schema_history`（`mvn flyway:info`，版本 6.3） |
| `docs/adr/ADR-0011-双仓与Gitea决策.md` | 双仓决策 |
| `docs/CI接入指南.md` | 接入、限流、回写、排障 |
