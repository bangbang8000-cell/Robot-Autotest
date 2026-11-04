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

---

## 10. 增量：多项目中心与体验基线（2026-11-04）

**背景**：大师 2026-11-04 提出四项需求（双主题 / 列表批量 / 中英一致 / 项目中心与资产解耦）并追加产品命名。
需求见 PRD §3.5~§3.8（`FR-v0.2-31~53`），口径、schema 与取舍见
`docs/plan/多项目与体验基线-评估与设计-20261104.md`。
**四项决策已定**：跟随系统+手动覆盖 / 资产共享池+项目引用 / 用例优先+通用组件 / 并入 v0.2 增量。

**八个实施包 + 一个复用包**（`UX-02b`，P2，本版可不做），可逐个独立验收。除 `AST-05` 外均不动表结构；`AST-05` 是唯一需要迁移与回填的包，单独作为一个可回滚批次。

### 10.1 施工顺序与依赖

```
NAM-01 ──► UX-01 ──► UX-03 ──► UX-01b ──► UX-02 ──► PRJ-01 ──► PRJ-04 ──► AST-05 ──► PRJ-03 ──► EXEC-01
（改名最先，否则后面所有截图作废）      （美术S0，UX-01 验收前置）        （依赖后端成员接口，已存在）  （读接口）（唯一动 schema）（跟随 AST-05）（人工触发，收尾）
```

**为什么 `NAM-01` 必须最先做**：它只改 3 处文案，5 分钟完成；但后续每个包都要交截图证据。
改名放最后，前面所有截图都要重截一遍——这是纯粹的白工。

**为什么 `UX-01b` 插在 UX-03 之后**：美术调研实锤两个 P0 缺陷（`--muted` 未定义 21 处、零 hover/focus），
且 D15（对比度校验器假绿）必须在任何 accent 变体落地前修掉。它只动样式不动逻辑，半天可验，
但**必须排在 UX-01 验收之前**（否则验的是带已知缺陷的界面）。

### 10.2 NAM-01 产品命名统一

| 步骤 | 内容 | 产出 | 验收命令 |
|---:|---|---|---|
| 1 | `zh-CN.ts` `app.title` → `AIDC自动化测试`；`en-US.ts` → `AIDC AutoTest` | 2 个文件 | `grep -n "AIDC" frontend/src/locales/*.ts` |
| 2 | `index.html` 静态标题改中性值；`App.vue` 监听 locale/route 用 `document.title` 同步 | 2 个文件 | 切英文后标签页显示 `AIDC AutoTest` |
| 3 | 报告页眉 / 打印页 / 导出物页眉改用 `t('app.title')` | 3 个文件 | 导出 PDF 抬头为当前语言名 |

**不做什么**：仓库名、`artifactId`、`package.json name`、Java 包名、数据库名**一律不动**（理由见评估文档 §5.5）。

### 10.3 UX-01 主题双轨

| 步骤 | 内容 | 产出 | 验收命令 |
|---:|---|---|---|
| 1 | 新建 `styles/theme.css`：primitives + semantic 两层，`:root[data-theme=light/dark]` 两套值 | 1 个文件 | `mvn` 无关；`npm run build` |
| 2 | `main.css` 全量替换 35 处 hex 为 `var(--*)` | 1 个文件 | `python tools/check_colors.py` |
| 3 | `views/` + `components/` 65 处 hex 替换（状态底色、边框、告警条） | 约 15 个文件 | 同上，命中数 = 0 |
| 4 | `composables/useTheme.ts`：三态 + `matchMedia` 监听 + 落 localStorage | 1 个文件 | 手动三态切换 + OS 切换跟随 |
| 5 | `index.html` 内联防闪烁脚本（挂载前设 `data-theme`） | 1 个文件 | 硬刷新首帧截图 |
| 6 | 侧栏主题按钮 + `user_preference` 迁移与读写（登录后覆盖本地） | 3 个文件 + 1 迁移 | `mvn -Pit test -Dtest=UserPreferenceIT` |

**回滚点**：步骤 3 之后若亮色出现大面积对比度问题，可只回滚 `theme.css` 的亮色值，不影响结构。

**实际完成情况（2026-11-04，全部 6 步完成）**

| 步骤 | 结果 | 与计划的偏差 |
|---:|---|---|
| 1 | 完成。`theme.css` 为 primitives（26 个）+ semantic（24 个 × 2 套） | 语义 token 比计划多出 `surface-2`/`overlay`/`shadow`/`shadow-sm`/`neutral-soft`/`chart-1~4` —— 这些是硬编码清零时**必然遇到**的用途，不补就得留 hex |
| 2 | 完成，`main.css` 35 处清零 | 无 |
| 3 | 完成，组件 78 处清零 | **计划估 65 处，实际 78 处**（含 `#fff`/`#999` 等 3 位简写，grep 六位 hex 时漏算）。教训：统计硬编码要匹配 `#rgb` 也要匹配 `#rrggbb` |
| 4 | 完成，`useTheme.ts` 三态 + matchMedia | 无 |
| 5 | 完成，`index.html` 内联脚本 | 无 |
| 6 | 完成，`user_preference` 表 + GET/PUT 接口 + 侧栏按钮 | 表加了 `created_by`/`updated_by`：`SchemaIT` 要求每张表都有审计四列。**没有给偏好表开豁免**——规范开第一个洞之后，每张新表都会想开 |

**实测推翻了三个假设（原计划没写对的）**

1. **「按经验配色就能达标」不成立**。第一版 20 项对比度检查有 5 项不达标：
   暗色 `neutral-soft` 2.81、暗色边框 1.14、亮色 accent 在 accent-soft 上 4.06、
   亮色 warn 在 warn-soft 上 4.23、亮色 `neutral-soft` 2.45。**颜色必须算，不能凭感觉**。
2. **暗色表格边框是既有缺陷**，不是这次改出来的。原 `#22303f` 压在 `#1a2634` 上只有 1.14:1，
   分割线在暗色下几乎看不见。已提到 1.29:1 并拆出 `border-strong`。
3. **图表色不能用 SVG presentation attribute 传 `var()`**。`TrendChart` 的 `stroke`/`fill`
   原为 `:stroke="line.color"`，换成 `var(--chart-1)` 后能否解析取决于浏览器；
   已改为 `:style="{ stroke: ... }"`（style 里的 `var()` 一定生效）。

**新增的守门脚本（计划里没有，但不加就会退回去）**

- `tools/check_colors.py`：只有 `theme.css` 允许定义色值。打印页用 `color-check: ignore-start/end`
  显式豁免并注明原因（打印页必须白底黑字，跟主题走会导出黑纸）。
- `tools/check_contrast.py`：解析两套主题 token 算 WCAG 对比度。**带自检**：解析出的 token 少于 25 个
  直接 FAIL，否则「全部跳过」会伪装成「全部通过」。

**踩到的流程坑（写给后来的人）**

Windows 下用 Python 的 `io.open(path, 'w')` 改文件，会把整个文件的 `\n` 写成 `\r\n`。
本次因此把 `scripts/db-audit.sh` 变成 CRLF，Linux 上执行 `set -euo pipefail` 直接报
`set: pipefail: invalid option name`。**改脚本/代码一律用 `newline=''` 或 `newline='\n'`**，
改完用 `grep -c $'\r'` 复查。仓库虽有 `.gitattributes`（`* text=auto eol=lf`）保证入库是 LF，
但它管不到工作区文件——直接跑脚本用的是工作区那一份。

### 10.4 UX-03 国际化与错误本地化

| 步骤 | 内容 | 产出 | 验收命令 |
|---:|---|---|---|
| 1 | `EditorView.vue` 24 处硬编码中文抽键（含 `'可编辑'/'只读'/'地址缺少…'`） | 1 个 vue + 2 个语言包 | `python tools/check_i18n.py` |
| 2 | 其余页面残留中文（`OverviewView` 未标注已处理，`ReportDetailView` 10 处）清理 | 约 4 个 vue | 同上 |
| 3 | 新建 `utils/statusLabels.ts`：reviewStatus / runStatus / priority / triggerType / memberRole 五组映射 | 1 个文件 | 中文界面无裸 `draft`/`approved` |
| 4 | 错误本地化：`http.ts` 按 `ErrorCode.name` 取 `error.<NAME>`，原文进 `detail` 折叠 | 1 个 ts + 2 个语言包 | 英文界面报错主文案为英文 |
| 5 | `formatDuration` / `formatBytes` / `Intl.DateTimeFormat` 统一出口 | 1 个文件 | 中英切换格式随之变 |
| 6 | `tools/check_i18n.py` 三项检查（键相等 / 模板中文 / 同值漏译） | 1 个脚本 | 故意删 en 键时报错退出 1 |

**已知边界（写在这里免得扯皮）**：服务端 `detail` 仍是中文（213 处业务异常），本包只做「主文案本地化 + 详情折叠」。
彻底解决需服务端错误结构化，单列 `UX-03b`，不在本包。

**实际完成（2026-11-04 回写）**：

| 计划步骤 | 实际情况 | 偏差说明 |
|---:|---|---|
| 1 | `EditorView.vue` 全部中文抽键 + `EditorView` 语言包段新建 | 计划写「24 处」，实测盘点 **14 处**——计划数是拍脑袋估计，以扫描为准 |
| 2 | 顺带清理：`api/execution.ts` 里 `'操作人取消'` 默认参数改由前端传键名 | 计划低估；实际用逐文件扫描兜底而不是凭印象列清单 |
| 3 | `utils/statusLabels.ts`：五组词表 + `ENUM_GROUPS` 声明 + 六个映射函数；下拉选项也从它取（`RUN_STATUSES`/`REVIEW_STATUSES`），消灭「下拉与映射两处漂移」 | 计划没提下拉选项同源，实现时发现必须一起做 |
| 4 | `utils/errors.ts`（localizeError + ErrorLine）+ `ErrorLine.vue` 组件 + `http.ts` 网络层归一（断网/超时/非 JSON 都有 codeName） | 计划只写「1 个 ts」，实际错误渲染散在 12 个 view 里，必须做组件否则模板重复 12 遍 |
| 5 | `utils/format.ts` 六个函数，替换 4 个 view 里各自手写的 `fmt`/`formatBytes`/`stateLabel` | 同上，扫描发现的重复实现比计划多 |
| 6 | `tools/check_i18n.py` **五项**检查 + 三轮破坏性自检（删 en 键 / 两侧同删枚举键 / 同值漏译） | 计划三项；第 4 项「代码引用键存在」、第 5 项「受控词表逐值核对」是本包真实事故（枚举键误删导致界面裸 `approved`）的直接产物 |

**计划外新增（超范围但必要）**：

- **后端总览接口去中文哨兵**：`OverviewRepositoryJdbc` 原来把空 `domain_tag` 渲染成 `'未标注'` 吐给前端——
  英文界面必冒中文。改为 `COALESCE(col, '')` 吐原始值，前端按语言翻成「未标注 / Not set」。
  `OverviewRepositoryIT` 新增一条用例锁住「分组键空值返回**空串**而非展示文案」（2 条全绿）。
- **`ProjectOverview.CountItem` javadoc 写明契约**：key 是原始库值，接口永不返回展示文案。

**本包踩的环境坑（记录给后续 UI 包，都是真实发生）**：

1. **遗留进程干扰**：上个会话留下的 vite（5173）与后端（WSL 内 java，占 8080，经 WSL2 localhost 转发）一直活着，
   走查打到**旧代码**上，「改了没生效」的假象查了三轮。教训：**UI 走查前先 `netstat` + 双平台进程清点**，杀干净再起。
2. **shell 代理劫持 curl**：本会话有 `HTTP_PROXY=127.0.0.1:54877`，所有 curl 打 localhost 都被代理转发，
   表现为「upstream connect failed (os error 10061)」。本地验证一律 `curl --noproxy '*'`。
3. **vite 默认只绑 IPv6 回环**：Chrome 走 IPv4 打不到。开发自测用 `--host 127.0.0.1`。
4. **`CRED_MASTER_KEY` 是启动硬前置**（有意设计，勿"修"成默认值）：本地固定密钥放 `~/.config/robot-autotest/env`
   （不入仓）。用临时密钥启动过的库，此前加密的 credential 行换钥后解不开——本机开发库 2 行已因此作废（试验数据，可弃）。

### 10.5 UX-02 列表批量（用例优先）

| 步骤 | 内容 | 产出 | 验收命令 |
|---:|---|---|---|
| 1 | `composables/useSelection.ts`：id 集合、本页全选、半选、跨页保留、清空 | 1 个文件 + 单测 | `npm run test`（新增 vitest） |
| 2 | `components/SelectionBar.vue`：计数、动作插槽、回执、失败保留 | 1 个 vue | 视觉走查 |
| 3 | 后端 `POST /cases/batch`：action 六种 + 逐条结果 + 每条审计 + 200 上限 | `CaseController` / `TestCaseService` | `mvn -Pit test -Dtest=CaseBatchIT` |
| 4 | `CasesView.vue` 接入：勾选列、批量条、状态前置提示、失败重试 | 1 个 vue + 1 个 api | 浏览器操作截图 |
| 5 | 批量审计带 `batch` 标识（复用 `AuditService.record`） | 1 个文件 | SQL 查同批次 3 条 |
| 6 | `UX-02b` 预留：组件文档 + 其余列表接入清单（不在本包实现） | 1 段注释/文档 | 代码评审 |

**契约硬规则**（实现时不许简化）：部分成功返回 **200**、逐条独立事务、单条失败不影响其他、
`ids` 为空或 >200 或 action 未知才 400。

### 10.6 PRJ-01 项目中心（成员与角色）

| 步骤 | 内容 | 产出 | 验收命令 |
|---:|---|---|---|
| 1 | `api/iam.ts` 补 `members` / `addMember` / `updateMember` / `removeMember` | 1 个文件 | 类型检查通过 |
| 2 | `AdminView.vue` 新增「项目成员」区块：选项目 → 表格 → 增改删 | 1 个 vue + 语言包 | 浏览器操作 |
| 3 | `owner` 保护：移除/降级 `owner` 返回 409；停用用户标「已停用」 | 后端 1 处 + 前端展示 | 接口 409 + 截图 |
| 4 | 无项目引导：`ProjectPicker` 空态 + 各页统一提示「联系管理员加入项目」 | 2 个文件 | 新用户账号登录截图 |

**权限口径落地**：`effectiveRole` 的「全局 admin → 项目成员角色 → 全局最高角色」写进类注释与 PRD §3.8，
前端成员表显示的是 `member_role`（项目内），不显示全局角色，避免用户混淆。

### 10.7 PRJ-04 报告结果导向

| 步骤 | 内容 | 产出 | 验收命令 |
|---:|---|---|---|
| 1 | `GET /api/projects/{id}/report-digest?days=30`：最近 5 份报告 / 失败 Top10 / 未执行用例数 / 通过率趋势 | Controller + Service + Repository | `mvn -Pit test -Dtest=ReportDigestIT` |
| 2 | 总览页补「报告结果」区块（在 OVW-01 四区之后） | 1 个 vue | 浏览器截图 |
| 3 | 空态：无报告时给「去执行页建计划」引导，**不显示 0%** | 1 个 vue + 语言包 | 空库截图 |
| 4 | 与真库 SQL 逐项对照并留证 | 1 份 log | `docs/evidence/v0.2/prj04-db-vs-api.log` |

### 10.8 AST-05 资产共享池（唯一动 schema）

| 步骤 | 内容 | 产出 | 验收命令 |
|---:|---|---|---|
| 1 | 迁移：三表 `owner_scope` + `project_id` 可空 + 新建 `project_asset` | 1 个迁移 + 1 个 down | `mvn -Pit test -Dtest=SchemaIT` |
| 2 | **存量回填**：每个存量资产按原 `project_id` 插 `project_asset`（升级后视图不变） | 迁移内 SQL 或独立脚本 | 升级前后各项目资产数逐项相等 |
| 3 | `AssetScopeSql` 常量：项目可见资产的统一 WHERE（私有 OR 平台+有引用） | 1 个文件 | 架构测试：资产仓储不得自拼条件 |
| 4 | `DeviceRepositoryJdbc` / `LinkRepositoryJdbc` / `CredentialRepositoryJdbc` 改查询口径 | 3 个文件（约 31 处 `project_id`） | `mvn -Pit test` 全绿 |
| 5 | 接口：`POST /assets/{type}/{id}/share`（转平台）/ `POST /projects/{id}/assets/{type}/{id}`（引用） | 2 个端点 | 一台设备被 3 项目引用仅 1 条 device |
| 6 | 唯一键调整：`uk_device_project_name` 不再适用平台级；平台级用 `uk_device_serial` | 迁移 | 同名不同项目可共存 |
| 7 | 解耦不退化断言：`test_case` 无 `device_id` 类列 | 1 条 Schema 测试 | `mvn -Pit test -Dtest=SchemaIT` |

**升级风险与对策**：本包是唯一会改线上数据的包。回填 SQL 必须在**同一事务**内完成，且先跑
「升级前资产数快照 → 升级 → 升级后可见数比对」，两列不相等即中止。down 迁移只回滚结构，
**不回滚 `project_asset` 数据**（宁可留冗余引用，不制造「资产消失」）。

### 10.9 PRJ-03 引用保护与跨项目视图

| 步骤 | 内容 | 产出 | 验收命令 |
|---:|---|---|---|
| 1 | 删除前置校验：被引用 → 409 并返回引用项目数 | Service | 接口 409 |
| 2 | 项目内「移除资产」只删 `project_asset`，不动物资本体 | 1 个端点 | 移除后资产仍在 |
| 3 | 删项目级联删引用、保留平台资产 | Repository | 删项目后 device 仍在 |
| 4 | 越权用例：无引用项目读平台资产 → 403（不是 404，避免探测） | 测试 | `mvn -Pit test -Dtest=AssetScopeIT` |

### 10.10 UX-01b 界面基线修复（美术 S0，详见 `docs/design/美术体系-开发与测试计划-20261104.md` §2.1）

| 步骤 | 内容 | 产出 | 验收命令 |
|---:|---|---|---|
| 1 | `var(--muted)` 21 处 → `--text-muted`；修 CONVENTIONS-frontend §8 错误 token 名 | 5 个文件 | `python tools/check_tokens.py` |
| 2 | `check_contrast.py` 解析按「(主题×强调色)」分组 + 自检（D15） | 1 个脚本 | 注入 accent 块复跑仍 PASS |
| 3 | 交互态 token + 六类元素 hover/focus-visible + 全局 focus outline | theme.css + main.css | `python tools/check_states.py` |
| 4 | `.btn.danger` / `.btn-ghost.danger` 定义并套用破坏性按钮 | main.css + PlansView | 截图 |
| 5 | 内容区 max-width 1440 + 超宽表内滚 | main.css | 1920 截图 |
| 6 | accent 两态 token（blue 先行，不出切换入口） | theme.css | 40 组对比度 PASS |
| 7 | 新守门 `check_tokens.py` + `check_states.py`（带自检） | 2 个脚本 | 破坏性自检各拦 1 次 |

**边界**：只动样式与守门脚本，不改业务逻辑与 i18n 键；半天体量。

### 10.11 EXEC-01 执行人工触发（详见 `docs/plan/执行人工触发-评估与计划-20261104.md` §4）

| 步骤 | 内容 | 产出 | 验收 |
|---:|---|---|---|
| 1 | `RunDispatcher` 提升公共组件；`RunQueuePolicy` 启用或删除（实现时定，不留死代码） | 1 个服务 | 并发 dispatch 串行化单测 |
| 2 | `POST /api/execution/quick-run`（校验→create→dispatch） | controller + service | IT 400/404/403 矩阵 |
| 3 | `POST /api/execution/runs`（CI 专用）接派发 worker | RunController | IT：run 不再永久 queued |
| 4 | 用例集执行：集合→套件路径解析 + 未绑定报错列表 | 1 service | IT 混合集合 400+编号 |
| 5 | RunsView 手动执行表单 + `executionApi.quickRun` | 1 vue + 1 api | 中英双走查截图 |
| 6 | 用例集「立即执行」入口 | 1 vue | 走查截图 |
| 7 | 审计 `RUN_TRIGGER` 埋点 | 1 处 | SQL 查审计行 |

**边界**：本包不做「勾选台账用例直接跑」（依赖用例↔脚本绑定模型，列 v0.4 EXEC-02）；排 UX-02/UX-01b 之后。

### 10.12 完成判定

上述各包独立验收，**不需要等全部做完**。合并判据：

1. 各包在 `WBS-STATUS.md` 由 `未开始` → `待验`，附证据路径；
2. 全量 `mvn -B test` + `-Pit` 绿；`npm run lint` / `vue-tsc` / `build` 三项 exit 0；
3. 评估文档 §9 的 A-01~A-21 逐条给出证据；
4. `待验 → 已完成` **只能由人改**（流程纪律，AI 不自批）。
