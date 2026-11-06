# v1.0 生产就绪 · 开发计划

**文档编号** RA-REL-2026-007-DEV ｜ **版本** v1.0 ｜ **门禁** G7 上线门 → 持续迭代评审

**范围** HRD-01~04 + 小型试点迭代验证 ｜ **对应 PRD** FR-v1.0-01 ~ 25

---

## 1. 任务拆解与依赖

| WBS | 任务 | 参考人日 | 前置 | AI 自主度 | 完成判定（命令级） |
|---|---|---:|---|---|---|
| HRD-01 | 权限矩阵全量校验（112 格） | 5 | G2~G6 | A（人签字） | 112 格逐格有结论，导出表格可勾 |
| HRD-02 | 审计日志（10 类关键操作） | 5 | G2~G6 | A | 10 类各抽查 1 次均有记录 |
| HRD-03 | 小型试点迭代验证与运维手册 | 6 | G7 前置 | **B** | 每轮试点记录齐全（崩溃/重启/内存趋势/连接数）；手册可执行（新人独立重启成功） |
| HRD-04 | 回滚预案与演练 | 6 | 全部 | **C** | 演练 RTO ≤30 分钟，签字 |
| B8 | 试点迭代（持续） | — | G7 | **C** | 每轮记录齐全 + 评审结论闭环（不设时长/次数硬门槛） |

### 1.1 顺序

```
HRD-01（权限）─┐
HRD-02（审计）─┼─► G7 ─► 试点迭代（持续）─► 每轮迭代评审
HRD-03（试点/手册）─┤
HRD-04（回滚演练）───┘
```

**HRD-04 必须最后做**：回滚要基于「最终版本的迁移集」，提前演练的结论会失效。

---

## 2. 权限矩阵（HRD-01）

### 2.1 矩阵结构

```
4 角色（管理员 / 项目经理 / 测试工程师 / 访客）
× 7 资源类（用户角色 / 项目 / 资产 / 凭据 / 用例 / 计划 / 审计）
× 4 动作（读 / 写 / 删 / 管理）
= 112 格
```

**每格的三种结论**：

| 结论 | 含义 | 要求 |
|---|---|---|
| `ALLOW` | 允许 | 有测试证明「真能成功」（不能只证明「返回 200」） |
| `DENY` | 拒绝 | 有测试证明「403 且不泄露」 |
| `N/A` | 业务上无此操作 | **必须写理由**，如「审计无写动作，仅系统写入」 |

### 2.2 逐格测试的生成方式

手写 112 个测试会失控。用参数化 + 一张矩阵表驱动：

```java
@ParameterizedTest
@MethodSource("matrix")
void 权限矩阵每一格都符合预期(Cell cell) {
    Response r = callAs(cell.role(), cell.action(), cell.resource());
    switch (cell.expected()) {
        case ALLOW -> assertEquals(200, r.status(), cell.toString());
        case DENY  -> {
            assertEquals(403, r.status(), cell.toString());
            assertFalse(r.body().contains(cell.resourceName()), "响应泄露了资源信息");
        }
        case N_A   -> assertTrue(cell.reason() != null && !cell.reason().isBlank(),
                                 "N/A 必须写理由: " + cell);
    }
}
```

**矩阵表放在 YAML**（`src/test/resources/authz-matrix.yaml`），评审时看 YAML 就能逐行勾，不用读代码。

### 2.3 导出供签字

```bash
mvn test -Dtest=AuthzMatrixTest -Dexport=authz-matrix.csv
# 产出：角色,资源,动作,预期,实际,用例ID,备注
```

CSV 直接给人勾选签字，作为 G7 证据。

### 2.4 必须单独测的「无角色」情形

矩阵覆盖的是 4 个已知角色。还缺一种：**登录了但没有分配任何项目**的用户。他的行为必须定义（403 或空集，不能 500）。这条不进矩阵，单独一条用例——因为它不属于任何角色。

---

## 3. 审计（HRD-02）

### 3.1 实现方式

```java
@Audited(action = "case:delete", resource = "test_case")
public void deleteCase(long id) { ... }
```

切面在 `@Around` 中：

```
1. 记录前值摘要（只查必要字段，不查全量）
2. 执行方法
3. 成功后记录后值摘要（删除类只记前值）
4. 写 audit_log（异步，失败不影响业务）
5. 脱敏：凭据类只记「已更新」，不记值
```

### 3.2 十类必须留痕的操作

| # | 操作 | 动作名 | 摘要内容 |
|---|---|---|---|
| 1 | 建/删用户 | `user:create` / `user:delete` | 用户名、角色 |
| 2 | 改角色 | `role:update` | 旧角色 → 新角色 |
| 3 | 建/删项目 | `project:create` / `project:delete` | 项目名 |
| 4 | 改项目成员 | `member:update` | 成员、旧角色 → 新角色 |
| 5 | 建/改凭据 | `credential:upsert` | **只记「已配置」+ 目标设备，不记值** |
| 6 | 删用例 / 用例集 | `case:delete` / `set:delete` | 编号、标题 |
| 7 | 改/删计划 | `schedule:update` / `schedule:delete` | 计划名、cron 变化 |
| 8 | 强制释放租约 | `lease:force-release` | resource_key、原持有 run |
| 9 | 补录 metadata | `run:metadata-patch` | 补了哪项、旧 → 新 |
| 10 | 回滚/部署 | `system:deploy` | 版本号、操作人 |

### 3.3 三条实现纪律

| 纪律 | 原因 |
|---|---|
| 审计写入失败**不阻断**业务，但必须告警 | 审计挂了不能让业务停；但不能静默丢 |
| 摘要**脱敏**：凭据、token、口令永不进审计 | 审计本身是敏感数据 |
| 审计**只增不改**，接口无 delete | 可篡改的审计等于没有审计 |

---

## 4. 试点迭代验证与运维手册（HRD-03）

### 4.1 小型试点项目持续迭代验证

| 项 | 设定 |
|---|---|
| 做法 | 选 1-2 条真实链路组成小型试点项目，按轮次持续跑通全链路（不设 24 h、不设固定时长与次数） |
| 监控 | 每轮记录崩溃/重启、JVM heap / RSS 趋势、DB 连接数、磁盘 |
| 判定 | **不设硬门槛**（无 24 h、无 RSS<20%）；趋势作为优化输入，「能力不倒退」是唯一硬约束 |
| 产出 | 每轮迭代记录（崩溃/重启/内存趋势/连接数）+ 容量基线三项（内存/磁盘/DB 体积） |

### 4.2 运维手册结构（必须能照着做）

```
1. 服务清单（平台后端:8081 / 前端:5173 / MySQL:3306）
   - 常驻形态为 systemd --user unit，需要 loginctl enable-linger
2. 启停：systemctl --user start|stop|restart <unit>
3. 日志位置：journalctl --user -u <unit> -n 200 / 应用日志路径
4. 健康检查：3 条 curl，各自期望返回
5. 备份：DB 每日全量命令、产物目录增量命令、验证备份可用
6. 恢复：从备份恢复到新库 → 切配置 → 验证（含所需时间）
7. 故障处置 5 类：
   - 服务起不来 → 查 journal → 常见原因（端口占用/配置缺失/venv 路径）
   - 运行卡住 → 查租约表 → 强制释放步骤
   - 磁盘满 → 清理产物 dry-run → 执行
   - DB 连不上 → 检查服务/账号/最大连接
   - 报告导不出 → 查 Playwright/chromium 是否存在
8. 回滚（指向 HRD-04 文档）
```

**「新人独立完成」的验证方式**：找一个没参与运维的人（可以是 AI 的新会话，但更好是真人），只给手册，让他完成「重启服务 + 从备份恢复到一个测试库」，全程不提问。**卡住的地方就是手册的缺陷**。

---

## 5. 回滚（HRD-04）

### 5.1 三步回滚

```
① 备份当前状态（DB dump + 产物目录清单）
② 停服务 → 部署上一版包（jar + dist）→ 执行 down 迁移
③ 起服务 → 健康检查 → 抽样验证（登录、看一个报告、跑一条用例）
```

### 5.2 down 迁移的顺序与风险

```sql
-- 按版本倒序执行，例如从 v1.0 回 v0.6：
db/down/V6_3__trigger_log.down.sql
db/down/V6_2__dependency.down.sql
db/down/V6_1__schedule.down.sql
-- v1.0 若只加索引/视图，down 也在此列
```

| 风险 | 处理 |
|---|---|
| down 会**删表**，v0.6 之后产生的计划数据丢失 | ① 回滚前 dump；② down 脚本只删结构，数据在 dump 里；③ 文档写清「哪些数据会丢」 |
| down 锁表导致停服变长 | 演练时测；大表用 `pt-online-schema-change` 或选低峰 |
| 回滚后旧版读不到新字段 | 只加列的回滚**不用 down**（列留着无害），只回退代码。**这条要写进脚本的默认策略** |
| 回滚一半失败 | 脚本每步可重入；失败时停在明确状态并输出「下一步做什么」 |

**默认策略很重要**：多数情况「只回退代码，不回退 schema」是更安全的选择（加列对旧代码无害）。`rollback.sh` 默认 `--schema=keep`，需要 `--schema=down` 才执行迁移回退。

### 5.3 演练脚本

```bash
scripts/rollback.sh --to v0.6.2 --dry-run     # 打印将执行的全部命令
scripts/rollback.sh --to v0.6.2               # 实际执行（默认 schema=keep）
scripts/rollback.sh --to v0.6.2 --schema=down # 含迁移回退（会删表，需二次确认）
```

### 5.4 演练记录（G7 证据）

| 步骤 | 开始时间 | 结束时间 | 耗时 | 结果 |
|---|---|---|---|---|
| 备份 | | | | |
| 停服 + 部署 | | | | |
| 起服 + 健康检查 | | | | |
| 抽样验证 | | | | |
| **合计 RTO** | | | **≤30 min** | |

---

## 6. 试点迭代验证（B8，替代原试运行观察窗）

### 6.1 指标采集（脚本化，不靠人记）

```sql
-- 每轮统计参考（scripts/window-stats.sql）
SELECT DATE(started_at) d,
       COUNT(*) runs,
       SUM(state='finished') ok,
       ROUND(SUM(state='finished')/COUNT(*)*100, 1) success_rate,
       SUM(exportable) exportable_runs
FROM run
WHERE started_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
GROUP BY DATE(started_at);
```

### 6.2 每轮记录字段

| 字段 | 数据来源 | 用途 |
|---|---|---|
| 本轮运行次数 / 结果 | `run` 中 `trigger_type IN (schedule, ci, manual)` 且 `dut IS NOT NULL` | 确认试点跑通 |
| 崩溃 / 重启 | 稳定性采样摘要 `restarts` | 作为优化输入 |
| 内存趋势 | RSS 采样 | 观察趋势（不设 `<20%` 硬门槛） |
| 连接数 | 连接采样 | 发现泄漏趋势 |
| 缺陷 / 用户反馈 | 缺陷列表 + 使用方原话 | 决定下一轮优化项 |

**「真实链路」的定义要写死**：`dut IS NOT NULL` 且不是回放。否则本地 sleep 用例也能凑数。

### 6.3 迭代记录模板（每轮一份）

| 项 | 内容 |
|---|---|
| 本轮运行次数 / 成功率 | 数字（如实记录，不设达标线） |
| 崩溃 / 重启 / 内存趋势 / 连接数 | 每轮采样与趋势说明 |
| 新增缺陷（按级别） | 列表 + 处理状态 |
| 用户反馈 | 原话 + 分类（缺陷/需求/体验） |
| 本轮优化项 / 下一轮动作 | 1~3 条，评审结论闭环 |

---

## 7. 实施顺序（Wave）

| Wave | 内容 | 中间可验证态 |
|---|---|---|
| **W1** | HRD-01 矩阵 + HRD-02 审计（并行） | 112 格有结论；10 类操作有记录 |
| **W2** | HRD-03 试点迭代启动（并行）+ 手册编写 | 首轮迭代记录；手册初稿 |
| **W3** | 手册验证（新人实测）+ 修补 | 卡点清单 → 修手册 |
| **W4** | HRD-04 回滚演练 | RTO 计时记录 |
| **W5** | UAT 8 条 | 使用方签字 |
| **W6+** | G7 → 试点迭代（持续）+ 每轮迭代评审 | 迭代记录与评审结论 |

**W2 的试点迭代在后台持续跑，不阻塞手册编写**。这是本版唯一适合并行的部分。

---

## 8. 完成判定

```bash
# 1. 权限矩阵
mvn test -Dtest=AuthzMatrixTest -Dexport=/tmp/authz.csv
wc -l /tmp/authz.csv            # 113（含表头）
grep -c "N/A" /tmp/authz.csv    # 每行 N/A 必须有备注列非空
awk -F, 'NR>1 && $5=="N/A" && $7=="" {print "缺理由: "$0}' /tmp/authz.csv   # 空

# 2. 审计
# 10 类操作各做一次，然后：
mysql robot_dev -e "SELECT action, COUNT(*) FROM audit_log GROUP BY action"
# 期望：10 个 action 都存在

# 3. 试点迭代记录
# 检查 docs/evidence/v1.0/pilot-iterations.md：每轮含崩溃/重启/内存趋势/连接数
# 不设硬门槛，趋势作为优化输入（不作为 24h 稳定性结论）

# 4. 手册可执行（新人实测）
# 记录：谁、耗时、卡住几次 → docs/evidence/v1.0/manual-test.md

# 5. 回滚演练
date +%s > /tmp/t0 && bash scripts/rollback.sh --to v0.6.2 && date +%s > /tmp/t1
echo $(( ($(cat /tmp/t1) - $(cat /tmp/t0)) / 60 ))   # ≤30

# 6. 迭代评审
# 查看 docs/evidence/v1.0/pilot-iterations.md 与 iteration-review.md，确认每轮结论闭环
```

---

## 9. 风险与降级

| 风险 | 信号 | 降级 | 谁批 |
|---|---|---|---|
| 回滚演练 >30 分钟 | 首次演练 45 分钟 | 分析瓶颈（多半是 DB dump 慢）→ 优化备份方式；不降低 RTO 标准 | 大师 |
| down 迁移删数据引发争议 | 回滚后计划配置丢了 | 默认 `schema=keep`；只回代码不回 schema | silence |
| 新人按手册卡住 | 实测 3 处以上卡点 | 修手册，不改标准；卡点本身就是手册的 bug | — |
| 试点迭代中发现能力倒退 | 任一已过能力项回归失败 | 停下补能力，不带病进入下一轮 | 大师 |
| UAT 使用方不配合 | 拖延 >2 周 | 由大师代执行并注明「代执行」，不冒充使用方 | 大师 |
| 多轮内存趋势持续上涨 | RSS 连续多轮上升 | 记录并归因（多半是日志缓冲/连接池），作为优化项；不设硬门槛、不阻断迭代 | CCB |

---

## 10. 交付物

| 类别 | 内容 |
|---|---|
| 代码 | 审计切面与查询、权限矩阵测试（YAML 驱动）、`scripts/rollback.sh`、`scripts/window-stats.sql` |
| 文档 | 运维手册、回滚预案、升级路径、UAT 记录表、试点迭代记录（每轮一份） |
| 证据 | `docs/evidence/v1.0/`：authz.csv（签字）、audit-10.md、manual-test.md、rollback-timing.md、uat-8.md、pilot-iterations.md（每轮崩溃/重启/内存趋势/连接数）、iteration-review.md（每轮评审结论） |

---

## 实际完成与偏差（2026-11-06 二轮）

| 计划项 | 实际 | 偏差原因 |
|---|---|---|
| 真实账号 HTTP 全链路 | 自建夹具项目 + 5 个 PBKDF2 账号，脚本化 36 条断言 | 「等人给账号」不是必要条件，库在本机就能造 |
| 真实浏览器截图 | 复用既有 Chrome CDP 通道，脚本化 8 页 × 2 主题 | 不必等 Playwright 依赖 |
| 只读联动 | 改 `Compartment` + `watch` | 原实现权限异步到达后不跟随，属越权可写 |
| 布局 | `.page` 轨道钉成 `minmax(0,1fr)` | 单列 grid 按 max-content 撑宽，1280 横滚 |
| 门禁 | 10 步 → 11 步（补 `packages/editor` 219 条） | 编辑器包有独立 vitest 工程，frontend 扫不到，其回归等于没人跑 |
| 工作量 | 多出 2 个缺陷修复 + 4 条编辑器回归 + 1 份夹具脚本 | 均为真链路暴露，非功能扩张 |
