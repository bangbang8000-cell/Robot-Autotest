# UX 修复三项（2026-11-07，大师反馈）

提交 80b0d67。复跑：node docs/evidence/v2.1/UX-fixes-20261107/verify.mjs（前置 Chrome CDP 9222 + :8001）。

| # | 修复 | 判据 | 实测 |
|---|------|------|------|
| F1 | 项目下拉去重 | 顶栏 .app-project=1、工作区 0 | PASS |
| F2 | AI 面板顶端对齐工作区 | drawerTop == headerBottom（58px），不占顶栏 | PASS |
| F3 | 主题/色彩跟随用户 | 登出回默认；重登恢复服务端偏好（dark） | PASS ×2 |

截图：shots/fix-ai-align.png（顶栏横贯 + AI 对齐）、shots/fix-pref-restore.png（重登恢复暗色）。
配套：vitest 276 条全绿；门禁 14 步 FAILS=0。
