# IAM-05 越权（授权边界）测试证据

- 测试类：`backend/src/test/java/com/qa/autotest/api/iam/AuthorizationBoundaryTest.java`
- 用例数：**36**（全部 `@Test`，无参数化；每个用例独立构造测试世界）
- 全量日志：`docs/evidence/v0.5/iam05-authz.log`
- 执行命令（cwd=`backend`，Windows PowerShell）：

```powershell
$env:JAVA_HOME='C:\Users\everg\.jdk\temurin-25\jdk-25.0.2'
& 'C:\Users\everg\.maven\maven-3.9.16\bin\mvn.cmd' -B test 2>&1 |
  Tee-Object -FilePath 'E:\workspace\自动化测试\Robot-Autotest\docs\evidence\v0.5\iam05-authz.log'
```

## 1. 全量测试真实结果（摘自日志）

```text
[INFO] Results:
[INFO]
[INFO] Tests run: 204, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
[INFO] Total time:  55.398 s
[INFO] Finished at: 2026-11-03T13:48:11+08:00
```

新增类单独一行：

```text
[INFO] Tests run: 36, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.399 s
       -- in com.qa.autotest.api.iam.AuthorizationBoundaryTest
```

204 = 既有 168 + 本批 36；无失败、无跳过、无 error。

> 日志里可见 JaCoCo / ArchUnit 的 `Unsupported class file major version 69` 告警，
> 成因是本次 `JAVA_HOME` 按任务给定用 JDK 25（字节码 69），而 JaCoCo 0.8.12 / ArchUnit
> 内置 ASM 尚未支持。它们是**非致命告警**，`mvn` 退出码 0，结果 0 失败；ArchUnit 6 条规则
> 仍全部通过。若换回 Java 21 运行，该告警会消失。

## 2. 测试层次（诚实说明）

| 层次 | 条数 | 说明 |
|---|---|---|
| 控制器级（MockMvc） | **36** | `MockMvcBuilders.standaloneSetup(...)` 装配 6 个控制器 + `AuthInterceptor` + `GlobalExceptionHandler`；越权判定走**真实** `AccessControlService`（真实 `PermissionMatrix` / `RoleCode`），仅 `UserRepository`、`ProjectRepository`、各业务仓储为内存桩 |
| 纯服务级 | 0 | 本批未新增“只调 Service”的用例；服务内部的授权调用是通过控制器链路被真实执行的 |
| 真实环境 HTTP 全链路 | 0 | **本地 WSL 真实环境 + 真账号的 HTTP 全链路仍未跑**；本批不连接任何远端环境，也不依赖数据库 |

其他说明：

- 认证维度经 MockMvc 注册真实 `AuthInterceptor` 完成，令牌解析走真实 `AuthService`，仅
  `SessionTokenRepository` 用内存桩（按“哈希 → 用户 + 过期时间”判定）。
- `RunController` 的取消端点不在本批范围，其 `CancelService` 构造参数以占位传入，未被调用。
- 每个用例在独立测试世界里重建用户/项目/成员关系，无跨用例状态泄漏；`TenantContext`
  在 `@AfterEach` 清理。

## 3. 36 条用例表

“不泄露断言”对所有 403 行统一执行：响应体不得包含目标项目名（`Gamma`）、其他项目名
（`Alpha`）、`不存在` 字样；`data` 必须为 `null`。

| 编号 | 身份 | 项目归属 | 资源:动作 | 期望 | 实际 | 断言点 |
|---|---|---|---|---|---|---|
| IAM05-01 | 未认证（无令牌） | — | CASE:READ | 401 | 通过 | `status=401`、`code=40100`、`codeName=UNAUTHENTICATED`、`message=缺少访问令牌`、`data=null` |
| IAM05-02 | 未认证（伪造令牌） | — | CASE:READ | 401 | 通过 | 同上，`message=令牌无效或已过期` |
| IAM05-03 | 未认证（过期令牌） | — | CASE:READ | 401 | 通过 | 同上，`message=令牌无效或已过期` |
| IAM05-04 | 已停用账号 | — | CASE:READ | 401 | 通过 | 同上，`message=令牌无效或已过期`（`enabled=false` 被拦截） |
| IAM05-05 | 空 Bearer 头 | — | CASE:READ | 401 | 通过 | 同上，`message=缺少访问令牌` |
| IAM05-06 | VIEWER | α | CASE:READ | 400 | 通过 | `status=400`、`code=40001`、`codeName=INVALID_ARGUMENT`、`message=X-Project-Id 不是数字` |
| IAM05-07 | VIEWER | α | CASE:READ | 400 | 通过 | 同上（`X-Project-Id` 溢出 long，不落 500） |
| IAM05-08 | 非成员（仅 β） | 目标 γ | EXECUTION:READ | 403 | 通过 | 403 + `code=40300` + `无权访问该项目` + `data=null` + 不泄露 |
| IAM05-09 | 非成员（仅 β） | 目标 γ | EXECUTION:CREATE | 403 | 通过 | 同上（写路径同样先拦项目） |
| IAM05-10 | 非成员（仅 β） | 目标 γ | ASSET:READ | 403 | 通过 | 同上 |
| IAM05-11 | 非成员（仅 β） | 目标 γ | ASSET:DELETE | 403 | 通过 | 同上 |
| IAM05-12 | 非成员（仅 β） | 目标 γ | CASE:READ | 403 | 通过 | 同上 |
| IAM05-13 | 非成员（仅 β） | 目标 γ | CASE:CREATE | 403 | 通过 | 同上 |
| IAM05-14 | 非成员（仅 β） | 目标 γ | CASE:READ（文件工作区） | 403 | 通过 | 同上 |
| IAM05-15 | 非成员（仅 β） | 目标 γ | EXECUTION:READ（报告） | 403 | 通过 | 同上 |
| IAM05-16 | 非成员（仅 β） | γ（存在）vs 999（不存在） | ASSET:READ | 403 | 通过 | 两次响应体**逐字相同**；不含 `Gamma`、不含 `不存在`（防项目枚举） |
| IAM05-17 | 非成员（仅 β） | 头=β、路径=γ | CASE:READ | 403 | 通过 | 伪造 `X-Project-Id` 不能替代路径归属 |
| IAM05-18 | VIEWER | α | CASE:READ | 200 | 通过 | `status=200`、`code=0`（只读允许） |
| IAM05-19 | VIEWER | α | CASE:CREATE | 403 | 通过 | 403 + `无权限：CASE:CREATE` + 不泄露 |
| IAM05-20 | VIEWER | α | CASE:DELETE | 403 | 通过 | 403 + `无权限：CASE:DELETE` + 不泄露 |
| IAM05-21 | VIEWER | α | CASE:DELETE（文件） | 403 | 通过 | 403 + `无权限：CASE:DELETE` + 不泄露 |
| IAM05-22 | VIEWER | α | EXECUTION:CREATE | 403 | 通过 | 403 + `无权限：EXECUTION:CREATE` + 不泄露 |
| IAM05-23 | VIEWER | α | ASSET:DELETE | 403 | 通过 | 403 + `无权限：ASSET:DELETE` + 不泄露 |
| IAM05-24 | ENGINEER | α | CASE:CREATE | 200 | 通过 | `status=200`、`code=0`、`data.testCase.caseCode=TC-ENG-001` |
| IAM05-25 | ENGINEER | α | CASE:DELETE | 200（ALLOW，按 PRD 冻结矩阵） | 通过 | `status=200`、`code=0`；与任务书表述不一致，见 §4 |
| IAM05-26 | ENGINEER | α | ASSET:DELETE | 403 | 通过 | 403 + `无权限：ASSET:DELETE` + 不泄露 |
| IAM05-27 | ENGINEER | α | PROJECT:UPDATE（管成员） | 403 | 通过 | 403 + `无权限：PROJECT:UPDATE` + 不泄露 |
| IAM05-28 | PM | α | PROJECT:UPDATE（管成员） | 200 | 通过 | `status=200`、`code=0`、`data.userId=5`、`data.roleCode=viewer` |
| IAM05-29 | PM | α | PROJECT:CREATE | 403 | 通过 | 403 + `无权限：PROJECT:CREATE` + 不泄露 |
| IAM05-30 | PM | α | PROJECT:DELETE | 403 | 通过 | 403 + `无权限：PROJECT:DELETE` + 不泄露 |
| IAM05-31 | ADMIN（非成员） | 目标 γ | ASSET:READ | 200（ALLOW，设计如此） | 通过 | `status=200`、`code=0`；管理员的跨项目读是明示能力，不是漏洞 |
| IAM05-32 | ADMIN（非成员） | 目标 γ | EXECUTION:READ（报告） | 200（ALLOW） | 通过 | `status=200`、`code=0` |
| IAM05-33 | ADMIN（非成员） | 目标 γ | CASE:READ | 200（ALLOW） | 通过 | `status=200`、`code=0` |
| IAM05-34 | 无角色、无成员 | 无 | PROJECT:READ（可见列表） | 200 空集 | 通过 | `status=200`、`code=0`、`data=[]`，不 500 |
| IAM05-35 | 无角色、无成员 | 无 | CASE:READ | 403 | 通过 | 403 + `无权访问该项目` + 不泄露，不 500 |
| IAM05-36 | 无角色、无成员 | 无 | EXECUTION:READ | 403 | 通过 | 403 + `无权访问该项目` + 不泄露，不 500 |

## 4. 发现的真实缺陷与口径差异

- **需要修改生产代码的缺陷：0 个。** 本批 36 条全部按现有生产实现通过，未放宽任何生产代码，
  也未改动任何既有测试断言。
- **口径差异（已用注释与用例名标注）**：任务书写“ENGINEER 可建用例但**不可删**用例”，但
  `docs/release/v0.2-ledger/PRD.md` §3.1「权限矩阵（本版定稿）」对“测试工程师 × 用例/用例集”
  明确是**增删改查**，`PermissionMatrix` 也是 `CASE` 全动作。测试按生产/PRD 口径把
  IAM05-25 写成 **ALLOW**。要收紧该边界属于权限矩阵变更（HRD-01 范围），不应由测试倒逼
  生产；如需调整，先改矩阵并同步 PRD，再回改本用例预期。
- PM 不能建/删项目、ENGINEER 不能管成员与删设备，均与矩阵一致，未发现越权缺口。

## 5. 仍未覆盖

- **本地 WSL 真实环境 + 真账号的 HTTP 全链路仍未跑**：本批全部是 MockMvc 控制器级，未经过真实
  Web 容器、真实数据库、真实令牌签发与真实项目数据。
- 未纳入本批的端点：凭据（CREDENTIAL）、报告下载/导出、文件上传/下载、审计日志导出的
  越权矩阵；建议在 HRD-01 全矩阵验收时统一补齐。
