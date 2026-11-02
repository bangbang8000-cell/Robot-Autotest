# v0.1 地基 · 测试计划

**文档编号** RA-REL-2026-001-TEST ｜ **版本** v0.1 ｜ **门禁** G1

**被测范围** 工程骨架、分层约束、数据库基线、CI 流水线、环境基线 ｜ **对应 PRD** FR-v0.1-01 ~ 22

---

## 1. 测试目标与范围

### 1.1 这一版测什么

v0.1 没有业务功能，测试的对象是**约束**，不是**功能**。所以测试设计的核心不是「功能对不对」，而是「**违规能不能被拦住**」：

| 被测对象 | 要证明的事 |
|---|---|
| 分层架构 | 有人（或 AI）写了跨层调用，CI 会红，而且报错指得出是哪一行 |
| 数据库迁移 | 空库能建、能拆、能重建；表结构与规范一致；关键约束真的生效 |
| 构建链路 | 干净环境能一次跑通；同一命令连跑两次结果相同 |
| CI 门禁 | 前一道关失败时后一道关不启动（不是全跑一遍再一起报） |
| 环境基线 | 换一台机器（149 ↔ Windows）照文档能跑起来 |

### 1.2 这一版不测什么（明确写出来，防止假覆盖）

| 不测 | 原因 | 何时补 |
|---|---|---|
| 任何业务功能 | 本版无业务代码 | v0.2 起 |
| 性能与并发 | 没有可压的对象 | v0.4（L5） |
| 安全（越权、注入） | 没有鉴权与入参 | v0.2（L6） |
| 端到端用户旅程 | 没有可走的旅程 | v0.5（L4） |
| UI 视觉与交互 | 只有占位页 | v0.2 起 |
| 浏览器兼容性 | 内部平台，只支持 Chromium | v1.0 视需要 |

> **不测的必须写出来。** 一张写着「本版覆盖 XXX 全部功能」却没有任何业务用例的测试报告，比没有报告更糟。

---

## 2. 测试分层分配

| 层 | 本版用途 | 工具 | 归属 |
|---|---|---|---|
| L1 单元 | 统一响应体、异常映射、工具类 | JUnit 5 + AssertJ | AI |
| L2 集成 | 迁移、约束、仓储骨架 | Spring Boot Test（`@Tag("it")`）+ 真实 MariaDB | AI |
| 架构测试 | 分层规则 + **元测试**（证明规则能拦住违规） | ArchUnit | AI |
| 构建验证 | bootstrap / build / lint 幂等 | Make + shell 断言 | AI |
| L3-L7 | 不适用 | — | — |

**覆盖率**：本版不设红线（骨架代码覆盖率无意义）。只统计并记录基线，供 v0.2 起对比。

---

## 3. 用例清单

### 3.1 构建与启动（对应 FR-v0.1-01/02/03）

| ID | 用例 | 步骤 | 期望 | 层 |
|---|---|---|---|---|
| TC-v0.1-001 | 干净环境一键启动 | 新目录 `git clone` → `make bootstrap` | 退出码 0；耗时 ≤30 分钟（记录实际值） | 构建 |
| TC-v0.1-002 | bootstrap 幂等 | 同一目录连跑两次 `make bootstrap` | 第二次退出码 0，无「已存在」类报错 | 构建 |
| TC-v0.1-003 | build 幂等 | 连跑两次 `make build` | 产物哈希一致；退出码均 0 | 构建 |
| TC-v0.1-004 | clean 彻底 | `make build` → `make clean` → `git status --porcelain` | 输出为空（无残留产物） | 构建 |
| TC-v0.1-005 | 换行符一致 | Windows 克隆后 `git ls-files --eol` | 无 `w/crlf` 条目（统一 LF） | 构建 |

### 3.2 分层约束（对应 FR-v0.1-04/05）

| ID | 用例 | 步骤 | 期望 | 层 |
|---|---|---|---|---|
| TC-v0.1-010 | 正常结构通过 | `mvn test -Dtest=LayerRulesTest` | 绿 | 架构 |
| TC-v0.1-011 | **domain 依赖 api 被拦** | 元测试：对 fixture 包 `fixture.violation.domain2api` 跑规则 | 抛 `AssertionError`，消息含违规类全名 | 架构 |
| TC-v0.1-012 | **api 直接调 infrastructure 被拦** | 元测试：fixture `fixture.violation.api2infra` | 同上 | 架构 |
| TC-v0.1-013 | **domain 带 JPA 注解被拦** | 元测试：fixture `fixture.violation.domainjpa` | 同上 | 架构 |
| TC-v0.1-014 | 分层无环 | 全包 slices 检查 | `beFreeOfCycles` 通过 | 架构 |

**元测试的写法**（这是本版最容易被糊弄过去的地方，必须这样写才叫真验证）：

```java
@Test
void 规则能拦住故意违规的包() {
    JavaClasses bad = new ClassFileImporter()
        .importPackages("com.qa.autotest.fixture.violation.api2infra");
    assertThatThrownBy(() -> 分层规则.check(bad))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("infrastructure");
}
```

> 只写「正常代码能过」不算测过分层规则——那只能证明规则没写错到把所有代码都判违规。

### 3.3 数据库基线（对应 FR-v0.1-12~15）

| ID | 用例 | 步骤 | 期望 | 层 |
|---|---|---|---|---|
| TC-v0.1-020 | 空库建到 38 表 | 删库 → `flyway migrate` → `SHOW TABLES` | 业务表恰好 38（不含 `flyway_schema_history`） | L2 |
| TC-v0.1-021 | 回滚到空 | `make db-undo` → `SHOW TABLES` | 仅剩 `flyway_schema_history` | L2 |
| TC-v0.1-022 | 往返稳定 | migrate → undo → migrate | 两次 migrate 后表集合完全一致（diff 为空） | L2 |
| TC-v0.1-023 | 审计四列齐全 | 遍历 38 表 `information_schema.COLUMNS` | 缺 `created_at/created_by/updated_at/updated_by` 的表数 = 0 | L2 |
| TC-v0.1-024 | 命名规范 | 检查表名 `snake_case`、主键名 `id`、时间类型 `datetime(3)` | 违规数 = 0 | L2 |
| TC-v0.1-025 | `run_id` 唯一 | 插入两条相同 `run_id` | 第二条报 `Duplicate entry` | L2 |
| TC-v0.1-026 | **租约天然互斥** | 同一 `resource_key` 插入两条 | 第二条报主键冲突（这是 v0.4 并发正确性的地基） | L2 |
| TC-v0.1-027 | 外键真的生效 | 插入引用不存在 `project_id` 的记录 | 报外键约束错误（不是静默成功） | L2 |
| TC-v0.1-028 | 字符集 | `SHOW CREATE TABLE` 全表 | 均为 `utf8mb4` | L2 |

### 3.4 配置与安全（对应 FR-v0.1-07/21）

| ID | 用例 | 步骤 | 期望 | 层 |
|---|---|---|---|---|
| TC-v0.1-030 | 缺环境变量启动失败 | 清空 `DB_PASSWORD` 后启动 | 启动失败，日志含变量名 `DB_PASSWORD` | L2 |
| TC-v0.1-031 | 无明文凭据 | `git grep -nEi "password\s*=\s*['\"]"`（排除 md） | 无命中（退出码 1） | 构建 |
| TC-v0.1-032 | 无外部地址 | 后端启动时抓 `ss -tnp` + 日志扫描 | 无对外网连接（对应 N13） | L2 |

### 3.5 前端骨架（对应 FR-v0.1-08~11）

| ID | 用例 | 步骤 | 期望 | 层 |
|---|---|---|---|---|
| TC-v0.1-040 | 构建成功 | `npm run build` | 退出码 0，`dist/index.html` 存在 | 构建 |
| TC-v0.1-041 | 6 路由可达 | 依次访问 `/overview /assets /cases /runs /reports /admin` | 均 200，刷新不 404（history 模式回退配好） | L4-lite |
| TC-v0.1-042 | i18n 切换 | 切 en-US → 刷新 | 菜单文案变化且刷新后保持 | L4-lite |
| TC-v0.1-043 | 请求封装 | 页面调 `/api/_ping` | 拿到统一结构并渲染 | L4-lite |

> 本版 L4 只做「页面能不能开」的冒烟，不叫端到端。

### 3.6 CI 门禁行为（对应 FR-v0.1-16~18）

| ID | 用例 | 步骤 | 期望 | 层 |
|---|---|---|---|---|
| TC-v0.1-050 | 三关顺序 | 提交一个 lint 错误 | `lint` 红；`test`/`build` 状态为 skipped 而非 failed | CI |
| TC-v0.1-051 | 测试失败阻断构建 | 提交一个必失败单测 | `build` 不启动 | CI |
| TC-v0.1-052 | 集成测试可重复 | 连续两次触发 workflow | 两次均绿，第二次不受第一次数据影响 | CI |
| TC-v0.1-053 | 路径过滤正确 | 只改 `docs/` 提交 | 不触发后端构建（或快速跳过），不误报红 | CI |

---

## 4. 环境与数据

| 项 | 内容 |
|---|---|
| 数据库（CI） | 149 本机 MariaDB，库 `robot_it`，每轮 `DROP + CREATE + migrate` |
| 数据库（本地） | 经 SSH 隧道连 `robot_it_local`，与 CI 库隔离（ADR-0002） |
| 运行时 | 149：Ubuntu 26.04 / JDK 21+ / Node 22 / MariaDB；本地：Windows + 同版本 JDK/Node |
| 数据 | **无业务数据**；仅 schema 与 flyway 元数据 |
| 隔离要求 | CI 与本地**不得共用同一库**，否则会出现「偶发失败」且查不出原因 |

---

## 5. 执行时机

| 时机 | 跑什么 | 谁触发 |
|---|---|---|
| 每次 commit | `lint` + L1 单元 | CI（pre-push 可选） |
| 每次 PR | 全部 L1 + L2 + 架构测试 + 构建 | CI 自动 |
| 合并到 main | 同上 + 全量（含慢集成） | CI 自动 |
| G1 评审前 | **全部用例 + 人工反向演示一次** | 人工（现场注入跨层引用） |

---

## 6. 退出标准（= G1 检查单）

全部满足才算本版完成：

- [ ] TC-v0.1-001 ~ 005 全绿（构建类，5 条）
- [ ] TC-v0.1-010 ~ 014 全绿，**其中 011/012/013 三条元测试必须存在且真能红**
- [ ] TC-v0.1-020 ~ 028 全绿（数据类，9 条）
- [ ] TC-v0.1-030 ~ 032 全绿（配置安全，3 条）
- [ ] TC-v0.1-040 ~ 043 全绿（前端冒烟，4 条）
- [ ] TC-v0.1-050 ~ 053 全绿（CI 行为，4 条）
- [ ] 用例总数 ≥25 条，全部可在 CI 复跑
- [ ] 人工反向演示：现场注入一条跨层引用，CI 变红并指出违规类
- [ ] 30 分钟冷启动实测记录（含网络条件说明）
- [ ] 无 P0/P1 未关闭缺陷

**未达标处理**：不降级验收。缺哪条补哪条，或在 CCB 上明确记录「这条不适用及理由」，不允许「这次先算了」。

---

## 7. 证据清单

存 `docs/evidence/v0.1/`：

| 文件 | 内容 |
|---|---|
| `bootstrap-timing.log` | 干净 clone → bootstrap 的完整输出与耗时 |
| `archunit-positive.log` | `mvn test -Dtest=LayerRulesTest` 绿 |
| `archunit-negative.log` | 三条元测试红（含 AssertionError 原文） |
| `archunit-manual-demo.md` | 现场注入引用的 diff + CI 红截图说明 |
| `migration-roundtrip.log` | migrate → undo → migrate 三次 `SHOW TABLES` 输出 |
| `schema-audit.txt` | 38 表规范检查结果（审计列、命名、字符集） |
| `ci-three-gates.md` | 三道关在 PR 上的状态截图 + 顺序验证 |
| `secret-scan.log` | `git grep` 扫描输出（空） |

---

## 8. 风险

| 风险 | 影响 | 应对 |
|---|---|---|
| 元测试被省掉，只留「正常代码通过」 | 分层规则形同虚设，v0.4 才发现 domain 被污染 | 退出标准里单列为硬项，评审时打开 fixture 包核对 |
| 集成测试用 H2 图省事 | 迁移在 CI 过、真实库崩（`DATETIME(3)` 差异） | ADR-0002 已否决 H2，评审时查 `pom.xml` 有无 H2 依赖 |
| CI 与本地共库 | 偶发失败查不出根因 | 库名带 `_local` 与 `_it` 区分，环境基线文档写死 |
| runner 未就绪 | WS-05 全部阻塞 | 降级为 `scripts/ci-local.sh` 人工跑，CI 推迟到 v0.2 首包 |
