# v0.5 报告 · ⑥ 测试报告

**文档编号** RA-REL-2026-v0.5-TR ｜ **版本** v0.5 ｜ **日期** 2026-11-06
**状态** 机器门复跑通过；TC 级沿用历史证据；门禁 G5 真值见 §4

> 本文件是 2026-11-06 W1 收口时补齐的版本流程第 ⑥ 道产物。**本轮重跑的是机器门**，
> 不是逐条 TC 重新执行；凡未重跑的 TC 一律标 `EVIDENCE-ONLY`，不得读作「本轮通过」。
> 真机 / 外部凭据 / 时间类条件见 §5，一律 `待现场`。

## 1. 结论

| 项 | 值 |
|---|---|
| 版本 / 批次 | v0.5 报告 / B6 |
| TEST-PLAN 中识别到 TC | 66 条 |
| 本轮机器门 | 8 项全绿（见 §2） |
| 本轮逐条重跑 TC | 0（口径见上文） |
| 历史证据文件 | 61 份（§3 列前 18 份） |
| 门禁 G5 | 无书面核验纪要，W2 因此不得开工 |
| 当前结论 | **机器门通过、TC 证据齐备；门禁未判过项与真机项不冒充通过** |

## 2. 本轮机器门实测（2026-11-06，W1 收口批次）

| 门 | 命令 | 实测读数 | 结果 |
|---|---|---|---|
| 后端单测 | `mvn -B test` | 644 / 644 通过，0 失败 0 错误 | PASS |
| 前端单测 | `cd frontend && npm run test` | 30 文件 / 221 用例通过 | PASS |
| 前端类型与构建 | `npm run build（vue-tsc --noEmit）` | 0 类型错误，dist 产出成功 | PASS |
| 后端静态检查 | `mvn -B checkstyle:check` | 0 violations / BUILD SUCCESS | PASS |
| 数据库体检 | `bash scripts/db-audit.sh` | 8 项 PASS / 47 业务表 | PASS |
| 仓库守门 | `bash scripts/lint.sh` | PASS（含 Checkstyle 接线） | PASS |
| 统一入口实测 | `bash scripts/verify-entry.sh` | 12 / 12 PASS | PASS |
| WBS 自洽 | `python3 tools/check_wbs_consistency.py` | 130 包 / 87 已完成，五数自洽 | PASS |

> 原始日志由 `docs/evidence/v1.0/policy-20261106/gates.sh` 复跑重建（logs/ 被 .gitignore 忽略）。

## 3. 历史证据清单

共 61 份，以下列前 18 份（相对仓库根）：

- `docs/evidence/v0.5/frontend-rpt02-rpt03-build.log`
- `docs/evidence/v0.5/frontend-rpt02-rpt03-lint.log`
- `docs/evidence/v0.5/frontend-rpt04-rpt06-build.log`
- `docs/evidence/v0.5/frontend-rpt04-rpt06-lint.log`
- `docs/evidence/v0.5/frontend-rpt05-build.log`
- `docs/evidence/v0.5/frontend-rpt05-lint.log`
- `docs/evidence/v0.5/iam05-authz-20.md`
- `docs/evidence/v0.5/iam05-authz.log`
- `docs/evidence/v0.5/mvn-test-rpt01.log`
- `docs/evidence/v0.5/mvn-test-rpt02-rpt03.log`
- `docs/evidence/v0.5/mvn-test-rpt04-rpt06.log`
- `docs/evidence/v0.5/mvn-test-rpt05.log`
- `docs/evidence/v0.5/rpt01-rpt02-real/README.md`
- `docs/evidence/v0.5/rpt01-rpt02-real/backend-it.log`
- `docs/evidence/v0.5/rpt01-rpt02-real/backend-test.log`
- `docs/evidence/v0.5/rpt01-rpt02-real/git-status.log`
- `docs/evidence/v0.5/rpt01-rpt02-real/lint.log`
- `docs/evidence/v0.5/rpt01-rpt02-real/pabot-p2.log`
- … 其余 43 份见 `docs/evidence/v0.5/`

## 4. TC 台账（口径：EVIDENCE-ONLY）

| TC | 本轮结果 | 判定依据 |
|---|---|---|
| TC-v0.5-101 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-102 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-103 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-104 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-105 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-106 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-107 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-108 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-109 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-110 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-111 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-201 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-202 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-203 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-204 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-205 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-206 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-207 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-208 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-209 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-210 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-211 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-212 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-213 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-214 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-215 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-216 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-217 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-218 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-219 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-220 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-221 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-301 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-302 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-303 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-304 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-305 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-306 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-307 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-308 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-401 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-402 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-403 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-404 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-405 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-406 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-407 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-408 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-409 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-410 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-411 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-412 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-413 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-414 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-415 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-501 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-502 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-503 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-504 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-505 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-506 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-507 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-508 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-601 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-602 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.5-603 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |

## 5. 遗留缺口与未验证项（不自评通过）

- 门禁 G5：无书面核验纪要，W2 因此不得开工。
- 真机 / 外部凭据类（DUT、交换机、GitHub runner、pabot）一律 `待现场`；ADR-0013 禁跑令有效。
- 条件类（4 并发、打印链路）与时间类（连续运行）不具备复跑条件，见 `docs/plan/门禁核验纪要-G2-G5-20261106.md`。
- v2.0 的 W2/W3 受 G5/G4 前置约束，本报告不将其计入任何「通过」结论。

## 6. 修订记录

| 日期 | 版本 | 变更 |
|---|---|---|
| 2026-11-06 | v1.2 | 机器门读数按最终门禁日志订正：后端 642→644、前端 220→221，WBS 补 87/130；不沿用估算值 |
| 2026-11-06 | v1.1 | TC 台账改为复用 `tools/check_release_docs.py::extract_ids()`，补齐区间展开与两种编号形态（v1.0 生成器漏 30 条被机检判 FAIL） |
| 2026-11-06 | v1.0 | 首次补齐；按「机器门实测 + 历史证据 + 门禁真值」三层写实，不用通过模板 |
