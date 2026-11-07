# A9 · AI 真实模型调用取证（2026-11-07）

> 对象：`/api/ai/*` 面板链路（AIX-02/03/04/05）＋ 本机真实模型 CLI 调用
> 判据（来自 `06-收口执行计划-20261106.md` A9 行）：**留 1 条真实输出（脱敏）+ 动作审计可查；无 CLI 时降级保持不报错**
> 复现：`cd docs/evidence/v2.0/a9-ai-call && python3 probe.py`
> 全部读数来自真实 HTTP（`http://127.0.0.1:8001`）、真库 `robot_dev`、真实 CLI（`claude 2.1.267`），未造任何数据。

---

## 1. 结论

| 判据 | 结果 | 依据 |
|---|---|---|
| 真实模型输出留档（脱敏） | **PASS** | `raw/a9-real-model-output.txt`，40 字符中文回答，四类敏感串扫描 0 命中 |
| 动作审计可查 | **PASS** | 会话 1001 下 `AI_SESSION_OPEN / AI_SUGGEST / AI_EXECUTE / AI_TERMINATE` 四行全部命中（`audit_log` 直查） |
| 无 CLI 降级不报错 | **PASS**（按代码路径 + 单测口径） | `suggest` 在 CLI 探测为 false 时仍返回 200 + L1 摘要，永不抛错；本轮产品探测到 `codex`（anyAvailable=true，NORMAL），降级分支由 `AiPanelService.suggest/execute` 的 `degraded` 分支保证，判据不变 |
| **产品真的调模型** | **FAIL（缺口 G-A9-1）** | 产品 `application/ai/` 包内**无任何 `ProcessBuilder`/`Runtime.exec`**，L3「已发起执行」只写审计，不起子进程 |
| **产品 argv 能被真实 CLI 接受** | **FAIL（缺口 G-A9-2）** | `--print --output-format text --prompt-file <f>` → `claude` 返回 `error: unknown option '--prompt-file'`（rc=1） |

**总判据 17 项：16 true / 1 false**（`raw/a9-result.json`）。唯一 false 是「产品 argv 与真实 CLI 契约一致」——即缺口本身，不是取证失败。

---

## 2. 产品状态实测（`GET /api/ai/state?projectId=29`）

```json
{"claude": false, "codex": true, "gemini": false, "aider": false,
 "anyAvailable": true, "maxLevel": "L3", "mode": "NORMAL",
 "nonInteractiveOnly": true, "projectId": 29, "timeoutSeconds": 60,
 "encoding": "UTF-8", "contextMaxChars": 8000, "executeRequiresConfirmation": true}
```

- 产品（systemd 用户服务，PATH 含 `~/.local/bin`）只看见 `codex`；**交互式 bash 里 `claude` 也在**（`/mnt/c/Users/everg/AppData/Roaming/npm/claude`），但产品看不到 —— 说明探测口径是「服务进程自身的 PATH」，这是正确的隔离行为，不是 bug。
- `maxLevel` 直接由 `anyAvailable` 推导为 `L3`，**但 L3 实际不调模型**（见 §6 缺口 1），该字段目前是"名义能力"。

## 3. 真实 HTTP 动作链（会话 1001）

| 步骤 | 请求 | 状态 | 返回 |
|---|---|---|---|
| 开会话 | `POST /api/ai/session?projectId=29&userId=1` | 200 | `1001` |
| L1 建议（含 IP/口令/路径/命令的上下文） | `POST /api/ai/suggest` | 200 | `level=L1, degraded=false, summary="上下文已打包，可执行 L2 解释（150 字符）"` |
| 未确认执行 | `POST /api/ai/execute {confirmed:false}` | **403** | `40300 FORBIDDEN "发起执行必须二次确认"` |
| 确认执行 | `POST /api/ai/execute {confirmed:true}` | 200 | `level=L3, summary="已按二次确认发起执行: PERF-IPERF-200G-001"` |
| 一键终止 | `POST /api/ai/terminate` | 200 | `"会话已关闭，进程树已终止"` |

- 注入的敏感上下文（`10.202.54.106` / `SuperSecret!42` / `/home/evergreen/...` / `ssh -o ...`）**未出现在任何响应里**（`suggest_response_secret_hits={}`）：L1 只回摘要，不回打包内容。
- 打包内容本身经 `ContextPacker.pack()` → `ReportViewRenderer.scrub()`（IP / CREDENTIAL / ABS_PATH / COMMAND 四条正则），摘要里的「150 字符」是脱敏后长度。

## 4. 动作审计（真库直查）

```sql
SELECT action, resource_type, resource_id, LEFT(detail,120), occurred_at
FROM audit_log WHERE resource_id='1001' AND action LIKE 'AI\_%' ORDER BY id;
```

本会话新增 4 行（`2026-11-07 14:49:01`）：

```
AI_SESSION_OPEN  ai_session 1001 project=29; user=1
AI_SUGGEST       ai_action  1001 上下文已打包，可执行 L2 解释（150 字符）
AI_EXECUTE       ai_action  1001 已按二次确认发起执行: PERF-IPERF-200G-001
AI_TERMINATE     ai_action  1001 会话已关闭，进程树已终止
```

**发现（缺口 G-A9-3，轻）**：`sessions` 是 `AtomicLong(1000)` 内存计数，**重启后从 1001 重来**；`audit_log.resource_id` 只存 `1001`，因此历史会话与新会话在审计里**同 id**（上表里 2026-11-06 ~ 11-07 共 9 条不同项目/时间的 `AI_SESSION_OPEN` 全是 `1001`）。按 id 反查会话会串台，需加时间或全局唯一 id。

## 5. 真实模型调用（1 条，已脱敏留档）

- CLI：`claude 2.1.267 (Claude Code)`，位于 `/mnt/c/Users/everg/AppData/Roaming/npm/claude`（Windows npm shim，WSL 内可执行）。
- 命令（真实、无包装）：`claude -p '<prompt>' --output-format text`
- 真实输出（`raw/a9-real-model-output.txt`，40 字符）：

  > 判定一次自动化测试是否通过，必须同时具备执行日志、测试报告与界面截图这三类证据。

- 脱敏扫描（与产品 `scrub()` 同一套四条正则）：`real_output_secret_hits={}` —— 无 IP / 凭据 / 绝对路径 / 命令。
- 提示词见 `raw/a9-prompt.txt`，本身不含任何内网信息。
- stderr 附一条 CLI 自身噪声：`[claude-code:unrecognized_model] {"model":"deepseek-v4-flash",...}`，不影响 stdout 有效性。

## 6. 两个产品缺口（不改代码，只记台账）

**G-A9-1（P1，功能缺口）L3 不真正调用模型。**
`AiPanelService.execute()` 确认后只做 `audit.record("AI_EXECUTE", ...)` 并返回文案；`application/ai/` 全包 `grep -E "ProcessBuilder|Runtime\.|exec\("` 结果为 **(none)**。`AiProcessManager` 只提供 `buildCommand/timeoutSeconds/terminate` 三个「准备动作」。因此「AI 发起执行」当前是**审计占位**，没有端到端模型调用链。

**G-A9-2（P1，契约缺口）构造的 argv 与真实 CLI 不兼容。**

```
产品构造: claude --print --output-format text --prompt-file /path/a9-prompt.txt
真实 CLI: error: unknown option '--prompt-file'   (rc=1)
可用写法: claude -p '<prompt>' --output-format text  (rc=0，40 字符)
```

`--prompt-file` 并非 Claude Code 的参数；代码若真接入，第一次调用即失败。另注：探测名单里的 `codex` 契约也不同（`codex exec "<prompt>"`，且需 `--skip-git-repo-check` 才能在工作区外跑），**同一套 argv 模板套多个 CLI 不成立**，需按 CLI 分别构造。

## 7. 复现

```bash
cd ~/robot-autotest/docs/evidence/v2.0/a9-ai-call
python3 probe.py          # 需后端 8001 已起、~/.config/robot-autotest/env 存在
cat raw/a9-result.json | python3 -m json.tool | head -40
```

`probe.py` 只读产品状态、开会话/建议/执行/终止（都会写审计，**不产生业务数据**）、直查 `audit_log`、跑真实 CLI；不删改任何 run/report。

## 8. 证据清单

| 文件 | 内容 | 关键值 |
|---|---|---|
| `raw/a9-result.json` | 17 项判据全量读数 | `16 true / 1 false`；`failed=[product_argv_matches_real_cli]` |
| `raw/a9-real-model-output.txt` | 1 条真实模型输出（脱敏） | 40 字符，敏感串 0 |
| `raw/a9-prompt.txt` | 提示词原文 | 无内网信息 |
| `probe.py` | 取证脚本 | 只读 + 审计写入 |

## 9. 边界（不要读作通过）

- 本轮**未**验证「CLI 不可用时」的产品行为：实际 `codex` 可见 → `mode=NORMAL`。降级分支只有代码路径与既有单测支撑，属 **PARTIAL 级证据**，不是实跑降级。
- 真实模型调用是「外部 CLI 能力」证明，**不代表产品具备该能力**（缺口 G-A9-1/2 未修）。
- 未验证多轮上下文、超时（60s）、终止正在跑的模型进程（`terminate(null)` 传 null，无进程可杀）。
