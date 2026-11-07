# B8 · 对比 / 趋势的真实浏览器回归

> 对应 `06-收口执行计划-20261106.md` §4 **B8**：以前只有组件单测，没有真实页面走查；
> 依赖 B4 的第 13 步（浏览器回归）。执行时间 2026-11-07 15:11–15:22，Chrome 159 @ `:8001`。
> 全部读数来自真实浏览器 + 真实 run，原始产物在 `raw/`。

## 1. 判据与结论

| 判据（06 计划原文） | 结论 | 依据 |
|---|---|---|
| 真浏览器走查对比 / 趋势 | **PASS** | 报告页 7 项断言全绿（`raw/browser-deep.log`）：路由、主区、无横滚、趋势图 111 个图元、对比开关可点、对比区 14 行、0 JS 异常 |
| 挂进第 13 步 | **PASS** | `scripts/browser-regression.mjs` 增 `reportDeepCheck`（`REGRESS_REPORT_RUN` 触发）；`scripts/gates.sh` 第 13 步自动挑「有 test_result 且 DUT 非空」的最新 finished run，无候选时退回路由巡检 |
| 数据能支撑对比 / 趋势 | **FAIL（产品缺陷 G-B8-1）** | 真实 `case_key` 带 runId 前缀，跨 run 永不匹配：对比 4 行全部 `SAME/NOT_RUN`、趋势只有 1 个点 |

## 2. 浏览器读数（真实 run `RUN-20261107151146-000000`，3 条用例，2 PASS + 1 SKIP）

```
PASS  report-route            path=/reports/RUN-20261107151146-000000
PASS  report-main             content=1240px
PASS  report-no-hscroll       scrollWidth=1440 viewport=1440
PASS  report-trend-rendered   {"hasTitle":true,"empty":false,"marks":111,"svgs":32}
PASS  report-compare-toggle   clicked
PASS  report-compare-rendered {"title":true,"noBaseline":false,"rows":14}
PASS  report-no-js-error      0 exception
BROWSER_REGRESSION checks=47 fails=0
```

截图：`raw/shot/report-RUN-20261107151146-000000.png`（整页，1440 宽）。

## 3. 产品缺陷 G-B8-1（P0，阻断 RPT-03 / RPT-04 的实际可用性）

**现象**：`test_result.case_key` 由「根套件名.用例名」构成，而根套件名是平台自己生成的
`run-<RUN_ID>`，因此**每次运行的用例键都不同**：

```
run-RUN-20261107151146-000000.B8 用例甲
run-RUN-20261107151146-000000.B8 用例乙
run-RUN-20261107151146-000000.B8 用例丙
```

**后果（均来自真实接口）**：

| 接口 | 读数 | 应为 |
|---|---|---|
| `GET /api/reports/<run3>/compare` | 4 行：上一轮的 `A5 循环与分支` 为 `NOT_RUN`；本轮 3 条全部 `history=null`，`change=SAME` | 本轮 3 条应与上一轮同用例配对；无对应历史时应标 `新增` 而不是 `SAME` |
| `GET /api/reports/<run3>/trend?caseKey=…` | `points` 只有 **1** 个（就是本轮），`series` 1 条 | 同用例的历史点应累积 |
| `GET /api/reports/<run3>/compare?baselineRunId=<run1>` | **400**「手动基线与本轮环境不一致：本轮 DUT=LOCAL-HOST…；基线 DUT=（空）」 | （次要）run1 的 `dut/link` 未落库，见 G-B8-2 |

**根因**：`RunExecutionService` 以 `--name run-<RUN_ID>` 创建根套件；`ReportRepositoryJdbc`
在入库时直接用「套件路径 + '.' + 用例名」做 `case_key`，没有剔除这个每次唯一的前缀。

**修复建议（未做，单独排期）**：入库时把根套件段（`run-<RUN_ID>`）从 `case_key` 中剔除，
或把 `--name` 改为与 run 无关的稳定名（如项目名）。改后需重算 RPT-03/04 的存量数据或明确
「只对修复后的新 run 生效」。

## 4. 次要缺陷 G-B8-2

`run1`（15:06 创建、全部通过）在 `run` 表里的 `dut/link` 为 **空**，而 `run3`（15:11）正常。
因此拿 run1 当手动基线会被 400 拒绝。成因未深挖（与报告入库时序有关），记为遗留：
**在「run 级 DUT/LINK 稳定落库」修好之前，跨轮手动基线不可依赖。**

## 5. 文件与复跑

| 文件 | 内容 |
|---|---|
| `make-runs.sh` / `make-runs2.sh` | 在项目 29 造真实 run1（全通过）与 run3（2 通过 + 1 跳过） |
| `raw/run1.txt` / `raw/run3.txt` | 两次真实 run 的 runId |
| `raw/api-compare-run3.json` | 自动基线对比（G-B8-1 的直接证据） |
| `raw/api-compare-run3-vs-run1.json` | 手动基线 400 原文（G-B8-2） |
| `raw/api-trend-run3.json` | 趋势只有 1 个点 |
| `raw/browser-deep.log` | 浏览器 47 项断言、0 失败 |
| `raw/shot/*.png` | 1440 宽整页截图 |

复跑：

```bash
cd ~/robot-autotest
bash docs/evidence/v2.0/b8-browser-compare-trend/make-runs2.sh
R3=$(cat docs/evidence/v2.0/b8-browser-compare-trend/raw/run3.txt)
REGRESS_REPORT_RUN="$R3" REGRESS_SHOT_DIR=/tmp/b8-shot node scripts/browser-regression.mjs
```
