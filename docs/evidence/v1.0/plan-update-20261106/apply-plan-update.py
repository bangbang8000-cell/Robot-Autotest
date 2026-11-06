#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""2026-11-06 现状复核后，更新四份规范性文档（幂等 / 按内容匹配 / 不用行号）。

依据 docs/plan/现状复核与文档更新评估-20261106.md §5。
刻意不改：文档版本号、FR / TC / G8 十条判定本体、KPI、波次依赖、WBS 分母、产品代码。
"""
from pathlib import Path

HERE = Path(__file__).resolve()
ROOT = next(x for x in HERE.parents
            if (x / "docs/plan").is_dir() and (x / "tools").is_dir())
RESULTS = []

PLAN = "docs/自动化测试平台中长期工作规划.md"
PRD = "docs/release/v2.0-platform/PRD.md"
DEV = "docs/release/v2.0-platform/DEV-PLAN.md"
TP = "docs/release/v2.0-platform/TEST-PLAN.md"
DEC = "docs/plan/v2.0-裁决建议表-20261106.md"
WBS = "docs/plan/WBS-STATUS.md"


def repl(rel, old, new, label, expect=1):
    p = ROOT / rel
    s = p.read_text(encoding="utf-8")
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


def insert_after_line(rel, prefix, new_line, marker, label):
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


# ---------------------------------------------------------------- 1. 中长期规划
PLAN_ROW = ("| **v3.0 补记** | 2026-11-06 | **按 `现状复核与文档更新评估-20261106.md` 补记口径（不升版本号）**："
            "① 记录 WBS 真值 96 包 = 75 / 14 / 7 / 0 / 0；② v2.0 D1–D10 十项经大师两轮问卷勾定、全按推荐值 A；"
            "③ 明确 P1 五包只出两案、P2 先出书面核验纪要、C2/C3/C5 等补齐再开 W2/W3；"
            "④ 门禁路线 G1 判过（带偏差）、G2 有初稿纪要但未判过、G3/G4/G5 无核验纪要，W2 需 G5 / W3 需 G4；"
            "⑤ 七版 `05-TEST-REPORT.md` 缺 7 份属流程 ⑥ 缺口，未复跑不得生成。"
            "**版本划分 / KPI / G0–G8 检查项 / 波次依赖 / WBS 分母一项未动** | §19.1.1；不改判据，仅补记 |")

insert_after_line(PLAN, "| **v3.0** | 2026-11-06 |", PLAN_ROW,
                  "| **v3.0 补记** | 2026-11-06 |", "§19.1 修订记录加补记行")

PLAN_NOTE = """### 19.1.1 补记 · 2026-11-06 现状复核（不升版本号）

依据 `docs/plan/现状复核与文档更新评估-20261106.md`（七道流程第 ② 道，13 条已核实事实 + 差距 + 风险）。
本次**只补记口径，版本仍 v3.0**：不改版本划分、KPI、G0–G8 检查项、波次依赖、WBS 分母。
规划 §19 的「L3 变更升次版本」适用于**改判据 / 改范围**，本轮两者均未改，故按
`决策纪要-20261106.md` §5「不擅自 bump、一律补记」执行。

| 主题 | 复核后的定案 |
|---|---|
| 进度真值 | WBS **96 包 = 75 已完成 / 14 待验 / 7 待现场 / 0 未开始 / 0 进行中**（`check_wbs_consistency.py` PASS） |
| v2.0 十项裁决 | 大师两轮向导问卷勾定，**D1–D10 全按推荐值 A**（D5 仅一层编排、不做 DAG；D10 设置分级）；见 `v2.0-裁决建议表-20261106.md` §6 |
| 版本状态 | 四份 v2.0 文档仍标「待评审」，**PRD 评审通过归大师**；WBS 分母仍 **96**，34 候选包未并入，**W1 未开工** |
| 批次裁决 | P1 五包（AST-04 / CAS-03 / UX-04 / EXEC-01 / PRJ-01）**全部只出两案**；P2 **先出 G2→G5 书面核验纪要** + 七版 `05-TEST-REPORT.md`；C2/C3/C5 **等补齐再开 W2/W3** |
| 门禁路线 | **G1 11-04 判过（带 1 偏差 + 1 整改包 + 1 待补签）**；**G2 有初稿核验纪要、明确「本门未判过」**（2 项口径已定、6 项待现场）；G3 / G4 / G5 未检索到核验纪要；W2 需 G5、W3 需 G4 |
| 流程硬门槛 | 七版 `05-TEST-REPORT.md` 缺 7 份，属流程 ⑥ 缺口；**未逐版复跑不得生成报告**（§7.6 六种「假进展」） |
| 边界 | 发版 / 破坏性变更 / 对外口径、`G-授权`、ADR-0013 禁跑令仍归人；真机项如实记 `待现场` |

**本轮同步展开**（大师 11-06 裁决口径）：四份文档更新 → P1 五包两案清单 → P2 门禁书面核验纪要 →
`WS-02` 静态检查接入（视扫描结果）；七版测试报告、其余 P1 技术包、W1 列后续，顺序不可跳。

"""
repl(PLAN, "### 19.2 本规划明确不解决的问题", PLAN_NOTE + "### 19.2 本规划明确不解决的问题",
     "新增 §19.1.1 现状复核补记")

# ---------------------------------------------------------------- 2. v2.0 PRD
PRD_12 = """### 1.2 十项裁决定案（2026-11-06 大师两轮向导问卷勾定）

权威记录 `docs/plan/v2.0-裁决建议表-20261106.md` §6、`docs/plan/决策纪要-20261106.md` §8。
十项**全取 `01-ASSESSMENT.md` §6 推荐默认值 A，无一项改选**；本表是正文定案，不新增 FR、不改 §6 的 G8 十条判定本体。

| 编号 | 定案值 | 主要落点 |
|---|---|---|
| D1 | A 统一入口 8001（前端静态 + 反代） | FR-v2.0-53、ENVX-01/02、W1 |
| D2 | A 新建 pytest 专用 venv | C4 插件白名单、KRN-03/04、W3 |
| D3 | A 测试报告必须引用 1..N 个 run | FR-v2.0-01~06、RPTX、W2 |
| D4 | A 组授权映射到项目角色（取高） | FR-v2.0-40~、IAMX、W5 |
| D5 | A 项目级有序编排，**仅一层、不做 DAG** | FR-v2.0-32~39、W5 |
| D6 | A 只接本机 CLI 子进程 | AIX-01~05、W6 |
| D7 | A HTML / PDF / DOCX 三格式导出 | RPTX、G8 第 5 条、W2 |
| D8 | A 不允许同一 run 混跑双内核 | KRN、§5.1 结果归一、W3 |
| D9 | A 历史 run 不批量补报告，只给按 run 手动生成入口 | RPTX、§4 不做清单、W2 |
| D10 | A 设置分级（平台：端口 / 并发 / 保留策略 / AI 开关；项目：默认内核 / 超时 / 标签集） | NAV、FR-v2.0-14、W1 |

> **状态边界**：本文件仍为「待评审」，**PRD 评审通过归大师**；勾定不等于评审通过。
> WBS 分母仍 96、34 个候选包未并入、W1 未开工。

"""
repl(PRD, "## 2. 用户与角色", PRD_12 + "## 2. 用户与角色", "PRD §1 增 1.2 十项裁决定案")

PRD_7 = """**11-06 勾定后的前置状态（只补前置，不改 FR 判据）**

| 前置 | 现状 | 处置 |
|---|---|---|
| C1 8001 语义 | **已由 D1 定案**为统一入口 | W1 按此实现，不再等裁决 |
| C2 真实 pytest 工程 / C3 真实 RF 工程 | **仍缺** | 大师裁决等补齐再开 W2/W3；**不得用合成 / 玩具工程顶替** G8 第 4 条的真实样本 |
| C5 对外报告字段白名单 | **仍缺**（只有「默认剔除」原则） | `check_report_sensitive.py` 无判据前，EXTERNAL 报告不得对外 |
| C4 pytest venv 白名单 | 与 D2 关联，未定 | W3 开工前定，落 `system_setting` 之外的专用配置 |
| C6 本机 AI CLI 非交互 | 未探测 | W6 前由学徒本机探测并留日志，**不假称可用** |
| 外部门 G5 / G4 | **未检索到书面通过记录** | W2 需 G5、W3 需 G4；当前只有 W1 无外部门，但仍需 PRD 评审通过 |

"""
repl(PRD, "## 8. 明确的非目标", PRD_7 + "## 8. 明确的非目标", "PRD §7 补前置状态表")

insert_after_line(PRD, "| 2026-11-06 | v1.2 |",
                  "| 2026-11-06 | v1.3 | ① §1 新增「1.2 十项裁决定案」正文表（D1–D10 全按 A，从末尾补记升级入正文）；② §7 补 C1–C6 与 G4/G5 外部门的前置状态表；③ 明确「PRD 评审通过归大师」 | 按 `现状复核与文档更新评估-20261106.md` §5 回写；**55 条 FR / 10 条 NFR / §6 G8 十条判定一项未动** |",
                  "| 2026-11-06 | v1.3 |", "PRD 修订记录 v1.3")

# ---------------------------------------------------------------- 3. DEV-PLAN
DEV_32 = """### 3.2 外部门与样本前置（2026-11-06 现状复核）

依据 `docs/plan/现状复核与文档更新评估-20261106.md`。**波次编号 / 依赖 / 交付判据一项未动**，
本表只把「当前能不能开工」说清楚；「无记录」不等于「未通过」。

| 波次 | 外部门 | 样本前置 | 当前可否开工 |
|---|---|---|---|
| W1 | 无（不依赖既有批次） | **PRD 评审通过 + 34 包并入 WBS** | 否：评审未过、分母仍 96 |
| W2 | **G5 已通过** | C5 对外报告字段白名单 | 否：G5 无书面通过记录、C5 缺 |
| W3 | **G4 已通过** | C2 pytest 工程、C3 RF 工程、C4 venv 白名单 | 否：G4 无书面通过记录、C2/C3 缺 |
| W4 | W3 | C2 / C3 真实工程（导入后可执行） | 否：前置未满足 |
| W5 | W4 | C7 组织模型（D4 已定案）、C8 场景边界（D5 已定案） | 否：前置未满足（裁决类已闭环） |
| W6 | W3, W4 | C6 本机 AI CLI 非交互探测 | 否：前置未满足 |
| W7 | W1–W6 | 8 页 × 2 主题截图基线 | 否：前置未满足 |

**C1–C10 落点**（条件本体见 `v2.0-裁决建议表-20261106.md` §2）：

| 条件 | 现状 | 落点波次 | 不满足时的处置 |
|---|---|---|---|
| C1 8001 语义 | D1 已定案 A | W1 | 已闭环 |
| C2 pytest 真实工程 | 缺 | W3 / W4 / G8 第 4 条 | 等大师提供；不造玩具样本，相关 TC 记 NOT-RUN |
| C3 RF 真实工程 | 缺 | W3 / W4 / G8 第 4 条 | 同 C2 |
| C4 pytest venv 白名单 | 未定（D2 关联） | W3 | 专用 venv 与允许插件清单落定前不开 W3 |
| C5 字段白名单 | 缺 | W2 / G8 第 6 条 | EXTERNAL 导出不得对外 |
| C6 AI CLI 非交互 | 未探测 | W6 | 学徒 W6 前探测留日志；无 CLI 时按 R6 降级 |
| C7 组织模型 | D4 已定案 A | W5 | 已闭环 |
| C8 场景与用例集边界 | D5 已定案 A（一层、无 DAG） | W5 | 已闭环 |
| C9 备份覆盖新表 | V8_x 未逐波登记 | W2 起每波 | 每波同步登记，否则不回退 |
| C10 不改 WBS 台账 | 已满足（仍 96） | 并入 34 包前 | 评审通过后才改分母 130 |

> **不得跳步**：PRD 评审通过（大师）→ 34 包并入 WBS（96 → 130）→ 才开 W1。
> W2 / W3 仍分别需 G5 / G4；`G-授权` 与 ADR-0013 禁跑令不因本次复核解除。

"""
repl(DEV, "## 4. 工作包", DEV_32 + "## 4. 工作包", "DEV-PLAN §3 增 3.2 外部门与样本前置")

insert_after_line(DEV, "| 2026-11-06 | v1.1 |",
                  "| 2026-11-06 | v1.2 | 补登记：D1–D10 十项裁决经大师两轮问卷勾定、全按推荐值 A（此前只以末尾补记留痕） | 与 PRD / TEST-PLAN 的 v1.2 对齐，修订记录不留空档 |\n"
                  "| 2026-11-06 | v1.3 | ① §3 新增「3.2 外部门与样本前置」：逐波写清外部门 / 样本前置 / 当前可否开工，并列 C1–C10 落点；② 明写「PRD 评审通过 → 34 包并入 WBS → 才开 W1」不可跳 | 按 `现状复核与文档更新评估-20261106.md` §5 回写；**波次编号 / 依赖 / 交付判据 / G8 十条 / C 类包风险要求一项未动** |",
                  "| 2026-11-06 | v1.3 |", "DEV-PLAN 修订记录 v1.2 / v1.3")

# ---------------------------------------------------------------- 4. TEST-PLAN
TP_6 = """**样本未到位时的硬约束（2026-11-06 现状复核）**

C2 真实 pytest 工程 / C3 真实 RF 工程 / C5 对外报告字段白名单当前**均无**，大师裁决
「等补齐再开 W2/W3」。因此下列约束与本文件 §7 退出标准同效：

1. **不得用合成 / 玩具工程顶替**真实 pytest 与 RF 样本去申请 G8 第 4 条（「pytest / RF 各 1 个真实工程导入后可直接执行并通过」）。
2. 样本到位前，双内核矩阵 M1–M10、导入正向 2 个真实工程、EXTERNAL 字段扫描这三类 TC 一律记
   `NOT-RUN（样本未到位）`，并在 `05-TEST-REPORT.md` 写明补测计划；按退出标准第 1 条，
   **存在 NOT-RUN 时不得申请 G8**。
3. C5 字段白名单未经大师确认前，`check_report_sensitive.py` 无判据，EXTERNAL 导出**只做内部验证、不得对外**。
4. C4 pytest 专用 venv 与插件白名单未定前不跑 W3 的 pytest 用例；C6 本机 AI CLI 由 W6 前探测并留日志，不假称可用。
5. 真机 / pabot / 外部凭据项按 ADR-0013 记 `待现场`，**不自评通过**。

> 本段只补环境与样本的约束，**67 条 TC / 10 条退出标准 / M1–M10 / T4 抽样复核一项未动**。

"""
repl(TP, "## 7. 退出标准", TP_6 + "## 7. 退出标准", "TEST-PLAN §6 补样本硬约束")

insert_after_line(TP, "| 2026-11-06 | v1.2 |",
                  "| 2026-11-06 | v1.3 | ① §6 补「样本未到位时的硬约束」5 条：C2/C3/C5 缺口、禁止玩具样本顶替 G8 第 4 条、相关 TC 记 NOT-RUN、EXTERNAL 不得对外；② 与 DEV-PLAN §3.2 的前置口径对齐 | 按 `现状复核与文档更新评估-20261106.md` §5 回写；**67 条 TC / 10 条退出标准 / M1–M10 / T4 一项未动** |",
                  "| 2026-11-06 | v1.3 |", "TEST-PLAN 修订记录 v1.3")

# ---------------------------------------------------------------- 复核断言
def verify():
    prd = (ROOT / PRD).read_text(encoding="utf-8")
    dev = (ROOT / DEV).read_text(encoding="utf-8")
    wbs = (ROOT / WBS).read_text(encoding="utf-8")
    dec = (ROOT / DEC).read_text(encoding="utf-8")
    checks = [
        ("PRD 仍待评审", "**状态** 待评审" in prd),
        ("DEV-PLAN 仍待评审", "**状态** 待评审" in dev),
        ("WBS 分母仍 96", "| 96 个任务包 |" in wbs),
        ("十项勾定栏完整", dec.count("| 大师勾定 | ☑") == 10),
        ("未擅自写评审通过", "评审通过归大师" in prd),
        ("G8 判定本体未被改写", "## 6. 验收清单（G8 门禁）" in prd),
    ]
    for name, ok in checks:
        print(("PASS  " if ok else "FAIL  ") + "VERIFY :: " + name)
        if not ok:
            raise SystemExit("FAIL  复核断言未通过，拒绝交付")

verify()
print("\n" + "=" * 60)
print(f"DONE  PASS={sum(1 for r in RESULTS if r[2] == 'PASS')}  "
      f"SKIP={sum(1 for r in RESULTS if r[2] == 'SKIP')}  共 {len(RESULTS)} 处 + 6 项复核断言")
