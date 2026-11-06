# W2–W7 前置条件证据（2026-11-06）

| 项 | 证据 | 结论 |
|---|---|---|
| 大师复核认可与二次授权 | `docs/plan/复核结论与二次开发授权-20261106.md` | 生效，中途不复核 |
| C1–C10 闭环 | `docs/plan/前置条件闭环-20261106.md` | 已闭环（含显式降级） |
| C2/C3 样本 | `samples.md` | 真实工程，本机预检级；G8 现场确认仍待 |
| C4 venv 白名单 | `config/pytest-venv.yml` | 已定案 |
| C5 字段白名单 | `docs/plan/C5-对外报告字段白名单-20261106.md` + `tools/check_report_sensitive.py --self-check` | 已定案，判据可执行 |
| C6 CLI 探测 | `cli-probe.log` | 实测留痕，按 R6 降级 |
| G4/G5 书面核验 | `docs/plan/门禁核验纪要-G2-G5-20261106.md` 附 2 | **两门均未判过**，不代判 |

`python3 tools/check_report_sensitive.py --self-check` 为本目录可复跑机器门。

**可复跑顺序**：`python3 docs/evidence/v1.0/w2-w7-preconditions/phase2_kickoff.py`
→ `python3 docs/evidence/v1.0/w2-w7-preconditions/fix_phase2.py`。
前者负责回写与生成，后者把 C5 扫描器的正则块规范化（剥离裸引号）；两步都幂等。
