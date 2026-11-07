# W0.8 · 新需求 N12–N15（菜单 IA / 系统设置 / 用例展开 / 执行进度动态可视化）

> 对应 `docs/plan/中期开发功能规划-20261107.md` **W0.8** 与 §2 新增需求 **N12 / N13 / N14 / N15**。
> 取证日期 2026-11-07；后端 JUnit 5 + AssertJ，前端 vitest 42 文件 / 277 用例，门禁 16 步 + 自检。
> **结论：四条需求全部落地；进度接口 `GET /api/execution/runs/{runId}/progress` 实测
> `completed ≤ total`、`lastTest` 为真实用例名、阶段按 `queued→preparing→running→done` 推进；
> 用例数未知的长任务走不确定进度条。**

## 1. 判据与结论

| 需求 | 判据 | 结论 | 依据 |
|---|---|---|---|
| **N12** 一级/二级菜单重设计 | 一级 ≤ 6 且二级全部显式声明 | **PASS** | `raw/navigation.ts.txt`；`grep -cE "^\s{2}\{"` = 6（工作台 / 资产 / 用例 / 执行 / 报告 / 系统设置） |
| 能力过滤下沉 | 二级才带 `capability`，一级只做分组 | **PASS** | `runs` 一级带 `execution:read`；`settings-audit` 带 `adminOnly + audit:read` |
| **N13** 系统设置独立一级 | 平台设置 / 用户 / 审计 / 关于 / 更新记录都在同一个一级下 | **PASS** | `settings → settings-platform(/settings) / settings-users(/admin) / settings-audit(/admin/audit) / settings-about(/about) / settings-changelog(/changelog)`；原「管理」组已删除 |
| **N14** 用例可展开看细节 | 列表首列展开 → 步骤表（序号 / 动作 / 预期） | **PASS** | `CasesView.vue` 首列 `type:'expand'` → `CaseDetailPanel`；步骤用 `DataTable.vue` 壳渲染 |
| **N15** 执行过程动态可见 | 运行中 3s 轮询，5s 内进度前进 | **PASS** | `live-progress-runtime.txt` A 段：`t+1s` 1/1 → `t+3s` 2/2，`last='A5 循环与分支'` |
| 阶段可读 | `queued / preparing / running / done` | **PASS** | A 段 `phase=preparing → running → done`；B 段同样 |
| 用例数未知时条不确定 | `total=0` 时不显示百分比、走扫动条 | **PASS** | B 段 `t+2s…t+8s total=0 completed=0 elapsed=1…8`；C 段 180s 长任务 27s 内一直 `total=0`，`elapsed` 递增 |
| 计数口径（**曾踩坑**） | `completed ≤ total`，`lastTest` 不是套件名 | **PASS** | A 段 `total=2 completed=2`（修复前同一夹具报 `completed=6 passed=6`、`last='run-RUN-…'`） |
| 回归门禁 | 16 步 + 自检全 PASS | **PASS** | `raw/gates-w0c.log`：`GATES_RESULT FAILS=0` |
| 前端单测 | 全绿 | **PASS** | `raw/vitest.txt`：`Test Files 42 passed (42)` / `Tests 277 passed (277)` |

## 2. 改了什么

| 文件 | 变更 |
|---|---|
| `frontend/src/config/navigation.ts` | 一级重排为 6 域；二级全部显式声明；删除 `admin` 组；`alsoMatch` 保留旧路由别名 |
| `frontend/src/config/navigation.spec.ts` | 按新 IA 全量重写断言 |
| `frontend/src/locales/*` | zh-CN / en-US 补 `menu.casesList / casesSets / runsNodes / settingsPlatform` 等 |
| `frontend/src/views/AdminView.vue` | 标题改 `t('menu.adminUsers')`（不再自称「管理」） |
| `frontend/src/components/CaseDetailPanel.vue`（新建） | 展开区：基本信息 + 步骤表（`DataTable.vue` 壳，`:max-height="null"`） |
| `frontend/src/views/CasesView.vue` | `columns` 首位插 `{ type: 'expand', renderExpand: … CaseDetailPanel }` |
| `backend/.../application/execution/RunProgressRegistry.java`（新建） | 从控制台行解析 PASS/FAIL/SKIP 与 RF 汇总行；**排除套件汇总行**；`pendingTest` 兜底取名；`synchronized` 保护分片并发 |
| `backend/.../api/execution/RunController.java` | 新端点 `GET /runs/{runId}/progress?projectId=`；合并 registry 与 run 记录（`total=0` 表示未知）；状态映射到 `phase` |
| `frontend/src/api/execution.ts` | `RunProgress` 类型 + `executionApi.progress(projectId, runId)` |
| `frontend/src/views/RunsView.vue` | 3s 轮询（`done`/终态即停）、进度条（未知时扫动、失败红条）、阶段文案、最近完成用例 |
| `backend/src/test/.../RunProgressRegistryTest.java` | 8 条：套件行排除、空名兜底、名称与日志同行、汇总总不低于已完成、阶段与耗时 |

## 3. 四个必须记住的坑

### G-W0.8-1 套件汇总行长得和用例行一样
RF/pabot 控制台除了用例结果行，还会打印子套件 `run-RUN-xxx.Demo :: 说明 … | PASS |`
和根套件 `run-RUN-xxx | PASS |`。按 `| PASS |` 无脑计数会把 2 个用例算成 6 个
（每个 worker 各 3 行）。**判据**：名称列以 `run-<runId>` 开头即套件行，不计入、不覆盖 `lastTest`。
`RunProgressRegistry.isSuiteRow()`。

### G-W0.8-2 用例名可能和日志挤在同一行，结果后缀单独成行
用例里 `Log To Console` 会紧跟在已打印的用例名后面，`| PASS |` 被挤到后面的独立行：

```
B2 输出一万行                    第 0 行 B2 log line payload     ← 名称 + 对齐填充 + 首条日志
第 1 行 B2 log line payload
| PASS |                                                          ← 名称为空
```

名称为空时用上一行「对齐填充（2+ 空格）或 ` :: ` 之前的名称段」兜底（`pendingTest`），
普通日志行没有填充，不会污染。修复前 B 段 `last=None`，修复后 `last='B2 输出一万行'`。

### G-W0.8-3 分片子套件汇总先到，`total` 会小于已完成数
pabot 每个 worker 各自汇总（`1 test, 1 passed`），先到的汇总会把 `total` 压到 1，
而两个分片的用例已经完成 2 个。因此 `total = max(total, 汇总总数, completed)`。

### G-W0.8-4 前端两条设计规则会挡路（门禁 06-lint / 15-css_vars）
- 设计规则**禁止原生 `<table>`**（`tools/check_tables.py`，仅 26 个历史文件白名单）→ 用例步骤表必须用
  `DataTable.vue` 壳，不能用 `<table>` 手写。
- **CSS 变量必须来自 `theme.css`**，且**不许带 hex fallback** → `--n-color-modal` / `--text-secondary` /
  `--border-color` 都不合规，改用 `--surface-2` / `--text-muted` / `--border`。
  首轮门禁 `FAILS=2`，改造后 `FAILS=0`；不要靠加白名单或放宽校验过关。

## 4. 复现

```bash
# 后端单测（8 条）
cd ~/robot-autotest/backend && mvn -o test -Dtest=RunProgressRegistryTest

# 前端单测 + 门禁
cd ~/robot-autotest/frontend && npx vitest run
cd ~/robot-autotest && bash scripts/gates.sh          # 期望 GATES_RESULT FAILS=0

# 运行时取证（A 快套件 / B 一万行 / C 180s 长任务，约 60s）
bash /tmp/w05_evidence.sh
```

实测读数（2026-11-07）：

```
A  RUN-20261107174205-510000   t+1s total=1 completed=1 last='NODX03 示例通过'
                               t+3s total=2 completed=2 last='A5 循环与分支'
                               t+4s phase=done  elapsed=2
B  RUN-20261107174209-510000   t+2s…t+8s total=0（不确定）→ t+11s 1/1 last='B2 输出一万行'
C  RUN-20261107174224-510000   27s 内 total=0 completed=0，elapsed 1→26（扫动条），cancel → CANCELED
门禁 FAILS=0；vitest 42 文件 / 277 用例全绿
```
