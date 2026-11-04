# PRJ-04 报告结果导向 — 证据包

> 2026-11-04 · 状态 `待验` · 结论：接口与真库 SQL 逐项一致，界面亮/暗可读，报告行可点进详情。
> 验收人只需看本文件 + `prj04-api.log` + 3 张截图，即可复核每一个数。

## 一、环境

| 项 | 值 |
|---|---|
| 宿主 | WSL2 Ubuntu-24.04（权威副本 `~/robot-autotest`） |
| 后端 | `java -jar backend/target/robot-autotest-backend-0.1.0-SNAPSHOT.jar --server.port=8081`，`/api/_ping` → `{"status":"UP"}` |
| 前端 | `vite` :5273（HMR），HTTP 200 |
| 数据库 | MySQL `robot_dev`（V7_1 / 44 表） |
| 账号 | `qa-ui`（演示库 admin，用于接口与界面走查） |
| 截图 | 1920×1080，`agent-browser` 全页截图，亮 / 暗各一张 |

## 二、改动清单

后端（5 个新文件 + 1 个测试）：

| 文件 | 作用 |
|---|---|
| `domain/report/ReportDigest.java` | 摘要 record；`passRateBp` / `coverage` 两个纯函数 |
| `application/repository/ReportDigestRepository.java` | 4 个查询方法 |
| `infrastructure/ReportDigestRepositoryJdbc.java` | 4 段 SQL，全部 `TenantGuard.requireProjectScope(sql, table)` |
| `application/report/ReportDigestService.java` | 权限 `EXECUTION.READ`、窗口夹取 1~90、最近 5 / Top 10 / 趋势 60 |
| `api/report/ReportDigestController.java` | `GET /api/projects/{projectId}/report-digest?days=30` |
| `test/.../application/report/ReportDigestTest.java` | 窗口夹取、覆盖率夹取、通过率口径 3 条 |

前端（3 个文件）：

| 文件 | 作用 |
|---|---|
| `api/overview.ts` | `ReportDigest` 系列类型 + `digestApi.load(projectId, days)` |
| `views/OverviewView.vue` | 「报告结果」区块：覆盖事实行 / 通过率柱 / 最近报告表（运行号可点进详情）/ 失败 Top10 空态；两个接口 `Promise.allSettled` 并行，互不拖垮 |
| `styles/main.css` + `locales/{zh-CN,en-US}.ts` | `.digest-facts` / `.rate-bars` / `.rate-bar.{pass,mixed,empty}`；14 个新键（中英各一份） |

## 三、接口实证（`prj04-api.log`）

`GET /api/projects/1/report-digest?days=30` → **HTTP 200**，与直接 SQL 逐项对照：

| 读数 | 接口 | SQL | 一致 |
|---|---|---|---|
| 最近完成报告 | 1 份：`RUN-20261104155814-952426` | `run` 表 1 行 `finished` | ✅ |
| 该报告 PASS / FAIL / SKIP | 2 / 0 / 0，通过率 100% | `total_tests=2, passed_tests=2` | ✅ |
| 失败用例 Top10 | 0 行（空态） | `verdict='FAIL'` 0 行 | ✅ |
| 用例总数 | 226 | `test_case WHERE project_id=1 AND status=1` = 226 | ✅ |
| 已覆盖 | 0 | `case_code = case_key` 精确匹配 = 0 | ✅ |
| 从未执行 | 226 | 226 − 0 | ✅ |
| 趋势点 | 1 点（100%） | 1 条 `finished` run | ✅ |
| `days=0` / `days=999` | `windowDays` 1 / 90 | — | ✅ 夹取生效 |
| 无 token | 401 | — | ✅ |

**覆盖率为什么是 0**：该冒烟运行的 `test_result.case_key` 是 `run-RUN-…Bench-1-A` 这类**脚本步骤名**，
与台账 `TN-IMP-001~226` 不同源。本包按「精确匹配」统计，因此覆盖 0 是**真实结果**而不是统计错误；
台账↔脚本绑定缺失已登记在 WBS #73 `EXEC-01`。界面加了一行口径说明（见截图），不把 0 伪装成 100%。

## 四、界面实证（3 张截图）

| 文件 | 内容 |
|---|---|
| `01-overview-digest-light.png` | 亮色总览：四区 +「报告结果」（覆盖 0/226 · 从未执行 226 · 口径说明 · 通过率柱 1 根 · 最近报告表含链接 · 失败 Top10 空态） |
| `02-overview-digest-dark.png` | 暗色同上，对比度全部 ≥4.5:1（`check_contrast.py` 80/80） |
| `03-report-detail-from-digest.png` | 点击最近报告行的运行号后落到 `/reports/RUN-20261104155814-952426`（TC-PRJ-10 实测） |

## 五、门禁读数（`prj04-checks.log`）

| 项 | 结果 |
|---|---|
| `mvn -B test` | **466 / 0 / 0**（新增 3 条，原 463） |
| `vue-tsc --noEmit` | exit 0 |
| `npm run lint` | exit 0 |
| `npm run build` | exit 0（`OverviewView-BC-mPiJD.js` 12.66 kB） |
| `check_tokens.py` | 0 悬空 var，331 处 px 全在白名单 |
| `check_states.py` | 8 类元素三态 + transition 齐备 |
| `check_contrast.py` | 4 组 × 20 项 = **80/80 PASS** |
| `check_i18n.py` | 468 / 468 键，0 硬编码中文，0 同值漏译 |

## 六、口径与边界（验收时请按此判定）

1. **只统计 `run_status IN ('finished','failed')`**，运行中 / 排队 / 取消不进任何读数。
2. **无明细 → 通过率为 `null`**（界面显示 `—`），全 SKIP → `0`。两者必须可区分，这是 OVW-01 定下的口径。
3. **覆盖率是缺口上界**：`case_code = case_key` 精确匹配，脚本步骤名不计入；界面上有说明文字。
4. 窗口 `days` 默认 30，夹取到 1~90；趋势最多 60 点（按完成时间正序）。
5. 未做：`ReportDigestIT`（改为真库 API↔SQL 对账，理由见 DEV-PLAN §10.7 偏差 1）；
   「零运行项目」空态截图（演示库造不出，文案已实现）。

## 七、复现命令

```bash
# 后端（已常驻，重启见 .tmp 脚本模式）
curl -s --noproxy '*' -H "Authorization: Bearer <token>" \
  'http://127.0.0.1:8081/api/projects/1/report-digest?days=30'

# 对账（令牌不入库，跑前先 export）
QA_TOKEN='<登录接口返回的 token>' bash ~/robot-autotest/docs/evidence/v0.2/prj04/prj04-api.sh
```
