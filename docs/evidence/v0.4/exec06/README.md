# EXEC-06 全量日志下载 — 证据

**包号** WBS #87 ｜ **状态** `待验`（AI 不改 `已完成`）｜ **日期** 2026-11-05
**需求依据** `docs/release/v0.4-execution/PRD.md` FR-v0.4-21（完整日志可下载）

## 1. 要解决的问题

实时日志走 `RunLogService` 的内存环形缓冲 + WebSocket，`recent` 接口最多回 2000 行 ——
这是「看当前在跑什么」的窗口，不是「事后取证」的载体。PRD FR-v0.4-21 要求完整日志可下载，
而完整日志其实早已由 `PabotOrchestrator` 全量写在 `<runDir>/run.log`，缺的只是：

1. 一个稳定、不猜目录的定位口径；
2. 一个带租户校验的下载端点；
3. 运行页上能点到的下载入口。

## 2. 实现

| 文件 | 改动 |
|---|---|
| `application/execution/RunLogFileService.java`（新增） | `locate(RunRecord)`：`run.artifactsDir` 为空 → 404；`<runDir>/run.log` 不是普通文件 → 404（消息含期望路径与成因）；`download` 返回 `FileSystemResource` |
| `api/execution/RunController.java` | 新增 `GET /runs/{runId}/log/download`：先 `runs.get(projectId, runId)` 做租户校验，再以 `Content-Disposition: attachment; filename="run-<runId>.log"` + `text/plain;charset=UTF-8` 返回文件 |
| `frontend/src/api/execution.ts` | 新增 `downloadLog(projectId, runId)`，走 `requestBlob`（带认证头） |
| `frontend/src/views/RunsView.vue` | 日志面板加「下载完整日志」按钮：blob → object URL → 触发下载 → 释放 URL；成功提示 / 失败走 `ErrorLineView` |
| `frontend/src/locales/{zh-CN,en-US}.ts` | `downloadLog` / `logDownloaded` 两键中英对等 |

### 端点契约

```
GET /api/execution/runs/{runId}/log/download?projectId=<id>

200  Content-Type: text/plain;charset=UTF-8
     Content-Disposition: attachment; filename="run-<runId>.log"
404  JSON 信封 {codeName: "NOT_FOUND", message: "该运行还没有完整日志文件…"}
```

- 文件位置固定为 **`<artifactsDir>/run.log`**，即 `PabotOrchestrator` 写的
  `outputDir.getParent()/run.log`，不在历史目录里做模糊搜索。
- 响应不走 JSON 信封（文件下载语义），出错时才由 `GlobalExceptionHandler` 返回信封。

## 3. 门禁实测

| 门禁 | 结果 | 日志 |
|---|---|---|
| 后端全量单测 | **514 tests / 0 failures / 0 errors**，BUILD SUCCESS | `backend-test.log` |
| 后端集成测试 | **28/28**，BUILD SUCCESS（`DB_NAME=robot_it`） | `backend-it.log` |
| 前端全量测试 | **156 passed**（新增 1 条 RunsView 用例） | `frontend-test.log` |
| `vue-tsc --noEmit` | 0 错误 | `frontend-tsc.log` |
| `vite build` | 成功（仅有既有的 chunk 体积提示） | `frontend-build.log` |
| `scripts/lint.sh` | PASS（含 WBS 自洽、i18n 键对等、像素白名单等六守门） | `lint.log` |
| 改动范围 | 仅工作区留存，**无 git 提交** | `git-status.log` |

## 4. 新增测试（+5：后端 +4、前端 +1）

- `RunLogFileServiceTest`（3 条）：无产物目录 404；`run.log` 缺失 404 且消息含期望路径；
  文件存在时定位到该路径。
- `RunControllerTest`（+1 条）：下载返回 200、`Content-Disposition` 含 `run-RUN-1.log`、
  正文为文件内容；服务抛 404 时状态码映射为 404。
- `RunsView.spec`（+1 条）：打开第二行日志面板后点「下载完整日志」，
  断言 `executionApi.downloadLog` 以 `projectId=1, runId=RUN-BBB` 调用。

## 5. 残余与边界（验收时请重点看）

1. **只有运行开始后才有文件**：`run.log` 由编排器在启动时创建，排队中 / 尚未开始的运行
   下载会得到 404；这是预期行为，不是缺陷。
2. **产物清理即失去下载**：`workspace/` 与 `run.log` 都留在 run 产物目录，目前没有清理策略；
   归档/保留期（RPT-07）落地时需把 `run.log` 一起纳入保留清单，否则日志会随目录清理消失。
3. **入口只在日志面板内**：按钮放在日志面板头部，必须先「查看日志」；列表行上暂无快捷下载。
4. **超大日志**：`FileSystemResource` 由容器流式输出，不会整包读进堆；但没有 Range /
   断点续传，也不做压缩。
5. **实时频道与完整日志的边界没在 UI 文案里说明**：用户可能以为面板滚到底就是全部。
   后续可在面板底部加「仅显示最近 2000 行，完整日志请下载」的提示（未做，避免文案未过审）。
