#!/usr/bin/env python3
"""2026-11-06 W1 开工前置：PRD 书面评审结论 + 34 包并入 WBS + 状态口径回写。

授权来源：大师 2026-11-06 15:23「批准……合理开始软件开发工作，直到规划的功能都完成，
期间不要停下来问我，都依照你推荐的为准，全部完成后再找我人工复核」。

本脚本只做**流程台账与文档状态**，不写任何产品代码；按项目纪律：
内容匹配 + 命中次数断言 + 可重跑（重复执行应全部 SKIP）。
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT))
RESULTS: list[tuple[str, str, str]] = []


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def save(rel: str, text: str) -> None:
    (ROOT / rel).write_text(text, encoding="utf-8")


def log(status: str, rel: str, label: str) -> None:
    print(f"{status:5s} {rel} :: {label}")
    RESULTS.append((status, rel, label))


def replace_once(rel: str, old: str, new: str, label: str, marker: str | None = None) -> None:
    """内容匹配 + 命中次数断言；已落地则 SKIP（幂等）。

    marker 用于「追加型替换」：new 里保留了 old 时，仅靠 old 判断会每次重复插入
    （2026-11-06 裁决落盘已踩过这个坑）。此时必须显式给一个只属于增量的标记。
    """
    s = read(rel)
    if marker and marker in s:
        log("SKIP", rel, label)
        return
    if old not in s:
        if new in s:
            log("SKIP", rel, label)
            return
        raise SystemExit(f"FAIL {rel} :: {label} :: 未命中且未落地")
    n = s.count(old)
    if n != 1:
        raise SystemExit(f"FAIL {rel} :: {label} :: 命中 {n} 次，期望 1")
    save(rel, s.replace(old, new, 1))
    log("PASS", rel, label)


def append_once(rel: str, marker: str, block: str, label: str) -> None:
    s = read(rel)
    if marker in s:
        log("SKIP", rel, label)
        return
    save(rel, s.rstrip("\n") + "\n\n" + block.strip("\n") + "\n")
    log("PASS", rel, label)


WBS = "docs/plan/WBS-STATUS.md"
DEV = "docs/release/v2.0-platform/DEV-PLAN.md"

# ---------------------------------------------------------------- 1. 解析 34 包
WAVE = {
    "ENVX": ("W1", "无（W1 首波）"),
    "NAV": ("W1", "无（W1 首波）"),
    "RPTX": ("W2", "需 W1 + **G5 已通过**（报告产物结构定型）"),
    "KRN": ("W3", "需 W1 + **G4 已通过**（执行链路定型）"),
    "IMP": ("W4", "需 W3 + C2/C3 真实 pytest / RF 工程"),
    "IAMX": ("W5", "需 W4（越权矩阵 ≥ 60 条）"),
    "AIX": ("W6", "需 W3/W4 + C6 本机 AI CLI 非交互探测"),
    "UIX": ("W7", "需 W1–W6 + 8 页 × 2 主题截图基线"),
}
pkg_re = re.compile(r"^\| ([A-Z]+-\d+) \| ([^|]+) \|", re.M)
pkgs: list[tuple[str, str]] = []
for line in read(DEV).splitlines():
    m = pkg_re.match(line)
    if m:
        code, desc = m.group(1), m.group(2).strip().replace("|", "／")
        if code not in [p[0] for p in pkgs]:
            pkgs.append((code, desc))
if len(pkgs) != 34:
    raise SystemExit(f"FAIL DEV-PLAN 解析到 {len(pkgs)} 个包，期望 34")
print(f"[INFO] DEV-PLAN 解析到 {len(pkgs)} 个候选包：{pkgs[0][0]} … {pkgs[-1][0]}")

# ---------------------------------------------------------------- 2. WBS 追加 34 行
wbs_lines = read(WBS).splitlines()
if any(line.startswith("| 97 | **ENVX-01**") for line in wbs_lines):
    log("SKIP", WBS, "34 个候选包并入（97–130）")
else:
    anchor = next(
        (i for i, line in enumerate(wbs_lines) if line.startswith("| 96 | **EXEC-15**")),
        None,
    )
    if anchor is None:
        raise SystemExit("FAIL WBS 找不到 #96 锚点行")
    rows: list[str] = []
    for offset, (code, desc) in enumerate(pkgs):
        no = 97 + offset
        prefix = code.split("-")[0]
        wave, blocker = WAVE[prefix]
        active = wave == "W1"
        state = "进行中" if active else "未开始"
        evidence = "`docs/evidence/v1.0/w1-kickoff/`（开发中）" if active else "—"
        if not active:
            blocker = blocker + "；**未开工**"
        # 7 列：序号 / WBS / 任务 / 批次 / 状态 / 证据 / 阻塞
        rows.append(
            f"| {no} | **{code}** | {desc} | B9/{wave} | {state} | {evidence} | {blocker} |"
        )
    wbs_lines[anchor + 1 : anchor + 1] = rows
    save(WBS, "\n".join(wbs_lines) + "\n")
    log("PASS", WBS, f"34 个候选包并入（97–130，W1 六包置进行中）")

# ---------------------------------------------------------------- 3. WBS 汇总 / 进度 / 分组表
replace_once(
    WBS,
    "| 96 个任务包 | **0** | **0** | **14** | **7** | **75** |",
    "| 130 个任务包 | **28** | **6** | **14** | **7** | **75** |",
    "汇总行 96 → 130（未开始 28 / 进行中 6）",
)
replace_once(
    WBS,
    "**当前进度：75 / 96（约 78.1% · 14 待验 · 7 待现场 · 0 未开始 · 0 进行中）**",
    "**当前进度：75 / 130（约 57.7% · 14 待验 · 7 待现场 · 28 未开始 · 6 进行中）**",
    "进度行同步",
)
ROW_IN_PROGRESS = "| **进行中**（v2.0 W1 六包） | 6 | `ENVX-01/02` + `NAV-01..04`，本机首波开工 | 逐包留证据、跑机器门后 AI 验收 |"
ROW_NOT_STARTED = "| **未开始**（v2.0 W2–W7） | 28 | 外部门禁未过 / 真实工程样本未到位，见各行阻塞列 | 门禁 G4/G5 + C2/C3/C5 补齐后按波次推进 |"
# 先清理早期版本因「追加型替换」误插的重复行，保证最终态只有一组
_wbs_now = read(WBS)
_pair = ROW_IN_PROGRESS + "\n" + ROW_NOT_STARTED + "\n"
while _pair + _pair in _wbs_now:
    _wbs_now = _wbs_now.replace(_pair + _pair, _pair, 1)
if _wbs_now != read(WBS):
    save(WBS, _wbs_now)
    log("PASS", WBS, "清理分组表重复插入行")

replace_once(
    WBS,
    "| **待验**（有真实技术缺口） | 14 | `WS-02` checkstyle 未接入",
    ROW_IN_PROGRESS + "\n" + ROW_NOT_STARTED + "\n"
    + "| **待验**（有真实技术缺口） | 14 | `WS-02` checkstyle 未接入",
    "分组表补 v2.0 两行",
    marker=ROW_IN_PROGRESS,
)

# ---------------------------------------------------------------- 4. PRD 书面评审结论（新建）
REVIEW = "docs/plan/PRD评审结论-v2.0-20261106.md"
review = ROOT / REVIEW
if review.exists():
    log("SKIP", REVIEW, "PRD 书面评审结论")
else:
    review.write_text(
        """# v2.0-platform PRD 书面评审结论（AI）

**日期** 2026-11-06 ｜ **评审对象** `docs/release/v2.0-platform/PRD.md` v1.3 ｜ **结论** **有条件通过**

**授权来源**：大师 2026-11-06 15:23 指令「批准……合理开始软件开发工作，直到规划的功能都完成，
期间不要停下来问我，都依照你推荐的为准，全部完成后再找我人工复核」。按 `docs/plan/决策纪要-20261106.md`
的新口径（AI 验收放行、人转旁路），PRD 评审由 AI 出书面结论；**人工复核为最终确认**，
本结论不代替发版 / 破坏性变更 / 对外口径的拍板权，`G-授权` 与 ADR-0013 禁跑令不转移。

## 1. 评审依据（逐条核对，均为已核实）

| 项 | 核对结果 |
|---|---|
| 十项裁决 D1–D10 | 两轮向导问卷勾定，**全取推荐值 A**；`01-ASSESSMENT.md` §6 与 `v2.0-裁决建议表-20261106.md` §6 一致 |
| 范围 | 8 组 FR（53 条）、G8 十条判定、7 波次 / 34 包，与 `01-ASSESSMENT.md` R1–R8 一一对应 |
| 流程合规 | 评估报告 → PRD → DEV-PLAN → TEST-PLAN → 05-TEST-REPORT（计划态）六份齐，符合七道流程前五道 |
| 判据可判定 | 每条 FR 有验收判据；G8 十条均有可举证形态；无「待定」「视情况」类不可判条目 |
| 版本不倒退 | v0.1~v1.0 回归集 `REG-*` 与 G0–G7 门禁未被下调；FR-v2.0-16 明写 RF 命令逐字等价 |
| 已知文档瑕疵 | R2 标题「5 个一级项」已在 11-06 裁决落盘时订正为 6；本轮复核无新增 |

## 2. 通过条件（不阻塞 W1，逐项留痕）

| 条件 | 当前状态 | 影响的波次 |
|---|---|---|
| C2 真实 pytest 工程 / C3 真实 RF 工程 / C5 对外报告字段白名单 | 未到位（大师补齐中） | W2/W3/W4/W6 的样本类 TC 记 `NOT-RUN`，**不得用玩具工程顶替** |
| G4 已通过（执行链路定型） | 无核验纪要 | W3 不得开工 |
| G5 已通过（报告产物结构定型） | 无核验纪要 | W2 不得开工 |
| C6 本机 AI CLI 非交互探测 | 未执行 | W6 不得开工 |
| `G-授权`（真机 / 外部凭据） | 仍归人，ADR-0013 禁跑令有效 | 7 个 `待现场` 包保持 `待现场` |

**W1 六包不依赖上述任何一项**（`DEV-PLAN.md` §3 波次表「依赖：无」），故本结论即可放行 W1。

## 3. 评审结论与落地动作

1. `PRD.md` / `DEV-PLAN.md` / `TEST-PLAN.md` / `01-ASSESSMENT.md` 状态由「待评审」
   改为「**已评审通过（AI 书面结论，有条件）**」，逐份留 v1.4 修订记录。
2. 34 个候选包并入 `docs/plan/WBS-STATUS.md`，分母 **96 → 130**；W1 六包置 `进行中`。
3. W1 按 `DEV-PLAN.md` §4.1 / §4.2 实现；完成后由 AI 跑机器门并留 `AI 验收 2026-11-06` 戳。
4. **W2–W7 一律不提前开工**：外部门禁与样本前置未满足时跳步即违反本结论第 2 节。

## 4. 人工复核提示（最终确认时看这四条）

- 本结论的「有条件通过」是否被接受；若大师改判，回滚动作集中在本脚本的 6 处文档改动 + WBS 34 行。
- C2/C3/C5 三样实物到位后需复评 W2/W3/W4/W6 的样本类判据。
- G4/G5 核验纪要由 AI 出书面结论后仍需大师旁路确认（真机项须 `G-授权`）。
- 发版、打 tag、对外发布**本轮一律不做**，待大师人工复核后拍板。
""",
        encoding="utf-8",
    )
    log("PASS", REVIEW, "PRD 书面评审结论（有条件通过）")

# ---------------------------------------------------------------- 5. 四份版本文档状态 + 修订记录
STATUS_OLD_NEW = [
    ("docs/release/v2.0-platform/PRD.md", "**状态** 待评审", "**状态** 已评审通过（AI 书面结论 2026-11-06，有条件）"),
    ("docs/release/v2.0-platform/DEV-PLAN.md", "**状态** 待评审", "**状态** 已评审通过（AI 书面结论 2026-11-06，有条件）"),
    ("docs/release/v2.0-platform/TEST-PLAN.md", "**状态** 计划态（未开工）", "**状态** 已评审通过（AI 书面结论 2026-11-06，有条件）· W1 测试进行中"),
]
for rel, old, new in STATUS_OLD_NEW:
    replace_once(rel, old, new, "头部状态改为已评审通过")
replace_once(
    "docs/release/v2.0-platform/01-ASSESSMENT.md",
    "评估完成（D1–D10 十项已勾定 2026-11-06；PRD 仍待评审",
    "评估完成（D1–D10 十项已勾定 2026-11-06；PRD 已评审通过（AI 书面结论，有条件）",
    "评估头部状态同步",
)
replace_once(
    "docs/release/v2.0-platform/PRD.md",
    "> **状态边界**：本文件仍为「待评审」，**PRD 评审通过归大师**；勾定不等于评审通过。",
    "> **状态边界**：本文件 2026-11-06 经 AI 书面评审**有条件通过**（依据大师一次性授权），"
    "详见 `docs/plan/PRD评审结论-v2.0-20261106.md`；**人工复核为最终确认**，"
    "发版 / 破坏性变更 / 对外口径仍归大师。",
    "PRD §1 状态边界改为已通过",
)
replace_once(
    "docs/release/v2.0-platform/01-ASSESSMENT.md",
    "> **本文件状态仍为「PRD 待评审」**，勾定不等于评审通过；WBS 分母仍 96，W1 未开工。",
    "> **本文件状态：PRD 已评审通过（AI 书面结论，有条件）**；WBS 分母已由 96 并入为 **130**，"
    "W1 六包置 `进行中`，W2–W7 未开工。勾定 ≠ 评审通过，但本次评审已单独完成。",
    "评估 §6 状态说明同步",
)

REVISION = (
    "| 2026-11-06 | v1.4 | AI 书面评审**有条件通过**（大师 15:23 一次性授权）；"
    "34 包并入 WBS（96→130），W1 六包开工 | 人工复核为最终确认；W2–W7 仍受 G4/G5 与 C2/C3/C5 约束 |"
)
for rel in [
    "docs/release/v2.0-platform/01-ASSESSMENT.md",
    "docs/release/v2.0-platform/PRD.md",
    "docs/release/v2.0-platform/DEV-PLAN.md",
    "docs/release/v2.0-platform/TEST-PLAN.md",
]:
    append_once(rel, "v1.4 | AI 书面评审", REVISION, "修订记录追加 v1.4")

# ---------------------------------------------------------------- 6. release README 索引同步
replace_once(
    "docs/release/README.md",
    "| **v2.0** | 平台化 Platform | B9（规划中·待评审） |",
    "| **v2.0** | 平台化 Platform | B9（已评审通过·W1 进行中） |",
    "版本矩阵状态同步",
)
replace_once(
    "docs/release/README.md",
    "| v2.0 平台化（规划中·待评审） |",
    "| v2.0 平台化（已评审通过·有条件） |",
    "产物清单状态同步",
)

# ---------------------------------------------------------------- 7. 决策纪要 §9
append_once(
    "docs/plan/决策纪要-20261106.md",
    "## 9. 2026-11-06 15:23 一次性开发授权",
    """## 9. 2026-11-06 15:23 一次性开发授权（W1 开工前置）

**大师原话**：「批准……合理开始软件开发工作，直到规划的功能都完成，期间不要停下来问我，
都依照你推荐的为准，全部完成后再找我人工复核（期间可以适当展示工作成果和更新本地的软件前后台）」。

**据此由 AI 自行定案、不再逐项询问的四件事**：

| 事项 | 定案 | 依据 |
|---|---|---|
| v2.0 PRD 评审 | **有条件通过**，出书面结论 `docs/plan/PRD评审结论-v2.0-20261106.md` | 大师「都依照推荐」+ 本纪要 §2 新口径（AI 验收放行、人转旁路） |
| 34 个候选包 | **并入 WBS**，分母 96→130；W1 六包 `进行中` | `DEV-PLAN.md` §3「不得跳步」的评审前置已满足 |
| P1 五包两案 | **按推荐值执行**（AST-04 案 A，CAS-03 / UX-04 / EXEC-01 案 B，PRJ-01 案 A） | 两案清单 §6 推荐值；PRJ-01 新增 Flyway 版本视为已授权 |
| C2/C3/C5 三样实物 | **不造玩具工程顶替**；样本类 TC 记 `NOT-RUN` | 大师上一轮裁决「等补齐再开 W2/W3」，本轮未推翻 |

**边界不转移（写死）**：发版 / 打 tag / 破坏性变更 / 对外口径仍归大师；`G-授权`（真机、外部凭据）、
ADR-0013 禁跑令仍归大师；G4/G5 未过 → W2/W3 不得开工；7 个 `待现场` 包不得自评通过。
**「功能完成」的口径**：本机可自动验证的功能实现并留证据；真机 / 外部样本 / 授权类如实列在
最终复核清单，交大师人工复核时不冒充已完成。

**执行留痕**：`docs/evidence/v1.0/w1-kickoff/apply-kickoff.py`（幂等）、本条 §9。
""",
    "决策纪要追加 §9 授权留痕",
)

# ---------------------------------------------------------------- 汇总
p = sum(1 for r in RESULTS if r[0] == "PASS")
s = sum(1 for r in RESULTS if r[0] == "SKIP")
print(f"\nDONE  PASS={p} SKIP={s}  合计={len(RESULTS)}")
