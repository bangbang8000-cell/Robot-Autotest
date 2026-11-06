#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""2026-11-06 两轮向导问卷裁决落盘（幂等、按内容匹配、不用行号定位）。

改动范围：
  docs/plan/v2.0-裁决建议表-20261106.md   → 性质 / 10 项勾定栏 / §2 / §5 / 新增 §6
  docs/plan/决策纪要-20261106.md          → 新增 §8 / §7 修订记录
  docs/release/v2.0-platform/01-ASSESSMENT.md → 状态行 / R2 笔误 / §6 标题与勾定说明 / 修订记录
  docs/release/v2.0-platform/PRD.md       → 修订记录 + 补记
  docs/release/v2.0-platform/DEV-PLAN.md  → 补记
  docs/release/v2.0-platform/TEST-PLAN.md → 修订记录 + 补记

刻意不改：任何文档的「待评审」状态、G8 十条判定、波次依赖、WBS 分母 96、代码。
"""
from pathlib import Path

HERE = Path(__file__).resolve()
ROOT = next(x for x in HERE.parents
            if (x / "docs/plan").is_dir() and (x / "tools").is_dir())

RESULTS = []


def repl(rel, old, new, label, expect=1, marker=None):
    """marker 用于「old 仍保留在 new 中」的追加型替换：标记已在即跳过，保证幂等。"""
    p = ROOT / rel
    s = p.read_text(encoding="utf-8")
    if marker and marker in s:
        print(f"SKIP  {rel} :: {label}")
        RESULTS.append((rel, label, "SKIP"))
        return
    # 追加型替换（old 仍保留在 new 中）：增量已落地即跳过，否则重跑会重复追加
    if old in s and old in new:
        delta = new.replace(old, "", 1)
        if delta and delta in s:
            print(f"SKIP  {rel} :: {label}")
            RESULTS.append((rel, label, "SKIP"))
            return
    if old not in s:
        if new in s:
            print(f"SKIP  {rel} :: {label}")
            RESULTS.append((rel, label, "SKIP"))
            return
        raise SystemExit(f"FAIL  {rel} :: {label} :: 未命中且未落地")
    n = s.count(old)
    if n != expect:
        raise SystemExit(f"FAIL  {rel} :: {label} :: 命中 {n} 次，期望 {expect}")
    p.write_text(s.replace(old, new), encoding="utf-8")
    print(f"PASS  {rel} :: {label}")
    RESULTS.append((rel, label, "PASS"))


def append(rel, block, marker, label):
    p = ROOT / rel
    s = p.read_text(encoding="utf-8")
    if marker in s:
        print(f"SKIP  {rel} :: {label}")
        RESULTS.append((rel, label, "SKIP"))
        return
    p.write_text(s.rstrip("\n") + "\n\n" + block.strip("\n") + "\n", encoding="utf-8")
    print(f"PASS  {rel} :: {label}")
    RESULTS.append((rel, label, "PASS"))


def insert_after_line(rel, prefix, new_line, marker, label):
    """按内容前缀定位唯一行并在其后插入；幂等靠 marker 判断。"""
    p = ROOT / rel
    lines = p.read_text(encoding="utf-8").splitlines(keepends=True)
    if marker in "".join(lines):
        print(f"SKIP  {rel} :: {label}")
        RESULTS.append((rel, label, "SKIP"))
        return
    idx = [i for i, ln in enumerate(lines) if ln.startswith(prefix)]
    if len(idx) != 1:
        raise SystemExit(f"FAIL  {rel} :: {label} :: 前缀命中 {len(idx)} 行，期望 1")
    i = idx[0]
    nl = "\n" if lines[i].endswith("\n") else ""
    lines.insert(i + 1, new_line + nl)
    p.write_text("".join(lines), encoding="utf-8")
    print(f"PASS  {rel} :: {label}")
    RESULTS.append((rel, label, "PASS"))


# ---------------------------------------------------------------- 1. 裁决建议表
DEC = "docs/plan/v2.0-裁决建议表-20261106.md"

repl(DEC,
     "**性质** **建议，不是裁决**",
     "**性质** **已勾定裁决记录（两轮向导问卷，2026-11-06）**",
     "文档性质改为已勾定")

repl(DEC,
     "**大师勾定后才评审 PRD**，PRD 未过不开 W1。",
     "**大师勾定后才评审 PRD**，PRD 未过不开 W1。\n"
     "**勾定结果** 大师 2026-11-06 两轮向导问卷：**D1–D10 全按推荐值 A**"
     "（D5 仅一层编排、不做 DAG；D10 取分级），另三项工作口径见 §6。",
     "头部补勾定结果")

repl(DEC,
     "> **本表没有代为裁决，也没有改动任何 v2.0 文档、WBS 分母或代码。**\n"
     "> 「推荐值」列沿用 `01-ASSESSMENT.md` §6 的推荐默认，学徒只补「依据」与「改选代价」。",
     "> **v1.0 阶段**：本表只是建议，未代为裁决，也未改动任何 v2.0 文档、WBS 分母或代码。\n"
     "> 「推荐值」列沿用 `01-ASSESSMENT.md` §6 的推荐默认，学徒只补「依据」与「改选代价」。\n"
     "> **v1.1 阶段（2026-11-06）**：大师两轮向导问卷逐项勾定，结果写入每项「大师勾定」栏与 §6；\n"
     "> 按 §3 第 2 步回写四份 v2.0 文档（只补勾定记录与 R2 笔误订正，**不改「待评审」状态**），\n"
     "> **WBS 分母仍 96**，34 个候选包未并入，W1 未开工。",
     "引用块区分 v1.0 建议 / v1.1 勾定")

# 10 项勾定栏：按章节出现顺序替换，断言恰好 10 行且第 5 行是 D5 形态
DECISION_LINES = [
    "| 大师勾定 | ☑ **A**　☐ B　☐ C　— 大师 2026-11-06 两轮问卷勾定，详见 §6 |",
    "| 大师勾定 | ☑ **A**　☐ B　☐ C　— 大师 2026-11-06 两轮问卷勾定，详见 §6 |",
    "| 大师勾定 | ☑ **A**　☐ B　— 大师 2026-11-06 两轮问卷勾定，详见 §6 |",
    "| 大师勾定 | ☑ **A**　☐ B　— 大师 2026-11-06 两轮问卷勾定，详见 §6 |",
    "| 大师勾定 | ☑ **A（一层编排，不做 DAG）**　☐ B　☐ A + DAG　— 大师 2026-11-06 两轮问卷勾定，详见 §6 |",
    "| 大师勾定 | ☑ **A**　☐ B　☐ C　— 大师 2026-11-06 两轮问卷勾定，详见 §6 |",
    "| 大师勾定 | ☑ **A**　☐ B　— 大师 2026-11-06 两轮问卷勾定，详见 §6 |",
    "| 大师勾定 | ☑ **A**　☐ B　— 大师 2026-11-06 两轮问卷勾定，详见 §6 |",
    "| 大师勾定 | ☑ **A**　☐ B　— 大师 2026-11-06 两轮问卷勾定，详见 §6 |",
    "| 大师勾定 | ☑ **A（分级）**　☐ B　☐ C　— 大师 2026-11-06 两轮问卷勾定，详见 §6 |",
]
p = ROOT / DEC
lines = p.read_text(encoding="utf-8").splitlines(keepends=True)
idx = [i for i, ln in enumerate(lines) if ln.startswith("| 大师勾定 |")]
if len(idx) != 10:
    raise SystemExit(f"FAIL  {DEC} :: 勾定栏命中 {len(idx)} 行，期望 10")
if "已勾定" in "".join(lines[i] for i in idx) or "☑" in "".join(lines[i] for i in idx):
    print(f"SKIP  {DEC} :: 10 项勾定栏")
    RESULTS.append((DEC, "10 项勾定栏", "SKIP"))
else:
    if "A + DAG" not in lines[idx[4]]:
        raise SystemExit(f"FAIL  {DEC} :: 第 5 项不是 D5 形态，拒绝按序替换")
    for i, new in zip(idx, DECISION_LINES):
        nl = "\n" if lines[i].endswith("\n") else ""
        lines[i] = new + nl
    p.write_text("".join(lines), encoding="utf-8")
    print(f"PASS  {DEC} :: 10 项勾定栏")
    RESULTS.append((DEC, "10 项勾定栏", "PASS"))

repl(DEC,
     "C6 由学徒在 W6 前做一次本机探测并留日志，不需要大师操作。",
     "C6 由学徒在 W6 前做一次本机探测并留日志，不需要大师操作。\n\n"
     "**11-06 裁决**：三样实物**等大师补齐再开 W2/W3**。W1 六包不依赖它们可先开工；"
     "W2 需 G5、W3 需 G4 本来还要等门禁。学徒**不造玩具工程顶替真实样本**，"
     "样本到位前 W2/W3/W6 相关验收项如实记「有条件」，不自评通过。",
     "§2 补三样实物处理口径")

repl(DEC,
     "| 2026-11-06 | v1.0 | 初版：D1–D10 逐条建议（推荐值 / 影响面 / 依据 / 改选代价 / 勾定栏）+ C1–C10 现状 + 勾定后七步 + 边界 | 大师裁决「先出建议清单再定」；候选期不得把未裁决内容写进 WBS |",
     "| 2026-11-06 | v1.0 | 初版：D1–D10 逐条建议（推荐值 / 影响面 / 依据 / 改选代价 / 勾定栏）+ C1–C10 现状 + 勾定后七步 + 边界 | 大师裁决「先出建议清单再定」；候选期不得把未裁决内容写进 WBS |\n"
     "| 2026-11-06 | v1.1 | 两轮向导问卷勾定：D1–D10 全按推荐值 A（D5 仅一层、D10 分级），10 项勾定栏写实；§2 补三样实物口径；新增 §6 裁决记录；按 §3 第 2 步回写四份 v2.0 文档 | 大师选「逐项再问一轮」后逐项勾定；**无假设被推翻**，故回写只补记录与订正 R2 笔误，不改判据 |",
     "§5 修订记录加 v1.1")

SECTION6 = """## 6. 两轮向导问卷裁决记录（2026-11-06）

**授权形式**：大师 2026-11-06 向导问卷，两轮、共 8 题；本表 §1 的十项由第 2 轮逐组勾定。

### 6.1 第 1 轮（4 题）

| # | 题目 | 大师裁决 |
|---|---|---|
| 1 | D1–D10 十项裁决如何勾定 | **逐项再问一轮**（不选「全按推荐值」，要求每项单列选项与改选代价） |
| 2 | P1 五个需产品口径的待验包授权范围 | **全部只出两案（推荐）**：AST-04 / CAS-03 / UX-04 / EXEC-01 / PRJ-01 逐项给「实现 / 显式降级」两案与依据，不动实现、不改状态 |
| 3 | C2 / C3 / C5 三样实物如何提供 | **等补齐再开 W2/W3（推荐）**：W1 可先开工，不造玩具工程顶替 |
| 4 | P2 门禁与现场项怎么处理 | **先出书面核验纪要（推荐）**：G2→G5 逐门书面结论 + 补齐七版 `05-TEST-REPORT.md`，真机项如实记 `待现场`，不解禁、不自评通过 |

### 6.2 第 2 轮（4 题，D1–D10 逐组）

| 组 | 题目 | 大师裁决 |
|---|---|---|
| 1 | D1–D3：8001 语义 / pytest 运行时 / 报告与 run 的关系 | **本组全按 A** |
| 2 | D4–D6：组与项目角色 / 测试场景 / AI 接入方式 | **本组全按 A**（含 D5 仅一层编排、不做 DAG） |
| 3 | D7–D9：导出格式 / 双内核混跑 / 历史 run 补报告 | **本组全按 A** |
| 4 | D10：设置分级边界 | **A 分级**（平台：端口 / 并发 / 保留策略 / AI 开关；项目：默认内核 / 超时 / 标签集） |

**结论**：D1–D10 **十项全取推荐值 A**，无一项改选，故 `01-ASSESSMENT.md` §6 的推荐默认
即本版定案；**没有假设被实测或裁决推翻**，四份 v2.0 文档的回写只补勾定记录 + 订正 R2 标题笔误，
不改 FR / TC / G8 判据 / 波次依赖。

### 6.3 定案后的边界（不可越线）

| 项 | 状态 |
|---|---|
| 四份 v2.0 文档状态 | 仍为「待评审」。**PRD 评审通过归大师**，本次勾定不等于评审通过 |
| WBS 台账 | 分母仍 **96**，34 个候选包未并入，W1 未开工 |
| W2 / W3 | 仍分别需 G5 / G4 通过；当前 G4/G5 无通过记录 |
| 发版 / 破坏性变更 / 对外口径 | 仍归大师（`决策纪要-20261106.md` §4） |
| `G-授权` 与 ADR-0013 禁跑令 | 未解除；真机 / 外部凭据不因本次裁决放行 |
| 三样实物 | 等大师补齐，学徒不代造真实样本 |

### 6.4 下一步顺序（不可跳）

1. 大师补齐 C2 pytest 工程 / C3 RF 工程 / C5 字段白名单（可与下列并行）；
2. **PRD 评审通过**（大师）；
3. 34 包并入 WBS，分母 96 → 130；
4. 才开 W1 六包；W2 / W3 等 G5 / G4。
5. P1 五包只出两案、P2 书面核验纪要与七版测试报告，按第 1 轮裁决推进。
"""
append(DEC, SECTION6, "## 6. 两轮向导问卷裁决记录", "新增 §6 裁决记录")

# ---------------------------------------------------------------- 2. 决策纪要
JILU = "docs/plan/决策纪要-20261106.md"

repl(JILU,
     "| 2026-11-06 | v1.0 | 初版：补 13:43 口径变更的授权留痕、定边界、列 7 处回写与 5 批工作范围 | 大师三项裁决需要一份可追溯的书面依据；此前该变更只写在 WBS 里 |",
     "| 2026-11-06 | v1.0 | 初版：补 13:43 口径变更的授权留痕、定边界、列 7 处回写与 5 批工作范围 | 大师三项裁决需要一份可追溯的书面依据；此前该变更只写在 WBS 里 |\n"
     "| 2026-11-06 | v1.1 | 追加 §8：两轮向导问卷裁决记录（D1–D10 全按 A + P1 / 实物 / 门禁三项口径） | 大师要求「把待决策事项通过向导问卷问我」，裁决须有书面留痕 |",
     "§7 修订记录加 v1.1")

SECTION8 = """## 8. 两轮向导问卷裁决记录（2026-11-06 14:25 起）

大师要求「**把待决策事项通过向导问卷问我**」，学徒据 `v2.0-裁决建议表-20261106.md`
与 WBS 缺口出题，两轮共 8 题，裁决如下（完整题目与逐项结果见该表 §6）。

| 类别 | 裁决 |
|---|---|
| v2.0 D1–D10 | 第 1 轮选「逐项再问一轮」；第 2 轮三组 + D10 单题，**十项全按推荐值 A**（D5 仅一层编排不做 DAG，D10 分级） |
| P1 五个产品口径包 | **全部只出两案**：只给「实现 / 显式降级」两案与依据，不动实现、不改状态 |
| C2 / C3 / C5 三样实物 | **等大师补齐再开 W2/W3**；W1 不依赖可先开工，不造玩具工程顶替 |
| P2 门禁与现场 | **先出书面核验纪要**：G2→G5 逐门书面结论 + 七版 `05-TEST-REPORT.md`；真机项记 `待现场`，不解禁、不自评通过 |

**回写动作**：按裁决表 §3 第 2 步回写 `01-ASSESSMENT.md` / `PRD.md` / `DEV-PLAN.md` / `TEST-PLAN.md`
四份文档——**只补勾定记录与订正 R2 标题笔误**（「5 个一级项」→ 6，同段表格本就 6 行、
`PRD.md` FR-v2.0-07 亦为 6），不改 FR / TC / G8 十条 / 波次依赖，不改「待评审」状态。

**边界不因本次裁决转移**：PRD 评审通过、发版、破坏性变更、对外口径、`G-授权`、
ADR-0013 禁跑令仍归大师；WBS 分母仍 96，34 个候选包未并入，**W1 未开工**。
本次十项无一项改选，故**没有被推翻的假设**，四份版本文档不写「假设修订」，只留勾定记录。
"""
append(JILU, SECTION8, "## 8. 两轮向导问卷裁决记录", "新增 §8 裁决记录")

# ---------------------------------------------------------------- 3. 01-ASSESSMENT
ASS = "docs/release/v2.0-platform/01-ASSESSMENT.md"

repl(ASS,
     "**状态** 评估完成（待裁决）",
     "**状态** 评估完成（D1–D10 十项已勾定 2026-11-06；PRD 仍待评审）",
     "状态行改为十项已勾定")

repl(ASS,
     "**建议 IA（5 个一级项，最多 2 层）**",
     "**建议 IA（6 个一级项，最多 2 层）**",
     "订正 R2 标题笔误 5 → 6")

repl(ASS,
     "## 6. 待裁决项（含推荐默认值）",
     "## 6. 十项裁决（2026-11-06 已勾定，全取推荐值 A）",
     "§6 标题改为已勾定")

repl(ASS,
     "| D10 | 项目级设置与平台级设置的边界 | A 端口/并发/AI 开关归平台，内核/超时/标签归项目 | **A** | 设置页信息架构 |\n\n---\n\n## 7. 风险登记",
     "| D10 | 项目级设置与平台级设置的边界 | A 端口/并发/AI 开关归平台，内核/超时/标签归项目 | **A** | 设置页信息架构 |\n\n"
     "> **勾定结果（2026-11-06，大师两轮向导问卷）**：D1–D10 **十项全取推荐默认值 A**，\n"
     "> 无一项改选；D5 明确「仅一层编排、不做 DAG」，D10 取「A 分级」。\n"
     "> 权威记录见 `docs/plan/v2.0-裁决建议表-20261106.md` §6 与 `docs/plan/决策纪要-20261106.md` §8。\n"
     "> **本文件状态仍为「PRD 待评审」**，勾定不等于评审通过；WBS 分母仍 96，W1 未开工。\n\n"
     "---\n\n## 7. 风险登记",
     "§6 表后补勾定结果")

repl(ASS,
     "| 2026-11-06 | v1.0 | 初版：8 条逐项评估、25 条关联需求、10 项不做、10 项前置、10 项待裁决、10 项风险 | 大师 8 条需求需先评估再进 PRD |",
     "| 2026-11-06 | v1.0 | 初版：8 条逐项评估、25 条关联需求、10 项不做、10 项前置、10 项待裁决、10 项风险 | 大师 8 条需求需先评估再进 PRD |\n"
     "| 2026-11-06 | v1.1 | ① §6 十项裁决由大师两轮问卷勾定，**全取推荐值 A**，标题与表后说明同步；② 状态行由「待裁决」改为「十项已勾定，PRD 仍待评审」；③ **订正 R2 标题笔误**：「5 个一级项」→「6 个一级项」（同段表格本就 6 行、注释明写 6 是上限、PRD FR-v2.0-07 亦为 6） | 大师「逐项再问一轮」后全按推荐；**无假设被推翻**，只补勾定记录与笔误订正；笔误由裁决表 §4 核出 |",
     "修订记录加 v1.1")

# ---------------------------------------------------------------- 4. PRD / 5. TEST-PLAN / 6. DEV-PLAN
NOTE = ("*补记（2026-11-06，不升版本号）：v2.0 的 D1–D10 十项裁决已由大师两轮向导问卷勾定，"
        "**全取 `01-ASSESSMENT.md` §6 推荐默认值 A**（D5 仅一层编排不做 DAG；D10 设置分级）。"
        "本文件的 **验收判据 / 波次依赖 / G8 十条判定一项未动**，状态仍为「待评审」——"
        "**PRD 评审通过归大师**，勾定不等于评审通过；WBS 分母仍 96、34 个候选包未并入、W1 未开工。"
        "权威记录见 `docs/plan/v2.0-裁决建议表-20261106.md` §6 与 `docs/plan/决策纪要-20261106.md` §8。*")
MARK = "**PRD 评审通过归大师**"

PRD = "docs/release/v2.0-platform/PRD.md"
insert_after_line(
    PRD,
    "| 2026-11-06 | v1.1 | ① 新增 §1.1",
    "| 2026-11-06 | v1.2 | D1–D10 十项裁决经大师两轮向导问卷勾定、全取推荐值 A；本文件 FR / 10 项验收 / G8 判定本体一项未动 | 勾定不等于 PRD 评审通过，状态保持「待评审」；留痕见 `v2.0-裁决建议表-20261106.md` §6 |",
    "| 2026-11-06 | v1.2 | D1–D10 十项裁决",
    "修订记录按序追加 v1.2")

append(PRD, NOTE, MARK, "PRD 末尾补勾定补记")

DEV = "docs/release/v2.0-platform/DEV-PLAN.md"
append(DEV, NOTE, MARK, "DEV-PLAN 末尾补勾定补记")

TP = "docs/release/v2.0-platform/TEST-PLAN.md"
insert_after_line(
    TP,
    "| 2026-11-06 | v1.1 | ① 新增 §2.1",
    "| 2026-11-06 | v1.2 | D1–D10 十项裁决经大师两轮向导问卷勾定、全取推荐值 A；67 条 TC / 10 条退出标准 / 波次出口一项未动 | 勾定不等于 PRD 评审通过；候选期仍不改 WBS 台账 |",
    "| 2026-11-06 | v1.2 | D1–D10 十项裁决",
    "修订记录按序追加 v1.2")

append(TP, NOTE, MARK, "TEST-PLAN 末尾补勾定补记")

# ---------------------------------------------------------------- 汇总
print("\n" + "=" * 60)
print(f"DONE  PASS={sum(1 for r in RESULTS if r[2] == 'PASS')}  "
      f"SKIP={sum(1 for r in RESULTS if r[2] == 'SKIP')}  共 {len(RESULTS)} 处")
