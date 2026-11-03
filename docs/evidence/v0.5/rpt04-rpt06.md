# RPT-04 / RPT-06 交付证据

- 范围：趋势与性能基线（RPT-04）、产物保留策略与清理（RPT-06）
- 日期：2026-11-03
- 结论：后端全量 251 条测试通过（Failures 0 / Errors 0），前端 build / lint 均退出 0。
- 重要声明：**本文中的接口响应示例为伪造数据，仅用于展示结构；未对真实库执行清理。**真实清理需在运维窗口显式 `dryRun=false` 才会发生。

## 1. 改动清单

### 1.1 后端主代码

| 文件 | 说明 |
|---|---|
| `backend/src/main/java/com/qa/autotest/domain/report/TrendEnv.java` | 环境键（DUT+LINK），趋势断线口径 |
| `backend/src/main/java/com/qa/autotest/domain/report/TrendPoint.java` | 趋势点领域记录 |
| `backend/src/main/java/com/qa/autotest/domain/report/PassRateCount.java` | 通过率聚合原始计数 |
| `backend/src/main/java/com/qa/autotest/domain/report/RetentionCandidate.java` | 清理候选（含三类保护标记） |
| `backend/src/main/java/com/qa/autotest/domain/report/RetentionArtifact.java` | 待清理产物行 |
| `backend/src/main/java/com/qa/autotest/application/repository/TrendRepository.java` | 趋势只读仓储端口 |
| `backend/src/main/java/com/qa/autotest/application/repository/RetentionRepository.java` | 保留策略仓储端口（无删除库记录方法） |
| `backend/src/main/java/com/qa/autotest/application/report/TrendService.java` | 趋势、基线中位数、1.5 倍偏离、通过率聚合 |
| `backend/src/main/java/com/qa/autotest/application/report/RetentionPlanner.java` | 纯函数候选集计算（超龄 / 配额 / 保护 / 最近 N 次） |
| `backend/src/main/java/com/qa/autotest/application/report/RetentionService.java` | dry-run / 执行 / 审计 / 管理员判定 |
| `backend/src/main/java/com/qa/autotest/application/report/ArtifactFileStore.java` | 文件删除端口（测试可替换为内存实现） |
| `backend/src/main/java/com/qa/autotest/application/report/RetentionProperties.java` | `@ConfigurationProperties(prefix="report.retention")` |
| `backend/src/main/java/com/qa/autotest/infrastructure/TrendRepositoryJdbc.java` | 趋势 SQL（全部带 project_id） |
| `backend/src/main/java/com/qa/autotest/infrastructure/RetentionRepositoryJdbc.java` | 候选 / 产物 / 标记 purged SQL |
| `backend/src/main/java/com/qa/autotest/infrastructure/ArtifactFileStoreImpl.java` | 只删登记路径，失败抛异常不改 availability |
| `backend/src/main/java/com/qa/autotest/infrastructure/RetentionScheduler.java` | 每日 `@Scheduled`，默认关闭 |
| `backend/src/main/java/com/qa/autotest/api/report/ReportTrendController.java` | 趋势 + 通过率接口 |
| `backend/src/main/java/com/qa/autotest/api/report/ReportRetentionController.java` | 清理接口（dryRun 默认 true） |
| `backend/src/main/resources/application.yml` | `report.retention.*` 缺省配置 |
| `db/migration/V5_4__artifact_pin.sql` | `run_artifact.pinned TINYINT NOT NULL DEFAULT 0` |
| `db/down/V5_4__artifact_pin.down.sql` | 回滚 pinned 列 |

### 1.2 前端

| 文件 | 说明 |
|---|---|
| `frontend/src/api/reports.ts` | 新增 trend / passRate / retention 三个方法与类型 |
| `frontend/src/components/report/trendChart.ts` | SVG 折线绘图类型 |
| `frontend/src/components/report/TrendChart.vue` | 自写 SVG 折线（无图表库）：断线、标黄、点击跳转、空态 |
| `frontend/src/views/ReportDetailView.vue` | 「趋势」区块：用例选择、基线提示、耗时折线、通过率按天/周切换 |
| `frontend/src/views/ReportsView.vue` | 管理员「清理产物」：dry-run 清单 + 二次确认后执行 |
| `frontend/src/locales/zh-CN.ts` / `en-US.ts` | 趋势与清理中英文案 |

### 1.3 测试

| 文件 | 条数 |
|---|---|
| `backend/src/test/java/com/qa/autotest/application/report/TrendServiceTest.java` | 12 |
| `backend/src/test/java/com/qa/autotest/application/report/RetentionPlannerTest.java` | 13 |

## 2. 趋势接口示例（**伪造数据**）

> 以下 JSON 为手写的结构示例，不是本机实跑响应；用于说明字段口径。

`GET /api/reports/RUN-003/trend?projectId=3&caseKey=Suite.RDMA-BW-001&limit=5`

```json
{
  "code": 0,
  "codeName": "OK",
  "message": "OK",
  "data": {
    "caseKey": "Suite.RDMA-BW-001",
    "currentEnvKey": "DUT=GPU-01;LINK=mlx5_0",
    "baselineMs": 184000,
    "deviated": true,
    "points": [
      { "runId": "RUN-001", "startedAt": "2026-10-30T02:00:00Z", "verdict": "PASS", "durationMs": 181000, "envKey": "DUT=GPU-01;LINK=mlx5_0", "deviated": false },
      { "runId": "RUN-002", "startedAt": "2026-10-31T02:00:00Z", "verdict": "PASS", "durationMs": 187000, "envKey": "DUT=GPU-01;LINK=mlx5_0", "deviated": false },
      { "runId": "RUN-003", "startedAt": "2026-11-01T02:00:00Z", "verdict": "PASS", "durationMs": 301000, "envKey": "DUT=GPU-01;LINK=mlx5_0", "deviated": true }
    ],
    "series": [
      {
        "envKey": "DUT=GPU-01;LINK=mlx5_0",
        "points": [
          { "runId": "RUN-001", "startedAt": "2026-10-30T02:00:00Z", "verdict": "PASS", "durationMs": 181000, "envKey": "DUT=GPU-01;LINK=mlx5_0", "deviated": false },
          { "runId": "RUN-002", "startedAt": "2026-10-31T02:00:00Z", "verdict": "PASS", "durationMs": 187000, "envKey": "DUT=GPU-01;LINK=mlx5_0", "deviated": false },
          { "runId": "RUN-003", "startedAt": "2026-11-01T02:00:00Z", "verdict": "PASS", "durationMs": 301000, "envKey": "DUT=GPU-01;LINK=mlx5_0", "deviated": true }
        ]
      },
      {
        "envKey": "DUT=GPU-02;LINK=mlx5_0",
        "points": [
          { "runId": "RUN-004", "startedAt": "2026-11-01T03:00:00Z", "verdict": "PASS", "durationMs": 186000, "envKey": "DUT=GPU-02;LINK=mlx5_0", "deviated": false }
        ]
      }
    ]
  }
}
```

字段口径：

- `baselineMs`：当前环境（DUT+LINK）历史耗时的中位数，**排除本轮**；无历史时为 `null`。
- `deviated`：本轮耗时 > 中位数 × 1.5；**等于 1.5 倍不算**（整数比较 `2*d > 3*base`）。
- `series`：按 `envKey` 分组，环境变化处天然断开；`points` 为全局升序。
- 所有 SQL 均带 `project_id`（`test_result` join `run`），跨项目不可见。

通过率示例（同样为**伪造数据**）：

`GET /api/reports/trend/pass-rate?projectId=3&granularity=week&limit=12`

```json
{
  "code": 0, "codeName": "OK", "message": "OK",
  "data": {
    "granularity": "week",
    "buckets": [
      { "bucket": "2026-44", "pass": 20, "fail": 4, "skip": 1, "total": 25, "passRate": 0.8 }
    ]
  }
}
```

## 3. 清理 dry-run 示例（**伪造数据**）

> 以下响应为手写示例，**未在本机或本地 WSL 执行过真实清理**；真实执行需管理员显式 `dryRun=false`，且默认开关 `report.retention.enabled=false`。

`POST /api/reports/retention/run?projectId=3&dryRun=true`

```json
{
  "code": 0, "codeName": "OK", "message": "OK",
  "data": {
    "dryRun": true,
    "runCount": 3,
    "totalBytes": 734003200,
    "projectBytes": 53687091200,
    "keptRecentRuns": 20,
    "deletedFiles": 0,
    "items": [
      { "runId": "RUN-OLD-001", "bytes": 314572800, "reason": "expired" },
      { "runId": "RUN-OLD-002", "bytes": 209715200, "reason": "expired" },
      { "runId": "RUN-OLD-003", "bytes": 209715200, "reason": "quota" }
    ]
  }
}
```

口径：

- 候选 = 超过 `max-days: 90` 或项目占用超 `max-project-gb: 50` 的 run 产物。
- 三类保护不入选：有 `export_record` / `run_artifact.pinned=1` / `note` 含 `p0_evidence`。
- 最近 `keep-recent-runs: 20` 次永不清理。
- dry-run **不删文件、不改 availability、不写审计**，`deletedFiles=0`。
- 执行时只删除 `run_artifact.path` 登记的文件 → `availability='purged'` → 写 `audit_log`（`artifact_purge`，含 runId/字节数/操作人）；**绝不删除 `run` / `test_result` 行**。

## 4. 测试与构建真实计数

### 4.1 后端（`mvn -B test`）

日志：`docs/evidence/v0.5/mvn-test-rpt04-rpt06.log`

- 全量：`Tests run: 251, Failures: 0, Errors: 0, Skipped: 0`
- `RetentionPlannerTest`：`Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`
- `TrendServiceTest`：`Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`
- `LayerRulesTest`（ArchUnit）：`Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`
- `BUILD SUCCESS`，耗时 1:09 min，退出码 0。
- 备注：首次采集时既有用例 `RunIdGeneratorTest.并发不重复` 出现一次随机碰撞（expected 1000 / actual 999），
  重跑即通过；该用例未改动，属既有偶发问题（见 §5 风险 8）。

### 4.2 前端

日志：`docs/evidence/v0.5/frontend-rpt04-rpt06-build.log`、`docs/evidence/v0.5/frontend-rpt04-rpt06-lint.log`

- `npm run build`（`vue-tsc --noEmit && vite build`）：退出码 **0**，构建 13.85 s，123 modules。
- `npm run lint`（`eslint .`）：退出码 **0**，无告警输出。

## 5. 未覆盖项与风险

1. **迁移需手工执行**：`spring.flyway.enabled=false`，新增的 `V5_4__artifact_pin.sql` 不会随应用启动自动落库，必须在部署窗口执行 `make db-migrate`；未执行前调用清理接口会因 `run_artifact.pinned` 不存在而报 SQL 错误。
2. **真实大产物清理未在本地 WSL 执行**：本机仅有单元/行为测试，`ArtifactFileStoreImpl` 的真实文件系统删除未做实机验证；本地 WSL 上建议先在 `dryRun=true` 核对清单，再由管理员在窗口期执行。
3. **清理为破坏性操作**：`report.retention.enabled=false` 默认关闭，接口 `dryRun` 默认 true；执行前仍须人工二次确认，且审计写入与原文件删除不是同一事务（删文件成功但审计失败时，文件已不可恢复）。
4. **趋势按最近 N 次跨环境取点**：`limit` 限制的是用例最近 N 次运行（可能跨环境），前端按 `envKey` 分组断线；若某环境历史点被其他环境挤出 N，则该环境线只显示剩余点，不会回补更早历史。
5. **通过率统计口径**：只统计 `run_status IN ('passed','failed')` 的 run，`created/running/cancelled` 不计入分母；bucket 使用数据库时区（Asia/Shanghai），跨时区部署需复核。
6. **`run_artifact.project_id` 历史空值**：候选 / 产物 / 总量 / purged 更新均改为 join `run` 取 `project_id`，兼容 V4_2 之前 `project_id` 为 NULL 的旧行，不会漏清理也不会跨项目误删。
7. **前端仅 build/lint 验证**：未做浏览器端交互回归（点选跳转、按天/周切换、标黄提示）。
8. **定时任务未实跑**：`RetentionScheduler` 仅在 `enabled=true` 且非 test profile 时装配，本次未触发真实调度。
9. **既有偶发用例**：`RunIdGeneratorTest.并发不重复` 首次全量运行出现一次随机碰撞（1000 期望 / 999 实际），
   重跑通过；未修改该测试断言，后续 CI 若偶发失败需单独治理。
