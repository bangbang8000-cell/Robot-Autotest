# v0.4 执行 · 开发计划

**文档编号** RA-REL-2026-004-DEV ｜ **版本** v0.4 ｜ **门禁** G4

**范围** EXE-01~08 ｜ **对应 PRD** FR-v0.4-01 ~ 34

---

## 1. 任务拆解与依赖

| WBS | 任务 | 参考人日 | 前置 | AI 自主度 | 完成判定（命令级） |
|---|---|---:|---|---|---|
| EXE-01 | 执行域骨架（队列 / worker / 状态机） | 15 | EDT-06 | A | 状态机单测覆盖全部合法边；非法跃迁抛错 |
| EXE-02 | 租约表与状态机（设备+端口） | 18 | EXE-01 | **B（T2）** | 100 线程抢同 key，成功数 = 1 |
| EXE-03 | pabot 编排（PATH / 双日志 / 退出码 / 产物校验） | 20 | EXE-01 | **B（T2）** | 故障注入 5 种，均能报出真实原因 |
| EXE-05 | 取消 / 超时 / 幂等 / 重启恢复 | 12 | EXE-03 | B | 取消后 `ps` 无残留；重启无僵尸 |
| EXE-04 | 实时日志（WebSocket + 限流） | 15 | EXE-03 | A | 4 并发 P95 <2 s |
| EXE-06 | 失败分类与重试 | 10 | EXE-03 | B | 三类样本分类 100% 准确 |
| EXE-07 | 4 并发压测与调优 | 16 | EXE-02/03/04 | **B** | 加速比 ≥2.5x，报告可复跑 |
| EXE-08 | DUT 实网联通（SSH 采集器） | 15 | EXE-03 + DUT 在线 | **C** | 一条真实链路端到端有产物 |

### 1.1 顺序（本版的顺序比前面任何一版都重要）

```
EXE-01 状态机
   └─► EXE-03 最小闭环（1 suite）  ← 必须最先跑通，不含并发
          ├─► EXE-02 租约（先单 key，再多 key）
          ├─► EXE-05 取消/超时/恢复
          ├─► EXE-04 实时日志
          ├─► EXE-06 失败分类
          └─► EXE-08 DUT 实网
                 └─► EXE-07 4 并发压测（最后，它验证前面全部）
```

**EXE-07 排最后不是因为它不重要，是因为它是总验收**：租约没对、日志拖后腿、进程没杀干净，压测都会暴露。提前压等于同时调三个变量。

### 1.2 最小闭环的定义（EXE-03 的第一个可验证态）

> 一条 suite → 一个 pabot 进程 → `output.xml` 落盘 → 解析出用例数 → 写 `run` 与 `test_result` → 前端运行记录能看到。

这个闭环**不含并发、不含租约、不含实时日志**。它跑通之前不写任何池化代码。

---

## 2. 状态机（EXE-01）

### 2.1 状态与合法迁移

| 状态 | 含义 | 可迁移到 |
|---|---|---|
| `queued` | 已创建，等调度 | `preparing`、`canceled` |
| `preparing` | 切分 + 申请租约 | `running`、`failed(lease_timeout)`、`canceled` |
| `running` | 进程已启动且有产物目录 | `finished`、`failed`、`canceled` |
| `finished` | 产物校验通过且用例数 >0 | — |
| `failed` | 产物缺失 / 进程异常 / 超时 / 中断 | — |
| `canceled` | 用户取消 | — |

**非法迁移示例**（必须抛错，不是静默忽略）：`queued → running`（跳过了租约）、`finished → running`（终态不可逆）、`canceled → finished`。

### 2.2 状态机实现

```java
public enum RunState { QUEUED, PREPARING, RUNNING, FINISHED, FAILED, CANCELED }

private static final Map<RunState, Set<RunState>> ALLOWED = Map.of(
    QUEUED,     Set.of(PREPARING, CANCELED),
    PREPARING,  Set.of(RUNNING, FAILED, CANCELED),
    RUNNING,    Set.of(FINISHED, FAILED, CANCELED),
    FINISHED,   Set.of(),
    FAILED,     Set.of(),
    CANCELED,   Set.of()
);

public void transition(RunState to) {
    if (!ALLOWED.get(this.state).contains(to))
        throw new IllegalStateTransition(this.state, to);
    this.state = to;
}
```

**带条件的迁移**：`PREPARING → RUNNING` 必须同时满足「租约已持有」+「产物目录已创建」，否则不允许。这条用 `transition(to, precondition)` 重载实现，**不能只靠调用方自觉**。

**持久化**：状态变更与业务动作在**同一事务**里（如：置 `running` 与写 `run.started_at` 同事务），避免「状态变了但时间没记」。

---

## 3. 租约（EXE-02，本版 T2 的一半）

### 3.1 表结构与互斥原理

```sql
CREATE TABLE resource_lease (
  resource_key VARCHAR(128) NOT NULL,     -- 'DUT-01/mlx5_0'
  job_id       CHAR(36)     NOT NULL,
  run_id       CHAR(36)     NOT NULL,
  project_id   BIGINT       NOT NULL,
  acquired_at  DATETIME(3)  NOT NULL,
  expires_at   DATETIME(3)  NOT NULL,
  PRIMARY KEY (resource_key)              -- 天然互斥，不靠应用层判断
) ENGINE=InnoDB;
```

**为什么不先 `SELECT` 再 `INSERT`**：并发下「查空 → 两个都插」是经典竞态。**直接 INSERT 让主键冲突来裁决**，捕获 `DuplicateKeyException` 视为「被占用」。

```sql
-- 申请（成功 = 插入成功；失败 = 主键冲突）
INSERT INTO resource_lease (resource_key, job_id, run_id, project_id, acquired_at, expires_at)
VALUES (?, ?, ?, ?, NOW(3), DATE_ADD(NOW(3), INTERVAL 120 SECOND));
```

### 3.2 生命周期

| 阶段 | 动作 | 触发 |
|---|---|---|
| 申请 | INSERT，冲突则进等待队列（按 `resource_key` 分组） | 调度前 |
| 续约 | `UPDATE expires_at = NOW(3) + 120s WHERE resource_key=? AND job_id=?` | 每 40 s 心跳 |
| 释放 | `DELETE WHERE resource_key=? AND job_id=?` | 进程退出（正常/异常/取消）|
| 回收 | `DELETE WHERE expires_at < NOW(3)`，对应 run 标 `failed(lease_expired)` | 每 60 s 定时任务 |

**三条硬约束**：

1. **释放必须带 `job_id`**：否则 A 任务可能删掉 B 任务刚抢到的租约（这是最隐蔽的并发 bug）。
2. **心跳与进程生命周期绑定**：进程死了心跳停，TTL 到期自动回收。**不依赖「优雅退出」**——`kill -9` 时没有优雅退出。
3. **回收后要标记 run**：只删租约不标 run，用户会看到永远 `running` 的任务。

### 3.3 调度与切分

```
1. 读取用例集 → 展开为 suite 列表
2. 分片数 = min(MAX_PARALLEL, suite 数)
3. 每个分片按「设备 + 端口」需求申请租约（顺序申请，全部拿到才继续）
4. 任一租约超时未拿到 → 释放已拿到的 → run 标 failed(lease_timeout)
5. 全部分片就绪 → 创建产物目录 → 置 running → 启动进程
```

**第 4 步是关键**：拿到一半租约就启动，会造成「部分跑、部分等」，日志和状态都乱。**要么全拿到，要么全放掉。**

---

## 4. pabot 编排（EXE-03，本版最容易踩坑的地方）

### 4.1 环境准备（四条，缺一条就会静默失败）

```java
ProcessBuilder pb = new ProcessBuilder(cmd);
Map<String,String> env = pb.environment();

// ① PATH 必须前置 venv/bin —— pabot 用裸名 "robot" 找子进程
env.put("PATH", rfVenvBin + ":" + env.getOrDefault("PATH", ""));

// ② 显式指定输出目录，不用默认值
// cmd 内含 --outputdir <artifactsDir>/<run_id>

// ③ 时区与语言固定，避免日志时间戳漂移
env.put("TZ", "Asia/Shanghai");
env.put("LANG", "C.UTF-8");

// ④ 不依赖 login shell（bash -lc 在服务环境里加载不到用户 PATH）
pb.directory(new File(workspaceDir));
```

> **这条来自实测**：直接调 `.venv/bin/pabot` 而不前置 `PATH`，所有子进程 `FileNotFoundError: 'robot'`，而 pabot 的 stdout 只说「subprocesses encountered an error」，`output.xml` 是空的。**必须做故障注入测试证明平台能报出真实原因**（TEST-PLAN TC-v0.4-402）。

### 4.2 命令构造

```bash
# 每个分片一条命令
<venv>/bin/pabot \
  --processes <分片内 suite 数> \
  --outputdir <artifacts>/<run_id>/shard-<i> \
  --name "<用例集名>-分片<i>" \
  --include <标签表达式，可选> \
  <suite 文件列表>
```

`--processes` 与服务端端口数无关（那是 iperf 的坑），但**分片内的 suite 数要 ≤ 该分片可用的 pabot 并发**，否则 pabot 内部再排队，加速比会失真。

### 4.3 产物校验（退出码不可信的落地）

```java
// 判定 success 的四个条件，全部满足才算成功
boolean ok = exitCode == 0
          && Files.exists(outputXml)                       // ① 产物存在
          && Files.size(outputXml) > 1024                   // ② 非空（空 xml 约 300 字节）
          && parse(outputXml).totalTests > 0;               // ③ 能解析且有用例
// ④ 每个分片目录都有 output.xml（合并前）
```

**为什么需要 ②**：RF 在「全部 suite 被过滤掉」时会生成一个只有统计节点的 `output.xml`，退出码 0，用例数 0。只判 `exists` 会把「什么都没跑」当成功。

### 4.4 双日志采集

| 来源 | 路径 | 用途 |
|---|---|---|
| 进程 stdout/stderr | 内存缓冲 + 落 `shard-<i>/stdout.log` | 常规进度 |
| pabot 管理器日志 | `<outputdir>/pabot_results/pabot_manager.log` | **真实异常在这里**（子进程报错、参数错误） |
| 分片 stderr | `pabot_results/<n>/robot_stderr.out` | 单分片崩溃原因 |

**失败时的诊断顺序**（写进运维手册）：先看 `pabot_manager.log` → 再看 `robot_stderr.out` → 最后看 stdout。**反过来查会浪费半小时**。

### 4.5 预检（创建运行前就拦住）

| 预检项 | 不通过时 |
|---|---|
| venv 里 `pabot --version` 可执行 | 拒绝创建，提示安装路径 |
| `artifacts` 根目录可写 | 拒绝，提示磁盘/权限 |
| 磁盘剩余 ≥ 5 GB | 拒绝，提示清理（v0.5 有清理任务前先人工） |
| 用例集非空 | 拒绝 |
| 目标设备状态非「维修/下线」 | 拒绝并列出冲突设备 |

---

## 5. 实时日志（EXE-04）

```
pabot stdout → LogPump（每 run 一个线程）→ RingBuffer（最近 2000 行）
                                        ├─► WebSocket /ws/runs/{runId}
                                        └─► 落盘 <artifacts>/<run_id>/run.log（全量）
```

| 设计点 | 决定 | 理由 |
|---|---|---|
| 频道 | 按 `run_id` 分组，不广播 | 4 并发广播会把每人 4 倍流量 |
| 限流 | 每 run 每 100 ms 合并推送一次，单次 ≤50 行 | 疯狂打日志不拖死后端 |
| 背压 | 客户端消费慢 → 丢弃旧行并插一条「已省略 N 行」 | 日志不是审计，可丢但要说 |
| 重连 | 补发 RingBuffer 最近 200 行 | 不要求全量重放（全量在落盘文件里） |
| 落盘 | 独立于推送，全量写 | 下载要全的 |

**延迟测量方法**：日志行里带生成时间戳（平台侧），前端收到时算差值，取 P95。**不能只测「看起来实时」**。

---

## 6. 取消与超时（EXE-05）

```java
// 杀进程树，不只杀父进程
ProcessHandle.of(pid).ifPresent(h -> {
    h.descendants().forEach(ProcessHandle::destroy);   // SIGTERM 等价
    h.destroy();
});
// 5 秒后仍未退
if (stillAlive(pid)) {
    h.descendants().forEach(ProcessHandle::destroyForcibly);
    h.destroyForcibly();
}
```

**四个必须验证的点**：

| 点 | 验证方式 |
|---|---|
| 子进程真的死了 | 取消后 `pgrep -af robot` 无输出 |
| 租约真的释放 | 取消后立刻新建 run，能拿到同一设备 |
| 状态真的改了 | run 状态为 `canceled`，`finished_at` 有值 |
| 产物保留 | 已产生的日志不删（用于事后分析） |

**重启恢复**：启动时扫 `running`/`preparing` 状态 → 标 `failed(interrupted)` → 释放其全部租约 → 写审计。**这一步不做，平台重启后就有一堆僵尸任务占着设备。**

---

## 7. 失败分类（EXE-06）

| 类别 | 判据（可配规则） | 是否重试 |
|---|---|---|
| `assert` | RF 输出含 `FAIL` 且不含连接/超时关键词；`test_result.failure_msg` 有断言文本 | ❌ |
| `env` | 含 `Connection refused` / `timed out` / `No route to host` / `SSH` 认证失败 | ✅ 最多 1 次 |
| `infra` | 产物缺失 / 解析失败 / 进程被杀 / 磁盘满 | ❌（重试也是同样结果，先修平台） |
| `canceled` | 用户取消或超时 | ❌ |

**为什么断言失败不重试**：重试会让「偶发真 bug」变成「第二次通过了」，掩盖问题。**只重试环境类**——环境抖动是客观存在的，断言不会自己变对。

---

## 8. 4 并发压测（EXE-07）

### 8.1 固定用例集（保证可比）

| 项 | 设定 |
|---|---|
| 结构 | 4 个 suite × 2 条用例 |
| 每条用例 | `Sleep 6s` + 一条 `Log`（模拟真实用例的固定耗时） |
| 串行基线 | `robot` 跑全部 4 suite ≈ 48 s + 启动开销 |
| 并行 | `pabot --processes 4` |
| 轮次 | 连续 20 轮 |

### 8.2 指标计算

```
加速比 = 串行总耗时 / 并行总耗时
调度开销 = (并行总耗时 - 最慢分片耗时) / 并行总耗时
冲突次数 = resource_lease 历史中同一 resource_key 同时存在两个持有者的次数（应为 0）
```

### 8.3 关于加速比的诚实说明

加速比**强烈依赖用例形态**：

| 用例形态 | 预期加速比 | 原因 |
|---|---|---|
| 4 suite × 12 s | ~3.0-3.5x | 固定开销占比小 |
| 4 suite × 6 s | ~2.5-3.0x | 启动开销（~1.5 s/进程）开始显形 |
| 4 suite × 1 s | ~1.2-1.5x | 全是开销，并行没意义 |

**所以**：验收用 12 s 形态（PRD NFR-v0.4-01），**报告里必须写明用例形态**，否则这个数字会被误用。若真实用例普遍很短，2.5x 这个目标本身要重新讨论（走 CCB，不改门禁但改口径说明）。

---

## 9. DUT 实网（EXE-08）

| 项 | 内容 |
|---|---|
| 路径 | 平台（149）→ `192.168.180.2` → DUT `10.202.54.106/107` |
| 协议 | SSH（paramiko 或系统 ssh + 密钥） |
| 凭据 | 从 `credential` 表解密注入，**不写临时文件、不进日志** |
| 采集项 | `ip -br addr`、`ethtool -S <if>` 关键计数、`cat /proc/interrupts` 摘要 |
| 超时 | 连接 5 s，命令 15 s |
| 失败分类 | `unreachable` / `auth_failed` / `cmd_not_found`，各给建议 |

**降级预案**：DUT 掉线时，用「录制回放」（预存的真实输出样本）验证解析链路，但**必须在证据里明确标注是回放，不是实网**——不允许用回放冒充实网通过。

---

## 10. 实施顺序（Wave）

| Wave | 内容 | 中间可验证态 |
|---|---|---|
| **W1** | EXE-01 状态机 + 表迁移 | 状态机单测全绿 |
| **W2** | EXE-03 最小闭环（1 suite，无租约无日志） | 一条用例跑通，`test_result` 有记录 |
| **W3** | EXE-02 租约（单 key → 多 key） | 100 线程抢锁成功数 = 1 |
| **W4** | EXE-05 取消/超时/重启恢复 | `ps` 无残留，重启无僵尸 |
| **W5** | EXE-04 实时日志 | 单 run 延迟 <1 s |
| **W6** | EXE-06 失败分类 + EXE-08 DUT（并行） | 三类样本准确；实网有产物 |
| **W7** | EXE-07 4 并发压测与调优 | 20 轮报告 |

---

## 11. 完成判定

```bash
# 1. 最小闭环
curl -X POST .../api/runs -d '{"setId":1,"concurrency":1}'
sleep 20 && curl .../api/runs/<id> | jq '.state,.totalTests'    # finished, >0

# 2. 租约互斥（并发单测）
mvn test -Dtest=LeaseConcurrencyTest    # 100 线程，成功 1

# 3. 故障注入：PATH 去掉
# 在 bash 里 unset PATH 前置后触发运行
# 期望：run.state=failed，log 含 "找不到 robot" 或 "No such file"，不是 "执行完成无产物"

# 4. 故障注入：退出码 0 但产物空
# 造一个所有 suite 被 --include 过滤掉的运行
# 期望：failed，原因 "no tests executed"

# 5. 取消
RID=$(curl -X POST .../api/runs ... | jq -r .id)
curl -X POST .../api/runs/$RID/cancel
sleep 6 && ps -ef | grep -c "[r]obot"          # 0
curl -X POST .../api/runs -d '...' | jq '.leaseWait'   # 立即拿到，无等待

# 6. 4 并发压测
bash scripts/bench-4way.sh --rounds 20 > docs/evidence/v0.4/bench.txt
grep -E "speedup|conflict|p95" docs/evidence/v0.4/bench.txt
```

---

## 12. 风险与降级

| 风险 | 信号 | 降级 | 谁批 |
|---|---|---|---|
| 加速比 <2.5x | 压测 3 轮均 <2.0x | 并发降为 2，UI 限制同时提交数（§13.3 预案） | CCB |
| 租约回收不及时 | 取消后仍需等 TTL | TTL 从 120 s 降为 60 s，心跳 20 s | silence |
| 日志量打爆磁盘 | 单 run >500 MB | 落盘前截断到 500 MB 并记「已截断」 | — |
| DUT 掉线 | EXE-08 无法实网 | 回放兜底 + 证据标注「回放」，不冒充 | 大师 |
| pabot 与 RF 版本冲突 | 升级后行为变化 | 锁版本（`pabot==5.2.2` / `robotframework==7.5`），升级走 ADR | — |

---

## 13. 交付物

| 类别 | 内容 |
|---|---|
| 代码 | 执行域（队列/worker/状态机）、租约服务、pabot 编排器、日志推送、失败分类器、DUT 采集器、前端运行记录与实时面板 |
| 迁移 | `V4_1__lease.sql`、`V4_2__run_artifact.sql`、`V4_3__failure_class.sql` |
| 文档 | 执行域设计说明（含 PATH/双日志/退出码三条硬约束）、`CONVENTIONS-exec`、ADR-0008 租约粒度、ADR-0009 加速比口径 |
| 证据 | `docs/evidence/v0.4/`：并发单测、5 种故障注入、取消前后 `ps`、20 轮压测报告、DUT 实网采集日志 |
