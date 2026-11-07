# B2 · 1 万行日志页面性能（G5 第 4 项）

> 对应 `06-收口执行计划-20261106.md` §3 **B2**：`1 份可复跑脚本 + 首屏/滚动计时读数`。
> 取证日期 2026-11-07，真实 Chrome 159 @ :8001（CDP 9222）+ 后端 :8081 + 真实 run。
> **本轮结论：脚本与计时读数齐（判据 1/2 PASS），但「1 万行日志在页面上」这一条不成立 —— PARTIAL。**
> 原因不是页面卡，而是那 1 万行**根本没进日志频道**（缺口 G-B2-1），另发现首屏日志重复（G-B2-2）。

## 1. 判据与结论

| 判据（06 计划原文） | 结论 | 依据（原始读数，均在 `raw/`） |
|---|---|---|
| 1 份可复跑脚本 | **PASS** | `make-run.sh`（造数 + 取接口读数）+ `probe.mjs`（真实浏览器量首屏/滚动/注入压力），命令见 §4 |
| 首屏计时读数 | **PASS** | 在 `/runs` 找到本 run 行并点「查看日志」→ 首个 `.log-line` 渲染 **158 ms**（`b2-result.json` `click.firstLineMs`） |
| 滚动计时读数 | **PASS** | 真实面板 90 帧：avg **16.67 ms** / p95 **16.70** / max **16.80**（≈60 fps，无掉帧）（`scrollReal`） |
| 1 万行日志页面不卡 | **PARTIAL** | 真实 run 的 **10,000** 行 `Log To Console` 只落在产物文件里，`/log?limit=20000` 返回 **26 行 / 0 行含夹具标记**；面板实收 26 行（渲染成 52 行，见 G-B2-2）。同结构注入 10,000 span 后 90 帧 avg **16.67** / p95 **16.70** / max **16.80**（`stress.10000`）——但这只证明「DOM 装 1 万行不卡」，不证明「日志量真能进来」 |

## 2. 造数与产物（`raw/artifact-counts.txt`）

夹具（经产品 `PUT /api/editor/save` 写入项目 29）`suites/b2/log10k.robot`：1 个用例，FOR 10000 次 `Log To Console 第 N 行 B2 log line payload`，
metadata 六必填齐全（`CASE_ID=B2-LOG-10K` / `DUT=LOCAL-HOST` / `LINK=LOCAL` / `SW_BUILD=Ubuntu 24.04 / RF 7.x`）。
真实 run `RUN-20261107151924-000000` → `finished`，耗时 **10.49 s**（`started_at 15:19:25.029` → `finished_at 15:19:35.515`），`totalTests=1`。

| 文件 | 字节 | 行数 | 含标记行 |
|---|---|---|---|
| `run.log`（实时频道 / `/log` / `/log/download` 的唯一来源） | 1,651 | 26 | **0** |
| `shard-0/stdout.log` | 1,087 | 22 | 0 |
| `shard-0/pabot_results/0/robot_stdout.out` | 329,580 | 10,009 | **10,000** |
| `shard-0/output.xml` | 3,420,164 | 90,034 | **10,000** |

接口（`raw/log-20000.json`）：`/log?limit=20000` → HTTP 200、**26 行**、4,084 B、25.2 ms；
`/log/download` → HTTP 200、1,609 B / 27 行、35 ms。两处都**只有 pabot 编排层日志**。

## 3. 缺口（如实记，未改产品代码）

### G-B2-1（P1）`Log To Console` 不进实时日志频道

- 机制：`PabotOrchestrator` 只把 **pabot 自身进程的 stdout/stderr** 逐行 `publish` 进 `RunLogService`
  （`PabotOrchestrator.java:141` `emit(...)`），而 robot 的 `Log To Console` 走 pabot 的
  per-process 文件 `pabot_results/<n>/robot_stdout.out`，不经过 pabot 的 stdout。
- 后果：用户看到「实时日志」只有 26 行 pabot 摘要，**用例自己打的 1 万行进不去**；
  `/log/download`（全量日志文件）同样只给 `run.log`，同样不含。
- 因此 G5 第 4 项若按「页面打开一次 1 万行日志」判定，**当前产品做不到**——
  本证据只能证明「假设 1 万行进了 DOM，滚动仍 60 fps」。

### G-B2-2（P1）首屏日志重复一倍

- 读数：面板 `.log-line` = **52**，而接口只返回 **26** 行，恰好 2×。
- 机制：`RunsView.selectRun` 先 `loadRecentLog()` 用 REST 拉最近 200 行（`RunsView.vue:380`），
  紧接着 `connect()` 订阅 WS；`RunLogService.subscribe` 连上又补发最近 200 行（`RunLogService.java:95`），
  前端 `logLines = [...logLines, ...payload.lines]`（`:406`）按 `seq-tsGen` 为 key，两批 `seq` 相同 → 重复渲染。
- 影响：日志面板每行显示两次；最多可重复 200 行。**属展示缺陷，不影响执行。**

## 4. 复现

```bash
cd ~/robot-autotest
bash docs/evidence/v2.0/b2-log-10k/make-run.sh        # 造数 + 落 /log 读数（约 20 s）
node docs/evidence/v2.0/b2-log-10k/probe.mjs          # 真实浏览器量测（约 40 s）
```

前置：后端 :8081、前端 :8001、Chrome CDP :9222、`qa-e2e-admin` 口令在 `~/.config/robot-autotest/env`。
`probe.mjs` 默认读 `raw/run.txt` 里的 runId，也可 `B2_RUN=<runId>` 覆盖。

## 5. 文件表

| 文件 | 内容 |
|---|---|
| `make-run.sh` | 建夹具 → 真实 run → 轮询至终态 → 取 `/log?limit=20000` 原始 JSON |
| `probe.mjs` | CDP 登录 → `/runs` 点「查看日志」→ 首行计时 + 面板/滚动读数 + 接口/下载 + 2k/1w 注入压力 → 截图 |
| `raw/run.txt` | runId |
| `raw/log-20000.json` | `/log` 原始响应（26 行） |
| `raw/artifact-counts.txt` | 四个产物的字节/行数/标记行 + 接口分布 |
| `raw/b2-result.json` | 浏览器侧全部读数（首屏 158 ms、60 fps、注入 2k/1w） |
| `raw/b2-report-RUN-20261107151924-000000.png` | 日志面板截图（52 行重复可见） |

## 6. 遗留

- G-B2-1 / G-B2-2 均为 P1 产品缺口，**本轮不改代码**（收口计划只补证据）。修复方向：
  ① 把 `pabot_results/*/robot_stdout.out` 纳入 `emit`（或给 pabot 加 `--verbose` 后统一收集）；
  ② 前端去重（按 `seq` 已存在即可 `Map` 合并）或 WS 订阅时带 `sinceSeq` 水位，与 `loadRecentLog` 二选一。
- G5 第 4 项的最终判定权仍在现场日；本证据只补「当场读数」，不构成门禁通过。
