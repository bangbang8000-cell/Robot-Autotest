# CI 接入指南（GitHub Actions → 平台 → 回写）

**归属** PLN-03 ｜ **版本** v0.6 ｜ **接口实现** `POST /api/ci/trigger`

本指南只描述**已实现**的能力。标记「未验证」的部分表示代码路径存在但未在真实 GitHub 上跑过，
不要把它当成已验证事实引用。

---

## 1. 整体链路

```
GitHub push
   │
   ▼
GitHub Actions（自托管 runner，必须能访问 10.202.54.x）
   │  curl POST /api/ci/trigger  (Bearer 项目 API token)
   ▼
平台后端 :8081  ──► 幂等检查 (plan_id, commit_sha)
   │                  ├─ 重复 → 202 + 已有 runId + duplicated=true
   │                  └─ 新建 → 202 + runId（执行异步派发，不在接口里等）
   ▼
run 结束（passed / failed）
   │
   ▼
回写 GitHub commit status（可选，默认关闭）
```

**为什么必须自托管 runner**：托管 runner 在 GitHub 的公网网段，到不了 `10.202.54.x`
被测设备与内网平台。自托管 runner 建议落在 149（见 ADR-0004）。

---

## 2. 前置条件

| 项 | 要求 |
|---|---|
| 平台 | 后端 `:8081` 可从 runner 访问；前端 `:5173`（或生产地址）可从 GitHub 打开报告页 |
| 项目 | 已在平台创建，且该项目的执行计划（`planId`）已配置套件 |
| 令牌 | 项目 API token，`token_kind='api'`、绑定单个 `project_id`、scope 含 `ci:trigger` |
| 迁移 | 数据库已到 **V6_3**（`api_token.project_id` / `scopes`、`ci_trigger` 表） |

---

## 3. 申请令牌与 scope

本版**没有令牌签发页面或接口**，由管理员直接在平台库插入（明文只在终端出现一次）：

```bash
# 1) 生成 32 字节随机明文（只显示这一次）
TOKEN=$(openssl rand -hex 32)
echo "$TOKEN"

# 2) 算 SHA-256 十六进制（平台只存哈希）
HASH=$(printf '%s' "$TOKEN" | sha256sum | awk '{print $1}')

# 3) 插入项目 API 令牌（把 1 换成真实的 project_id / user_id）
mysql robot_dev -e "
INSERT INTO api_token (user_id, name, token_hash, token_kind, project_id, scopes, expires_at)
VALUES (1, 'ci-github-actions', '$HASH', 'api', 1, 'ci:trigger', NULL);
"
```

要点：

- **scope 只有 `ci:trigger`**；留空表示「无 scope」，不是「全部权限」。
- `project_id` 必须非空；未绑定项目的令牌调用 CI 接口一律 **403**。
- `expires_at=NULL` 表示长期有效；建议按季度轮换：新插一行 → 改 Actions secret → 吊销旧行。
- 吊销：`UPDATE api_token SET revoked_at = NOW(3) WHERE name='ci-github-actions';`
- 不要把明文写进仓库、Issue、日志或截图。GitHub Actions 侧存 Repository secret。

---

## 4. 调用接口

```bash
curl -i -X POST "http://<平台地址>:8081/api/ci/trigger" \
  -H "Authorization: Bearer $QA_CI_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
        "planId": 12,
        "commitSha": "'"$GITHUB_SHA"'",
        "ref": "'"$GITHUB_REF"'",
        "build": "SW-2026.11-rc1",
        "repo": "huanchen811509-silence/Robot-Autotest"
      }'
```

请求体：

| 字段 | 必填 | 说明 |
|---|---|---|
| `planId` | 是 | 平台执行计划 ID，必须属于令牌绑定的项目 |
| `commitSha` | 是 | 1~64 位十六进制，统一转小写后作为幂等键 |
| `ref` | 否 | 如 `refs/heads/main`，进报告 metadata `CI_REF`（≤40 字符） |
| `build` | 否 | 缺省取 `commitSha` 前 8 位，进报告 metadata `BUILD`（≤40 字符） |
| `repo` | 否 | `owner/repo` 或群晖 `ssh://...`，用于判定回写目标 |

响应（**成功与重复都是 202**）：

```json
{
  "code": 200,
  "data": {
    "runId": "RUN-20261103-000123",
    "duplicated": false,
    "writeback": "pending"
  }
}
```

- `duplicated=true`：同 `(planId, commitSha)` 已触发过，直接返回已有 `runId`，**不新建 run**。
- `writeback` 是本次触发的回写预期：`pending` / `disabled` / `not_supported`。
  实际结果在 run 结束后落到 `ci_trigger.writeback_status`。

**接口不等待执行结果**：run 落库后立即返回，执行在平台单线程派发器里异步进行。
Actions 侧如需等待结论，请轮询 `/api/runs/{runId}`（注意串行队列可能排队）。

---

## 5. GitHub Actions 示例（照抄可用）

```yaml
name: trigger-qa

on:
  push:
    branches: [main]

jobs:
  trigger:
    # 必须自托管：托管 runner 到不了 10.202.54.x
    runs-on: [self-hosted, linux, x64]
    steps:
      - name: Trigger platform run
        env:
          QA_PLATFORM_URL: ${{ vars.QA_PLATFORM_URL }}
          QA_CI_TOKEN: ${{ secrets.QA_CI_TOKEN }}
        run: |
          curl -fsS -X POST "$QA_PLATFORM_URL/api/ci/trigger" \
            -H "Authorization: Bearer $QA_CI_TOKEN" \
            -H "Content-Type: application/json" \
            -d "{\"planId\":12,\"commitSha\":\"${GITHUB_SHA}\",\"ref\":\"${GITHUB_REF}\",\"repo\":\"${GITHUB_REPOSITORY}\"}"
```

- `QA_PLATFORM_URL` 放 Repository variable，例如 `http://10.202.54.149:8081`。
- `QA_CI_TOKEN` 放 Repository secret，值就是第 3 步生成的明文。
- 不要用 `${{ secrets.* }}` 直接拼进 `run:` 之外的地方，也不要 `set -x`。

---

## 6. 幂等与限流

| 规则 | 行为 |
|---|---|
| 幂等键 | `UNIQUE(plan_id, commit_sha)`；重复触发返回 **202** + 已有 `runId` + `duplicated=true`，不新建 run |
| 并发同 commit | 两个请求同时插入时，唯一键冲突被捕获，回读胜者并返回 `duplicated=true`，不会 500 |
| 限流 | **同一令牌每分钟 ≤10 次**（`ci.trigger-per-minute`），超出 **429** |
| 多实例 | 限流是单实例内存计数；本版单实例部署，若扩多实例需换共享存储（见代码注释） |

Actions 里对 202 视为成功即可；429 表示提交过于频繁（例如批量 push），需要退避重试。

---

## 7. 回写

### 7.1 GitHub commit status（默认关闭）

| 配置 | 值 |
|---|---|
| `ci.writeback.enabled` | 默认 `false`；开启用环境变量 `CI_WRITEBACK_ENABLED=true` |
| `GITHUB_STATUS_TOKEN` | **只从环境变量读取**，不落配置文件、不落库、不落日志 |
| `ci.writeback.target-base-url` | 报告页基址，`target_url = 该值 + /reports/{runId}` |
| `ci.writeback.api-base-url` | 默认 `https://api.github.com`（GitHub Enterprise 可改） |
| `ci.writeback.context` | 默认 `qa-platform/ci` |

行为：

- run 结束 **FINISHED → `state=success`**，**FAILED → `state=failure`**；取消不回写。
- 调用 `POST /repos/{owner}/{repo}/statuses/{sha}`，`target_url` 指向报告页。
- **回写失败绝不改变 run 结论**：只写 `ci_trigger.writeback_status='failed'` 与 `writeback_error`。
- 未配置令牌：只记 `skipped`（`writeback_error='token_missing'`），不发请求。
- 默认关闭：只记 `skipped`（`writeback_error='disabled'`），不发请求。

> **未验证**：仓库内没有可用的 `GITHUB_STATUS_TOKEN`，因此「真实 GitHub commit status 回写成功」
> **未验证**。当前只有伪 HTTP 客户端的单测（passed→success / failed→failure / 失败不改结论）。
> 首次启用时请先用一个测试仓库验证 status 是否出现在 commit 上。

### 7.2 群晖 Git：明确不支持

群晖 `git server` 只有 SSH，没有 HTTP/Web UI/webhook，**无法接收 commit status**。

- 代码判定 `ssh://` / `git@` / 含 `synology`、`syno.` 的仓库为群晖；
- 回写时直接返回 **`not_supported`** 并写审计（`CI_WRITEBACK`，`result=not_supported`），
  **不发起任何 HTTP 请求，不假装成功**；
- 触发响应里的 `writeback` 也会显示 `not_supported`。

因此群晖仓库接入 CI 时，只能做到「触发 + 平台内看结论」，GitHub 侧没有状态徽章。

---

## 8. 故障排查

| 症状 | 原因 | 处理 |
|---|---|---|
| **401** `令牌无效或已过期` | 令牌明文与库中 `token_hash` 不匹配、`revoked_at` 非空、`expires_at` 已过 | 重新算哈希核对；轮换令牌 |
| **401** `缺少 Bearer 令牌` | 忘了 `Authorization` 头，或写成 `Token xxx` | 用 `Bearer <token>` |
| **403** `令牌未绑定项目` | 插入时 `project_id=NULL` | 补 `project_id`，或用新令牌 |
| **403** `令牌缺少 ci:trigger scope` | `scopes` 为空或写错 | 改成 `'ci:trigger'` |
| **403** `计划不存在或不属于该令牌绑定的项目` | `planId` 属于别的项目，或不存在 | 核对项目的计划 ID；不会泄露计划是否存在 |
| **400** `commitSha 必须是 1~64 位十六进制` | 传了分支名/短标签 | 传 `${GITHUB_SHA}` |
| **429** | 同令牌每分钟超过 10 次 | 退避重试；批量提交合并为一次触发 |
| **202 但 `duplicated=true`** | 同计划同 commit 已触发过 | 正常；要重跑请换 commit 或换 plan |
| run 长时间 Pending | 平台单 worker 串行队列 | 等或取消；不是 CI 接口问题 |
| **回写 failed**，run 仍 passed | 设计如此：回写失败不改变结论 | 看 `ci_trigger.writeback_error`；修 token/权限后手工重试（当前无自动重试） |
| 回写 `not_supported` | `repo` 是群晖 SSH 地址 | 预期行为；群晖不支持状态回写 |
| 回写 `skipped / token_missing` | 未配置 `GITHUB_STATUS_TOKEN` | 配环境变量并重启后端 |
| 回写 `skipped / disabled` | `ci.writeback.enabled=false` | 显式开启 |
| 回写 `github_http_403` | GitHub token 无 `repo:status` 权限或仓库不对 | 重新签发 token，确认 `repo` 字段 |

---

## 9. 已验证 / 未验证清单

| 项 | 状态 |
|---|---|
| 触发接口、幂等、限流、scope/项目校验 | ✅ 单测覆盖（19 个用例，见 `docs/evidence/v0.6/pln03-pln04.md`） |
| 迁移 V6_3 应用到 dev 库 | ✅ `flyway_schema_history` 到 6.3 |
| GitHub 真实 commit status 回写 | ⚠️ **未验证（无 GITHUB_STATUS_TOKEN）** |
| GitHub Actions 端到端触发 | ⚠️ **未在真实 GitHub 上跑过**（示例基于接口契约编写） |
| 群晖回写 | ❌ **不支持**（代码返回 `not_supported`） |
