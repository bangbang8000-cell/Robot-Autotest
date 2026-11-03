# HRD-01 / HRD-02 交付证据（v1.0）

> 生成时间：2026-11-03 ｜ 机器：Windows 本地开发机 ｜ 后端 Spring Boot 3.3.5（JDK 25 运行）/ 前端 Vue 3 + Vite
> 本文件所有计数与退出码均来自同目录下的真实日志，未做任何人工改写。

## 0. 环境与执行命令

| 项 | 值 |
|---|---|
| 后端工作目录 | `Robot-Autotest/backend` |
| Java | `JAVA_HOME=C:\Users\everg\.jdk\temurin-25\jdk-25.0.2` |
| Maven | `C:\Users\everg\.maven\maven-3.9.16\bin\mvn.cmd`，`-B -o`（离线） |
| 前端工作目录 | `Robot-Autotest/frontend`，`npm run build` / `npm run lint` |
| dev 库 | MySQL `127.0.0.1:3306/robot_dev`，用户 `robot`，迁移前版本 v6.3 |
| 运行中的服务 | 8081（后端）与 5173（前端）**按要求未重启**，线上进程仍是本包改动前的代码 |

> JaCoCo 0.8.12 在 JDK 25 上会对 JDK 内部 `major version 69` 类打印
> `IllegalClassFormatException`，属已知工具链告警，不影响测试结果与退出码（日志中
> 全部测试仍为 0 失败）。

---

## 1. HRD-01：权限矩阵 112 格全量校验 + CSV 导出

### 1.1 矩阵来源与统计

- 唯一真源：`backend/src/test/resources/authz-matrix.yaml`（117 行 = 5 行注释 + 112 行数据）。
- 结构：4 角色（ADMIN / PM / ENGINEER / VIEWER）× 7 资源（用户角色 / 项目 / 资产 / 凭据 / 用例 / 计划 / 审计）× 4 动作（READ / WRITE / DELETE / MANAGE）= **112 格**。
- 每行字段：`role, resource, action, expected(ALLOW|DENY|N/A), reason, case_id`。
- 期望分布（由 `authz-matrix.csv` 实测统计）：**ALLOW 62 / DENY 38 / N/A 12**。

| 角色 | ALLOW | DENY | N/A |
|---|---:|---:|---:|
| ADMIN | 25 | 0 | 3 |
| PM | 20 | 5 | 3 |
| ENGINEER | 13 | 12 | 3 |
| VIEWER | 4 | 21 | 3 |

12 个 N/A 全部集中在「审计」的 WRITE / DELETE / MANAGE 三列：审计只增不改，无写、无删、无管理入口，每行均写了中文理由（见 CSV 备注列）。

### 1.2 全量校验测试

`AuthzMatrixTest` 从 YAML 读取每个格子，经 MockMvc 打**真实控制器 + 真实 `AccessControlService`**（仓储用伪实现，做法同既有 `AuthorizationBoundaryTest`）：

- ALLOW：必须 2xx **且证明业务成功**（返回值/删除集合中能看到真实结果，不只断言 HTTP 200）。
- DENY：必须 403，且响应体不得出现项目名/资源存在性信息（防枚举）。
- N/A：不发起请求，断言 `reason` 非空。
- 另加 1 条「登录但无任何项目成员身份」用例：403 或空集，**不得 500**。

实测（`mvn-test-hrd01-hrd02.log` 第 911 行）：

```
[INFO] Tests run: 113, Failures: 0, Errors: 0, Skipped: 0 ... in com.qa.autotest.api.iam.AuthzMatrixTest
```

113 = 112 格 + 1 条无角色用户用例。

### 1.3 CSV 导出与自检

导出命令（在 `backend` 目录执行；测试内部会把相对路径解析到仓库根）：

```powershell
mvn -B -o "-Dtest=AuthzMatrixTest" "-Dexport=docs/evidence/v1.0/authz-matrix.csv" test
```

产物 `docs/evidence/v1.0/authz-matrix.csv`：

- 表头：`角色,资源,动作,预期,实际,用例ID,备注`
- 行数：**113**（表头 + 112 格）
- `实际` 列填真实结果：`ALLOW` / `DENY` / `N/A` / `SKIP`，ALLOW/DENY 行同时带真实 HTTP 码
- N/A 行备注列为 YAML 中的中文理由，非空

自检命令与真实输出：

```bash
$ wc -l docs/evidence/v1.0/authz-matrix.csv
113 docs/evidence/v1.0/authz-matrix.csv

$ grep -c "N/A" docs/evidence/v1.0/authz-matrix.csv
12

$ awk -F"," -f backend/target/check_na.awk docs/evidence/v1.0/authz-matrix.csv
NA_TOTAL=12 NA_EMPTY_REMARK=0
```

> awk 脚本内容（只读检查，非交付代码）：
> `NR>1 && $4 ~ /N\/A/ { if ($7 == "") empty++; total++ } END { printf "NA_TOTAL=%d NA_EMPTY_REMARK=%d\n", total, empty+0 }`

---

## 2. HRD-02：审计日志（10 类关键操作留痕）

### 2.1 数据结构与迁移

- 新增 `db/migration/V7_1__audit_summary.sql`：给 `audit_log` 增加 `operator` / `summary` / `ip` 三列（只加列，不改语义）。
- 回滚：`db/down/V7_1__audit_summary.down.sql`（`DROP COLUMN IF EXISTS ip, summary, operator`）。
- 已应用到 dev 库，Flyway 实测：

```
Migrating schema `robot_dev` to version "7.1 - audit summary"
Successfully applied 1 migration to schema `robot_dev`, now at version v7.1
| Versioned | 7.1 | audit summary | SQL | 2026-11-03 19:37:58 | Success | No |
```

日志：`docs/evidence/v1.0/flyway-migrate-v7_1.log`、`docs/evidence/v1.0/flyway-info-v7_1.log`。

### 2.2 注解与写入纪律

- 新增 `@Audited(action="...", resource="...")`（`application/iam/Audited.java`），标在 12 个关键写方法上，作为动作/资源契约；`AuditAspectTest` 用反射校验声明与实现一致。
- **AOP 降级说明（必须记录）**：本地 m2 无 `spring-boot-starter-aop:3.3.5` / `aspectjweaver:1.9.22`，任务要求不引入新依赖且不联网，因此**未引入 `@Around` 切面**；改由业务方法内**显式调用** `AuditService.recordChange(...)`，行为等价，且更易在单测中断言。
- 三条纪律：
  1. **异步写**：`AuditService` 默认用 `CompletableFuture.delayedExecutor`，不占业务事务；测试注入 `Runnable::run` 同步执行。
  2. **失败只告警**：`write(...)` 捕获 `RuntimeException` → `log.warn("审计写入失败 ...")`，业务照常返回。
  3. **只增不改**：`AuditService` 没有 update / delete 方法；`AuditController` 只有 `GET`；DELETE 返回 **405**。
- 前后摘要：变更类记 `before={...}; after={...}`；删除类只记前值并标 `deleted=true`；新增标 `created=true`。
- 摘要上限 1000 字符，超长截断。

### 2.3 10 类操作落点表（真实方法与 action）

| # | action | 资源 | 落点（生产代码） |
|---|---|---|---|
| 1 | `user:create` | `app_user` | `UserService.create` |
| 2 | `user:delete` | `app_user` | `UserService.delete`（只记前值） |
| 3 | `role:update` | `role` | `RoleService.update` |
| 4 | `project:create` | `project` | `ProjectService.create` |
| 5 | `project:delete` | `project` | `ProjectService.delete`（只记前值） |
| 6 | `member:update` | `project_member` | `ProjectService.addMember` / `updateMemberRole` |
| 7 | `credential:upsert` | `credential` | `CredentialService.create` / `update`（脱敏） |
| 8 | `case:delete` / `set:delete` | `test_case` / `test_set` | `TestCaseService.delete` / `TestSetService.delete`（只记前值） |
| 9 | `schedule:update` / `schedule:delete` | `execution_plan` | `PlanService.update` / `delete` |
| 10 | `lease:force-release` | `resource_lease` | `LeaseService.releaseRun` |

`run:metadata-patch`、`system:deploy`：**当前代码库无对应 HTTP 入口**（全仓库检索无 `@PatchMapping` 元数据补丁接口、无 deploy 接口），按任务要求不硬造，记录为「无入口，待后续」。

### 2.4 脱敏证明

- 摘要构造只放可读元信息：凭据仅 `name=...; target=<username>; configured=true; rotated=<bool>`，**不读取也不拼接 secret**（`CredentialService` 的 `create` / `update` 摘要构造处）。
- `AuditService.sanitize` 对 `password/passwd/pwd/token/secret/credential/口令/密码/密钥` 的键值统一替换为 `***`，支持 `key=value`、`key: value`、JSON `"key":"value"` 三种形态，保留引号以保证 JSON 合法。
- 实测（`AuditAspectTest`，13 tests 全绿）：

```
{"password":"abc"}  →  {"password":"***"}
password=abc; token=xyz; secret=123; 口令=mm  →  password=***; token=***; secret=***; 口令=***
```

- `AuditAspectTest` 中「credential:upsert 只记已配置与目标账号，绝不含密文」用例断言摘要不含明文口令。

### 2.5 查询接口与前端

- `GET /api/audit?projectId=&action=&from=&to=&page=&size=`：仅 **ADMIN 或项目所有者**；其他人 403 且消息不含项目存在性信息；分页归一化（page≥1，size 默认 20、上限 100）。
- `GET /api/audit/export?...`：导出 CSV，固定表头 `时间,操作人,动作,资源类型,资源ID,变更摘要,IP`，上限 10000 行；动作/资源列不做敏感正则替换，避免误伤 `credential:upsert`。
- 前端：
  - 新增 `frontend/src/views/AuditView.vue` + `frontend/src/api/audit.ts`；
  - 路由 `/admin/audit` 挂在「管理」菜单下，**未新增第 7 个一级菜单**；
  - 侧边栏入口 `v-if="auth.isAdmin"`，非管理员不显示；手输 URL 也会被后端 403 拦截；
  - 支持项目/动作/日期筛选、分页、CSV 导出；中英文案已补齐（`locales/zh-CN.ts`、`en-US.ts` 的 `audit` 段）。

### 2.6 审计测试

`AuditAspectTest`（13 tests）：覆盖 10 类动作各自产生记录、凭据脱敏、删除只记前值、业务异常不写「成功」、审计写失败不阻断业务、@Audited 声明一致、脱敏函数。

`AuditQueryTest`（7 tests）：ADMIN 全局查询、ENGINEER 403、PM 无 projectId 403、PM owner 200、筛选与分页归一化、CSV 表头+数据行、DELETE 不被接受（405）。

实测（`mvn-test-hrd01-hrd02.log` 第 1113 / 1136 行）：

```
Tests run: 13, Failures: 0, Errors: 0 ... AuditAspectTest
Tests run: 7,  Failures: 0, Errors: 0 ... AuditQueryTest
```

---

## 3. 全量测试、ArchUnit 与前端构建

| 项 | 命令 | 真实结果 | 证据 |
|---|---|---|---|
| 后端全量 | `mvn -B -o test` | **Tests run: 444, Failures: 0, Errors: 0, Skipped: 0**，`MVN_EXIT_CODE=0` | `docs/evidence/v1.0/mvn-test-hrd01-hrd02.log` |
| ArchUnit | 同上（`LayerRulesTest`） | **6 tests, 0 失败** | 同上第 25722 行 |
| 前端构建 | `npm run build` | **BUILD_EXIT_CODE=0**；产物含 `dist/assets/AuditView-Cpc_pYso.js 4.38 kB` | `docs/evidence/v1.0/frontend-hrd01-hrd02-build.log` |
| 前端 lint | `npm run lint` | **LINT_EXIT_CODE=0**（无输出即无问题） | `docs/evidence/v1.0/frontend-hrd01-hrd02-lint.log` |
| 迁移 | `mvn -B -o flyway:migrate -Dflyway.placeholderReplacement=false` | **Successfully applied 1 migration, now at v7.1** | `docs/evidence/v1.0/flyway-migrate-v7_1.log` |

> 构建日志末尾的 `node.exe : NativeCommandError` 是 PowerShell 把 npm 的 stderr 当错误流的已知噪声；`✓ built in 4.81s` 与退出码 0 证明构建成功。

---

## 4. 本包发现并修复的真实缺陷

1. **`GlobalExceptionHandler` 把「请求方法不支持」翻译成 500**：对只读审计接口 `DELETE /api/audit` 返回 500，看起来像服务故障。已新增 `HttpRequestMethodNotSupportedException` → **405** 映射与 `ErrorCode.METHOD_NOT_ALLOWED(40500)`，`AuditQueryTest.auditIsAppendOnly` 由 500 变为 405。
2. **CSV 导出把敏感正则套在动作列上**：`credential:upsert` 中的 `credential:` 被当成敏感键值，导出成 `credential:***`。已改为只对摘要脱敏，动作/资源/ID/IP 原样输出。
3. **`sanitize` 不识别 JSON 形态且会吃掉值引号**：`{"password":"abc"}` 未脱敏；修正后兼容键名后的引号，并保留值两侧引号，输出 `{"password":"***"}`。

以上修复均在 `mvn -B test` 全绿下验证，未放宽任何既有测试断言。

---

## 5. 未覆盖项与风险

1. **真实 HTTP 全链路未跑**：8081 服务按要求未重启，仍是改动前代码；本地 WSL 账号/真实库的「登录 → 产生审计 → 查询/导出」端到端未执行。当前证据为 MockMvc + 伪仓储 + dev 库迁移。
2. **AOP 未引入**：见 §2.2；若后续允许联网，可补 `spring-boot-starter-aop` 后把显式调用收口为 `@Around`，注解契约已就位。
3. **`run:metadata-patch` / `system:deploy` 无入口**：代码库当前没有这两个接口，未硬造；待后续版本补入口时再加审计。
4. **down 脚本未在真实库执行回滚**：避免破坏 dev 库现有数据，仅按 ADR-0003 提供文件；语法与列名已对齐 V7_1。
5. **审计异步线程的租户上下文**：`AuditService.execute` 在调用线程先快照 `projectId/actorId/operator/ip` 再异步写，避免 ThreadLocal 丢失；该点由 `AuditAspectTest` 间接覆盖（测试用同步执行器）。
