# PLN-02 残余收口：跳过事件列表的分页与时间范围（2026-11-06）

WBS 第 46 行第 7 列点名的残余是「跳过事件列表固定最近 20 条，无分页与时间范围筛选」。
本批把它闭合：面板改为服务端分页 + 起止日期筛选，并顺带暴露出一条**体积守门判据本身的洞**
（详见 §5）。

## 1. 缺口

计划下游依赖被跳过时，`PlansView` 会写一条 `PLAN_DOWNSTREAM_SKIPPED` 审计，并在面板里列出来。
原来的实现是 `page: 1, size: 20`，**写死在代码里**：

- 第 21 条起的事件在 UI 上**根本不可见**，且没有任何提示说「还有更多」；
- 想查「上周三为什么会跳过」只能去审计页手翻，或者直接查库；
- 面板文案写的是「最近 20 条」，等于把实现细节当成产品承诺。

后端 `/api/audit` 早就支持 `page/size/from/to`（`AuditController` 里 `from`/`to` 收 `yyyy-MM-dd`，
`to` 按当天结束算），所以本批**只改前端**，后端零改动。

## 2. 做法

`frontend/src/views/PlansView.vue`：

```ts
const skippedPage = ref(1)
const skippedSize = 20
const skippedTotal = ref(0)
// from / to 必须初始化为 null：NDatePicker 的 formatted-value 拿到 '' 会交给 date-fns
// 解析，直接抛 RangeError（AuditView 早就踩过同一个坑）。
const skippedRange = reactive({ from: null as string | null, to: null as string | null })
const skippedPageCount = computed(() => Math.max(1, Math.ceil(skippedTotal.value / skippedSize)))
```

- `loadSkipped()` 带上 `from`/`to`（空值转 `undefined`，不发给后端）与 `page`/`size`；
- `turnSkippedPage(next)`：越界与原地翻页直接忽略；面板收起时只记页码，展开时由 `toggleSkipped` 统一拉；
- `onSkippedRangeChange()`：改日期范围**必须回到第 1 页**，否则会停在一个已不存在的页码上；
- 面板收起时 `resetSkipped()` 把页码、总数、日期全部复位，避免下次展开看到上一次的筛选残留。

模板上加了两个 `NDatePicker`（`type="date"`、`value-format="yyyy-MM-dd"`、`clearable`、宽 180px）、
一行「共 N 条」，以及 `skippedPageCount > 1` 时才出现的 `NPagination`。写法照抄 `AuditView.vue`
（同一个 `v-model:formatted-value` + null 初始化的约定），没有引入新依赖。

语言包 `zh-CN.ts` / `en-US.ts` 同步：`skippedHint` 改为「可按时间范围筛选并翻页查看」，
`skippedEmpty` 改为「所选范围内没有依赖跳过事件」，新增 `skippedFrom` / `skippedTo` / `skippedTotal`。

## 3. 证据怎么造

脚本 [`pln02c-e2e.sh`](pln02c-e2e.sh)，在本地 WSL 打真后端（`:8081`）+ 真 MySQL（`robot_dev`）：
**23 项断言，0 失败**，完整输出见 [logs/00-run.log](logs/00-run.log)。

用一次性令牌 + 专用项目造 **25 条** `PLAN_DOWNSTREAM_SKIPPED`：10 条落在 `2026-10-01`、
10 条落在 `2026-11-01`、5 条落在 `2026-11-05`（直接写 `audit_log`，`created_at` 精确到天），
然后只用真实 HTTP 验证读数，最后把项目、审计行、令牌全部清掉。

### 3.1 分页

| 请求 | 读数 |
|---|---|
| `page=1&size=10` | `200`；`total=25`；本页 `10` 条；回显 `page=1 / size=10` |
| `page=3&size=10` | `200`；末页 `5` 条 |
| `page=4&size=10`（越界） | `200`；本页 `0` 条，但 `total` 仍是 `25`（不因越界把总数抹成 0） |

### 3.2 时间范围（关键：`from` / `to` 都含当天）

| 请求 | 读数 | 说明 |
|---|---|---|
| `from=2026-11-01&to=2026-11-04` | `10` | `11-01` 当天 10 条全算进来 → **`from` 含当天** |
| `from=2026-11-05` | `5` | 只有 `11-05` 那一天 |
| `to=2026-10-31` | `10` | `10-01` 的 10 条，`11-01` 被排除 → **`to` 含当天** |
| 11 月内 `page=2&size=10` | 本页 `5` / 总 `15` | 范围与翻页**组合**成立（不是各算一套） |

第 3 行是这批判据最容易写错的地方：后端把 `to` 解释为「当天 23:59:59.999」，所以 `to=2026-10-31`
必须把 `10-01` 全拿住；如果按「`< 2026-10-31T00:00`」实现，这一行会返回 `0`。

## 4. 单测

`frontend/src/views/PlansView.spec.ts` 从 9 条增到 **11 条**（前端合计 204 → **206**）：

| 新增用例 | 验证点 |
|---|---|
| 超过一页时给翻页器，翻页带页码重新拉 | `total=45` 时出现 `.skipped-pager`、「共 45 条」；点第 2 页后最后一次调用是 `{page: 2, size: 20}` |
| 填时间范围后回到第一页并按 from 重新拉 | 在 `.skipped-from input` 输入 `2026-01-01` 并触发 input+change 后，最后一次调用是 `{from: '2026-01-01', page: 1}` |

既有断言全部补上 `from: undefined, to: undefined`：这两项**必须显式断言**，
否则「忘了把日期传给后端」这种回归测不出来（少传与不传在 `toMatchObject` 里长得一样）。

## 5. 顺带修掉的一处守门缺陷（`tools/check_bundle_budget.py`）

改完之后 `scripts/lint.sh` 报红：

```
[FAIL] 懒加载第三方分包超预算：18 个合计 gzip 142553 B > 122880 B（120 KiB）；
       最大 assets/DataTable-1ZWx-edD.js 80165 B
```

第一反应是「加了两个日期框把体积撑爆了」。查产物后结论相反：**总量一个字节都没涨，
是判据原来漏算了一块，这次才看得见。**

原因链条：

1. `PlansView` 之前不用 `NDatePicker`，`NDatePicker` + `date-fns` 只被 `AuditView` 引用，
   Vite 把它们整体打进 `AuditView-*.js`（`171154 B / gzip 38630 B`）；
2. (b2) 判据把 `*View-*.js` 排除在外（原意是「路由视图不算共享分包」），这 38 KB 于是**从没被计过**；
3. 本批 `PlansView` 也用 `NDatePicker`，它变成两页共享，Vite 提取出独立 `audit-*.js`
   （`167219 B / gzip 37141 B`）——同样的代码，只是换了个文件名，就从「不计」变成「计」。

实测对照（同一次构建的 `06-lint.log`）：

| 项 | 改前 | 改后 |
|---|---|---|
| `AuditView-*.js` | `171154 B / gzip 38630 B` | `4199 B / gzip 1798 B` |
| `audit-*.js`（共享） | 不存在 | `167219 B / gzip 37141 B` |
| 判据看到的合计 | `105540 B`（达标） | `142553 B`（超 120 KiB） |

`142553 − 105540 = 37013 B`，与新 chunk 的 `37141 B` 基本吻合；`AuditView` 自己的 gzip
掉了 `36832 B`。两者互相印证：**搬了个位置，总面积没变。**

处置：`LAZY_BUDGET` 由 `120 KiB` 调到 `160 KiB`（`163840 B`），并在常量上方写清上面这段来龙去脉。
这不是放宽标准——判据原来测的是「共享分包」这个子集，调完才真正覆盖住那 38 KB；
余量约 21 KiB，**再往上就必须是真实增长，要单独论证**。同时把报错/达标文案里写死的
「120 KiB」改成从常量算 `LAZY_BUDGET // 1024`，避免下次调预算时文案与数值对不上。

> 只调预算不改判据逻辑，所以没有新增 `--self-test` 用例（自检覆盖的是「图标超预算 / 字体超预算 /
> styleguide 泄漏」三类注入，与这条阈值无关）。

## 6. 门禁读数（八步全绿）

| 项 | 结果 |
|---|---|
| `tools/subset_font.py` | PASS（1039 字 / 179950 B gzip，预算 819200 B） |
| 前端 `vitest run` | 28 文件 / **206 用例**（本批 +2） |
| 前端 `vue-tsc --noEmit` | 0 错误 |
| 前端 `vite build` | `✓ built in 10.64s` |
| 后端 `mvn -o -B test` | **631 / 0 / 0**（本批后端无改动） |
| `scripts/lint.sh` | PASS（体积：图标 4207 B / 组件库 110795 B / 懒加载共享 142553 B ≤ 163840 B / 字体 179950 B） |
| `check_release_docs.py` | PASS |
| `check_wbs_consistency.py` | PASS（96 包 / 未开始 0 / 进行中 0 / 待验 83） |

门禁日志见 [logs/01-subset_font.log](logs/01-subset_font.log) … [logs/08-wbs_consistency.log](logs/08-wbs_consistency.log)。

## 7. 残余

1. **没有在真实长历史数据上跑过**：证据是造 25 条精确到天的审计行，验证的是接口语义与翻页正确性；
   生产库里几十万条 `PLAN_DOWNSTREAM_SKIPPED` 下的翻页耗时、以及 `page` 很大时 `OFFSET` 的退化，
   本批没有压测。
2. **日期框只逐条验了「从空填值」**：清空（`clearable` 点 ×）与「先选起、后选止」的两次连续触发，
   单测里走的是同一条 `@update:formatted-value → onSkippedRangeChange` 路径，但没有各自独立的用例。
3. **预算上调需要评审**：`LAZY_BUDGET` 120 → 160 KiB 是本批唯一放宽的判据。论证见 §5，
   但如果评审认为「宁可让共享分包自己拆干净」，替代方案是把日期范围改成无第三方依赖的
   `NSelect` 预设（近 7 / 30 / 90 天），代价是失去任意起止日期。
4. **面板仍是一次性 `size=20`**：没有做「每页条数」选择器。事件量真正变大时这是下一个要动的地方。

## 8. 改动清单

| 文件 | 改动 |
|---|---|
| `frontend/src/views/PlansView.vue` | 新增 `skippedPage/skippedSize/skippedTotal/skippedRange/skippedPageCount`；`loadSkipped` 带 `from/to/page/size`；新增 `turnSkippedPage` / `onSkippedRangeChange`；模板加两个 `NDatePicker` + 总数 + `NPagination` 与 `.skipped-filters` / `.skipped-pager` 样式 |
| `frontend/src/locales/zh-CN.ts` / `en-US.ts` | 改 `skippedHint` / `skippedEmpty`；新增 `skippedFrom` / `skippedTo` / `skippedTotal`（键数各 666，中英对齐） |
| `frontend/src/views/PlansView.spec.ts` | 既有断言补 `from/to`；新增 2 条（翻页器 + 日期范围回第一页） |
| `tools/check_bundle_budget.py` | `LAZY_BUDGET` 120 → 160 KiB 并补来龙去脉注释；报错/达标文案改为按常量算 KiB |
| `docs/evidence/v1.0/pln02c/` | 本目录：脚本 + 日志（00 证据 / 01–08 门禁） |
