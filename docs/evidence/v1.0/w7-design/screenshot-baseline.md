# W7 / UIX-04 截图基线（真实浏览器实测）

**状态：DONE（2026-11-06 本机实测）**。

## 1. 为什么改口径

本文件原为「NOT-RUN / 待现场」的待办清单，理由是「本机没有真实浏览器逐页截图条件」。
本轮实测推翻了这个假设：本机有 **Windows Chrome 159**，且 WSL 为 mirrored 网络，
`127.0.0.1:9222` 的 CDP 端口双向直通。于是 16 张基线在本机就能真截，不必留到现场。

## 2. 采集方式（可复跑）

```text
# 1) 启动带调试端口的 Chrome（Windows 侧）
chrome.exe --headless=new --remote-debugging-port=9222 --remote-allow-origins=* \
           --user-data-dir=%TEMP%\chrome-cdp-profile about:blank
# 2) 采集（WSL 侧，走 8001 统一入口）
node docs/evidence/v1.0/w7-design/capture.mjs
```

脚本做三件真事，都不接受 CSS 推导：

1. 在**真实页面上下文**里调 `/api/auth/login` 拿令牌写进 `localStorage`（与前端 auth store 同键名）；
2. 逐页 `Page.navigate` 后等真实渲染，用 `Page.captureScreenshot` 落 PNG；
3. 用页面实测值算利用率（`main.getBoundingClientRect().width / innerWidth`），
   并在 1280 宽度下检查 `documentElement.scrollWidth` 是否溢出。

## 3. 逐页实测（8 页 × 2 主题 = 16 张）

| 页面 | 路由 | 主题 | 1440 利用率 | 1280 横滚 | 截图 |
|---|---|---|---|---|---|
| 资产 | `/assets` | dark | 86.1% | 否 | `shots/assets-dark-1440.png` |
| 用例 | `/cases` | dark | 86.1% | 否 | `shots/cases-dark-1440.png` |
| 用户组与授权 | `/groups` | dark | 86.1% | 否 | `shots/groups-dark-1440.png` |
| 导入预检 | `/imports` | dark | 86.1% | 否 | `shots/imports-dark-1440.png` |
| 工作台 | `/overview` | dark | 85.1% | 否 | `shots/overview-dark-1440.png` |
| 报告 | `/reports` | dark | 86.1% | 否 | `shots/reports-dark-1440.png` |
| 执行 | `/runs` | dark | 86.1% | 否 | `shots/runs-dark-1440.png` |
| 对外测试报告 | `/test-reports` | dark | 86.1% | 否 | `shots/test-reports-dark-1440.png` |
| 资产 | `/assets` | light | 86.1% | 否 | `shots/assets-light-1440.png` |
| 用例 | `/cases` | light | 86.1% | 否 | `shots/cases-light-1440.png` |
| 用户组与授权 | `/groups` | light | 86.1% | 否 | `shots/groups-light-1440.png` |
| 导入预检 | `/imports` | light | 86.1% | 否 | `shots/imports-light-1440.png` |
| 工作台 | `/overview` | light | 85.1% | 否 | `shots/overview-light-1440.png` |
| 报告 | `/reports` | light | 86.1% | 否 | `shots/reports-light-1440.png` |
| 执行 | `/runs` | light | 86.1% | 否 | `shots/runs-light-1440.png` |
| 对外测试报告 | `/test-reports` | light | 86.1% | 否 | `shots/test-reports-light-1440.png` |

截图文件：16 张，位于 `docs/evidence/v1.0/w7-design/shots/`。
原始读数：`capture-result.json`。

## 4. 判定

| 判据（DEV-PLAN W7 交付判据） | 实测 | 结论 |
|---|---|---|
| 1440 利用率 ≥ 78% | 16/16 页均 ≥ 85.1% | **达成** |
| 1280 无横滚 | 16/16 页 `scrollWidth ≤ innerWidth` | **达成** |
| 亮/暗两主题均生效 | 16/16 页 `data-theme` 与预期一致 | **达成** |

## 5. 实测发现并修掉的布局缺陷

首轮采集 **12/16 达标**，`/test-reports` 与 `/groups` 在 1280 下 `scrollWidth=1392 > 1280`，
亮暗两个主题共 4 张不达标。

定位过程（CDP 遍历所有元素找 `rect.right > innerWidth`）：主内容区宽 1065，
但 `.page-head` 与 `.data-table` 实测 **1144**，即子项比容器宽 79px。

**根因**：各视图根元素 `.page` 是 `display: grid` 且**没写 `grid-template-columns`**，
单列轨道取默认 `auto`，轨道宽度按所有子项的 **max-content** 计算；
含宽表格的页面因此把轨道撑到 1144px，整页跟着横滚。

**修法**（`frontend/src/styles/main.css` 末尾，全局规则）：

```css
.page { grid-template-columns: minmax(0, 1fr); }
.page > * { min-width: 0; }
```

超宽表格的横向滚动交回 `.table-scroll` 容器自己处理（FR-v0.2-57），
不再让整页承担横滚。修后复采 **16/16 全绿**。

## 6. 边界

- 本轮截图是**本机 Chrome headless** 渲染，不是现场 4K 屏 / 多缩放比 / 打印预览；
- 字体渲染与滚动条样式在 Windows Chrome 与现场环境可能有像素级差异；
- 主题切换验的是 `data-theme` 生效与逐页截图，不覆盖「首帧防闪」的视觉闪动录屏（该点由
  `index.html` 内联脚本 + `useTheme` 单测覆盖）。
