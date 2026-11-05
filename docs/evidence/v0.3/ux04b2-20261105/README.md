# UX-04b 第三批：运行列表 + 用例列表迁库组件（2026-11-05）

对应 WBS `#80 UX-04b`（进行中）。本批把 **`RunsView`、`CasesView`** 两页从原生元素迁到
`naive-ui` 组件，并首次为**视图层**建立回归测试。

## 1. 本批做了什么

| 页面 | 迁移内容 |
|---|---|
| `RunsView` | 原生 `table` → `NDataTable`（`scroll-x` 保留宽表横滚）；筛选 `select` → `NSelect`；按钮 → `NButton`（`quaternary` 为主，操作列用 `text`）；分页 → `NPagination`；状态列文字 → `StatusTag`；`checkbox` → `NCheckbox`；时间列改走 `formatDateTime` 统一 `—` |
| `CasesView` | 筛选/表单控件 → `NInput` / `NSelect`（表单存 `type="textarea"` + `autosize`）；表格 → `NDataTable`；**勾选列自渲染 `NCheckbox`**（见 §2）；每行动作 → `NButton text`；批量条 8 个按钮 → `NButton`；分页 → `NPagination`；评审状态列 → `StatusTag` |

两个页面都补了空态（表格 `#empty` 槽 → `EmptyState`，含「未选项目」文案分支）。

## 2. 三个必须记下的决策

### 2.1 勾选列**不能**用 `NDataTable` 内置的 `type: 'selection'`

内置 selection 列只认 `checked-row-keys`，而它会在 `data` 变化时把**不在当前页的键丢掉**。
本项目的选择契约是「**跨页保留**」（FR-v0.2-35），两者直接冲突 —— 用内置列会让用户翻页后
前面勾的全没了，而且这种丢失是静默的。

所以列定义里自己 `render: h(NCheckbox, ...)`，勾选状态仍由 `useSelection` 持有，
表头半选态照旧从 `pageIndeterminate` 派生。代价是接线变成「本页责任」，因此本批补了
视图级测试（§3）来兜。

### 2.2 选中行底色用 `accent-soft` 而不是 `surface-selected`

`surface-selected` 是固定中性蓝（亮 `#dbe9fb` / 暗 `#17324d`），在金色、翡翠绿预设下与整站
强调色对不上。`accent-soft` 本来就是这项目「选中」的统一语义色（菜单选中项也用它），
换预设自动跟随。

同时给「正在看日志的那一行」加 `row-active` 底色：否则用户滚到日志面板后无法确认自己读的是
哪一条运行 —— 这是原有实现就有的信息缺失，本次顺手补上。

### 2.3 新增 `reviewStatusTone`，`draft` 用 `neutral` 而不是 `warn`

`pending`（等评审）才用 `warn`。若草稿也标黄，列表里「等我审的」会淹没在草稿堆里。
显示契约集中在 `utils/statusLabels.ts`，`StatusTag` 反向只 import 类型，不引入循环依赖。

## 3. 视图级回归测试（本轮首次建立）

此前前端测试只在 composable / util 层，视图迁移**没有任何回归保护** —— 迁错了只能靠人眼。
本批验证了 `NDataTable` 在 happy-dom 下可以正常渲染（不需要 `ResizeObserver` 补齐），
于是把模式立起来：

| 文件 | 覆盖 |
|---|---|
| `frontend/src/views/CasesView.spec.ts` | 行渲染、勾一行出现批量条且计数正确、表头全选只选本页且可再次取消、状态列渲染 2 个 `StatusTag` |
| `frontend/src/views/RunsView.spec.ts` | 行渲染、点「查看日志」打开面板且**高亮只落在对应行**、状态列图标+文字双编码、已终止行不显示「取消」 |
| `frontend/src/utils/statusLabels.spec.ts` | 两个色档映射的大小写无关性、`canceled` 不是失败、`draft ≠ pending`、未知值/空值降级 `neutral` |

两个视图 spec 各 4 条，共 8 条；全量前端测试 47 → **61**。

踩到的两个坑（后续写视图测试还会遇到）：

1. `vi.mock` 会被提升到 import 之前 —— 工厂里引用外部 `const` 会 TDZ 报错，数据必须写进工厂内部。
2. 组件里 `<RouterLink v-slot="{ navigate }" custom>` 需要路由；不装整个 vue-router 时，
   给一个「把 `navigate` 交给默认插槽」的桩即可（`Slots` 类型要显式标注，否则 `unknown`
   传给 `h()` 会 TS 报错）。

## 4. 实测

全套日志见本目录，`run-all.sh` 可一键复现。

| 项 | 结果 |
|---|---|
| `fe-test` | **61 / 61 passed**（10 文件） |
| `view-test` | **8 / 8 passed**（2 文件） |
| `status-tone` | 6 / 6 passed |
| `lint-sh` | **PASS**（secret-scan / eslint / mvn compile / 12 条静态守门） |
| `backend-mvn-test` | **504 tests / 0 failures / 0 errors**（68 suite） |
| `check-i18n` | 通过（键集相等 / 硬编码中文 0 / 23 个受控值全覆盖） |
| `component-imports` | 28 个 `.vue` 全部有 import；负向自检 rc=1（符合预期） |
| `bundle-budget` | 图标 4,207 B／vendor 104,957 B／懒加载分包 97,028 B（最大 `DataTable` 91,595 B）／字体 164,957 B —— 全部达标 |
| `subset-font` | 935 / 935 字全覆盖，164,957 B gzip |

### 命令与 rc

```text
fe-build                       rc=0
fe-test                        rc=0
view-test                      rc=0
status-tone                    rc=0
lint-sh                        rc=0
component-imports              rc=0
component-imports-selftest     rc=1 (期望 1)
bundle-budget                  rc=0
subset-font-check              rc=0
check-i18n                     rc=0
backend-mvn-test               rc=0
diff-stat                      rc=0
```

## 5. 改动文件

```
frontend/src/views/RunsView.vue              (重写，329 → 约 450 行)
frontend/src/views/CasesView.vue             (重写，527 → 约 640 行)
frontend/src/utils/statusLabels.ts           (+20 行：reviewStatusTone)
frontend/src/views/RunsView.spec.ts          (新增)
frontend/src/views/CasesView.spec.ts         (新增)
frontend/src/utils/statusLabels.spec.ts      (新增)
frontend/src/assets/fonts/noto-sans-sc-ui.woff2 (重新子集化)
docs/evidence/v0.3/ux04b2-20261105/*         (本目录)
```

> 注：`diff-stat.log` 是 `git diff --stat`，**不含 untracked 新增文件**；新增文件清单以上表为准。

## 6. 未完成 / 风险

1. **视觉未人验**：本地拿不到可用的登录测试账号，进不了登录后的列表页；Windows → WSL 的
   dev server 也不可达（`127.0.0.1` 与 WSL IP 均 000）。表格观感、四态在真机上的效果仍需
   大师在 `/dev/styleguide` 或实机上过一眼 —— 与前几轮同一类待现场项。
2. **`window.prompt` 未换**：用例批量「驳回」仍用原生 `prompt`。项目只挂了 `NConfigProvider`，
   没有 `NDialogProvider`，换 `useDialog` 要先动 `App.vue`。保持既有行为，另记待办。
3. **剩余未迁页面**：`OverviewView`、`AssetsView`、`AdminView`、`PlansView`（部分）、
   `ReportDetailView` 仍为原生元素。打印页（RPT-05）按约定保持零库组件依赖。
