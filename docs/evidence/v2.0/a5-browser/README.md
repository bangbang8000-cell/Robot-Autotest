# A5 · 真实浏览器 / 键盘取证（v2.0 收口）

对 `docs/release/v2.0-platform/TEST-PLAN.md` 中 A5 的 13 条 TC 做**真实 Chrome 走查**：
CDP 驱动 Windows Chrome 159（WSL mirrored 直通 `127.0.0.1:9222`），在 `http://localhost:8001`
真实页面里登录 4 个角色、真实按键（`Input.dispatchKeyEvent`）、真实点按、真实切项目，
逐条落「判据 → 实测数字 → 截图 → 结论」。

> 所有数字来自 `raw/tc-TC-*.json`（逐条断言）与 `raw/run-full.log`（本轮控制台流水），
> 没有手填值；截图在 `shots/`，8 页 × 2 主题的全页基线在 `shots/pages/`。

## 结论表

| TC | 判据 | 实测 | 结论 |
|---|---|---|---|
| TC-01 | 报告详情路由不跳；无 `/test-reports` 死链；创建入口存在 | detail 路由 `/reports/<id>/detailed` 不跳；detail 内 `/test-reports` 链接 **0** 个；`/test-reports` 路由不跳；创建入口存在 | **PASS** |
| TC-02 | 报告树嵌套展示 FOR/IF；日志可见；可下载 | `kw-node` **9**、`kw-children` **0**、FOR/IF 节点 **无**；循环 3 轮消息（`A5-STEP-0/1/2`）在日志可见；下载 **200 / 1548 B**（含命令与 `[stdout]`） | **PARTIAL**（结构扁平化） |
| TC-11 | 菜单按角色收敛（13/12/11/9）；无权限直达 → /403 | admin **13** ✓、pm **9**（期望 12）、engineer **9**（期望 11）、viewer **9** ✓；`/api/auth/me.roles` admin=`[admin]`，pm/engineer/viewer **全空**；engineer/viewer 直达 `/settings` **不跳 /403**，渲染完整表单（写操作后端 403） | **FAIL** |
| TC-12 | Ctrl+K 打开、结果 ≤20、↓+Enter 跳转、空态、最近 ≤5 | Ctrl+K 打开且焦点在 `palette-input`；「用例」**1** 条；↓+Enter `/overview → /cases`；「roce」0 条空态；最近 **1** 条 | **PASS** |
| TC-15 | About 页字段与 `/api/system/build-info` 逐项一致 | version / gitCommit / buildTime / license / deps / 4 项自检 **全部一致**；依赖 **23** 条；git HEAD `7a6a604a` ≠ build `61c49dd`（产物早于 HEAD） | **PASS** |
| TC-17 | 侧栏/分组折叠与收藏持久化；切项目保路由 | 侧栏折叠并跨路由保持 ✓；分组 `aria-expanded` true→false，刷新后保持 ✓；收藏 刷新后 1 条；29→1 后路由仍 `/reports`，首条数据从 `RUN-20261107121600-e80` 换为 `RUN-RPT05EXT-20261106-` | **PASS** |
| TC-47 | 切项目不串数据；面包屑存在 | 项目 29→1 后 **10 个** 旧 runId 残留 **0**；新列表 3 行含 `RUN-RPT05EXT` ✓；面包屑元素 **0** 个 | **PASS**（面包屑子项 GAP） |
| TC-51 | AI 抽屉开合/调宽/刷新记忆/无重叠/无残留 | 开 ✓（抽屉 340 px / 主区 1044 px，无重叠）；调宽写入 360 但**实际仍 340 px**（CSS 栅格硬编码）；刷新后仍开、宽度键 360；关闭后 DOM 残留 0，但 `robot.ai.drawer.open` 仍 `true`，**再刷新又弹开** | **FAIL** |
| TC-59 | 8 页 × 2 主题 = 16 张；token/颜色门禁通过；深色无白块 | 16/16 张，主题生效 16/16；`check_tokens` exit 0、`check_colors` exit 0；**深色白块**：cases **120** 块（9.5%）、runs **178**（14.1%）、reports **414**（32.9%） | **PARTIAL** |
| TC-60 | 1440×900 + 抽屉：主区利用率 ≥78%，首屏 ≥12 行，无横滚 | 侧栏 200 + 主区 1044 + 抽屉 340；利用率 **94.9%**（按整视口 72.5%）；无横滚；首屏行 **10/11 < 12** | **FAIL** |
| TC-62 | 1280×720 无横滚；侧栏自动折叠；关键路径可达 | 横滚 **无**（scrollWidth 1280 = vw）；侧栏自动折叠 **✗**（媒体查询阈值 999 px，1280 仍 200 px）；菜单 13 项；`/cases` 可达、6 行、无横滚 | **PARTIAL** |
| TC-63 | Tab 焦点可见；弹层焦点陷阱；Esc 关闭并归还焦点 | Tab 12 步全部可见焦点（12/12）；面板内 Tab 6 步 **0** 次跑出；Esc（焦点在输入框）能关，但焦点落在 **BODY**；Esc（焦点在结果按钮）**关不掉**（handler 只绑在 input） | **PARTIAL** |
| TC-64 | 16 张基线存在；改动可定位；无意外差异 | 基线 16/16（复制自 v1.0 `w7-design/shots`），当前 16/16；逐像素 diff 1.99%–64.78%，每张都有 bbox | **PASS**（差异见下） |

合计：**PASS 6 / PARTIAL 4 / FAIL 3**。

## 子项 GAP（可直接转缺陷单）

| # | 现象 | 证据 | 根因定位 |
|---|---|---|---|
| G-1 | pm / engineer 菜单与 viewer 完全相同（9 项） | `raw/tc-TC-11.json`、`raw/tc-11-roles.txt` | `/api/auth/me` 只回**全局** `user_role`；fixture 的 `project_member.member_role`（PM / ENGINEER / VIEWER）未参与能力计算，`navigation.ts:roleFromCodes` 落为 viewer |
| G-2 | 无权限直达 `/settings` 不拦截 | `raw/tc-TC-11.json`、`shots/tc-11-viewer-403.png` | `router/index.ts` 守卫在 `auth.user` 未就绪时放行；页面完整渲染（读 200），仅写操作 403（`raw/tc-11-settings-perm.txt`） |
| G-3 | 报告树结构扁平化：`kw-node` 有、`kw-children` 0、FOR/IF 不渲染 | `raw/tc-TC-02.json`、`shots/tc-02-tree.png` | 后端 `KeywordSteps` 的 children 未下发 / 前端未递归（与 v1.0 已知项一致） |
| G-4 | AI 抽屉调宽无效、关闭不持久化 | `raw/tc-TC-51.json` | `main.css` 栅格固定 `340px`；`useAiDrawer.toggleDrawer` 是唯一写 `robot.ai.drawer.open` 的地方，`AiPanelDrawer.close()` 未写 |
| G-5 | 深色主题 cases/runs/reports 大面积白块 | `raw/tc-64-diff.json`（`tc59.pages`）、`shots/pages/*-dark-1440.png` | 表格/卡片仍用浅色背景变量，未覆盖深色 |
| G-6 | 1440 首屏表格行 10/11 < 12 | `raw/tc-TC-60.json` | 行高/表头占位偏高，或默认 `pageSize` 偏小 |
| G-7 | 面包屑组件缺失 | `raw/tc-TC-47.json` | 无 `breadcrumb` 元素（0 个） |
| G-8 | 1280 不自动折叠侧栏；Esc 焦点不归还、非输入框焦点时无效 | `raw/tc-TC-62.json`、`raw/tc-TC-63.json` | 断点只有 999 px；`CommandPalette` 的 `keydown` 绑在 input 上，关闭后未 `focus()` 回触发按钮 |

## 偏差与边界（读结论前必看）

1. **TC-11 的期望值 13/12/11/9 来自 TEST-PLAN**；实测 pm/engineer 与 viewer 相同，是产品缺陷，不是脚本取错。角色来源已双证：`raw/tc-11-db-roles.txt`（`project_member` 有 PM/ENGINEER/VIEWER）+ `raw/tc-11-roles.txt`（`/api/auth/me` 只回 admin）。
2. **TC-47 判 PASS 但面包屑 0 个**：TC 正文只要求「切项目不串数据」，面包屑是子项；已单列 G-7，不改变 TC 结论。
3. **TC-64 的 16 张全部有差异**：基线来自 v1.0，两版数据量与深色缺陷不同（reports-dark 64.78% 主要就是 G-5 的白块）；因此给 PASS（基线齐、差异可定位），差异幅度本身不作为「无意外差异」的证明。
4. **TC-02 下载断言用「含命令 + 含 `[stdout]`」**：pabot 会吞掉 robot 的 `Log To Console`，`A5-FOR-*` 不会进下载日志；FOR 可见性以报告日志里的 `A5-STEP-0/1/2` 为准。
5. **TC-60 利用率按 `主区 / (视口 − 抽屉)`** 计（94.9%）；同时记录 `主区/整视口`=72.5%，避免口径歧义。
6. **TC-59 的 token/颜色脚本只覆盖静态令牌与硬编码色**，白块由 `analyze.py` 的 32×32 纯白窗口扫描补齐；两者都过才算视觉合格 —— 本轮因此判 PARTIAL。
7. 运行环境：Chrome 159 / 应用 `8001`（前端静态 + `/api` 反代 `8081` + MySQL）；真实 run `RUN-20261107121600-e80000`，项目 29。

## 复跑

```bash
cd docs/evidence/v2.0/a5-browser
bash run.sh          # node run.mjs（13 条）+ python3 analyze.py（白块 + diff）
# 单条：node run.mjs --only TC-11
```

前置：Chrome `--remote-debugging-port=9222`；`http://localhost:8001` 可用；
`~/.config/robot-autotest/env` 内有 `QA_E2E_PASSWORD`（口令不落库）；run 已显式 parse。

## 文件

```
run.mjs                 13 条 TC 的 CDP 驱动与断言
analyze.py              TC-59 深色白块扫描 + TC-64 逐像素 diff
run.sh                  一条命令复跑
baseline/               16 张 v1.0 基线（从 docs/evidence/v1.0/w7-design/shots 复制）
raw/tc-TC-*.json        逐条断言与事实
raw/tc-64-diff.json     tc59 白块统计 + tc64 每页 diff（changedPixels / ratioPct / bbox）
raw/tc-11-*.txt         角色与设置权限的原始 HTTP / DB 记录
raw/check-tokens.log    tools/check_tokens.py 原始输出
raw/check-colors.log    tools/check_colors.py 原始输出
raw/run-full.log        本轮控制台流水
shots/*.png             关键步骤截图
shots/pages/*.png       8 页 × 2 主题 @1440×900
```
