# RPT-03 / RPT-04 残余收口：真实数据下的双列对比与趋势（2026-11-06）

WBS 第 40 行第 7 列挂着「真实环境对比未跑」，第 41 行挂着「真实本地 WSL 历史数据未接入」。
两者的共同点是：接口与单测都在，但**从没在真实库里、用真实 HTTP 读过一次**。
本批不写业务代码，只把这两条残余打穿——造一份能区分「选对了 / 选错了」的数据，
逐项读返回值，并把读不到的边界也钉住。

## 1. 缺口

单测用的是 mock 仓库：`CompareServiceTest` 断言的是「给定 history/current 怎么算 change」，
`TrendServiceTest` 断言的是「给定点集怎么算中位数与偏离」。真正容易错的都不在这一层：

- 自动基线的 SQL 条件（同项目 + 同 DUT/LINK + `finished` + 早于本轮 + 取最近一条）**从没对着真库跑过**；
- 手动基线的两条拒绝路径（跨环境 400、跨项目 400）只验过抛异常，没验过线上文案；
- 趋势的「环境分线」靠 `envKey` 分组，跨环境点必须**在同一条 points 里但断成两条 series**——
  mock 里想怎么写都行，真 SQL 的 `ORDER BY started_at DESC LIMIT N` 才是判据；
- 通过率按天/周聚合依赖 `DATE_FORMAT(COALESCE(started_at, created_at))`，`started_at` 为 NULL 的
  历史运行到底落在哪个桶，也只有真库能回答。

## 2. 做法

脚本 [`rpt0304b-e2e.sh`](rpt0304b-e2e.sh)：打真后端（`:8081`）+ 真 MySQL（`robot_dev`），
一次性项目 `EVD-RPT0304-<ts>` 里造数，只用真实 HTTP 读，最后把运行 / 用例结果 / 项目 / 令牌全删。
**94 项断言，0 失败**，完整输出见 [logs/00-run.log](logs/00-run.log)。

### 2.1 造数模型

同项目 6 次运行 + 另建项目 1 次运行，共 23 行 `test_result`：

| 运行 | started_at | DUT / LINK | 用例结果（用例=判定/耗时ms） | 用途 |
|---|---|---|---|---|
| `-H1` | 10-01 10:00 | DUT-A / L0 | A=PASS/1000 B=FAIL/2000 C=PASS/3000 F=PASS/1000 G=PASS/1000 | 手动基线（A 耗时 1000，与 H3 的 3000 可区分） |
| `-H2` | 10-02 10:00 | DUT-A / L0 | A=FAIL/2000 B=FAIL/2000 C=PASS/3000 **D=PASS/500** F/G=PASS/1000 | 反向证据：多一条 CaseD，若自动基线误选它，D 会出现在结果里 |
| `-H3` | 10-03 10:00 | DUT-A / L0 | A=PASS/3000 B=FAIL/2000 C=PASS/3000 F/G=PASS/1000 | 自动基线应选中的那一条 |
| `-CUR` | 10-04 10:00 | DUT-A / L0 | A=FAIL/5000 B=FAIL/2000 E=PASS/100 F=PASS/1500 G=PASS/1501 | 本轮：PASS→FAIL、双侧 FAIL、本轮缺 C、全新 E、1.5× 与 1.501× |
| `-OTH` | 10-03 12:00 | **DUT-B / L9** | A=PASS/9999 | 跨环境：不得成为自动/手动基线，趋势里要另起一条 series |
| `-NUL` | **NULL**（created_at 09-30） | DUT-A / L0 | A=PASS/1000 | `started_at` 为空：不能当自动基线，但可以出现在候选里 |
| `-X1` | 10-03 11:00 | DUT-A / L0 | A=PASS/1000 | 属于另一个项目，用来验跨项目 400 / 404 |

设计上每条数据都承担一个**排他性**断言，而不是「有返回值就算过」：
`H2` 的 CaseD、`H1` 的 1000ms、`OTH` 的 9999ms 都是「选错就露馅」的探针。

## 3. 读数

### 3.1 自动基线（`GET /reports/{runId}/compare?projectId=`）

| 断言 | 读数 |
|---|---|
| 选中 `-H3`（同环境、最近、早于本轮） | `true` |
| 对比行数 | `6`（基线 ∪ 本轮） |
| 键集合 | `Suite.CaseA,B,C,E,F,G` |
| 基线独有的 `CaseD` 不出现 | `false`（说明没误选 H2） |
| A 的 `change` / 基线判定 / 基线耗时 / 本轮耗时 | `PASS_TO_FAIL` / `PASS` / `3000`（H3，不是 H2 的 2000）/ `5000` |
| B 双侧 FAIL | `SAME` |
| C 本轮未执行 | `NOT_RUN`，`current = null`，`history.durationMs = 3000` |
| E 基线无记录、本轮通过 | `SAME`，`history = null` |
| `summary` | `newFail=1 bothFail=1 notRun=1 newPass=0` |

### 3.2 手动基线与候选

| 断言 | 读数 |
|---|---|
| `baselineRunId=-H1` 时选中 H1 | `true` |
| A 基线耗时改成 H1 的 `1000` | `1000`（同一份数据、只换基线，读数跟着变） |
| 候选列表顺序 | `H3,H2,H1,NUL`——倒序、排除本轮、**含 `started_at` 为 NULL 的 NUL** |
| 跨环境基线 `-OTH` | `400`，文案含「手动基线与本轮环境不一致」+「DUT-B」 |
| 跨项目基线 `-X1` | `400`，文案含「不存在或不属于当前项目」 |
| `started_at` 为 NULL 的运行**作本轮** | `200`，`baseline = null`，只对比自己那 1 条 |

### 3.3 趋势与性能基线（`GET /reports/{runId}/trend`）

| 断言 | 读数 |
|---|---|
| A 的点数（含跨环境 1 点） | `5`，升序首点 H1、末点本轮 |
| series 条数 / 主环境点数 / 次环境点数 | `2` / `4` / `1` |
| envKey | `DUT=DUT-A;LINK=L0` 与 `DUT=DUT-B;LINK=L9` |
| `baselineMs`（同环境历史 3000/2000/1000 的中位数） | `2000`——跨环境那次 9999 **没有**污染基线 |
| 本轮 5000ms vs 3×2000 | `deviated = true` |
| `deviated` 出现次数 / 被标的是本轮 | `1` / `true`（只标本轮，不回标历史点） |
| F 恰好 1.5×1000 | `deviated = false`（边界严格大于才偏离） |
| G 1.501×1000 | `deviated = true` |
| E 无任何同环境历史 | `baselineMs = null`、`deviated = false`、只有 1 点 |

### 3.4 通过率（`GET /reports/trend/pass-rate`）

| 断言 | 读数 |
|---|---|
| 按天桶数 / 顺序 | `5` / `2026-09-30,10-01,10-02,10-03,10-04` |
| 10-03 两天（H3 与 OTH）合并 | `pass=5 fail=1 total=6 passRate=0.8333` |
| 10-04 | `passRate=0.6`（3/5） |
| 按周桶数 / 键 | `1` / `2026-40`（跨月落在同一 ISO 周） |
| 周合计 | `pass=17 fail=6 total=23` |

> **顺带发现（不是缺陷，是接口契约）**：脚本最初用 `limit=60` 拿通过率，返回 400。
> 读 `TrendService` 确认上限是 `MAX_PASS_RATE_LIMIT = 52`（趋势接口是 50）。
> 已把请求改到 52，并**把边界本身钉成断言**：`limit=53 → 400` 且文案「limit 不能超过 52」，
> `granularity=month → 400`。这类上限以前只写在代码里，没有任何用例守着。

### 3.5 跨项目隔离

| 断言 | 读数 |
|---|---|
| 用别项目 `projectId` 读本轮 compare / trend / candidates | `404` / `404` / `404` |
| 别项目自身仍只有 1 次运行 | `1` |

## 4. 清理

脚本末尾删 `test_result` → `run` → `project` → `api_token`，并断言三个计数归零（`0 / 0 / 0`）。
重复执行不会留垃圾，也不会污染下一次读数。

## 5. 门禁读数（八步全绿）

本批**后端与前端业务代码零改动**，门禁是为了证明「加证据没有碰坏别的东西」。

| 项 | 结果 |
|---|---|
| `tools/subset_font.py` | PASS（1039 字 / 179950 B gzip，预算 819200 B） |
| 前端 `vitest run` | 28 文件 / **206 用例** |
| 前端 `vue-tsc --noEmit` | 0 错误 |
| 前端 `vite build` | `✓ built in 11.11s` |
| 后端 `mvn -o -B test` | **631 / 0 / 0** |
| `scripts/lint.sh` | PASS（八项子检查全 PASS，含体积与 WBS 自洽） |
| `check_release_docs.py` | PASS |
| `check_wbs_consistency.py` | PASS（96 包 / 未开始 0 / 进行中 0 / 待验 83 / 已完成 13） |

门禁日志见 [logs/01-subset_font.log](logs/01-subset_font.log) … [logs/08-wbs_consistency.log](logs/08-wbs_consistency.log)。

## 6. 残余

1. **前端交互没有浏览器回归**：详情页的基线下拉、双列并置、SVG 折线的实际渲染（断线、空态、
   点跳转）仍只有组件单测，没有在真浏览器里走查。这条与 UX-04b 的四态人工截图同属「人做」项。
2. **没有长历史压测**：趋势按 `LIMIT` 取点，但没验证过十万级 `test_result` 下 `CASE_POINTS` /
   `PASS_RATE_DAY` 的耗时；通过率上限 52 桶是接口层约束，不解决大表聚合。
3. **`started_at` 为 NULL 的运行在 UI 上时间未知**：接口已如实返回 `startedAt = null`，
   候选列表里怎么显示（「未知时间」还是置灰）尚未定，本批只保证接口语义。
4. **跨环境基线是拒绝而非降级**：目前直接 400。若实际使用中「换过机器还要对比」是常态，
   需要产品口径决定是否允许并显式标注环境差异——属 G2-1 同类口径问题，不由开发单方面定。

## 7. 改动清单

| 文件 | 改动 |
|---|---|
| `docs/evidence/v1.0/rpt0304b/rpt0304b-e2e.sh` | 新建：造数 + 94 项真实 HTTP 断言 + 清理 |
| `docs/evidence/v1.0/rpt0304b/logs/00-run.log` | 证据运行日志（94 PASS / 0 FAIL） |
| `docs/evidence/v1.0/rpt0304b/logs/01..08-*.log` | 八步门禁日志 |
| `docs/plan/WBS-STATUS.md` | 第 40 / 41 行补本批证据与残余收口说明 |
