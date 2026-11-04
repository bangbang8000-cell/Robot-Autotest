# v0.2 台账 · 开发计划

**文档编号** RA-REL-2026-002-DEV ｜ **版本** v0.2 ｜ **门禁** G2

**范围** IAM-01~05、AST-01~04、CAS-01~05、DOC-02（backend/frontend）、DOC-03 ｜ **对应 PRD** FR-v0.2-01 ~ 24

---

## 1. 任务拆解与依赖

| WBS | 任务 | 参考人日 | 前置 | AI 自主度 | 完成判定（命令级） |
|---|---|---:|---|---|---|
| CAS-01 | 双模用例模型建模与评审 | 8 | WS-04 | **C** | 评审纪要 + ADR-0005；模型冻结 |
| IAM-01 | 用户/角色/项目 CRUD | 7 | WS-04 | A | 三对象 CRUD + 分页搜索的集成测试绿 |
| IAM-02 | 登录与会话 + 密码策略 | 6 | IAM-01 | A | 弱密码被拒；连续 5 次失败锁定 |
| IAM-04 | 租户隔离（行级 + 拦截器） | 7 | IAM-01 | **B** | 漏写条件被测试捕获（元测试） |
| IAM-03 | RBAC + 项目成员 | 8 | IAM-01、IAM-04 | A | 权限矩阵每格有测试 |
| IAM-05 | 越权测试集 ≥20 条 | 5 | IAM-03 | B | 20 条全绿，产出越权报告 |
| AST-01 | 设备与端口台账 | 6 | IAM-04 | A | 设备下挂端口可下钻；序列号唯一 |
| AST-02 | 环境与拓扑 | 6 | AST-01 | A | 一条链一次查询取全 |
| AST-03 | 凭据加密存储 | 5 | AST-01 | **B** | 磁盘/日志无明文；接口不回显 |
| AST-04 | 技术域标签与筛选器 | 5 | AST-01、CAS-02 | A | 用例与设备共用标签集 |
| CAS-02 | 用例库/用例集 CRUD | 7 | CAS-01、IAM-04 | A | 一例多集；排序持久化 |
| CAS-03 | 461 条导入（映射/校验/回滚） | 8 | CAS-02 | **B** | 461 条入库、抽查 20 条一致、重导幂等 |
| CAS-04 | 文件工作区 6 端点 | 6 | CAS-02、IAM-04 | A | 6 端点各有集成测试 |
| CAS-05 | 能力索引 | 4 | CAS-02 | A | 按能力反查非空 |
| DOC-02 | CONVENTIONS-backend / frontend | 6 | IAM-03、CAS-03 | A | 各被 CI 或评审实际引用一次 |
| DOC-03 | IA 评审与冻结（6 菜单） | 4 | 本版页面成型 | **C** | 评审纪要；改 IA 需 ADR |

### 1.1 依赖图

```
CAS-01（模型冻结）─┬─► CAS-02 ─┬─► CAS-03（导入）
WS-04 ─► IAM-01 ─┼─► IAM-04 ─┼─► CAS-04
                  │           ├─► CAS-05
                  │           └─► AST-04
                  ├─► IAM-02  │
                  └─► IAM-03（需 IAM-04 先行）─► IAM-05 ─► G2
AST-01（需 IAM-04）─► AST-02、AST-03
```

**唯一必须前置的**是 `CAS-01`：模型不定，`CAS-03` 的字段映射与 `v0.3` 的保存链路都是猜。它也是本版的冻结窗口（G2 后改模型要 ADR）。

---

## 2. 数据库变更（本版补 22 张业务表）

### 2.1 迁移文件划分

| 文件 | 内容 | 表数 |
|---|---|---:|
| `V2_1__iam_fields.sql` | 身份 5 + 项目 3 的业务字段（`display_name`、`email`、`status`、`project_setting` 等） | 8 |
| `V2_2__asset_fields.sql` | 资产 7 的业务字段（`serial_no`、`mgmt_ip`、`speed`、`protocol`、`cipher_blob` 等） | 7 |
| `V2_3__case_fields.sql` | 用例 7 的业务字段（`external_id`、`title`、`priority`、`domain`、`content`、`sha256`） | 7 |
| `V2_4__audit_indexes.sql` | `audit_log` 的索引（按 `created_at`、`actor_id`、`resource` 组合） | 1 |

每份配 `.down.sql`（ADR-0003），CI 跑 up→down→up 三态。

### 2.2 双模模型（ADR-0005，本版最重要的表设计）

```sql
-- 台账：人工用例，可无脚本
CREATE TABLE test_case (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  external_id   VARCHAR(64)  NOT NULL,        -- 如 IM-FUN-001，来自 Excel
  title         VARCHAR(255) NOT NULL,
  category      VARCHAR(64)  NULL,
  priority      VARCHAR(8)   NOT NULL DEFAULT 'P2',
  precondition  TEXT         NULL,
  expected      TEXT         NULL,
  domain        VARCHAR(32)  NULL,            -- 技术域标签
  source        VARCHAR(32)  NOT NULL DEFAULT 'import',
  review_status VARCHAR(16)  NOT NULL DEFAULT 'draft',
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by    BIGINT       NULL,
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by    BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_case_project_external (project_id, external_id),   -- 编号在项目内唯一
  KEY idx_case_domain_priority (project_id, domain, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 脚本：唯一真源是 content 字符串，不存结构化 AST
CREATE TABLE robot_file (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  path          VARCHAR(512) NOT NULL,
  content       LONGTEXT     NOT NULL,        -- 唯一真源
  sha256        CHAR(64)     NOT NULL,
  current_version INT        NOT NULL DEFAULT 1,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_file_project_path (project_id, path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**四条不可协商的设计**（对应基线报告 §11.2）：

1. **`robot_file.content` 是唯一真源**。不另存 AST——存了必然双写不一致。编辑器读写这个字符串，执行时落盘为临时 `.robot`。
2. **台账与脚本解耦**。`test_case` 不带 `robot_file_id`；绑定关系用 `case_requirement` 或 `[Tags]` 回链。一条台账可无脚本，一个脚本可挂多条台账（数据驱动）。
3. **版本层是 `robot_file_version` 快照，不是 Git**。每次保存生成快照；批量提交 Git 是异步的另一件事。运行时**不依赖 Git 工作区**，避免「改一半就跑」。
4. **编号在项目内唯一**，不是全局唯一。跨项目同名编号是合法的（不同项目各有一套 Excel）。

### 2.3 租户隔离的实现（本版最容易漏的地方）

三层防护，缺一层都不算完成：

| 层 | 机制 | 漏了会怎样 |
|---|---|---|
| 1. 拦截器 | MyBatis `Interceptor` 解析 SQL，对白名单表自动追加 `project_id = ?` | 手写 SQL 漏条件 → 跨项目泄露 |
| 2. 上下文 | `TenantContext`（ThreadLocal）由登录态注入 | 定时任务/异步线程取不到 → 全表查 |
| 3. 兜底元测试 | 故意写一条不带 `project_id` 的查询，断言被拦截器改写或报错 | 拦截器版本升级后静默失效没人知 |

```java
// 第 3 层的元测试示意：证明拦截器真的在改写
@Test
void 未带租户条件的查询会被拦截器改写() {
    List<Map<String,Object>> r = mapper.selectAllWithoutTenant(); // 故意写危险 SQL
    assertThat(lastSql()).contains("project_id = ?");             // 断言 SQL 被改写
}
```

**白名单**：`flyway_schema_history`、`app_user`（登录前需查）、`role`、`system_setting`、`audit_log`（写审计时无当前项目）。
**黑名单（必须带租户）**：`project`、`project_setting`、`project_label`、`device`、`device_port`、`link`、`credential`、`env_profile`、`topology_node`、`test_case`、`test_set`、`test_set_item`、`robot_file`、`robot_file_version`、`case_requirement`。

### 2.4 凭据加密

| 项 | 决定 |
|---|---|
| 算法 | AES-256-GCM，随机 12 字节 IV，密文与 IV 一同存（`cipher_blob`） |
| 密钥 | 环境变量 `CRED_MASTER_KEY`（32 字节 base64），启动时校验长度，缺失即启动失败 |
| 读取 | 只有执行域（v0.4）通过 `CredentialService.reveal(id)` 取用；该调用**写审计** |
| 接口 | 列表/详情只返回 `configured: true/false` 与最后修改时间，**永不回显密文或明文** |
| 轮换 | 本版不做；预留 `key_version` 字段，轮换在运营期 |

---

## 3. 461 条导入的实现

```
上传 Excel → 解析表头 → 人工确认字段映射 → 逐行校验 → 预览（前 20 行 + 统计）
   → 事务提交（分批 upsert）→ 导入报告（成功/更新/跳过/失败）
```

**四个必须做对的点**：

| 点 | 做法 | 为什么 |
|---|---|---|
| 幂等 | 按 `(project_id, external_id)` upsert，不按 id | 重导不能产生重复；这是可重复操作的前提 |
| 事务边界 | 整批一个事务，任一行校验失败**整体回滚** | 半批数据比没导入更糟（对账对不上） |
| 分批 | 每 200 行一批 flush，避免长事务锁表 | 461 条虽小，但模板会被复用到大文件 |
| 报告 | 每行给「行号 + 原因 + 建议」 | 只说「导入失败」等于让人手工找 |

**键值映射表**（本版固化，改它要 ADR）：

| Excel 列 | 目标字段 | 规则 |
|---|---|---|
| 用例编号 | `external_id` | 必填、去空格、校验格式 `^[A-Z]{2,4}-[A-Z]{2,4}-\d{3}$` |
| 用例名称 | `title` | 必填、≤255 |
| 分类 | `category` | 可空 |
| 优先级 | `priority` | 枚举 P0/P1/P2/P3，非法值报错不猜测 |
| 预置条件 | `precondition` | 可空、换行归一 |
| 步骤 | `test_case_step`（多行） | 按序号拆行，空的步骤行跳过 |
| 预期结果 | `expected` | 可空 |
| 技术域 | `domain` | 枚举预置值，空则按关键词推断并标「待确认」 |

---

## 4. 权限实现

- 接口级：`@PreAuthorize("hasAuthority('case:write')")`，权限点命名 `资源:动作`。
- 资源级：项目成员校验在 service 层**必做一次**，不能只靠接口注解（同一接口跨项目调用时注解拦不住）。
- 越权返回 **403**，且响应体不带「该资源存在」的信息（防止枚举探测）。
- 前端只做体验（隐藏按钮），**不作为安全边界**；测试用直连 HTTP 验证。

---

## 5. 实施顺序（Wave）

| Wave | 内容 | 中间可验证态 |
|---|---|---|
| **W1** | CAS-01 模型评审 + ADR-0005（**先会再写码**） | 评审纪要签发 |
| **W2** | 迁移 `V2_1`~`V2_4` + IAM-01 | 8+7+7 表字段齐；三对象 CRUD 能跑 |
| **W3** | IAM-04 租户隔离 + 元测试 | 漏条件被捕获 |
| **W4** | IAM-02 登录、IAM-03 RBAC | 权限矩阵可测 |
| **W5** | AST-01~04 | 10 设备 + 20 端口 + 一条拓扑链 |
| **W6** | CAS-02 + CAS-04 | 用例库/集能用，文件树能下钻 |
| **W7** | CAS-03 导入 | 461 条入库，报告完整 |
| **W8** | CAS-05、IAM-05 越权集、DOC-02/03 | 20 条越权绿，IA 冻结 |

**W1 不许跳**：模型评审没做就写 CRUD，等于用代码替评审做决定——这是基线报告 R3 的原始成因。

---

## 6. 完成判定

```bash
# 1. 迁移三态
make db-reset && mysql robot_dev -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='robot_dev'"   # 38+1
make db-undo-v2 && make db-migrate                                                                                     # 不报错

# 2. 权限与越权
mvn test -Dgroups=security          # IAM-05 的 20 条越权，全绿

# 3. 租户隔离元测试
mvn test -Dtest=TenantInterceptorTest   # 断言 SQL 被改写

# 4. 导入（用真实 461 条文件）
curl -F "file=@fixtures/cases-461.xlsx" -H "Authorization: Bearer $T" \
     -X POST localhost:8080/api/import/preview     # 返回统计 461/0/0
curl ... /api/import/commit                        # 提交
mysql robot_dev -e "SELECT COUNT(*) FROM test_case WHERE project_id=1"   # 461
# 再导一次，条数仍为 461（幂等）

# 5. 凭据
grep -rEi "<项目口令>|<历史口令>" /var/lib/mysql /home/evergreen/robot-test 2>/dev/null   # 无输出
curl localhost:8080/api/credentials/1 | grep -i "password\|secret"                      # 无明文

# 6. 前端主流程（Playwright）
npm run test:e2e -- --grep "v0.2 核心场景"   # 1~7 步全过
```

---

## 7. 风险与降级

| 风险 | 信号 | 降级 | 谁批 |
|---|---|---|---|
| 双模模型评审卡住 | CAS-01 超过两轮未过 | 先按「台账 + 脚本可空」最简模型冻结，脚本绑定延后到 v0.3 | 大师 |
| 461 条字段映射缺失 | 导入报告失败行 >5% | 分批导入，把失败原因清单交回大师补 Excel；不猜值 | — |
| 租户拦截器改写复杂 SQL 出错 | 联表查询结果异常 | 联表一律写显式子查询 + 手工加条件，拦截器只做兜底 | silence |
| 密码策略与现有习惯冲突 | 使用方抱怨 | 策略可配置（`system_setting`），默认值不变 | 大师 |
| IA 评审拖延 | DOC-03 未签 | 按当前 6 菜单冻结，异议期 3 天，过期视为通过 | 大师 |

---

## 8. 交付物

| 类别 | 内容 |
|---|---|
| 代码 | IAM/AST/CAS 三个域（api/application/domain/infrastructure）、租户拦截器、凭据服务、导入器、前端 4 个菜单页 |
| 迁移 | `V2_1`~`V2_4` 及对应 down |
| 文档 | `CONVENTIONS-backend.md`、`CONVENTIONS-frontend.md`、`ADR-0005 双模模型`、`ADR-0006 租户隔离`、IA 冻结纪要 |
| 证据 | `docs/evidence/v0.2/`：越权 20 条输出、导入对账、凭据扫描、租户元测试、核心场景录屏 |

---

## 9. 增量：OVW-01 总览真实化（2026-11-04）

**背景**：v0.2 原计划的总览「只有数量统计」，实测是 43 行 ping 占位页。需求见 PRD §3.4（`FR-v0.2-25~30`），
口径与取舍见 `docs/plan/总览页真实化-评估与设计-20261104.md`。

| 步骤 | 内容 | 产出 | 验收命令 |
|---:|---|---|---|
| 1 | 领域模型：`domain/overview/ProjectOverview`（嵌套 record，含 `passRateBp` 整数基点 + `null` 表无样本） | 1 个文件 | `mvn -B -q compile` |
| 2 | 仓储接口 + JDBC 实现：7 条只读 SQL，全部显式 `project_id` 并过 `TenantGuard` | `OverviewRepository` / `OverviewRepositoryJdbc` | `mvn -Pit test -Dtest=OverviewRepositoryIT` |
| 3 | 应用服务：`bindProject` + `require(CASE, READ)`，参数夹边界 | `OverviewService` | 同上 |
| 4 | 接口：`GET /api/projects/{projectId}/overview` | `api/overview/OverviewController` | `curl` 见 TEST-PLAN §12 |
| 5 | 修 `TrendRepositoryJdbc` 的 `run_status IN ('passed','failed')` → `('finished','failed')` | 1 行 + 1 条单测 | `mvn -B test -Dtest=TrendServiceTest` |
| 6 | 前端：`api/overview.ts` + 重写 `OverviewView.vue`（四区 + 空态 + 近 30 天 CSS 柱状） | 2 个文件 + i18n | `npm run build` / `lint` / `vue-tsc` |
| 7 | 证据：真库对照日志 + 浏览器截图 | `docs/evidence/v0.2/ovw01-*.log/.png` | 见 TEST-PLAN §12 |

**依赖**：无新表、无迁移、无外部依赖，不阻塞 G2。
**不做**：图表库、实时刷新、跨项目聚合、自定义布局（理由见评估文档 §8）。
