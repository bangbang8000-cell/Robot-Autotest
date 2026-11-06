# ORCH-02 证据 · 依赖图视图与关键路径

- **执行时间**：2026-11-06 20:48（GMT+8）
- **链路**：真实 HTTP（`:8001` 统一入口 → `:8081`）+ 真实 MySQL `robot_dev` + 真实 Chrome 159（CDP）
- **结论**：**通过**。HTTP 9 项全 PASS；浏览器 9 项全 PASS；单测 11 条全绿。
  过程中抓到并修掉一个**渲染级缺陷**（节点渲染成黑块，见 §4），并据此新增门禁第 13 步。

## 1. 命令原文

```bash
cd ~/robot-autotest
# 纯函数单测（关键路径三层 tie-break、分层、孤立点）
cd backend && mvn -o -B test -Dtest=PlanGraphTest
# 组件单测（jsdom，只验「属性被设进去了」）
cd ../frontend && npx vitest run src/components/plan/PlanGraph.spec.ts
# 真实 HTTP 验收（夹具：qa-e2e-orch02 项目内 52 条计划 / 50 条边）
python3 docs/evidence/v2.1/ORCH-02/run.py
# 真实浏览器验收（首屏耗时 + 视觉可辨 + 截图）
node docs/evidence/v2.1/ORCH-02/capture.mjs
```

前置：后端 jar 含本包代码、`:8001` 静态产物 `frontend/dist` 已重建、Chrome 以
`--remote-debugging-port=9222` 启动。`capture.mjs` **不造数据**，读 `run.py` 建好的夹具。

## 2. 判据逐条核对

DEV-PLAN §10.3 三条判据 + 「真实链路」附加项：

| # | 判据 | 实测 | 结论 |
|---|---|---|---|
| 1 | 50 节点首屏 ≤ 1s | `firstPaint=172ms`（导航→50 节点入 DOM 后首帧），接口本身 15ms | 通过 |
| 2 | 关键路径与手算一致 | `p01→…→p50→p51`，长度 51，`criticalSeconds=4200` = 50×60+1200 | 通过 |
| 3 | 孤立点视觉可辨 | `orcho2-p52` 唯一，`stroke-dasharray: 4px, 3px`（真实 computed style） | 通过 |
| 4 | 真实 HTTP 九项 | 见 §3 上半段，`FAIL 0` | 通过 |
| 5 | 真实浏览器九项 | 见 §3 下半段，`FAIL 0` | 通过 |
| 6 | 两次请求逐字节一致 | `ORCH02-09` diff 为空 | 通过 |

## 3. 原始输出

`run.log` 摘要（完整见同目录 `run.log`）：

```
# 夹具项目 projectId=44，52 个计划 / 50 条边

PASS  ORCH02-01   接口返回 200                               HTTP 200
PASS  ORCH02-02   节点数=52                                 nodes=52
PASS  ORCH02-03   边数=50                                  edges=50
PASS  ORCH02-04   关键路径与手算一致（p01→…→p50→p51）               长度=51 起=[p01,p02] 止=[p50,p51]
PASS  ORCH02-05   关键路径总时长=4200s                          criticalSeconds=4200 (手算 50×60+1200)
PASS  ORCH02-06   孤立点只有 p52                              isolated=[orcho2-p52]
PASS  ORCH02-07   分层正确（p01=0 / p50=49 / p51=50 / p52=0）  p01=0 p50=49 p51=50 p52=0
PASS  ORCH02-08   接口耗时 ≤ 1000ms（首屏预算的后端部分）               15 ms
PASS  ORCH02-09   两次请求结果逐字节一致                            diff 为空

合计 9 项，FAIL 0 项

### 真实浏览器（Chrome/159.0.8130.0）

PASS  ORCH02-B1  首屏探针点火（≥50 节点入 DOM）  —  firstPaint=172ms, wall=210ms
PASS  ORCH02-B2  首屏 <= 1000ms（导航 -> 50 节点首帧）  —  172 ms
PASS  ORCH02-B3  DOM 节点数=52  —  nodes=52
PASS  ORCH02-B4  DOM 边数=50  —  edges=50
PASS  ORCH02-B5  关键路径边数=50 且加粗（stroke-width=2.5）  —  criticalEdges=50, widths=2.5
PASS  ORCH02-B6  孤立点只有 1 个（orcho2-p52）且为虚线框  —  isolated=["orcho2-p52"], dasharray=4px, 3px
PASS  ORCH02-B7  节点着色真实生效（无黑色回落，var() 换成了 CSS 类）
                  —  fills=rgb(255,255,255) / rgb(238,242,247), criticalStroke=rgb(21,103,201)
PASS  ORCH02-B8  关键路径段落与接口一致（估算 4200s）  —  关键路径估算 4200s
PASS  ORCH02-B9  图不顶破页面：无整页横向滚动（图在自身容器内滚）
                  —  scrollWidth=1425, viewport=1440, svg=9688x176

合计 9 项，FAIL 0 项
```

单测：

```
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0  -- PlanGraphTest
✓ src/components/plan/PlanGraph.spec.ts (7 tests)  -- 含新增的「不得写 SVG 表现属性」「图例走语言包」回归
```

截图（`shots/`，1440×900，浅色主题）：

| 文件 | 看什么 |
|---|---|
| `first-screen-1440.png` | 首屏：p01..p06 关键路径加粗、p52 虚线孤立点、图例、下方 4200s 估算段落 |
| `graph-mid-1440.png` | 中段 p23..p29：链连续、每条边都有箭头，证明不是只画了头几个 |
| `graph-tail-1440.png` | 尾段 p46..p51：p51 显示 `1200s`（尾节点权重），与手算对得上 |

> 不产出「整图一张」：图宽 **9688px**（52 列 × 190px）而内容区有 `max-width`，
> 任何缩放都只会被布局压扁或裁到页头，截出来反而误导。用三张等距切片拼出整条链。

## 4. 抓到的缺陷与修法（本节是全篇重点）

**现象**：首轮浏览器验收 9 项里 7 项 FAIL，截图里 52 个节点全是**黑色方块**，
节点名、连线、箭头全部不可见（`nodes=52 / edges=50` 却是对的）。

**根因是两层叠加**：

1. 颜色写成了 SVG **表现属性** `fill="var(--surface-raised)"`。表现属性不接受 `var()`，
   浏览器把整条声明判为无效值，`fill` 回落到初始值 **`black`**；
2. 更根本的是变量名本身**在主题里不存在**：`--surface-raised` / `--surface-sunken` /
   `--text-link` / `--text-primary` / `--text-secondary` / `--text-danger` / `--text-warning` /
   `--border-subtle` 这 8 个名字全是凭空写的，真名是 `--panel` / `--surface-2` / `--accent` /
   `--text` / `--text-muted` / `--fail` / `--warn` / `--border`。
   共 **4 个文件 20 处**（`PlanGraph.vue` / `PlanGraphPage.vue` / `PlanGroupsView.vue` /
   `FailureClustersView.vue`），全部静默失效。

**为什么三道机器关都没拦住**：

| 关 | 为什么拦不住 |
|---|---|
| `vue-tsc --noEmit` | 只查 TS，CSS 字符串不在类型系统里 |
| `vitest`（jsdom） | 不算样式；scoped CSS 由 Vite 单独出包，挂载时根本没被加载 |
| `vite build` | 不校验变量名是否存在，构建照样成功 |

**修法**：

1. 颜色一律走 CSS **类**（`.node-rect` / `.edge-critical` / `.marker-critical`），
   变量名换成主题里真实存在的令牌；
2. 单测加一条「rect/path 上不得出现 `fill` / `stroke` 属性」的回归——这条能挡住第 1 层，
   但挡不住「名字写错」，因为 jsdom 拿不到 scoped CSS；
3. 新增 **`tools/check_css_vars.py`** 并接成门禁第 **13** 步 `13-css_vars`：
   扫全部 `.css` / `.vue` / `.ts` 里的 `var(--x)`，要求每个名字都能在某个文件里找到定义
   （scoped 局部变量也算；`var(--preset-${c})` 这类动态拼接按前缀放行）。
   反向验证过：往 `theme.css` 塞一个 `var(--no-such-token)`，脚本非 0 退出并指出
   `theme.css:434`；去掉后回到 `[OK] CSS 变量引用全部有定义（已定义 108 个）`。

> 这类错只有「真实浏览器 + 量 computed style」能抓：`ORCH02-B7` 断言的就是
> `getComputedStyle(rect).fill !== rgb(0,0,0)`。jsdom 里写多少条断言都抓不到。

### 4.1 同轮被 `lint` 拦下的另外两处

这两处**不是**本包新写的逻辑错，而是本包新建的视图撞上了既有规范门禁，一并改正：

| 拦下来的 | 门禁 | 修法 |
|---|---|---|
| `PlanGraph.vue` 图例与 `aria-label` 硬编码中文 | `06-lint` → `tools/check_i18n.py`，4 处 | 拆成 `planGraph.legendCritical / legendIsolated / graphAriaLabel` 三个词条（中英各一份），组件改走 `useI18n()`；单测补一条「图例文案不许出现键名、且参数 300 要填进去」 |
| `FailureClustersView.vue` 用原生 `<table>` 画数据网格 | `06-lint` → `tools/check_tables.py`，1 处 | 改走 `DataTable` 壳（列定义改 `DataTableColumns` + `render`，行标记用 `row-props` 转发 `data-testid`），并删掉 `.clusters th/td` 那几条自绘边框；单测 5 条无需改动即通过 |
| `PlanGroupsView.vue` 用了 `320px` | `06-lint` → `tools/check_tokens.py` px 白名单 | 改成白名单里已有的 `240px`（左栏最小宽度，两列都够用） |

> 顺带确认：`check_tables.py` 打印 `[FAIL]` 时**确实**返回非 0（`lint.sh` 的 `set -e` 会中断），
> 本次就是被它挡下的，不是「只打印不拦」的假门禁。

## 5. 手算对照表

夹具：`qa-e2e-orch02` 项目，`orcho2-p01..p52`；p01..p50 链式依赖（每条 `timeout=60s`），
p51 依赖 p50（`timeout=1200s`），p52 无任何边。

| 量 | 手算 | 实测 | 一致 |
|---|---|---|---|
| 节点数 | 50（链）+ 1（p51）+ 1（p52）= **52** | 52 | 是 |
| 边数 | 49（p01→p02 … p49→p50）+ 1（p50→p51）= **50** | 50 | 是 |
| 关键路径长度 | 50（链）+ 1（p51）= **51** | 51 | 是 |
| 关键路径时长 | 50×60 + 1200 = **4200s** | 4200 | 是 |
| 分层 | p01=0 … p50=49 / p51=50 / p52=0 | 0…49 / 50 / 0 | 是 |
| 孤立点 | 只有 p52（无前置也无后置） | `[orcho2-p52]` | 是 |
| 图尺寸 | 宽 = 24×2 + 51×190 − 190 + 140 = **9688**；高 = 24×2 + 2×64 = **176** | 9688×176 | 是 |

## 6. 设计决定（后续维护要看）

| 决定 | 理由 | 反例（如果做成另一种） |
|---|---|---|
| **只读 SVG，不做拖拽编辑** | 拖一条边会绕过保存时的环检测，编辑器画得出的图后端不一定收得下 | 可拖拽 → 用户在一张「看起来合法」的图上反复被 400 |
| 布局用**固定列宽/行高**而非自适应 | 50 节点时自适应必然重叠，且两次渲染位置漂移 | 自适应 → 每次刷新图都长得不一样，没法对比 |
| 关键路径 tie-break 三层：总权重 → 链长 → id 更小 | 全零权重时只比总权重会退化成单节点 | 只比权重 → 权重全 0 时关键路径变成随便一条 |
| `nodes()` 并上 `weights.keySet()` | 孤立计划一条边都没有，只从边集推点会整条漏画 | 只从边推 → 图看着正常，少画了计划 |
| 权重口径 = **超时秒数之和**，页面标「估算」 | 它是理论上限不是历史耗时 | 拿它当实际耗时会误判容量 |
| 分页 `PAGE_SIZE=100` 拉全量、上限 `MAX_PLANS=500` | 图接口要一次算完整张图，不能只算一页 | 只取首页 → 关键路径跨页时算错 |

## 7. 遗留

| # | 事项 | 影响 | 归属 |
|---|---|---|---|
| 1 | 图接口返回项目内**全部**计划，夹具混项目会让节点数不可断言 | 验收脚本必须用独立项目（本包已改 `qa-e2e-orch02`） | 已记录，脚本 docstring 有说明 |
| 2 | 52 列 × 190px = 9688px，只能横向滚动看 | 大项目首屏看不到全貌，缺「缩略图 / 适配宽度」 | 归 NODX-04 之后评估 |
| 3 | 关键路径按超时秒数算，未接历史耗时 | 只能作相对排序，不能作容量预测 | 归 v2.2 性能面 |
| 4 | 深色主题未单独截图 | 已用主题令牌（`--panel/--surface-2/--accent`），深浅色自动跟随，但没留证据 | 归 A5 真实浏览器批次补齐 |
