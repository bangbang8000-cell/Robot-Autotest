# v3.0.1 证据包（W5：覆盖扩量）

> 日期 2026-11-07 ｜ 对应规划 `docs/plan/中期开发功能规划-20261107.md` §3 W5
> 结论：**W5 全部落地**。门禁 `bash scripts/gates.sh` **`GATES_RESULT FAILS=0`（01–18 全 PASS）**；
> W5 后端定向 **28 用例全绿**；前端定向 **21 用例全绿**；示例用例 **6 条写入 `robot_file`、本地 12 tests 全过**；
> 浏览器取证 `W5_CAPTURE PASS`（16 项断言）。原始件见同目录 `raw/`。

## W5.1 示例用例（5–10 条写 robot_file）

`examples/` 下 6 个纯离线 Robot 套件，全部带 `Metadata CASE_ID / CASE_KIND` 与 `Force Tags sample`：

| 文件 | 内容 | 本地实跑 |
|---|---|---|
| `01_quick_check.robot` | 变量与断言 | 2 passed |
| `02_list_ops.robot` | 列表增删查（`Collections`） | 2 passed |
| `03_math.robot` | 算术与取整 | 2 passed |
| `04_string_ops.robot` | 字符串替换/分割（`String`） | 2 passed |
| `05_dict_ops.robot` | 字典键值（`Collections`） | 2 passed |
| `06_timing.robot` | 时间戳与格式化 | 2 passed |

写入走平台既有 `PUT /api/projects/{id}/files`（`tools/seed_sample_cases.py`），不直插数据库：
写前 GET 比对内容，相同则 `skip`，因此可反复执行。实测 6 条写入，后续修改 3 条 → `version 2`；
本次复跑 `SEED_RESULT PASS written=0 skipped=6 total=6`（幂等）。

## W5.2 计划编排冲突检测 + 批量应用

| 能力 | 说明 | 代码 |
|---|---|---|
| 四类冲突 | `INVALID_CRON`(FAIL) / `DUPLICATE_TARGET`(WARN) / `SAME_SLOT`(WARN) / `DISABLED_DEPENDENCY`(FAIL) | `application/plan/PlanConflictService.java` |
| 同刻聚合 | 同一组计划在未来多个触发点都会撞上，按「参与撞点的计划集合」聚合成一条，只报最早时刻，避免面板被逐点刷满 | 同上 `firstSlot` |
| 只读检测 | `GET /api/plans/conflicts?projectId=`，不自动改计划 | `api/plan/PlanAssistantController.java` |
| 批量启停 | `POST /api/plans/batch`，`@Audited("schedule:batch")` + `@Transactional`；PAUSED 清 `next_fire_at`，ENABLED 重算并置 `last_fire_at`；单条脏 id 不中断整批，已同状态返回 `changed=false` | `application/plan/PlanService.batchStatus()` |
| 前端 | 计划页底部「编排冲突」卡片：按严重级左侧色条、勾选组合、批量暂停/启用、回执「成功 N 条 / 未变更或失败 M 条」 | `views/PlansView.vue` |

检测与批量刻意分成两个动作：自动修复会把用户没想动的计划一起停掉，事故成本远高于多点一次按钮。

## W5.3 跨 run 失败聚类

`GET /api/failure-clusters/recent?projectId=&runs=5`（`runs` 1..20）把最近 N 轮的失败行交给同一套
归一化规则合并，返回作用域 `runId="recent:N"`。前端失败聚类页新增口径切换：

- **单轮**：沿用原 `runId` 查询，可按簇重跑；
- **最近 N 轮**：不要求输入 runId，摘要显示「最近 N 轮共 X 条失败，合并成 Y 个类别」；
  跨轮簇**禁用重跑**（后端按簇重跑要求单轮 runId，跨轮簇合并了多轮用例，拿任意一轮重跑会破坏统计口径）。

## 验证读数

| 项 | 读数 | 原始件 |
|---|---|---|
| 门禁 | `GATES_RESULT FAILS=0`，01–18 全 PASS | `raw/gates-full.log` |
| 后端定向 | 28 tests / 0 failures（PlanConflictService 4、PlanServiceBatch 5、FailureClusterService 17、FailureClusterController 2） | `raw/backend-w5-tests.log` |
| 前端定向 | 21 tests / 0 failures（PlansView 14、FailureClustersView 7） | `raw/frontend-w5-tests.log` |
| 示例用例播种 | `SEED_RESULT PASS written=0 skipped=6 total=6`（幂等复跑） | `raw/seed-sample-cases.log` |
| 冲突接口 | HTTP 200，`count=2`，类型 `DUPLICATE_TARGET,SAME_SLOT`（项目 29 的 3 条同 cron 演示计划 + 其他同目标计划） | `raw/plans-conflicts-29.json` |
| 跨轮聚类接口 | HTTP 200，`runId=recent:5` | `raw/failure-clusters-recent-29.json` |
| 浏览器取证 | `W5_CAPTURE PASS`，16 项断言：面板行数与接口一致、全选显示「已选 N 条计划」、跨轮不需要 runId、跨轮重跑全禁用、无横滚、无 JS 异常 | `raw/ui-measure.json` + 两张 1440 截图 |

> 取证脚本刻意**不点「暂停所选计划」**：项目 29 的计划是共用演示数据，取证不应改变其状态。
> 批量接口正确性由 `PlanServiceBatchTest` / `PlansView.spec` 覆盖。

## 过程中修掉的两个实际问题

1. **`SAME_SLOT` 逐触发点重复报**：两条计划在 3 个未来触发点全撞上时会产出 3 条同名冲突，
   面板看上去像 3 个独立问题。改为按计划集合聚合、只报最早时刻，`PlanConflictServiceTest` 断言回到 2 条。
2. **批量启用后 `nextFireAt` 预期值算错**：测试原先期望 `02:00Z`，实际 cron 是 `Asia/Shanghai` 的 02:00，
   在 `NOW=00:00Z`（上海 08:00）时应为次日 02:00+08 = `18:00Z`。这是测试期望错误，不是实现缺陷，
   已在测试里写明换算过程。

## 复跑命令

```bash
cd ~/robot-autotest
set -a && . ~/.config/robot-autotest/env && set +a
bash scripts/gates.sh                                    # 期望 GATES_RESULT FAILS=0
python3 tools/seed_sample_cases.py                       # 示例用例写 robot_file（幂等）
cd backend && mvn -o test -Dtest='PlanConflictServiceTest,PlanServiceBatchTest,FailureClusterServiceTest,FailureClusterControllerTest'
cd ../frontend && npx vitest run src/views/PlansView.spec.ts src/views/FailureClustersView.spec.ts
node scripts/w5-capture.mjs                              # 浏览器取证（需 Chrome :9222）
```
