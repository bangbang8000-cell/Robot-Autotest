# v0.6 自动化 · 开发计划

**文档编号** RA-REL-2026-006-DEV ｜ **版本** v0.6 ｜ **门禁** G6

**范围** PLN-01~04 ｜ **对应 PRD** FR-v0.6-01 ~ 21

---

## 1. 任务拆解与依赖

| WBS | 任务 | 参考人日 | 前置 | AI 自主度 | 完成判定（命令级） |
|---|---|---:|---|---|---|
| PLN-01 | 执行计划（cron / metadata 注入 / 重叠与错过策略） | 10 | G5 | **B** | 定时 run 的 `exportable=true`；7 天偏差 ≤30 s |
| PLN-02 | 依赖前置（DAG / 环检测 / 超时） | 10 | PLN-01 | A | 环检测拒绝保存；前置失败后置 `blocked` |
| PLN-03 | CI 触发 + 回写（Actions → API → run） | 8 | PLN-01 | **B** | push 触发；同 commit 重放幂等 |
| PLN-04 | Gitea 或双仓决策落地 | 5 | — | **C** | ADR 签发，方案可执行 |

### 1.1 顺序

```
PLN-01（metadata 注入必须先于 cron 落地）
   ├─► PLN-02 依赖
   └─► PLN-03 CI 触发 ─► 回写
PLN-04 并行（是决策，不阻塞开发）
```

**`metadata 注入` 是 PLN-01 的第一件事**，不是最后补的。理由见 PRD §1.2：没有它，自动跑的 run 全部不可对外。

---

## 2. 调度器（PLN-01）

### 2.1 为什么不用 Quartz 集群模式

| 选项 | 问题 |
|---|---|
| Quartz + JDBC JobStore 集群 | 引入 11 张 `QRTZ_*` 表，为「单机、几十个计划」的场景过度设计 |
| **表驱动 + 定时扫描 + 行锁** | ✅ 逻辑全在自家表里，可查可调，无黑盒 |

### 2.2 核心循环（每 30 秒一次）

```java
@Scheduled(fixedDelay = 30_000)
public void tick() {
    Instant now = Instant.now();
    // 1. 取到期计划（next_run_at <= now，enabled=1）
    List<Schedule> due = scheduleMapper.findDue(now, 50);

    for (Schedule s : due) {
        // 2. 用 last_triggered_at 做乐观锁，防止多线程/重启重复触发
        int claimed = scheduleMapper.claim(s.getId(), s.getLastTriggeredAt(), now);
        if (claimed == 0) continue;                  // 别人抢到了

        // 3. 判定 misfire / overlap
        if (Duration.between(s.getNextRunAt(), now).toMinutes() > 5 && !s.isCatchUp()) {
            triggerLog.skip(s, "misfire");            // 错过太久，不补
        } else if (runningExists(s.getId())) {
            triggerLog.skip(s, "overlap");            // 上一轮还没跑完
        } else {
            enqueue(s);                               // 创建 run
        }
        // 4. 无论跑没跑，都要算下一次
        scheduleMapper.updateNextRun(s.getId(), cron.next(s.getNextRunAt()));
    }
}
```

**三个必须的实现细节**：

| 细节 | 做法 | 不做的后果 |
|---|---|---|
| 防重复触发 | `UPDATE ... WHERE id=? AND last_triggered_at=?`，影响行数 0 即放弃 | 重启瞬间可能触发两次 |
| 先抢占再执行 | 抢占成功才创建 run | 并发扫描时同一计划起两个 run |
| 无论结果都推进 `next_run_at` | 跳过也要推进 | 错过的计划会每秒重试，日志爆炸 |

### 2.3 cron 与时区

```java
CronExpression expr = CronExpression.parse(s.getCron());          // Spring 6 段式
ZonedDateTime next = expr.next(ZonedDateTime.now(ZoneId.of("Asia/Shanghai")));
```

- **时区硬编码 `Asia/Shanghai`**，不读 `systemDefault`——149 的 TZ 变了会影响触发，这是运维事故源。
- 校验时用 `CronExpression.isValidExpression()`，并在保存前算 5 次触发时间给用户确认。
- 支持 6 段（秒 分 时 日 月 周），**不支持年字段**，文档写清。

### 2.4 metadata 注入（本版最高优先级）

```java
Run createFromSchedule(Schedule s) {
    Run r = new Run();
    r.setProjectId(s.getProjectId());
    r.setTestSetId(s.getTestSetId());
    r.setTriggerType("schedule");
    // 十项中的固定项来自计划配置
    r.setQaProject(s.getProjectName());
    r.setDut(s.getDut());
    r.setLink(s.getLink());
    r.setSwBuild(s.getSwBuild());        // 必填，保存计划时校验非空
    r.setCaseKind(s.getCaseKind());      // smoke/regression/nightly
    r.setBaseline(s.getBaseline());       // 可为空（表示无基线）
    r.setOperator(s.getCreatedBy());      // 计划创建人
    // 运行时生成项
    r.setCaseId(s.getTestSetId() + "-" + s.getId());
    r.setRunId(newRunId());
    r.setStartedAt(Instant.now());
    ...
}
```

**保存计划时强制校验**：`SW_BUILD` 非空、`DUT`/`LINK` 必选、`CASE_KIND` 枚举合法。**空值不允许保存**——否则定时任务会持续产出不可对外的报告，而没人会去看运行记录。

> 这一条比 cron 本身更重要。定时任务无人值守，错误会静默积累 30 天。

---

## 3. 依赖前置（PLN-02）

### 3.1 数据模型

```sql
CREATE TABLE schedule_dependency (
  schedule_id       BIGINT NOT NULL,
  depends_on_id     BIGINT NOT NULL,
  timeout_minutes   INT    NOT NULL DEFAULT 30,
  PRIMARY KEY (schedule_id, depends_on_id)
);
```

### 3.2 环检测（保存时）

```java
void assertNoCycle(long id, List<Long> deps) {
    // DFS 从每个 dep 出发，能回到 id 即有环
    Deque<Long> path = new ArrayDeque<>();
    for (Long d : deps) {
        if (reaches(d, id, path)) {
            throw new CycleException(path);   // 报错时把环路径带上
        }
    }
}
```

**报错必须带环路径**（`A → B → C → A`），只说「存在循环依赖」等于让人自己找。

### 3.3 触发时的依赖判定

```
对每个前置 P：
  取 P 在「本计划本轮时间窗内」的最近一次 run（当日 00:00 ~ 现在）
  若不存在          → 等待，直到 timeout
  若 P 的 run 未结束 → 等待
  若 P 最后结论失败  → 本计划 blocked(upstream_failed)，不启动
  全部成功          → 继续
```

| 决策 | 规则 | 理由 |
|---|---|---|
| 「最近一次」的口径 | **当日窗口内**，不跨天 | 跨天会把昨天晚上的成功当成今天的通过 |
| 前置失败 | 后置 `blocked`，不重试 | 前置失败说明环境或代码有问题，后置跑了也是白跑 |
| 等待超时 | 默认 30 分钟，可配 | 无限等会让整个链条卡死 |
| 前置被停用 | 视为「不满足」，后置 `blocked(upstream_disabled)` | 显式失败好过静默通过 |

---

## 4. CI 触发（PLN-03）

### 4.1 接口契约

```http
POST /api/ci/trigger
Authorization: Bearer <project-api-token>
Content-Type: application/json

{
  "planId": 12,
  "commitSha": "2b236b3...",
  "ref": "refs/heads/main",
  "build": "SW-2026.11-rc1",       // 可选，缺省用 sha 前 8 位
  "repo": "huanchen811509-silence/Robot-Autotest"
}
→ 202 { "runId": "...", "duplicated": false }
```

| 安全点 | 做法 |
|---|---|
| 鉴权 | `api_token`（v0.2 表），scope 限 `ci:trigger`，绑定单项目 |
| 幂等 | `UNIQUE(plan_id, commit_sha)`；重复插入捕获冲突，返回已有 runId + `duplicated:true` |
| 限流 | 每 token 每分钟 ≤10 次，超出 429 |
| 不做的事 | 不在接口里等执行结果（异步），避免 Actions 超时 |

### 4.2 GitHub Actions 示例（写进文档，用户照抄）

```yaml
name: trigger-qa
on:
  push:
    branches: [main]
jobs:
  trigger:
    # 必须自托管：托管 runner 到不了 10.202.54.x
    runs-on: [self-hosted, linux, x64]
    steps:
      - name: Trigger platform run
        run: |
          curl -fsS -X POST "${{ vars.QA_PLATFORM_URL }}/api/ci/trigger" \
            -H "Authorization: Bearer ${{ secrets.QA_CI_TOKEN }}" \
            -H "Content-Type: application/json" \
            -d "{\"planId\":12,\"commitSha\":\"${GITHUB_SHA}\",\"ref\":\"${GITHUB_REF}\"}"
```

### 4.3 回写

| 目标 | 可行性 | 做法 |
|---|---|---|
| **GitHub commit status** | ✅ 有 API | 运行结束回调：POST `/repos/{owner}/{repo}/statuses/{sha}`，`state=success/failure`，`target_url` 指向报告页 |
| **群晖 Git** | ❌ 无 API | **不假装**。只写 `schedule_trigger_log` 与审计；文档明确「群晖侧无状态回写」 |
| 通知（IM/邮件） | ⬜ 不在范围 | 待办池 |

**回写失败的容错**：回写失败不影响 run 结论，只记 `trigger_log.writeback_error`。**不能让「回写不上」把一次成功的运行变成失败**。

---

## 5. 双仓与 Gitea 决策（PLN-04）

### 5.1 现状事实

| 项 | 事实 |
|---|---|
| 群晖 `git server` | 只有 SSH 协议，**无 HTTP、无 Web UI、无 webhook、无细粒度权限** |
| 群晖 Container Manager | 24.0.2 已安装，可跑容器（149 没有 Docker） |
| GitHub | 个人号仓库，协作者权限上限 write；已转 public 才有 branch protection |

### 5.2 决策（ADR-0011）

| 方案 | 内容 | 成本 | 结论 |
|---|---|---|---|
| A | 群晖为权威源 + GitHub 协作与 CI，无 Web 评审 | 0 | **✅ 采用（本版）** |
| B | 群晖上 Gitea（容器），内网 Web 评审 | 半天部署 + 长期维护 | 触发条件：涉密代码不得出内网，或需要内网 PR 流程时 |

**采用的 A 方案，必须写清它的代价**：

- 内网没有 PR 评审界面，评审只能在 GitHub 做；
- 因此「涉密内容不得进 GitHub」是一条纪律（文档、真实口令、客户信息）；
- 若将来违反这条纪律，立即切 B。

> 这不是「暂时凑合」，是**有明确触发条件的决策**。ADR 里写清楚触发条件，避免半年后变成「当初为什么这么将就」。

---

## 6. 实施顺序（Wave）

| Wave | 内容 | 中间可验证态 |
|---|---|---|
| **W1** | metadata 注入 + 计划 CRUD + cron 预览 | 手动试跑的 run `exportable=true` |
| **W2** | 调度循环 + 防重复 + misfire/overlap | 改 cron 到 1 分钟后能触发一次 |
| **W3** | 依赖 DAG + 环检测 + 超时 | 环被拒；前置失败后置 blocked |
| **W4** | CI 触发 + 幂等 + 回写 | push 触发一次，重放不重复 |
| **W5** | 7 天观测 + PLN-04 ADR | 成功率统计 |

**W5 的 7 天是唯一「需要等」的环节**。不能压缩成 7 小时——跨天触发、周末、系统重启这些场景必须真实跨越才有意义。这 7 天可以与其他工作并行。

---

## 7. 完成判定

```bash
# 1. metadata 注入（关键）
curl -X POST .../api/schedules -d '{"name":"t","cron":"0 * * * * ?","setId":1,"swBuild":"SW-2026.11","dut":"DUT-01","link":"L1"}'
curl -X POST .../api/schedules/1/run-now
curl .../api/runs/<id> | jq '.exportable,.swBuild'     # true, "SW-2026.11"

# 2. 空 SW_BUILD 拒绝保存
curl -X POST .../api/schedules -d '{"swBuild":""}'      # 400 + "SW_BUILD 不能为空"

# 3. cron 校验与预览
curl .../api/schedules/preview?cron=0%200%2025%20*%20*%20%3F   # 400 小时超范围
curl .../api/schedules/preview?cron=0%200%208%20*%20*%20%3F    # 5 个时间点

# 4. 防重复（并发 tick）
# 并发调用两次 tick（测试端点），观察只产生 1 个 run
mysql robot_dev -e "SELECT COUNT(*) FROM run WHERE trigger_type='schedule' AND plan_id=1"

# 5. 环检测
curl -X POST .../api/schedules/1/deps -d '{"dependsOn":2}'
curl -X POST .../api/schedules/2/deps -d '{"dependsOn":1}'   # 400 + 环路径

# 6. CI 触发幂等
for i in 1 2 3; do curl -X POST .../api/ci/trigger -d '{"planId":12,"commitSha":"abc123"}'; done
mysql robot_dev -e "SELECT COUNT(*) FROM run WHERE commit_sha='abc123'"   # 1

# 7. 群晖不虚报
curl .../api/ci/trigger/repo=syno | jq '.writeback'   # "not_supported"（不返回 success）
```

---

## 8. 风险与降级

| 风险 | 信号 | 降级 | 谁批 |
|---|---|---|---|
| 7 天观测期间触发失败 | 成功率 <100% | 查 tick 日志与 DB 行锁；必要时 tick 间隔从 30 s 降到 10 s | — |
| 重启导致重复触发 | 同一分钟两个 run | 行锁逻辑加固；已产生的重复 run 手工取消 | silence |
| GitHub token 权限不足 | 回写 403 | 降级为「只记录不回写」，报告链接手工贴 | — |
| 群晖被误以为能回写 | 用户问「为什么群晖没状态」 | 文档 + 页面提示「群晖侧不支持状态回写」 | — |
| 依赖等待卡住队列 | 多个 blocked 堆积 | 超时默认调为 15 分钟；blocked 不占执行资源 | — |
| CI token 泄露 | 恶意触发 | token 按项目隔离 + 限流 + 可即时吊销 | 大师 |

---

## 9. 交付物

| 类别 | 内容 |
|---|---|
| 代码 | 调度器、计划 CRUD、依赖 DAG、CI 触发接口、回写服务、前端执行计划页 |
| 迁移 | `V6_1__schedule.sql`、`V6_2__dependency.sql`、`V6_3__trigger_log.sql` |
| 文档 | 计划编排说明、CI 接入指南（含 Actions 示例与自托管 runner 要求）、`ADR-0011 双仓决策` |
| 证据 | `docs/evidence/v0.6/`：7 天触发日志、metadata 注入验证、幂等测试、环检测、回写记录 |
