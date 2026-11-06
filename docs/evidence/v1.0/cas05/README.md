# CAS-05 残余收口：能力索引反查走真实 MariaDB（2026-11-06）

## 1. 缺口

`CapabilityService.list` / `CaseController#capabilities` 与前端能力索引条都有实现，但**后端零测试**：

| 层 | 已有 | 缺什么 |
|---|---|---|
| 服务层 | `CapabilityService` 只做 `bindProject` + `require(CASE, READ)` 再转仓储 | 权限样板不是风险点，SQL 才是 |
| 仓储层 | `TestCaseRepositoryJdbc#capabilities` 的 `GROUP BY capability` | NULL / 空串 / 纯空白会不会各成一个能力桶；跨项目会不会叠加；下钻能否真取到用例 |
| 前端 | `CasesView` 渲染 `capability.key/count` 并支持点击筛选 | 索引非空在前端只能靠真实数据才有意义 |

## 2. 做法

### 新增 `CapabilityIndexIT`（3 条，真实 MariaDB，Flyway `clean()+migrate()`）

| 用例 | 验证点 |
|---|---|
| `反查非空并可按能力下钻` | 2 条 `roce-connectivity` + 1 条 `pfc-backpressure` → 索引返回 `[roce:2, pfc:1]`（计数倒序）；再用 `page(..., capability="roce-connectivity", ...)` 下钻，`total=2` 且拿到的正是那两条用例 —— 索引数字与下钻结果互相印证，不是只数了个 COUNT |
| `空能力不产生条目` | `NULL` / `''` / `'  '` 三种空值都不出现在索引里，不渲染空标签 |
| `跨项目隔离` | A 项目 2 条 `roce`、B 项目 1 条 `roce` + 1 条 `ib`：A 只见自己的 2 条，B 两个桶各 1 条，互不叠加；计数并列时按能力名字典序兜底（`ib` 在 `roce` 前），保证多次查询顺序稳定 |

### 顺带修掉的真实缺陷

能力索引 SQL 原来判空是 `capability IS NOT NULL AND capability <> ''`。**纯空白串**在库里两者都不满足判断条件，
会变成一个 key 为空白的桶，前端渲染出一枚点不动的空标签。

`TestCaseRepositoryJdbc#capabilities` 改为 `TRIM(capability) <> ''`，并把该场景写进 IT 固定下来。
未改动 `page` 侧的能力筛选（那是显式等值查询，传什么就查什么）。

## 3. 门禁读数

| 项 | 结果 |
|---|---|
| `tools/subset_font.py` | PASS |
| 前端 `vitest run` | 28 文件 / **202 用例** |
| 前端 `vue-tsc --noEmit` | 0 错误 |
| 前端 `vite build` | `built in 8.39s` |
| 后端 `mvn -o -B test` | **612 / 0 / 0**（本批未新增单测，计数不变） |
| `scripts/lint.sh` | PASS |
| `check_release_docs.py` | PASS（7 版本 / FAIL 0 / WARN 0） |
| `check_wbs_consistency.py` | PASS（96 包 / 待验 83 / 已完成 13） |
| `mvn -Pit -Dtest=CapabilityIndexIT` | **3 / 0 / 0**，`Time elapsed: 7.404 s` |

门禁日志见 `01-subset-font.log` … `08-wbs.log`；IT 独立运行，不进默认单测（`@Tag("it")` 默认 excluded）。

## 4. 残余

1. 能力索引只有仓储层真实数据验证，`CapabilityService` 的权限样板与 `GET /capabilities` 的 JSON 形状仍无 MockMvc/接口层断言。
2. 能力值不做归一：导入侧未强制小写/去空格，`RoCE` 与 `roce ` 是否落同一个桶取决于库的排序规则，未加约束。
3. 能力索引没有分页，用例数很多时一次全量返回；当前按项目规模可接受。
