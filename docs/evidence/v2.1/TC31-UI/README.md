# TC-v2.1-31 证据：三页 × 四态 × 双视口（真浏览器）

## 结论

- **36 项断言全 PASS、FAIL 0**（`31-run.log`，退出码 0）：模板配置 / 执行节点 / 失败聚类
  三页各自的**加载、空、错、有数据**四态都能在真实浏览器里判出来。
- **1440 内容区利用率 86.1%**（要求 ≥ 78%），**1280 整页无横滚**（`scrollWidth=1280`），
  三页 × 四态共 12 组全部达标。
- 24 张截图（12 组 × 1440/1280）落 `shots/`，度量原始值落 `31-measure.json`。

## 判据对照（TEST-PLAN §4.7 TC-v2.1-31）

| 判据 | 证据 |
|---|---|
| 四态齐全且可判 | templates：加载=`加载中…`、空=列表 0 项+新建按钮在、错=`[data-testid=error]` 有文案、有数据=`template-list` 1 项+平台内置标记；nodes：加载=`aria-busy=true` 且骨架 48 条、空=`还没有执行节点`、错=`error-line` 有文案、有数据=9 个 `node-status`；clusters：加载=查询按钮 disabled 且无 summary、空=`no-failure`、错=`[data-testid=error]`、有数据=`共 1 条失败，分成 1 个类别` + 1 组 + 1 行 |
| 1440 利用率 ≥ 78% | 12/12 组实测 **86.1%**（`.app-main` 1240px / 视口 1440px） |
| 1280 无横滚 | 12/12 组实测 `scrollWidth=1280`、`overflowX=false` |

## 命令

```bash
# 前置：Chrome 带 --remote-debugging-port=9222；8001 统一入口可用；
#       QA_E2E_PASSWORD 在 ~/.config/robot-autotest/env（不入库）
cd ~/robot-autotest
node scripts/v21/ui-three-pages.mjs          # 退出码 0 = 全 PASS
```

可选过滤：`TC31_PAGE=templates|nodes|clusters`、`TC31_STATE=loading|empty|error|data`、
`TC31_DEBUG=1`。

## 四态怎么造

用 CDP `Page.addScriptToEvaluateOnNewDocument` 在**文档级**打桩 `window.fetch`，只拦目标接口：

- **加载**：目标接口返回永不 resolve 的 Promise（页面停在 loading，挂起 ≈1.5s，远小于前端 10s 超时）；
- **空**：返回 `data: []`；聚类页返回 `{runId, total:0, classes:[]}`；
- **错**：返回 `code≠0` 的响应信封（`http.ts` 据此抛 ApiError，走错误态）；
- **有数据**：不注入脚本，走真实请求（真实库、真实项目）。

为什么不用 CDP `Fetch` 域拦截：同一脚本里连续造三页 loading 态时，`Fetch.requestPaused`
偶发「已暂停但页面仍拿到真实响应」，隔离复现却正常——不可依赖，已改文档级打桩。

导航前会置 `robot.ai.drawer.open=false` 与 `robot.layout.side.collapsed=false`：
AI 抽屉默认展开占 340px，会把 1440 下的内容区压到 62.5%，不是页面本身的问题。

## 夹具

| 项 | 值 |
|---|---|
| 登录账号 | `qa-e2e-admin`（口令只在 `~/.config/robot-autotest/env`） |
| 模板页项目 | `project 44` |
| 节点页项目 | `project 44` |
| 聚类页项目 / run | `project 1` / `RUN-RPT05EXT-20261106-101647`（真实 1 条失败） |

## 原始输出

| 文件 | 内容 |
|---|---|
| `31-run.log` | 36 行 PASS/FAIL 与每组 snap/util 摘要，末行「合计 36 项，FAIL 0 项」 |
| `31-measure.json` | 每组 state 的 snap、1440/1280 的 content/utilization/scrollWidth 原始值 |
| `shots/31-<page>-<state>-<1440\|1280>.png` | 24 张截图 |

## 口径说明

1. **templates 空态没有专门文案**（`ReportTemplatesView` 只在列表 0 项时仍显示新建按钮），
   判据取「列表 0 项 + 新建按钮可用」，README 在此写明，避免以后被当成漏测。
2. **clusters 首屏无数据**：必须先输入 runId 再点查询才进入四态；脚本的 `trigger` 用原生
   setter + `input` 事件，兼容 Vue 的 v-model。
3. loading/empty/error 是**接口注入**（不伪造后端故障），有数据态是**真实库**；本 TC 验的是
   前端四态呈现与布局，不验后端错误处理逻辑（那在 A4/A5 的 HTTP TC 里）。
