# v0.1 地基 · ⑥ 测试报告

**文档编号** RA-REL-2026-v0.1-TR ｜ **版本** v0.1 ｜ **日期** 2026-11-06
**状态** 机器门复跑通过；TC 级沿用历史证据；门禁 G1 真值见 §4

> 本文件是 2026-11-06 W1 收口时补齐的版本流程第 ⑥ 道产物。**本轮重跑的是机器门**，
> 不是逐条 TC 重新执行；凡未重跑的 TC 一律标 `EVIDENCE-ONLY`，不得读作「本轮通过」。
> 真机 / 外部凭据 / 时间类条件见 §5，一律 `待现场`。

## 1. 结论

| 项 | 值 |
|---|---|
| 版本 / 批次 | v0.1 地基 / B1 |
| TEST-PLAN 中识别到 TC | 60 条 |
| 本轮机器门 | 8 项全绿（见 §2） |
| 本轮逐条重跑 TC | 0（口径见上文） |
| 历史证据文件 | 26 份（§3 列前 18 份） |
| 门禁 G1 | 11-04 判过（带偏差），见 `docs/evidence/v0.1/G1-核验纪要-20261104.md` |
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

共 26 份，以下列前 18 份（相对仓库根）：

- `docs/evidence/v0.1/G1-核验纪要-20261104.md`
- `docs/evidence/v0.1/ci-local-20261104-1958.log`
- `docs/evidence/v0.1/doc-gate-20261104-1953.log`
- `docs/evidence/v0.1/env-README.md`
- `docs/evidence/v0.1/env-probes-20261104-1953.log`
- `docs/evidence/v0.1/env-probes-win.log`
- `docs/evidence/v0.1/env-probes-wsl.log`
- `docs/evidence/v0.1/env-setup-rf.log`
- `docs/evidence/v0.1/env02-build-test-20261104.log`
- `docs/evidence/v0.1/env04-anonymize.log`
- `docs/evidence/v0.1/env04-header-check.log`
- `docs/evidence/v0.1/flyway-downup-20261104-2005.log`
- `docs/evidence/v0.1/flyway-downup-20261104-2030.log`
- `docs/evidence/v0.1/g1-boot-check.md`
- `docs/evidence/v0.1/g1-jar-boot.log`
- `docs/evidence/v0.1/lint-20261104-1953.log`
- `docs/evidence/v0.1/mvn-compile-wsl.log`
- `docs/evidence/v0.1/mvn-it-20261104-1955.log`
- … 其余 8 份见 `docs/evidence/v0.1/`

## 4. TC 台账（口径：EVIDENCE-ONLY）

| TC | 本轮结果 | 判定依据 |
|---|---|---|
| TC-v0.1-001 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-1 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-002 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-2 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-003 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-3 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-004 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-4 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-005 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-5 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-010 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-10 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-011 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-11 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-012 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-12 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-013 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-13 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-014 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-14 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-020 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-20 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-021 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-21 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-022 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-22 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-023 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-23 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-024 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-24 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-025 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-25 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-026 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-26 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-027 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-27 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-028 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-28 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-030 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-30 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-031 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-31 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-032 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-32 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-040 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-40 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-041 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-41 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-042 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-42 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-043 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-43 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-050 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-50 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-051 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-51 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-052 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-52 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-053 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |
| TC-v0.1-53 | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |

## 5. 遗留缺口与未验证项（不自评通过）

- 门禁 G1：11-04 判过（带偏差），见 `docs/evidence/v0.1/G1-核验纪要-20261104.md`。
- 真机 / 外部凭据类（DUT、交换机、GitHub runner、pabot）一律 `待现场`；ADR-0013 禁跑令有效。
- 条件类（4 并发、打印链路）与时间类（连续运行）不具备复跑条件，见 `docs/plan/门禁核验纪要-G2-G5-20261106.md`。
- v2.0 的 W2/W3 受 G5/G4 前置约束，本报告不将其计入任何「通过」结论。

## 6. 修订记录

| 日期 | 版本 | 变更 |
|---|---|---|
| 2026-11-06 | v1.2 | 机器门读数按最终门禁日志订正：后端 642→644、前端 220→221，WBS 补 87/130；不沿用估算值 |
| 2026-11-06 | v1.1 | TC 台账改为复用 `tools/check_release_docs.py::extract_ids()`，补齐区间展开与两种编号形态（v1.0 生成器漏 30 条被机检判 FAIL） |
| 2026-11-06 | v1.0 | 首次补齐；按「机器门实测 + 历史证据 + 门禁真值」三层写实，不用通过模板 |
