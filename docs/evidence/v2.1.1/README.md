# v2.1.1 证据包（W1：v2.1 欠账收口）

> 日期 2026-11-07 ｜ 对应规划 `docs/plan/中期开发功能规划-20261107.md` §3 W1
> 结论：W1 六个包全部落地，`bash scripts/gates.sh` **17 步 FAILS=0**，前端 22 项定向用例全绿，
> 后端 46 项定向用例全绿；TC-32 迁移往返扩到 V9_5 后 **67 项断言 OK / FAIL 0**。
> 原始输出见同目录 `raw/`，门禁全量日志在 `docs/evidence/gates/logs/`。

## 逐包结论

| 包 | 做了什么 | 证据 |
|---|---|---|
| **N3 pytest metadata** | `PytestKernel.fromJson` 读 `metadata` 并交给 `MetadataGate`；pytest 项目六必填不再是「只声明不校验」 | `raw/backend-w1-tests.txt`（相关回归含于门禁 05）；代码 `kernel/pytest/PytestKernel.java` |
| **N4 B6 跨环境基线** | 手动 `baselineRunId` 必须与当前运行**同项目同环境**，否则 400 并给中文原因；**绝不静默退回自动基线**（原行为是换基线不告知） | `CompareService.java:27/179–192`；`ReportCompareControllerTest`（门禁 05） |
| **N8 ORCH 六项** | ① 保存组内顺序即重算依赖边（无需再点「应用」）② 边带 `data-source`（GROUP/MANUAL）③ 依赖图 minimap 缩略导航 ④ 预计时长改用历史 P50 ⑤ 簇键截断保留完整键尾，避免误并簇 ⑥ 重跑回填 `retry_of_run_id` | `frontend/src/components/plan/PlanGraph.vue`、`raw/frontend-w1-vitest.txt`（PlanGraph 10 项） |
| **N9 PUBX** | 平台用例集**只读页** `/api/platform/case-sets`（管理员限定，`test_set.project_id IS NULL`）；同步新增 `strategy=ADD_ONLY`（只补平台新增，保留本地标题/优先级/顺序，不删本地条目） | `raw/backend-w1-tests.txt`：AssetVersion 18 / AssetUpgrade 9 / TestSetPlatform 4 / PlatformCaseSetController 3；`raw/frontend-w1-vitest.txt`（CaseSetsView 12 项） |
| **N10 NODX 心跳限流** | 节点心跳窗口节流（默认 5s）同节点重复心跳不写库；期望上报周期默认 30s 仅作对外口径 | `ExecutionNodeService.java`；`ExecutionNodeServiceTest` 8 项 |
| **N11 文档漂移** | 交接文档 §3.2 两条证据缺口改为「已关闭」并附路径；§3.3 六个遗留逐条改写为已补/已澄清；TC-32 证据 README 从 V9_1–V9_4 更新为 V9_1–V9_5（61 → 67 项断言） | `docs/plan/交接文档-v2.1-20261107.md` §3.2/§3.3；`docs/evidence/v2.1/TC32-MIG/README.md` |

## 本轮顺带修掉的真实缺陷

1. **`ExecutionNodeService` 多构造器导致 Spring 启动失败**（门禁 05 第 1 次跑红）：
   类里另有 3 个测试用构造器时，Spring 不再自动挑选，去找无参构造并抛
   `No default constructor found`。已在生产构造器上加 `@Autowired`，并在注释里写明原因。
2. **CSS/px 白名单**：`PlanGraph` 画布 `480px` 改为 `420px`（与报告详情一致），
   同步策略下拉 `190px` 改为 `180px`，门禁 06/15 复绿。

## 复跑命令

```bash
cd ~/robot-autotest
set -a && . ~/.config/robot-autotest/env && set +a
bash scripts/gates.sh                     # 期望 GATES_RESULT FAILS=0
cd frontend && npx vitest run src/views/CaseSetsView.spec.ts src/components/plan/PlanGraph.spec.ts
cd ../backend && mvn -o test -Dtest='AssetVersionServiceTest,AssetUpgradeServiceTest,TestSetPlatformServiceTest,PlatformCaseSetControllerTest,ExecutionNodeServiceTest'
bash ../scripts/v21/migrate-roundtrip.sh  # 期望 TC-v2.1-32: PASS（67 项 OK）
```
