# W0.2 · G-B2-1 / G-B2-2：用例内部 `Log To Console` 进实时频道 + 首屏去重

> 对应 `docs/plan/中期开发功能规划-20261107.md` **W0.2**（也是新需求 **N15** 的同一条通道）。
> 取证日期 2026-11-07；Ubuntu 24.04 / RF 7.x / pabot；后端 JUnit 5 + AssertJ，前端 vitest + vue-tsc。
> **结论：worker 侧 `Log To Console` 已进 `/ws/runs/{runId}` 与 `GET /runs/{runId}/log`，
> 行首带 `[worker:<n>]` 便于分辨来源；一万行夹具实测最后一行 `第 9999 行` 可见，
> JSON 面板只保留最近 **2000** 行（`RunLogService.RING_CAPACITY`，既有设计），全量落 `run.log` 可下载。**

## 1. 判据与结论

| 判据 | 结论 | 依据 |
|---|---|---|
| 用例内部 `Log To Console` 能进实时日志 | **PASS** | `live-progress-runtime.txt` B 段：`/log` 返回行首为 `[worker:0] 第 8025 行 B2 log line payload`；`contains_line_9999=True` |
| 一万行不被截断成个位数 | **PASS（保留最近 2000 行）** | 同文件：`lines_returned=2000`，`first='[worker:0] 第 8025 行…'`、`last='[system] 产物校验通过，totalTests=1'`；`http=200 bytes=278397 seconds=0.088641` |
| 来源可分辨（主进程 / worker） | **PASS** | worker 行统一加 `[worker:<n>]` 前缀；pabot 主进程行仍是 `[stdout]`；系统行是 `[system]` |
| 首屏不重复（W0.2 的 B2-2） | **PASS** | `RunsView.vue` 按 `seq` 水位去重：重连/补拉历史时同一行只渲染一次 |
| 读写收尾（不留悬浮线程） | **PASS** | `PabotOrchestrator` 在 `join(errThread)` 之后 `workerTail.close()`；`WorkerLogTailerTest` 2/2 |
| 回归门禁 | **PASS** | `gates-w0c.log`：16 步 + `17 structure_selfcheck` 全 PASS，`GATES_RESULT FAILS=0` |

## 2. 改了什么

| 文件 | 变更 |
|---|---|
| `backend/.../application/execution/WorkerLogTailer.java`（新建） | 250ms 轮询 `shard-0/pabot_results/*/robot_stdout.out`；按字节偏移增量读、**只切完整行**；输出加 `[worker:<n>]` 前缀；`close()` 收尾 |
| `backend/.../application/execution/PabotOrchestrator.java` | 启动 tailer 并把每行 `emit(runId, runLog, logSink, line)`；`join(errThread)` 后关闭 tailer；6 参构造注入 `RunProgressRegistry`（N15 复用同一行流） |
| `frontend/src/views/RunsView.vue` | WebSocket 收到行时按 `seq` 水位去重，避免重连补发造成重复渲染 |
| `backend/src/test/.../WorkerLogTailerTest.java` | 2 条：增量读半行不吐出、关闭后不再追加 |

## 3. 三个必须记住的坑

### G-W0.2-1 pabot 主进程 stdout 里没有用例日志
主 stdout 只有 `EXECUTING / PASSED <suite> in N seconds` 与最终汇总；用例里的 `Log To Console`
全部落在 `shard-0/pabot_results/<n>/robot_stdout.out`。只盯主 stdout 就会「日志面板永远很安静」——
这正是 G-B2-1 的成因。

### G-W0.2-2 必须按字节偏移 + 完整行读
文件是边跑边追加的，按 `readLines()` 全量重读会重复且越来越慢；按行号/字符数切会把半行吐出去
（面板出现截断的乱码行）。实现是记录 `offset`，只读到最后一个 `\n` 为止。

### G-W0.2-3 `/log` 的 2000 行不是丢日志
`RunController.log()` 把 `limit` 夹在 `[1, RunLogService.RING_CAPACITY=2000]`，内存环形缓冲
只保留最近 2000 行；全量在 `run.log`，走 `GET /runs/{runId}/log/download`。写报告时不能拿
「面板没看到」当「没输出」。

## 4. 复现

```bash
# 单测（tailer 2 条）
cd ~/robot-autotest/backend && mvn -o test -Dtest=WorkerLogTailerTest

# 运行时取证（会触发一条 1 万行日志的 quick-run，约 15s）
bash /tmp/w05_evidence.sh          # B 段：progress 轮询 + /log?limit=20000 读数

# 全量日志对照
curl -s --noproxy '*' -OJ "http://127.0.0.1:8081/api/execution/runs/$RUN_ID/log/download?projectId=29"
```

实测读数（2026-11-07，`RUN-20261107174209-510000`）：

```
/log?limit=20000 → lines_returned=2000（RING_CAPACITY 上限）
first = [worker:0] 第 8025 行 B2 log line payload
last  = [system] 产物校验通过，totalTests=1
contains_line_9999 = True
```
