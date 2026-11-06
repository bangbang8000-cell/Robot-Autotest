# W6 / AIX-01..05 AI 面板 · 交付证据（2026-11-06）

## 实现

| 包 | 交付 |
|---|---|
| AIX-01 | `AiPanelDrawer.vue` 右侧抽屉 + 顶栏开关 + 宽度记忆（`useAiDrawer.ts`）+ 与 W7 三栏栅格共存（不是覆盖层）；路由 `/imports`、`/groups`、`/kernels` 的工具页走命令面板进入，不新增第七个一级菜单 |
| AIX-02 | `config/ai-cli.yml`；`AiCliProbe` 只探 PATH 与可执行位，**不调用模型**；`AiProcessManager` 构造 `--print --output-format text --prompt-file` 非交互命令、超时与进程树终止 |
| AIX-03 | `ContextPacker` 复用 C5 `ReportViewRenderer.scrub()` 脱敏 + 8000 字符截断；`aiPanel.spec.ts` |
| AIX-04 | `AiPanelService` 建议 / 发起执行（**二次确认硬闸门**）/ 效果评估；`AiPanelController` 五个端点先做项目访问校验 |
| AIX-05 | 全部动作写审计；无 CLI 时 L1 静态建议 `DEGRADED` 不阻断；一键终止与 AI 是否可用无关；`ai_session` / `ai_action` / `ai_cli_probe` 三表 |

## 实测

- `AiPanelTest` **6 条**：脱敏 + 截断、无 CLI 降级不阻断、缺二次确认抛 `FORBIDDEN`、终止后会话失效、非交互命令构造、四类动作均写审计。
- C6 本机 CLI 非交互探测：`claude` / `codex` FOUND，`gemini` / `aider` NOT_FOUND，日志
  `docs/evidence/v1.0/w2-w7-preconditions/cli-probe.log`。
- 后端 771 条 / 前端 239 条单测全绿；`tools/check_api_prefix.py` 守门已接入 lint。
- 迁移 `V8_4` 实跑并核表。

## 边界

- **本机只探测 CLI 是否存在，不发起任何模型调用**；L2/L3 的真实模型交互待现场。
- 缺陷订正：W2–W5 新增前端 API 模块曾把 `/api` 前缀写进 `request()` 路径，而 `http.ts` 自身会拼
  `BASE_URL=/api`，实际请求会变成 `/api/api/...`。本波全部修正并新增 `tools/check_api_prefix.py`
  静态守门（见 `docs/evidence/v1.0/w6-ai/api-prefix-fix.md`）。
- G8 不申请。

## C9 新表登记

`ai_session`、`ai_action`、`ai_cli_probe`。
