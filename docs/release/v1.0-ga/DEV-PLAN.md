# v1.0 生产就绪 · 开发计划

**文档编号** RA-REL-2026-007-DEV ｜ **版本** v1.0 ｜ **门禁** G7 → G8

**范围** HRD-01~04 + 试运行观察窗 ｜ **对应 PRD** FR-v1.0-01 ~ 25

---

## 1. 任务拆解与依赖

| WBS | 任务 | 参考人日 | 前置 | AI 自主度 | 完成判定（命令级） |
|---|---|---:|---|---|---|
| HRD-01 | 权限矩阵全量校验（112 格） | 5 | G2~G6 | A（人签字） | 112 格逐格有结论，导出表格可勾 |
| HRD-02 | 审计日志（10 类关键操作） | 5 | G2~G6 | A | 10 类各抽查 1 次均有记录 |
| HRD-03 | 压测报告与运维手册 | 6 | G7 前置 | **B** | 24 h 无崩溃；新人独立重启成功 |
| HRD-04 | 回滚预案与演练 | 6 | 全部 | **C** | 演练 RTO ≤30 分钟，签字 |
| B8 | 试运行观察窗 | — | G7 | **C** | 4 周 ≥20 次 零 P0 成功率 ≥99% |

### 1.1 顺序

```
HRD-01（权限）─┐
HRD-02（审计）─┼─► G7 ─► 观察窗（4 周）─► G8
HRD-03（压测/手册）─┤
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

## 4. 压测与运维手册（HRD-03）

### 4.1 24 小时稳定性压测

| 项 | 设定 |
|---|---|
| 负载 | 定时任务每 10 分钟一次（轻量用例集）+ 每 2 小时一次 4 并发 |
| 监控 | JVM heap、RSS、DB 连接数、磁盘、GC 次数 |
| 判定 | 无崩溃/无重启；RSS 增长 <20%；无连接泄漏（连接数回到基线） |
| 产出 | 24 h 曲线 + 容量基线三项（内存/磁盘/DB 体积） |

### 4.2 运维手册结构（必须能照着做）

```
1. 服务清单（roboscope:8145 / robotdashboard:8080 / 平台:8080x）
   - 都是 systemd --user unit，需要 loginctl enable-linger
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

## 6. 试运行观察窗（B8）

### 6.1 指标采集（脚本化，不靠人记）

```sql
-- 每日统计（scripts/window-stats.sql）
SELECT DATE(started_at) d,
       COUNT(*) runs,
       SUM(state='finished') ok,
       ROUND(SUM(state='finished')/COUNT(*)*100, 1) success_rate,
       SUM(exportable) exportable_runs
FROM run
WHERE started_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
GROUP BY DATE(started_at);
```

### 6.2 四项判据的采集方式

| 判据 | 数据来源 | 判定 |
|---|---|---|
| ≥4 周 | 观察窗起止日期 | 日历差 ≥28 天 |
| ≥20 次真实链路运行 | `run` 中 `trigger_type IN (schedule, ci, manual)` 且 `dut IS NOT NULL` | 计数 ≥20 |
| 零 P0 | 缺陷列表 P0 计数 | = 0 |
| 一周成功率 ≥99% | 最近 7 天 `finished / total` | ≥99% |

**「真实链路」的定义要写死**：`dut IS NOT NULL` 且不是回放。否则本地 sleep 用例也能凑数。

### 6.3 周报模板（4 份）

| 项 | 内容 |
|---|---|
| 本周运行次数 / 成功率 | 数字 |
| 新增缺陷（按级别） | 列表 + 处理状态 |
| 用户反馈 | 原话 + 分类（缺陷/需求/体验） |
| 下周动作 | 1~3 条 |

---

## 7. 实施顺序（Wave）

| Wave | 内容 | 中间可验证态 |
|---|---|---|
| **W1** | HRD-01 矩阵 + HRD-02 审计（并行） | 112 格有结论；10 类操作有记录 |
| **W2** | HRD-03 压测启动（24 h，后台跑）+ 手册编写 | 压测曲线出来；手册初稿 |
| **W3** | 手册验证（新人实测）+ 修补 | 卡点清单 → 修手册 |
| **W4** | HRD-04 回滚演练 | RTO 计时记录 |
| **W5** | UAT 8 条 | 使用方签字 |
| **W6+** | G7 → 观察窗 4 周 → G8 | 周报 ×4 |

**W2 的 24 h 压测在后台跑，不阻塞手册编写**。这是本版唯一适合并行的部分。

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

# 3. 24h 压测
grep -E "restarts|rss_growth|conn_leak" docs/evidence/v1.0/stability-24h.txt
# 期望：0 / <20% / 无

# 4. 手册可执行（新人实测）
# 记录：谁、耗时、卡住几次 → docs/evidence/v1.0/manual-test.md

# 5. 回滚演练
date +%s > /tmp/t0 && bash scripts/rollback.sh --to v0.6.2 && date +%s > /tmp/t1
echo $(( ($(cat /tmp/t1) - $(cat /tmp/t0)) / 60 ))   # ≤30

# 6. 观察窗
psql/mysql -e "$(cat scripts/window-stats.sql)"
```

---

## 9. 风险与降级

| 风险 | 信号 | 降级 | 谁批 |
|---|---|---|---|
| 回滚演练 >30 分钟 | 首次演练 45 分钟 | 分析瓶颈（多半是 DB dump 慢）→ 优化备份方式；不降低 RTO 标准 | 大师 |
| down 迁移删数据引发争议 | 回滚后计划配置丢了 | 默认 `schema=keep`；只回代码不回 schema | silence |
| 新人按手册卡住 | 实测 3 处以上卡点 | 修手册，不改标准；卡点本身就是手册的 bug | — |
| 观察窗出 P0 | 任一 P0 | **计时重置**，修完重新计 4 周 | 大师 |
| UAT 使用方不配合 | 拖延 >2 周 | 由大师代执行并注明「代执行」，不冒充使用方 | 大师 |
| 24h 压测内存缓涨 | RSS +25% | 查泄漏（多半是日志缓冲/连接池）；不达标记为 P1 但不阻断，观察窗继续盯 | CCB |

---

## 10. 交付物

| 类别 | 内容 |
|---|---|
| 代码 | 审计切面与查询、权限矩阵测试（YAML 驱动）、`scripts/rollback.sh`、`scripts/window-stats.sql` |
| 文档 | 运维手册、回滚预案、升级路径、UAT 记录表、试运行周报 ×4 |
| 证据 | `docs/evidence/v1.0/`：authz.csv（签字）、audit-10.md、stability-24h.txt、manual-test.md、rollback-timing.md、uat-8.md、window-4weeks.csv |
