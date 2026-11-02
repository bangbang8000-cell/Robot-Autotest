# v0.1 地基 · 开发计划

**文档编号** RA-REL-2026-001-DEV ｜ **版本** v0.1 ｜ **门禁** G1

**范围** WS-01~06 + DOC-01 + DOC-04（机制）｜ **对应 PRD** `PRD.md`（FR-v0.1-01 ~ 22）

---

## 1. 任务拆解与依赖

| WBS | 任务 | 参考人日 | 前置 | AI 自主度 | 完成判定（命令级） |
|---|---|---:|---|---|---|
| WS-01 | monorepo 骨架 + 构建脚本 | 5 | — | A | `make bootstrap && make build` 退出码 0 |
| WS-04 | MariaDB schema v1（38 表）+ Flyway | 6 | WS-01 | A | `mvn flyway:migrate` 后 `SHOW TABLES` = 38 |
| WS-02 | Spring Boot 分层骨架 + ArchUnit | 5 | WS-01 | A | `mvn test` 绿；注入跨层引用后红 |
| WS-03 | Vue 3 骨架 + 路由 + i18n | 5 | WS-01 | A | `npm run build` 成功；6 路由可访问 |
| WS-05 | 自托管 runner + PR 三关 | 6 | WS-02/03/04 | B | PR 上三 job 独立可见；非法提交变红 |
| WS-06 | 环境与配置基线 | 6 | WS-02 | B | 149 与本地各跑一次 `make bootstrap` |
| DOC-01 | `AGENTS.md` | 6 | WS-01~05 | A | 未参与者按它提第一个 PR |
| DOC-04 | ADR 机制 + 首批 2 条 | 3 | — | A | `docs/adr/README.md` 索引链到 2 个文件 |

### 1.1 可并行的两条线（文件级不冲突）

```
WS-01 ─┬─► WS-04（db/ 目录）  ──┐
       ├─► WS-02（backend/）    ├─► WS-05（.github/ + scripts/） ─► WS-06 ─► G1
       └─► WS-03（frontend/）   ┘
```

WS-02 与 WS-03 全程可并行（前后端目录物理隔离）；WS-04 与两者也可并行（只动 `db/`）。**唯一串行点是 WS-05**，因为 CI 要等三个可构建目标都存在。

---

## 2. 目标目录结构

```
Robot-Autotest/
├── backend/                    Spring Boot（Maven 单模块，包内分层）
│   ├── src/main/java/com/qa/autotest/
│   │   ├── api/                控制器、DTO、参数校验、异常映射
│   │   ├── application/        用例编排、事务边界
│   │   ├── domain/             实体、值对象、领域服务、仓储接口（无框架注解）
│   │   ├── infrastructure/     仓储实现、外部适配、（将来）执行器
│   │   └── common/             统一响应、错误码、工具
│   └── src/test/java/...       ArchUnit 规则 + 单测 + 集成测试（@Tag("it")）
├── frontend/                   Vue 3 + Vite + TS
│   └── src/{router,stores,views,components,api,locales,styles}
├── packages/
│   └── editor/                 （v0.3 填充，本版只建空目录 + README 说明边界）
├── db/
│   ├── migration/              Flyway：V1__baseline.sql ...
│   └── down/                   手写���滚：V1__baseline.down.sql（Flyway 社区版无 undo）
├── docs/
│   ├── release/                版本规划与各版三件套
│   ├── plan/WBS-STATUS.md      进度真源
│   ├── adr/                    架构决策记录
│   └── evidence/<版本>/        门禁证据
├── scripts/                    bootstrap.sh / ci-*.sh / db-reset.sh
├── Makefile
├── AGENTS.md
└── README.md
```

---

## 3. 关键技术决策（本版必须落成 ADR）

### 3.1 ADR-0001 · monorepo 单体仓库 + 目录即边界

| 项 | 内容 |
|---|---|
| 背景 | 前后端分离但强耦合（同一批接口契约频繁变），多仓会导致「改一处要提两个 PR」 |
| 选项 | ① 多仓；② monorepo；③ monorepo + 独立构建产物流 |
| **决定** | **② monorepo**，`backend`/`frontend`/`packages` 各自独立构建，仓库统一版本号 |
| 后果 | CI 需要路径过滤（只跑受影响侧）；发布时打两个产物 |
| 关联 | WS-01、N9（本地目录约定） |

### 3.2 ADR-0002 · 集成测试不用 Testcontainers（149 无 Docker）

| 项 | 内容 |
|---|---|
| 背景 | 规划 §8.1 原写 `Testcontainers(MariaDB)`；基线报告实测 **149 无 Docker**（`command -v docker` 空），且规划 §3.2 明确不做容器化 |
| 选项 | ① 装 Docker；② Testcontainers 改远程 Docker；③ **真实 MariaDB 独立库**；④ H2 内存库 |
| **决定** | **③**。CI 用 149 本机 MariaDB 的 `robot_it` 库，每轮 `flyway clean + migrate`；本地需要时用 SSH 隧道连 `robot_it_local`（独立库，与 CI 不冲突） |
| 否决④的理由 | H2 与 MariaDB 在 `DATETIME(3)`、`JSON`、`ON UPDATE` 上语义不同，**集成测试会骗人** |
| 后果 | 集成测试串行执行（同库不能并行）；`mvn test` 默认跳过 `@Tag("it")`，CI 显式 `-Dgroups=it` |
| 关联 | WS-05、FR-v0.1-19 |

### 3.3 ADR-0003 · 迁移回滚用手写 down 脚本

| 项 | 内容 |
|---|---|
| 背景 | Flyway **社区版不支持 `undo`**，而 G1 要求「迁移可回滚」 |
| **决定** | 每个 `V*.sql` 配一份同名 `.down.sql` 放 `db/down/`，`make db-undo` 按版本倒序执行 |
| 后果 | 迁移评审必须同时看 up 与 down；down 脚本纳入 CI 校验（空库 up→down→up 必须成功） |
| 关联 | WS-04、FR-v0.1-13 |

### 3.4 38 表 DDL 骨架的写法

只写「结构必然存在」的部分，字段不全：

```sql
-- V1__baseline.sql（节选，示意粒度）
CREATE TABLE app_user (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  username    VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  status      TINYINT      NOT NULL DEFAULT 1,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by  BIGINT       NULL,
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by  BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**三条硬规则**：① 每表必须有审计四列（`created_at/created_by/updated_at/updated_by`）；② 主键统一 `id BIGINT`，业务唯一键用 `uk_` 前缀；③ 外键约束**只加在强关系上**（`run.test_set_id` 等），弱引用不加 FK 只加索引，避免迁移地狱。

**v0.1 允许缺字段的表**：`execution_job`、`run`、`resource_lease`、`test_result`、`keyword_result`、`run_artifact`、`schedule*`、`retention_policy`、`export_record`。这些表本版只建骨架（主键 + 域内关键唯一键），字段在 v0.4/v0.5/v0.6 用新迁移补。

---

## 4. ArchUnit 规则（本版的核心产出之一）

```java
@AnalyzeClasses(packages = "com.qa.autotest")
class LayerRulesTest {

    @ArchTest
    static final ArchRule domain_独立于框架与上层 =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..api..", "..infrastructure..", "..application..",
                                "org.springframework..", "jakarta.persistence..");

    @ArchTest
    static final ArchRule api_不直接碰基础设施 =
        noClasses().that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule 分层不得成环 =
        slices().matching("com.qa.autotest.(*)..").should().beFreeOfCycles();
}
```

**`domain` 不允许 `jakarta.persistence` 注解**——实体用纯 POJO，持久化映射放 `infrastructure`。这一条会有争议（多写映射代码），但它是「domain 可单测、可脱离数据库」的前提，v0.4 的租约状态机单测靠它。

> 若评审否决，必须给出替代方案保证「状态机可脱离 Spring 单测」，否则 v0.4 的 EXE-02 会变成只能起库才能测。

---

## 5. CI 流水线要点

```yaml
# .github/workflows/pr.yml（要点，非全文）
on: pull_request
jobs:
  lint:
    runs-on: [self-hosted, linux, x64]
    steps:
      - {uses: actions/checkout@v4}
      - run: make lint            # 后端 checkstyle + 前端 eslint
  test:
    needs: lint                   # lint 不过不启动 test
    runs-on: [self-hosted, linux, x64]
    steps:
      - {uses: actions/checkout@v4}
      - run: make db-reset        # 清 robot_it 库 + flyway migrate
      - run: make test            # 含 ArchUnit + @Tag("it") 集成测试
  build:
    needs: test
    runs-on: [self-hosted, linux, x64]
    steps:
      - {uses: actions/checkout@v4}
      - run: make build           # jar + dist
```

**四个必守点**：
1. `runs-on` 必须是自托管标签——托管 runner 到不了 `10.202.54.x`（R13）。
2. `test` 前必须 `db-reset`，否则脏数据会让偶发失败变成常态。
3. 缓存 `~/.m2` 与 `~/.npm`，但**缓存 key 含 lock 文件哈希**，否则依赖变了还在用旧缓存。
4. 集成测试串行（同库互斥），不开 JUnit 并行。

---

## 6. 实施顺序（Wave）

| Wave | 内容 | 为什么这个顺序 | 预计可验证的中间态 |
|---|---|---|---|
| **W1** | WS-01 monorepo + Makefile | 没有骨架，其他都无处落地 | `make bootstrap` 过 |
| **W2** | WS-04 schema（先 `V1__baseline.sql` + down） | 表结构是所有层的地基，且能独立验证 | 空库 → 38 表 → 空库 |
| **W3** | WS-02 后端分层 + ArchUnit | 要在表结构之上定分层 | 跨层引用被拦 |
| **W4** | WS-03 前端骨架 | 与 W3 并行，互不阻塞 | 6 路由 + i18n |
| **W5** | WS-05 CI 三关 | 等三个可构建目标齐 | PR 三绿 |
| **W6** | WS-06 环境基线 + DOC-01/04 | 最后写文档，因为要照着实际命令写 | 新人录屏 30 分钟 |

**Wave 之间不设并行上限**，但每个 Wave 结束必须有可复现输出（告警：不许「写了一半就进下一个」）。

---

## 7. 完成判定（G1 逐条对应）

```bash
# 1. 一键启动
rm -rf /tmp/v01 && git clone <repo> /tmp/v01 && cd /tmp/v01 && time make bootstrap
# 期望：退出码 0，≤30 分钟

# 2. 分层可拦（正向）
make test
# 期望：绿

# 3. 分层可拦（反向，必须现场做）
# 在 api 包内 import infrastructure 的类，然后：
make test
# 期望：红，报错含 "api_不直接碰基础设施" 与违规类名

# 4. 迁移可回滚
make db-reset && mysql -e "SHOW TABLES" robot_dev | wc -l    # 期望 38+1（含 flyway 表）
make db-undo && mysql -e "SHOW TABLES" robot_dev | wc -l     # 期望 1
make db-migrate && mysql -e "SHOW TABLES" robot_dev | wc -l  # 期望 39

# 5. 无明文口令
git grep -nEi "password\s*=\s*['\"][^'\"]+" -- ':!*.md' ; echo "退出码应为 1（无命中）"

# 6. 前端
cd frontend && npm run build && ls dist/index.html

# 7. 文档
test -f AGENTS.md && ls docs/adr/*.md
```

**门禁裁决人**：silence（G1）。裁决依据是上面 7 条的实际输出，不是「AI 说做完了」。

---

## 8. 风险与降级

| 风险 | 触发信号 | 降级动作 | 谁批 |
|---|---|---|---|
| runner 落点未定（P3） | WS-05 无法开始 | 先用人工脚本跑三关（`scripts/ci-local.sh`），CI 延后到 v0.2 开头 | 大师 |
| 38 表 DDL 返工 | 后续版本频繁改表结构 | **不降级**：改表是正常的，靠 Flyway 增量解决；但禁止改历史脚本 | — |
| ArchUnit domain 纯 POJO 有争议 | 评审否决 | 保留规则但把 `jakarta.persistence` 放宽为「允许注解、禁止 Repository」 | silence |
| 149 MariaDB 版本与本地不一致 | 迁移在 CI 过、本地不过 | 固定版本（`mariadb --version` 写进环境基线），本地用同版本 | — |
| 集成测试串行导致 CI 慢 | test job >10 分钟 | 拆 `it-fast` / `it-slow` 两个 job，slow 只在合并前跑 | — |

---

## 9. 交付物

| 类别 | 内容 |
|---|---|
| 代码 | monorepo 骨架、Makefile、Spring Boot 分层骨架 + ArchUnit、Vue 骨架、`db/migration` + `db/down`、CI workflow |
| 文档 | `AGENTS.md`、`docs/adr/0001~0003`、环境基线、`CONVENTIONS` 骨架（v0.2 补全） |
| 证据 | `docs/evidence/v0.1/`：bootstrap 计时日志、ArchUnit 正反两次输出、迁移三态输出、CI 三绿截图 |
