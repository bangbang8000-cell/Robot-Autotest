#!/usr/bin/env python3
"""七道流程第 ⑦ 道：本轮开发完成后的规范性文档回写（2026-11-06）。

把「被实测推翻的假设」逐条改掉并留修订记录，不悄悄改数字：
  - v2.0 已从「规划中·待评审 / 34 候选包不计入 / W1 未开工」
    变为「PRD 有条件通过 / 34 包并入 WBS 分母 130 / W1 六包已实测 AI 验收」；
  - v2.0 ⑥ 测试报告不再是「全部 NOT-RUN 计划态快照」；
  - 中长期规划的规模口径 96+34 候选 → 130 已立项。

两类文档区别对待：
  - **活文档**（release README、规划、v2.0 头部、研究报告状态）直接订正 + 留修订记录；
  - **历史快照**（评估、裁决表、四件套末尾勾定补记）保留原文，追加「后续演进」补记，
    说明该段描述的是哪个时点，权威状态以文件头 / WBS 为准。

幂等：每处内容匹配 + 命中次数断言 + 完成标记，重复执行只打印 SKIP。
"""
from __future__ import annotations

import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[4]


def edit(rel: str, label: str, old: str, new: str, marker: str) -> None:
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    if marker in text:
        print(f"SKIP  {rel} · {label}")
        return
    hits = text.count(old)
    if hits != 1:
        raise SystemExit(f"FAIL  {rel} · {label}：期望命中 1 次，实际 {hits}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"PASS  {rel} · {label}")


def append(rel: str, label: str, addition: str, marker: str) -> None:
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    if marker in text:
        print(f"SKIP  {rel} · {label}")
        return
    path.write_text(text.rstrip("\n") + "\n\n" + addition.strip() + "\n", encoding="utf-8")
    print(f"PASS  {rel} · {label}")


def release_readme() -> None:
    rel = "docs/release/README.md"
    edit(rel, "版本矩阵 v2.0 行",
         "| **v2.0** | 平台化 Platform | B9（已评审通过·W1 进行中） | G8 | 新前缀 **ENVX / NAV / RPTX / KRN / IMP / IAMX / AIX / UIX**（未并入 WBS 台账） | 34 | 双内核（RF+pytest）、报告对内对外分家、原生用例集合导入、用户组与公用资产、右侧智能体、统一入口 8001 | — |",
         "| **v2.0** | 平台化 Platform | B9（PRD 已评审通过；**W1 六包已完成**、W2–W7 未开工） | G8 | 新前缀 **ENVX / NAV / RPTX / KRN / IMP / IAMX / AIX / UIX**（**2026-11-06 已并入 WBS #97–130**） | 34 | 双内核（RF+pytest）、报告对内对外分家、原生用例集合导入、用户组与公用资产、右侧智能体、统一入口 8001 | 已并入 130 包总盘 |",
         "2026-11-06 已并入 WBS #97–130")

    edit(rel, "v2.0 并入 WBS 说明",
         "> **v2.0 不计入 79 包**：上表 v0.1~v1.0 是已登记进 `docs/plan/WBS-STATUS.md` 的主线；v2.0 的 34 个包是**新前缀的规划包**，\n"
         "> 仅在 `docs/release/v2.0-platform/DEV-PLAN.md` 内编号，待 PRD 评审通过后再决定是否并入 WBS 台账（本轮刻意不改，避免唯一进度真源被未裁决内容污染）。",
         "> **v2.0 已于 2026-11-06 并入 WBS（分母 96 → 130）**：PRD 书面评审「有条件通过」后，\n"
         "> `docs/evidence/v1.0/w1-kickoff/apply-kickoff.py` 把 34 个候选包登记为 `#97–#130`；当前 WBS 读数为\n"
         "> **130 包 / 87 已完成 / 8 待验 / 7 待现场 / 28 未开始 / 0 进行中**。W1 六包（ENVX-01/02、NAV-01..04）\n"
         "> 已开发、实测并 AI 验收；W2–W7 受 G4/G5、C2/C3/C5 与 `G-授权` 约束未开工，不计入任何「通过」结论。\n"
         "> 历史口径「v2.0 不计入 79 包 / 待评审后再决定」已在 v2.25 修订记录中标注作废。",
         "v2.0 已于 2026-11-06 并入 WBS")

    edit(rel, "79 包历史口径订正",
         "> **合计 79 个任务包全覆盖**：按版本计 15+28+11+9+8+4+5 = **80**，其中 DOC-02 跨 v0.2/v0.3 计了两次，\n"
         "> 按唯一 WBS 去重恰为 **79**（与 `docs/plan/WBS-STATUS.md` 汇总行一致）。",
         "> **合计 79 个任务包全覆盖**（**v1.0 时点口径，当前分母为 130**）：按版本计 15+28+11+9+8+4+5 = **80**，其中 DOC-02 跨 v0.2/v0.3 计了两次，\n"
         "> 按唯一 WBS 去重恰为 **79**（当时与 `docs/plan/WBS-STATUS.md` 汇总行一致）。\n"
         "> **订正**：此后 WBS 先由 79 扩到 96（增量包），再于 2026-11-06 并入 v2.0 的 34 包成为 **130**；\n"
         "> 上表 79 只作历史沿革，不再是当前分母，见 `docs/plan/WBS-STATUS.md`。",
         "v1.0 时点口径，当前分母为 130")

    edit(rel, "六件套矩阵 v2.0 行",
         "| v2.0 平台化（已评审通过·有条件） | `docs/release/v2.0-platform/` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅（计划态） | G8 |",
         "| v2.0 平台化（已评审通过·有条件；W1 实测） | `docs/release/v2.0-platform/` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅（W1 实测 + 其余 NOT-RUN） | G8 |",
         "W1 实测 + 其余 NOT-RUN")

    edit(rel, "六件套状态说明",
         "v2.0 已按严格模式落六份产物：⑤ 为计划、⑥ 为**计划态快照（全部 NOT-RUN）**，不代表已执行。",
         "v2.0 已按严格模式落六份产物：⑤ 为计划；⑥ 在 W1 完成后已**由计划态快照改写为分层实测台账**——\n"
         "W1 相关 11 条给出本轮证据与 `PARTIAL` / `NOT-RUN` 细分，其余 56 条仍 `NOT-RUN`，不代表已执行。\n"
         "**G8 未判过**，不得据 ⑥ 宣称 v2.0 通过。",
         "由计划态快照改写为分层实测台账")

    append(rel, "v2.25 修订记录（废止 v2.24 三处假设）", """
*v2.25（2026-11-06，本轮开发收口）：**废止 v2.24 的三处假设**——
①「规划中·待评审」→ PRD 已由 AI 出书面评审结论「有条件通过」（`docs/plan/PRD评审结论-v2.0-20261106.md`）；
②「34 包不计入 79 包 / 不改 WBS」→ 34 包已并入 WBS `#97–#130`，分母 96 → 130，收口读数 **87 / 130**；
③「⑥ 计划态全部 NOT-RUN」→ W1 六包已开发实测，`05-TEST-REPORT.md` 改为分层台账
（W1 相关 11 条有本轮证据、其余 56 条仍 `NOT-RUN`）。W2–W7 未开工，G8 未判过。
证据 `docs/evidence/v1.0/w1-delivery/README.md`；完成报告 `docs/plan/本轮开发完成报告-20261106.md`。*
""", "v2.25（2026-11-06，本轮开发收口）")


def long_plan() -> None:
    rel = "docs/自动化测试平台中长期工作规划.md"
    edit(rel, "规模口径 96+34 → 130",
         "| 规模口径 | **已立项 96 个任务包**（唯一真源 `docs/plan/WBS-STATUS.md`）+ **候选 34 个**（B9 平台化，待评审并入）；人日 / 人月仅作**相对规模参考**，不作为进度承诺、不用于考核、不驱动验收 |\n"
         "| 进度口径 | **进度 = 已通过门禁的已立项任务包数 / 96**。候选包**不计入分母**，评审并入台账后才变。唯一进度真源是 `docs/plan/WBS-STATUS.md`，不是日历、不是甘特图 |",
         "| 规模口径 | **已立项 130 个任务包**（唯一真源 `docs/plan/WBS-STATUS.md`；2026-11-06 把 B9 平台化 34 个候选包并入，分母 96 → 130）；人日 / 人月仅作**相对规模参考**，不作为进度承诺、不用于考核、不驱动验收 |\n"
         "| 进度口径 | **进度 = 已完成任务包数 / 130**（2026-11-06 收口读数 **87 / 130**）。34 个 B9 包已计入分母，不再是「候选不计入」。唯一进度真源是 `docs/plan/WBS-STATUS.md`，不是日历、不是甘特图 |",
         "已立项 130 个任务包")
    append(rel, "补记二：规模口径回写", """
*补记二（2026-11-06，本轮开发收口，不升版本号）：**推翻补记一之前沿用的两处规模假设**——
① 规模口径原写「已立项 96 + 候选 34（待评审并入）」，实测为 **34 包已并入 WBS #97–#130，分母 130**；
② 进度口径原写「候选包不计入分母」，现更正为 `已完成 / 130`，收口读数 **87 / 130**。
触发依据：大师 2026-11-06 一次性开发授权（`docs/plan/决策纪要-20261106.md` §9）、
PRD 书面评审结论、W1 六包 AI 验收。**版本划分、G0–G8 门禁、KPI、七步循环、四条纪律一项未动**；
G-授权 / ADR-0013 禁跑令 / 发版与对外口径仍归人。W2–W7 因 G4/G5 与 C2/C3/C5 未补齐而未开工。*
""", "补记二（2026-11-06，本轮开发收口")


def v2_docs() -> None:
    edit("docs/release/v2.0-platform/00-RESEARCH.md", "调研状态",
         "**状态** 调研完成（待评审）", "**状态** 调研完成（已随 PRD 评审通过 2026-11-06，有条件）",
         "已随 PRD 评审通过 2026-11-06")

    evolution = """
*后续演进（同日 15:23 之后，非本补记原意）：大师给出一次性开发授权后，AI 出具
`docs/plan/PRD评审结论-v2.0-20261106.md`（**有条件通过**），34 包并入 WBS（分母 96 → 130），
W1 六包已开发、本机实测并 AI 验收。本补记描述的「状态仍为待评审 / WBS 分母仍 96 / W1 未开工」
是**D1–D10 勾定时点**的事实，不是当前状态；权威当前状态以本文件头部与
`docs/plan/WBS-STATUS.md` 为准。W2–W7 未开工，G8 未判过。*
"""
    for name in ["PRD", "DEV-PLAN", "TEST-PLAN"]:
        append(f"docs/release/v2.0-platform/{name}.md", "末尾补记：后续演进",
               evolution, "后续演进（同日 15:23 之后")


def history_snapshots() -> None:
    append("docs/plan/现状复核与文档更新评估-20261106.md", "修订记录 v1.2", """
*v1.2 补记（2026-11-06 收口）：本文 F3「四份版本文档仍标待评审」、RK-A「把勾定当评审通过 → 提前开 W1」
描述的是**本文成文时点（15:23 之前）**的状态，不是当前状态。此后大师给出一次性开发授权，
AI 出具 PRD 书面评审结论「有条件通过」，34 包并入 WBS，W1 六包开工并本机实测完成。
**F3 / RK-A 的结论本身未被推翻**（勾定确实不等于评审通过、PRD 未过确实不能开 W1），
被推进的是它们描述的时点条件；故保留原文，此处只补演进链。*
""", "v1.2 补记（2026-11-06 收口）")

    append("docs/plan/v2.0-裁决建议表-20261106.md", "§6.5 后续演进", """
### 6.5 后续演进（2026-11-06 收口补记，不改写上文裁决记录）

上文 §6 的四份文档状态「仍为待评审」、WBS「分母仍 96 / 34 包未并入 / W1 未开工」是**两轮问卷勾定时点**的事实。
此后：① 大师给出一次性开发授权（`docs/plan/决策纪要-20261106.md` §9）；② AI 出具
`docs/plan/PRD评审结论-v2.0-20261106.md`，结论「有条件通过」；③ 34 包并入 WBS `#97–#130`，分母 130；
④ W1 六包开发、本机实测、八步门禁全绿并 AI 验收。**D1–D10 的勾定结果一项未变**（全取推荐值 A），
W2/W3 仍受 G5/G4 与 C2/C3/C5 约束未开工，`G-授权` 与 ADR-0013 禁跑令未解除。
""", "6.5 后续演进")

    append("docs/plan/决策纪要-20261106.md", "§11 最终机器门读数", """
## 11. 最终机器门读数（2026-11-06，开发收口）

`bash docs/evidence/v1.0/policy-20261106/gates.sh` 第四次复跑：
**10 步全 PASS，`GATES_RESULT FAILS=0`**（01 字体 / 02 前端测试 / 03 类型 / 04 前端构建 /
05 后端测试 / 06 lint / 07 版本文档 / 08 WBS 自洽 / 09 Markdown 表格 / 10 diff check）。
`bash scripts/verify-entry.sh` 复验 **12 / 12 PASS**（8001 入口、双探针、深链接、缓存策略、API 反代）。
`python3 tools/check_release_docs.py`：**FAIL 0 项**（v2.0 PASS，其余七版 `IDLE` 不计违规）。

第一次复跑的 `FAILS=3`（后端测试 / lint / 版本文档）与后续 lint 的三轮收敛（色值 → i18n → px → 表格规范）
已在 `docs/evidence/v1.0/w1-delivery/README.md` 逐条留痕，修复脚本全部幂等可复跑。
**本纪要 §9 的授权边界不因机器门全绿而转移**：发版 / 破坏性变更 / 对外口径、`G-授权`、
ADR-0013 禁跑令仍归大师；本轮未自评通过 G4/G5/G8。
""", "11. 最终机器门读数")


def main() -> int:
    release_readme()
    long_plan()
    v2_docs()
    history_snapshots()
    print("DONE  规范性文档回写完成")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
