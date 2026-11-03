# 后端编码约定（CONVENTIONS-backend）

**文档编号** RA-CONV-2026-001 ｜ **适用** `backend/` ｜ **版本** v0.2

> 本文只写"代码怎么写"。为什么这么定见 `docs/adr/`；任务与门禁见 `docs/plan/WBS-STATUS.md`。
> 新增约定直接改本文（≤250 行），并由 `LayerRulesTest` 或评审至少引用一次（DOC-02 完成判定）。

## 1. 分层与依赖方向

```
api → application → domain
              ↑
       infrastructure（实现 domain / application 定义的端口）
```

| 层 | 允许依赖 | 禁止 |
|---|---|---|
| `api` | `application`、`common` | 直连 `domain` 仓储实现、直写 SQL |
| `application` | `domain`、`common` | 引用 `api`、`infrastructure` 具体类 |
| `domain` | JDK | Spring 注解、Web 类型、`common` 之外的框架 |
| `infrastructure` | `domain`、`application` 端口、`common` | 反向被上述三层引用（由 ArchUnit 守卫） |

- **禁止跨域直接访问别的域的 Repository**，必须经 Service。例：资产域要读项目信息，走 `ProjectService`，不注入 `ProjectRepository`。
- 包内分层由 `LayerRulesTest`（ArchUnit R1~R5 + 无环）守护；新增包先确认规则仍通过。
- 新增对外依赖（尤其 ORM / 缓存）必须写 ADR；当前只允许 `spring-boot-starter-jdbc` + `JdbcTemplate`。

## 2. 命名

| 对象 | 规则 | 示例 |
|---|---|---|
| 包 | 小写、按域划分 | `application.caseasset` |
| 类 | 大驼峰；领域对象不带 `Entity` 后缀 | `TestCase`、`DeviceService` |
| 仓储端口 | `XxxRepository`（接口，放 `application.repository`） | `TestCaseRepository` |
| 仓储实现 | `XxxRepositoryJdbc`（放 `infrastructure.persistence`） | `TestCaseRepositoryJdbc` |
| 控制器 | `XxxController`；路由用复数资源名 | `/api/projects/{id}/cases` |
| 命令对象 | `XxxCommand` / `XxxRequest`（record） | `CaseCommand` |
| 领域事件 / 审计动作 | `资源.动作`，小写点分 | `case.update`、`credential.reveal` |

- API 路径统一 `/api` 前缀；项目内资源**必须**带 `/projects/{projectId}`，路径参数是租户绑定的权威来源。
- DTO 一律 `record`，不暴露可变对象；对外视图不得含 `passwordHash`、`cipherBlob` 等敏感字段。

## 3. 事务

- 一个用例（用户动作）= 一个事务边界，方法上 `@Transactional`，放在 **application 的 Service**，不放 Controller / Repository。
- 只读查询不加事务；跨表写入（台账 + 步骤、导入整批）必须同一事务，任一行失败整体回滚。
- 事务内**不做远程调用、不发消息、不写文件**；需要时用"先落库 + 事务后事件"。
- 批量写按 200 行一批 flush（导入器已实现），避免长事务锁表。

## 4. 异常与响应

- 业务失败抛 `BusinessException(ErrorCode, message)`；`ErrorCode` 只取既有枚举，不新增同义项。
- 参数不合法 → `INVALID_ARGUMENT`（400）；未登录 → `UNAUTHENTICATED`（401）；越权 → `FORBIDDEN`（403）；
  不存在 → `NOT_FOUND`（404）；唯一键冲突 → `CONFLICT`（409）；其余 → `INTERNAL_ERROR`（500）。
- 对外响应统一 `ApiResponse<T>`（`{code, codeName, message, data}`，`code=0` 成功）；异常由 `GlobalExceptionHandler` 兜底。
- **越权响应不得泄露"资源是否存在"**：先判权限，再判存在。
- 不吞异常；`catch` 后必须转换或记录，禁止空 `catch`。

## 5. 租户隔离（IAM-04，最易漏）

1. Controller 首行 `access.bindProject(pathProjectId)`，把项目写进 `TenantContext`；**不信任** `X-Project-Id` 头。
2. 所有租户表 SQL 必须含 `project_id = ?`；拼接 SQL 前调用 `TenantGuard.requireProjectScope(sql, table)`。
3. 白名单表（`app_user`、`role`、`system_setting`、`audit_log`、`flyway_schema_history`）才允许不带项目条件。
4. 新增租户表必须同时加进 `TenantGuard.TENANT_TABLES`，否则守卫直接报 `INTERNAL_ERROR`（故意做成响错，不静默放行）。
5. 元测试 `TenantGuardMetaTest` 必须保持可运行：它证明"漏写条件会被捕获"，不是可选项。

## 6. 凭据与敏感信息

- 明文口令只在入参瞬间存在；落库前 `SecretCipher.encrypt`，读出口只在执行域 `resolveSecret` 且**必写审计**。
- 列表 / 详情接口只返回 `configured` 与时间戳，永不回显明文或密文。
- 日志、审计 detail、异常消息里不得出现口令、密文、完整连接串。
- `CRED_MASTER_KEY` 只从环境变量注入（32 字节 base64），缺失即启动失败；不得写进代码、配置或测试资源。

## 7. 数据访问

- 只用 `JdbcTemplate` + 显式 SQL；不使用字符串拼接值，参数一律占位符。
- 行映射集中在 `infrastructure.persistence`；`RowMapper` 不写业务判断。
- 分页统一 `PageResult<T>(items, total, page, size)`；`size` 由 Controller 夹到 `[1, 200]`。
- 排序字段必须白名单校验，禁止把请求参数直接拼进 `ORDER BY`。
- 时间统一 `Instant`（UTC 存储、前端本地化展示）；数据库列用 `DATETIME(3)`。

## 8. 迁移

- 只写正向 `db/migration/V*__*.sql`，**每份必须配手写 `db/down/*.down.sql`**（ADR-0003）。
- 迁移只加字段 / 索引 / 表，不删不改语义；破坏性变更另开版本并在评审中说明。
- 迁移文件一旦被任一环境执行，不再修改，只能追加新版本。

## 9. 审计

- 每个写操作在 Service 内落一条审计：`audit.record(动作, 资源类型, 资源 id, 摘要)`。
- 摘要只写可公开的标识（用户名、编号），不写敏感值；审计失败不得吞掉主流程异常。
- 读敏感数据（凭据 reveal、导出）也要审计，动作名以 `.reveal` / `.export` 结尾。

## 10. 测试

| 类型 | 位置 | 规则 |
|---|---|---|
| 单测 | `src/test/java/**` | 纯 JVM，不依赖 Spring 上下文优先 |
| 架构测试 | `arch/LayerRulesTest` | 改分层 / 加依赖必须同步规则 |
| 元测试 | `common/tenant/TenantGuardMetaTest` | 证明守卫有效，禁止删 |
| 集成测试 | `@Tag("it")` | 真实 MariaDB 独立库（ADR-0002），`mvn -Pit test` |

- 测试命名用中文描述行为，断言失败信息要能直接定位。
- 不为迁就实现改测试；接口契约变了先改测试再改实现。

## 11. 日志

- 用 SLF4J 占位符 `log.info("run {} finished", id)`，不拼字符串。
- INFO 只记状态跃迁与外部调用结果；DEBUG 记细节；WARN 记可恢复异常；ERROR 记需人工介入。
- 不打印完整请求体 / 响应体（可能含凭据）。
