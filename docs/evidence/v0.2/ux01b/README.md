# UX-01b 界面基线修复（美术 S0）— 证据与走查

> 运行日期：2026-11-04 ｜ 环境：WSL2 Ubuntu-24.04（后端 `java -jar` :8081，前端 `vite` :5273）
> 台账：`docs/release/v0.2-ledger/DEV-PLAN.md` §10.10、`TEST-PLAN.md` §13.8
> 状态：**待验**（`待验 → 已完成` 只能由人改）

## 一、改了什么

| 缺陷 | 处理 | 文件 |
|---|---|---|
| **D1** `var(--muted)` 21 处引用不存在的 token（属性失效 → 辅助文字退回继承色，与正文同色） | 全部改 `var(--text-muted)` | `styles/main.css`(13)、`ReportDetailView.vue`(2)、`ReportsView.vue`(1)、`report/SuiteTree.vue`(1)、`report/TrendChart.vue`(4) |
| **D2** 六类元素只有默认态，无 hover / 无焦点环 | 新增 `--surface-hover` / `--surface-selected` / `--focus-ring` / `--accent-hover` / `--accent-active`；`*:focus-visible` 全局 2px 焦点环；`.btn` / `.btn-ghost` / `.link-btn` / `.input` / `select` / `.tab` / `.app-nav-item` / `.table tbody tr` 三态 + `transition` | `styles/theme.css`、`styles/main.css` |
| **D3** 危险操作与普通按钮同款 | `.btn.danger`（实底红）+ `.btn-ghost.danger`（描边红字，覆盖在 `.btn.danger` 之后按顺序决出） | `styles/main.css` |
| **D13** 1920 屏内容拉满 1900px | `.app-body` `max-width:1440px` + 居中；10 列表格用 `.table-scroll` 容器级横滚（报告详情 3 张表 / 用例列表 / 报告列表） | `styles/main.css` + 3 个 vue 模板块 |
| **D15** 校验器把 `[data-accent]` 变体块 `update` 进同一份变量表 → 变体值覆盖默认值，默认态永远没被测（假绿） | `parse_theme` 改为按 **(主题 × 强调色)** 分组；`accent` 机制就位（`data-accent='blue'`，不暴露切换入口）；新增分组数与判定数下限自检 | `tools/check_contrast.py`、`styles/theme.css` |
| 新增守门 | `check_tokens.py`（悬空 var + px 白名单，带 `--self-test` / `--write-px-allow`）、`check_states.py`（三态 + transition，带 `--self-test`） | `tools/`、`tools/px-allow.txt` |

**边界遵守**：只动样式与守门脚本；模板改动仅给 5 张宽表套 `.table-scroll` 容器，未改任何业务逻辑。

## 二、守门与构建结果

命令与全量输出见 `ux01b-checks.log`、`ux01b-frontend.log`、`backend-mvn-test.log`。

| 项 | 结果 |
|---|---|
| `python3 tools/check_contrast.py` | **PASS**，4 组（dark×default / dark×blue / light×default / light×blue）共 **80 项**全部达标 |
| 同上（注入 `[data-accent='teal']` 块，TC-S0-06） | 默认 `dark/blue` 组数值不变（`--accent` 未被覆盖），分组数随注入增加 → **D15 修复生效** |
| `python3 tools/check_states.py` | **PASS**，8 类元素三态齐备 |
| `check_states.py --self-test` | 删掉 `.btn:hover` 后按预期 **FAIL** |
| `python3 tools/check_tokens.py` | **PASS**，63 个 token 定义 / 104 处引用，**0 悬空**；px 字面量全部在白名单内 |
| `check_tokens.py --self-test` | 假 `var(--muted)` 与未登记 `37px` 均可识别 |
| `python3 tools/check_i18n.py` | PASS |
| `npm run build` / `npm run lint` / `vitest run` | 全部 **exit 0**，vitest **16/16** |
| `mvn -B package` | **BUILD SUCCESS**，`Tests run: 459, Failures: 0, Errors: 0` |

## 三、界面走查（1920×1080，真实登录后截图）

| 截图 | 看什么 |
|---|---|
| `01-login-dark.png` | 深色登录页：输入框、主按钮、辅助说明层级 |
| `02-overview-dark.png` / `07-overview-light.png` | 总览：卡片层次、辅助文字弱化 |
| `03-cases-dark.png` / `08-cases-light.png` | 226 条用例表格、分页、红色"删除" |
| `04-runs-dark.png` | **真实执行记录**：`RUN-20261104155814-952426` 已完成 `2/2` |
| `05-reports-dark.png` / `09-reports-light.png` | 报告列表 |
| `06-report-detail-dark.png` / `10-report-detail-light.png` | 报告详情 **10 项 metadata 表**（宽表容器横滚） |
| `11-plans-dark.png` / `13-plans-light.png` | 计划页：`删除` 为红色危险态（D3） |
| `12-focus-dark.png` | Tab 后焦点环（实测 `outline: solid 2px`，作用于 `.app-nav-item`） |

实测读数（报告详情页注入脚本取值）：

```
theme=light  scrollWidth=1905  clientWidth=1905  .app-body clientWidth=1440  --text-muted=#5b6b7c
```

→ 页面无横向滚动（`scrollWidth == clientWidth`），内容区 1440 居中，辅助色已生效。

## 四、顺带验证：执行域本机端到端

为了让界面有真实数据，同时验证 v0.4 执行域，触发了一条**小样真跑**（不用 DUT）：

1. 修复 `PabotVersionProbeImpl`：原实现执行 `pabot --version` 取末段 →
   拿到 `linux)` 与期望前缀 `5.2` 比较，**必然失败**，等于所有运行在预检阶段被永久拒绝。
   改为用同 venv 解释器读包元数据 `importlib.metadata.version('robotframework-pabot')`（实测 `5.2.2`）。
2. 后端以 `EXECUTION_WORKSPACE_ROOT=fixtures/bench/4suite-2cases`、`EXECUTION_VENV_BIN=~/rf-stack/.venv/bin`
   起本地环境（默认值指向 149，WSL 上必须覆盖）。
3. `POST /api/plans/1/trigger-now` → `RUNNING` → `FINISHED`，`2 passed / 0 failed`，
   产物落 `var/artifacts/RUN-20261104155814-952426/shard-0/{output.xml,log.html,report.html}`。
4. `POST /api/reports/{runId}/parse` → 报告入库 `pass=2 / fail=0`。
   `metadataMissing` 列出 10 项（该 fixture 未写 metadata，**符合预期**，不是回归）。
5. 演示计划已 `paused`，避免以后开调度器时每分钟空跑。

## 五、未覆盖 / 留给人工

- 悬停态没有截图证据（只能靠 `check_states.py` 静态守门 + 焦点环实测）；如需视觉留证请人工移鼠标连拍。
- TC-S0-02 的"取色 = `--text-muted`"只在报告详情页取了 `#5b6b7c`，表格表头/图表轴标签未逐处取色。
- 真实 DUT 实网用例仍未跑（`PERF-IPERF-200G-001` / `RDMA-PRE-001` 属 149 现场项）。
