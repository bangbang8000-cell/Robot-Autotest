# EXEC-07 租约可视化与管理员强制释放 — 证据

**包号** WBS #88 ｜ **状态** `待验`（AI 不改 `已完成`）｜ **日期** 2026-11-05
**需求依据** `docs/release/v0.4-execution/PRD.md` FR-v0.4-09（租约可视化）、FR-v0.4-10（强制释放）

## 1. 要解决的问题

租约（`resource_lease`）本就存在并被执行链路使用，但界面上**看不见**：资源被谁占着、
还剩多久、为什么这次执行一直排队，只能翻库或读日志。FR-v0.4-09 要求资产页能看占用状态，
FR-v0.4-10 要求管理员能在死锁时强制释放，且释放后原任务要被标记 `failed(lease_revoked)`。

## 2. 实现

| 文件 | 改动 |
|---|---|
| `application/execution/LeaseAdminService.java`（新增） | `forceRelease(projectId, resourceKey, confirm)`：`bindProject` 后判管理员（否则 403）→ 取该资源的生效租约（没有则 404）→ `confirm` 必须等于占用者 `runId`（否则 400）→ 仍 `RUNNING` 的原运行置 `FAILED`（`failureClass = failureReason = lease_revoked`）→ 走 `LeaseService.releaseRun`（沿用既有 `@Audited(lease:force-release)` 审计） |
| `api/execution/LeaseController.java` | 新增 `POST /api/execution/leases/force-release?projectId=&resourceKey=&confirm=`，返回 `{runId, runState, releasedLeases}` |
| `frontend/src/api/execution.ts` | 新增 `LeaseView` / `ForceReleaseResult` 类型与 `listLeases` / `forceReleaseLease` |
| `frontend/src/views/AssetsView.vue` | 新增「租约」页签（只读）：资源键 / 占用运行 / 剩余时间（`m s` 人话，0 显示「已到期」）/ 状态；管理员行内「强制释放」，二次确认文案念出占用者与资源；该页隐藏搜索框与「新建」 |
| `frontend/src/locales/{zh-CN,en-US}.ts` | `assets.tabs.leases` + `assets.lease.*` 中英对等 |
| `frontend/src/styles/fonts.css` + `src/assets/fonts/*` | 新增文案的 4 个字（申/租/谁/踩）重新子集化，1011/1011 全覆盖 |

### 端点契约

```
GET  /api/execution/leases?projectId=<id>
200  [{resourceKey, runId, jobId, projectId, acquiredAt, expiresAt, remainingSeconds, state}]

POST /api/execution/leases/force-release?projectId=<id>&resourceKey=<key>&confirm=<runId>
200  {runId, runState, releasedLeases}
403  非管理员
404  该资源没有生效中的租约
400  confirm 不等于占用者 runId
```

- 列表只返回 `lease_state = held`（生效中）的租约；历史租约查审计。
- 强制释放**只摘租约 + 标记运行失败**，不终止已拉起的 pabot 子进程（见残余 1）。

## 3. 门禁实测

| 门禁 | 结果 | 日志 |
|---|---|---|
| 后端全量单测 | **521 tests / 0 failures / 0 errors**，BUILD SUCCESS | `backend-test.log` |
| 后端集成测试 | **28/28**，BUILD SUCCESS（`DB_NAME=robot_it`） | `backend-it.log` |
| 前端全量测试 | **160 passed / 23 files**（新增 4 条 AssetsView 用例） | `frontend-test.log` |
| `vue-tsc --noEmit` | 0 错误 | `frontend-tsc.log` |
| `vite build` | 成功（仅既有 chunk 体积提示） | `frontend-build.log` |
| `scripts/lint.sh` | PASS（含字体子集 1011/1011、WBS 自洽、i18n 键对等） | `lint.log` |
| WBS 自洽 | PASS（88 包 / 74 待验） | `wbs-gate.log` |
| 发布文档一致性 | 见日志 | `release-docs-gate.log` |
| 改动范围 | 仅工作区留存，**无 git 提交** | `git-status.log` |

## 4. 新增测试（+11：后端 +7、前端 +4）

- `LeaseAdminServiceTest`（4 条）：非管理员 403 且租约仍在；无租约 404；`confirm` 不匹配 400；
  成功路径 → 运行 `FAILED`、`failureClass/reason = lease_revoked`、历史含 `lease_revoked`、`releasedLeases = 1`。
- `LeaseControllerTest`（3 条）：列表字段透传；强制释放三个查询参数原样进服务；无租约 404 / confirm 不匹配 400 的错误码映射。
- `AssetsView.spec`（4 条）：租约页签渲染资源/运行/剩余时间且无「新建」与搜索框；
  非管理员看不到按钮；管理员以 `(projectId, resourceKey, runId)` 调用并刷新列表；取消则不发请求。

## 5. 残余与边界（验收时请重点看）

1. **不杀进程**：强制释放摘掉租约并把运行标记为失败，但已拉起的 pabot 子进程仍会跑完
   （本地无进程句柄持久化，跨重启不可杀）。要彻底终止需接 `RunCancellationService` 与子进程管理，另开包。
2. **只列生效中的租约**：`held` 之外的历史租约不在页面上，只能查 `resource_lease_history` / 审计。
3. **非管理员没有任何释放入口**：连按钮都不渲染，避免"点了报 403"。
4. **剩余时间取服务端快照**：`remainingSeconds` 在请求时计算，页面不本地倒计时；刷新才更新。
5. **审计只记录操作（`lease:force-release`）**：被标记失败的运行本身有状态历史（含 `lease_revoked`），
   两处可交叉验证。
6. **实跑待现场**：真实占用 → 强制释放 → pabot 实际行为需验收人在有 DUT 的环境确认（ADR-0013）。
