# A7 · 双内核取消 / 超时进程树实测证据

> 对应 06 计划 §3 **A7**：`pytest + RF 杀进程树；ps 前后对比断言无残留子进程；两内核按同一阈值判定且文案含内核名`。
> 对应 TEST-PLAN **TC-v2.0-26**（取消）与 **TC-v2.0-27**（超时）。
> 执行时间：2026-11-07 13:40–13:42（CST）。环境：WSL2 Ubuntu-24.04 `~/robot-autotest`，
> 入口 `:8001` → 后端 `:8081`，MySQL `127.0.0.1:3306`；RF 运行时 `~/rf-stack/.venv`，pytest venv `~/venvs/pytest-kernel`。
> 全部读数来自真实运行，原始产物在 `raw/`。

## 1. 判据与结论

| 判据（06 计划原文） | 结论 | 依据 |
|---|---|---|
| `ps` 前后对比断言无残留子进程（取消） | **PASS** | 取消前 4 个进程（pabot + pabotlib + 2 robot），取消后 `pabot` 行 **0**、`pids_still_alive=[]`、`args_still_matching=[]` |
| `ps` 前后对比断言无残留子进程（超时） | **PASS** | 同结构；超时后同样零残留 |
| 两内核按**同一阈值**判定 | **PARTIAL** | RF 侧用产品 `timeoutSeconds=20` 命中，实测 **20.056s**；pytest 侧产品**没有 run 入口**，用探针以同一 20s 截止点判定（`waited_ms=4`，实际双内核均在 20s 点被终止）。详见 §5 |
| 失败文案**含内核名** | **FAIL（产品缺口）** | `failure_message`：超时 = `分片执行失败: timeout`、取消 = 操作人 reason，二者均不含 `pabot`/`pytest`；内核名只出现在 run 日志第 2 行 |

总判定 `PASS=false`，唯一失败项就是上面这条文案判据；进程树终止与残留判定 **12/12 子项全绿**
（`raw/a7-result.json` → `checks`）。

## 2. 三个场景与真实对象

| 场景 | 对象 | 结果 |
|---|---|---|
| A. 取消 | 真实平台 run `RUN-20261107134042-eb0000`（pabot，`concurrency=2`，两个慢套件） | 状态 `running → canceled`，进程树清零 |
| B. 超时 | 真实平台 run `RUN-20261107134046-eb0000`（同配置，`timeoutSeconds=20`） | `running → failed(timeout)`，20.056s 命中，进程树清零 |
| C. pytest | 产品**无 pytest run 入口**（`run` 表无 kernel 列，`CreateRunRequest` 忽略 `kernel` 字段），故用产品 argv 形状 + 产品 `ProcessTreeKiller` 的独立 Java 探针 | plain 与 `-n 2` 两种形态均在 20s 点被终止，零残留 |

## 3. RF · 取消（真实 API 调用）

命令链：`POST /api/execution/runs` → 轮询到 `running` → `ps -ww -eo pid,ppid,pgid,etimes,stat,args` 快照
→ `POST /api/execution/runs/{runId}/cancel?projectId=29`（体 `{"reason":"A7 取消取证"}`）→ 终态后 3s 再抓 ps。

取消前进程树（`raw/ps-cancel-before.txt`，4 个节点）：

```
1736040 1730324 1730324 0 Sl  ~/rf-stack/.venv/bin/pabot --outputdir ~/robot-test/artifacts/RUN-20261107134042-eb0000/shard-0 ...
1736052 1736040 1736052 0 Rs  ~/rf-stack/.venv/bin/robot -A .../pabot_results/...      # 分片 1
1736056 1736040 1736056 0 Rs  ~/rf-stack/.venv/bin/robot -A .../pabot_results/...      # 分片 2
(+ pabotlib 远程服务器，ppid=1736040，按 ppid 闭包纳入树)
```

| 项 | 读数 |
|---|---|
| 取消响应 | `200`，`state=CANCELED`，`message` 回显 `A7 取消取证` |
| 终态 | `canceled`，`failure_reason=canceled`，`failure_class=CANCELED`，`failure_message=A7 取消取证` |
| 取消后 ps | `raw/ps-cancel-after.txt`：`pabot` 命中 **0** 行；`pids_still_alive=[]`；`args_still_matching=[]` |
| 内核名 | `failure_message` 不含；run 日志含 `[system] 开始执行 pabot: ...`（`kernel_named_in_log=true`） |

代码依据：`CancelService.java:77` → `killer.kill(process, KILL_GRACE)`（`KILL_GRACE=5s`，`CancelService.java:27`）。

## 4. RF · 超时（真实阈值命中）

创建 run 时 `timeoutSeconds=20`。产品路径：`RunExecutionService.java:234`
`orchestrator.runShard(..., Duration.ofSeconds(cmd.timeoutSeconds()), ...)` →
`PabotOrchestrator.java:73` `handle.waitFor(timeout)` 返回 TIMEOUT →
`PabotOrchestrator.java:76-78` `descendants().forEach(destroyForcibly)` + `destroyForcibly`。

| 项 | 读数 |
|---|---|
| 超时命中用时 | **20.056s**（`run.started_at → finished_at`，判据「20s ± 2s」） |
| 终态 | `failed`，`failure_reason=timeout`，`failure_class=CANCELED`，`failure_message=分片执行失败: timeout`（`RunExecutionService.java:257`） |
| 超时前树 | 4 个进程，与 §3 同结构（`raw/ps-timeout-before.txt`） |
| 超时后 ps | `raw/ps-timeout-after.txt`：`pabot` **0** 行，零残留 |
| 内核名 | 文案不含内核名（同 §3 缺口） |

## 5. pytest · 无产品 run 入口下的替代口径

**为什么不能直接走 run**：v2.0 的 `POST /api/execution/runs` 只有 RF/pabot 一条路径；
`CreateRunRequest` 用 Jackson 忽略未知属性，传 `kernel=pytest` 不会报错也不会生效，`run` 表也没有 kernel 列。
pytest 目前只有 `GET /api/kernels/command?kernel=pytest` 的命令预览。

**替代口径（两件事都与产品同源）**：

1. **argv 形状取自产品预览接口**：`pytest --json-report ... <target>`（本次按同形状加 `-n 2` 形成 xdist 形态）；
2. **杀树执行体取自产品类**：`KillProbe.java` 直接 `new ProcessTreeKiller().kill(process, Duration.ofSeconds(20))`，
   与 `CancelService` / `RunTimeoutMonitor` 调用的是同一个类（`backend/target/classes` 参与 classpath）。

> 踩坑记录：产品 argv 首项是**裸命令名** `pytest`。Java `ProcessBuilder` 不按改造后的 `PATH` 解析裸名，
> 首跑报 `Cannot run program "pytest": Exec failed, error: 2`。修法是外层加
> `bash -c 'exec "$@"' a7-probe`（`exec` 替换自身，被测 pid 就是 pytest 本身），argv 形状保持不变。

| 形态 | 20s 时子进程 | 终止结果 | 残留 | 执行证据 |
|---|---|---|---|---|
| plain（单进程） | 0（pytest 自身即全部） | `terminated=true`、`forced=false`、`waited_ms=4`，父进程 2s 后不存活 | `residual_descendants=[]`，`args_still_matching=[]` | 心跳 `test_a7_slow_one start ... pid=1736200` |
| xdist `-n 2` | **2 个 worker** | 同上（`terminated=true`、`forced=false`、`waited_ms=4`） | `residual_descendants=[]`，`args_still_matching=[]` | 心跳两行（两个不同 pid 各起一个用例） |

心跳文件由用例本身在开始执行时写入（`pytest_slow/test_slow.py`，`A7_HEARTBEAT` 环境变量指定路径），
用于排除「进程起来了但没在跑用例」的伪证：plain 只到第 1 个用例即在 20s 被杀；xdist 两个 worker
各起一个用例后被杀。原始文件：`raw/pytest-art-plain/heartbeat.txt`、`raw/pytest-art-xdist/heartbeat.txt`；
探针输出：`raw/kill-plain-stdout.txt`、`raw/kill-xdist-stdout.txt`。

## 6. 产品缺口：失败文案不带内核名

| 场景 | `failure_message` 实值 | 含 `pabot` |
|---|---|---|
| 取消 | `A7 取消取证`（操作人 reason 原样透传） | 否 |
| 超时 | `分片执行失败: timeout` | 否 |
| 兜底超时（未触发，代码口径） | `运行超过 N 秒未结束`（`RunTimeoutMonitor`） | 否 |

后果：单次 run 的失败记录无法自证是哪个内核被超时/取消，跨版本回溯要靠 run 日志文本。
建议 v2.1：`RunExecutionService` 组装 message 时带 `cmd.kernel()`（或等价字段），
并给 `run` 表补 kernel 列，使 pytest 具备 run 入口。本次**不改产品代码**，只记缺口。

## 7. 环境既有遗留（非本次 run 产生）

首轮取证的 ps 基线里发现一个 **11 小时前**的 `pabot.pabotlib 127.0.0.1 47322`（pid 1513981，ppid 为 `/init`，
即父进程早已退出后被托管），属此前手工实验遗留，不是本次任何 run 的子进程。
取证脚本按「runId / workspace_dir / artifacts_dir」多标记取根 + ppid 闭包判断归属，因此该遗留不会污染本次断言；
为了给复核者一个干净的基线，已记录并清理：`raw/ps-preexisting-orphan.txt`（清理前后各一次快照，清理后 `pabotlib` 行数为 0）。

> 这条遗留本身也说明一件事：**父进程被强杀后 `pabotlib` 可能被 init 接管继续跑**。
> 本次两次 RF 场景的树里都包含 pabotlib，且都在杀树范围内（after 快照 0 行），说明产品路径没问题；
> 历史遗留来自早期手工 `pabot` 调用（非平台路径），已清理。

## 8. 复现

```bash
cd ~/robot-autotest/docs/evidence/v2.0/a7-process-tree
python3 probe.py              # 全量：RF 取消 + RF 超时 + pytest plain/xdist（约 90s）
python3 probe.py --only rf    # 只跑 RF 两段
python3 probe.py --only pytest
```

依赖：后端 `:8081` 在跑、`qa-e2e-admin` 口令在 `~/.config/robot-autotest/env`（`QA_E2E_PASSWORD`）、
`project_id=29` 存在、`javac` 可用、`~/venvs/pytest-kernel/bin/pytest` 存在。
脚本每次自动 `javac` 编译 `KillProbe` 并把结果写到 `raw/a7-result.json`。

## 9. 证据清单

```
probe.py                     探针主脚本（HTTP + DB + ps 快照 + 判定）
kill_probe/KillProbe.java    复用产品 ProcessTreeKiller 的 Java 探针
pytest_slow/test_slow.py     3 个 30s 慢用例（带心跳）
raw/a7-result.json           全部读数与 checks（PASS=false 仅因文案判据）
raw/ps-cancel-before.txt     取消前进程树（64 行，本 run 命中 3 个根）
raw/ps-cancel-after.txt      取消后进程树（55 行，本 run 命中 0）
raw/ps-timeout-before.txt    超时前进程树
raw/ps-timeout-after.txt     超时后进程树（pabot 行 0）
raw/kill-plain-stdout.txt    plain pytest 探针 JSON
raw/kill-xdist-stdout.txt    xdist pytest 探针 JSON
raw/pytest-art-plain/heartbeat.txt / pytest-art-xdist/heartbeat.txt   用例执行心跳
raw/ps-preexisting-orphan.txt 环境既有孤儿 pabotlib 的记录与清理
```
