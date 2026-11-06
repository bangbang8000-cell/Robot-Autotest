#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""2026-11-06 状态口径回写（大师裁决：AI 验收放行）。

用法（WSL 权威副本内）：
    python3 docs/evidence/v1.0/policy-20261106/apply-policy.py

纪律：所有替换按「内容前缀」定位并断言命中次数 == 1，绝不用行号；
      不做静默兜底，任一处不匹配即整体退出（此时文件可能已部分修改，按输出核对）。
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[4]
PLAN = "docs/自动化测试平台中长期工作规划.md"
DEV20 = "docs/release/v2.0-platform/DEV-PLAN.md"
REL = "docs/release/README.md"
PROC = "docs/process/版本开发流程.md"
WBS = "docs/plan/WBS-STATUS.md"

results = []


def _lines(rel):
    return (ROOT / rel).read_text(encoding="utf-8").splitlines(keepends=True)


def _save(rel, lines):
    (ROOT / rel).write_text("".join(lines), encoding="utf-8")


def _one(rel, prefix, label):
    lines = _lines(rel)
    idx = [i for i, ln in enumerate(lines) if ln.startswith(prefix)]
    if len(idx) != 1:
        print(f"FAIL  {rel} :: {label} :: 前缀命中 {len(idx)} 次（期望 1）")
        print(f"       prefix={prefix!r}")
        sys.exit(1)
    return lines, idx[0]


def _skip(rel, label):
    print(f"SKIP  {rel} :: {label} :: 已应用（幂等）")


def replace_line(rel, prefix, new_line, label):
    text = (ROOT / rel).read_text(encoding="utf-8")
    if new_line.rstrip("\n") in text:
        _skip(rel, label)
        return
    lines, i = _one(rel, prefix, label)
    nl = "\n" if lines[i].endswith("\n") else ""
    lines[i] = new_line.rstrip("\n") + nl
    _save(rel, lines)
    print(f"PASS  {rel} :: {label}")
    results.append((rel, label))


def insert_after(rel, prefix, block, label):
    first = block.strip("\n").split("\n")[0]
    if first in (ROOT / rel).read_text(encoding="utf-8"):
        _skip(rel, label)
        return
    lines, i = _one(rel, prefix, label)
    ins = [x + "\n" for x in block.strip("\n").split("\n")]
    lines[i + 1:i + 1] = ins
    _save(rel, lines)
    print(f"PASS  {rel} :: {label}")
    results.append((rel, label))


def append_text(rel, block, label, marker):
    p = ROOT / rel
    s = p.read_text(encoding="utf-8")
    if marker in s:
        _skip(rel, label)
        return
    p.write_text(s.rstrip("\n") + "\n\n" + block.strip("\n") + "\n", encoding="utf-8")
    print(f"PASS  {rel} :: {label}")
    results.append((rel, label))


# ---------------------------------------------------------------- 规划 v3.0 §7
replace_line(
    PLAN,
    "| 7 裁决与登记 | 人看证据",
    "| 7 裁决与登记 | AI 复跑机器门 + 核对证据 → 自行判定并登记状态，行内留 `AI 验收 <日期>` 戳；人做旁路建议 / 改判 | 状态变更 + 证据 | AI（判定与登记）+ 人（旁路） |",
    "§7.1 第 7 步改为 AI 判定",
)

insert_after(
    PLAN,
    "> **没有第 3 步的 PR 不算 PR**",
    """> **2026-11-06 口径变更**（大师裁决，见 `docs/plan/决策纪要-20261106.md`）：第 7 步不再挂人工签字。
> `待验 → 已完成` 由 AI 按「机器门复跑通过 + 证据可打开 + 无未收口技术缺口」自行判定；
> 缺真机 / 外部凭据 / 可选运行时记 `待现场`。人转为**旁路角色**，随时提建议、补需求或改判，不阻塞推进。
> 边界：**发版 / 破坏性变更 / 对外口径仍由大师拍板**；`G-授权`（真机 / 外部凭据）与 ADR-0013 禁跑令不因本裁决解除。""",
    "§7.1 追加口径变更说明",
)

replace_line(
    PLAN,
    "| **G-评审** |",
    "| **G-评审** | 每个 PR | AI 核对证据并留 `AI 验收` 戳；人旁路抽验、可改判 | 旁路，不计时 | **可省**（2026-11-06 放行） |",
    "§7.3 G-评审转旁路",
)

replace_line(
    PLAN,
    "| **G-门禁** |",
    "| **G-门禁** | 每道 G 门 | AI 按附录 B 核验并出书面结论，缺真机项如实记「待现场 / 有条件」；人旁路复核 | 机器门耗时 | **可省**（2026-11-06 放行；发版门仍大师） |",
    "§7.3 G-门禁转旁路",
)

insert_after(
    PLAN,
    "**人类不需要做的**",
    """> **仍然不能省的**：`G-决策`（L3 变更 / 发版 / 对外口径）与 `G-授权`（真机 / 外部系统 / 凭据）。
> 2026-11-06 放行的是**包状态判定与门禁核验**，不是把拍板权一并移交（见 `docs/plan/决策纪要-20261106.md`）。""",
    "§7.3 追加边界说明",
)

replace_line(
    PLAN,
    "| 6 | **绕过门禁**",
    "| 6 | **绕过门禁**：无机器门输出 / 无证据链，或缺真机却自评为「通过」 | 门禁记录里点不开命令与原始输出；`待现场` 项被标成 `已完成` | 无效，退回并如实登记 `待现场` |",
    "§7.6 反面清单第 6 条改写（保留禁止自评精神）",
)

append_text(
    PLAN,
    """---

*补记（2026-11-06，不升版本号）：按大师「新口径为准并回写」裁决，§7.1 / §7.3 / §7.6 三处改为「AI 验收放行、人旁路」，
与 `docs/plan/WBS-STATUS.md` 13:43 口径一致；授权与边界见 `docs/plan/决策纪要-20261106.md`。
**版本划分、KPI、门禁检查项一项未动**；G-决策 / G-授权 / 发版 / 对外口径仍归人。*""",
    "规划末尾追加口径补记",
    "*补记（2026-11-06，不升版本号）：按大师「新口径为准并回写」裁决",
)

# ------------------------------------------------------- v2.0 DEV-PLAN §3 / §3.1
replace_line(
    DEV20,
    "> 波次间不强绑日历",
    """> 波次间不强绑日历，按进入条件推进；每波完成即按五态把包登记为 `待验`，机器门复跑 + 证据核对通过后
> 由 AI 自行置 `已完成` 并留 `AI 验收 <日期>` 戳（2026-11-06 口径，与 `docs/plan/WBS-STATUS.md` 一致）；人做旁路建议。""",
    "§3 波次说明改为 AI 验收",
)

replace_line(
    DEV20,
    "| 状态 | 五态：",
    "| 状态 | 五态：未开始 / 进行中 / 待验 / 待现场 / 已完成；每包完成代码 + 单测 + 证据后才可置 `待验`；机器门 + 证据核对通过后由 AI 置 `已完成`（2026-11-06 口径） |",
    "§3.1 状态行对齐新口径",
)

replace_line(
    DEV20,
    "| AI 自主度 |",
    "| AI 自主度 | 以规划 §7 为准（不在此重复）；C 类包（KRN-01/03、IAMX-02、UIX-04）风险高，AI 判定前须额外做破坏性验证并在证据里写明，人旁路重点复核 |",
    "§3.1 AI 自主度行保留安全意图、去掉「必须人工主导」",
)

append_text(
    DEV20,
    """---

*补记（2026-11-06，不升版本号）：§3 / §3.1 的状态判定口径由「人裁决后才 `已完成`」改为「AI 验收放行」，
与 WBS 13:43 及大师裁决一致；授权见 `docs/plan/决策纪要-20261106.md`。波次依赖（W2 需 G5、W3 需 G4）、
G8 十条判定、C 类包风险要求**一项未动**。*""",
    "DEV-PLAN 末尾追加口径补记",
    "*补记（2026-11-06，不升版本号）：§3 / §3.1 的状态判定口径",
)

# ------------------------------------------------------------ release README §3.3
replace_line(
    REL,
    "| 主线版本达成 `v0.x` |",
    "| 主线版本达成 `v0.x` | 门禁检查单全绿 | AI 核验并出书面结论；人旁路复核（2026-11-06 口径） |",
    "§3.3 主线版本达成改 AI 核验",
)

insert_after(
    REL,
    "**明确不做的事**",
    """> **2026-11-06 口径变更**（大师裁决，见 `docs/plan/决策纪要-20261106.md`）：上表「主线版本达成」的批准方由人改为 AI，
> 与 `WBS-STATUS.md` 的「AI 验收放行」对齐。**「对外发布 `v1.0.0`」与「运营期功能包」两行仍为大师**——
> 发版、破坏性变更、对外口径不随本次放行转移。""",
    "§3.3 追加边界说明",
)

# ------------------------------------------------------------- 流程文档 §2.6/2.7/§5
replace_line(
    PROC,
    "| **谁做 / 谁批** | AI 执行并留证；",
    "| **谁做 / 谁批** | AI 执行并留证、按检查单核验门禁并出书面结论；**关键项人旁路抽验**（往返保真抽 10 样本、并发现场看一次、导出抽 3 份）（2026-11-06 口径） |",
    "§2.6 门禁由人签字 → AI 核验",
)

replace_line(
    PROC,
    '| **谁做 / 谁批** | AI 起草；技术负责人确认；',
    "| **谁做 / 谁批** | AI 起草并自行判定状态（机器门 + 证据核对通过即置 `已完成`，行内留 `AI 验收 <日期>` 戳）；技术负责人旁路复核、可改判（2026-11-06 口径） |",
    "§2.7 状态已完成只能由人改 → AI 判定",
)

replace_line(
    PROC,
    "| ⑥ 测试执行 | 执行、留证、如实记 BLOCKED |",
    "| ⑥ 测试执行 | 执行、留证、如实记 BLOCKED、**按检查单出书面门禁结论** | **机器门留证 + AI 核验；关键项人旁路抽验** |",
    "§5 分工表 ⑥ 去掉门禁签字",
)

replace_line(
    PROC,
    "| ⑦ 回写 |",
    "| ⑦ 回写 | 起草、按实测重标、**自行判定并改状态** | 旁路复核与改判 |",
    "§5 分工表 ⑦ 去掉人改状态",
)

replace_line(
    PROC,
    "**人只守四道闸门**",
    """**人守两道硬闸门**（决策 / 授权），评审与门禁转为 AI 核验 + 人旁路，其余时间不必在场。
但**发版、破坏性变更、对外口径，以及需要真机 / 外部凭据的授权，闸门仍不能由 AI 自己开**（2026-11-06 口径，见 `docs/plan/决策纪要-20261106.md`）。""",
    "§5 四道闸门 → 两道硬闸门 + 边界",
)

replace_line(
    PROC,
    "| v1.0 | 2026-11-02 | 首版",
    "| v1.0 | 2026-11-02 | 首版：七道环节 + 六份产物 + 回写清单 + 例外条款 | 大师 |\n| v1.0 补记 | 2026-11-06 | 按大师裁决改状态判定口径（§2.6 / §2.7 / §5）：AI 验收放行、人旁路；**七道环节与 ⑥⑦ 不可裁一项未动** | 大师 |",
    "§10 修订记录追加补记行",
)

# ------------------------------------------------------------------- WBS 授权来源
insert_after(
    WBS,
    "> **2026-11-06 起由 AI 验收放行**",
    "> **授权来源**：大师 2026-11-06 向导问卷裁决「新口径为准并回写」，见 `docs/plan/决策纪要-20261106.md`；\n> 该纪要同时写明边界：发版 / 破坏性变更 / 对外口径、`G-授权`、ADR-0013 禁跑令仍不归 AI。",
    "WBS 头部补授权来源",
)

# ----------------------------------------------------------------------- 说明
# 两份新增文档（docs/plan/决策纪要-20261106.md、本目录 README.md）为静态内容，
# 单独落盘，不由本脚本生成——脚本只负责「存量规范性文档」的口径回写。
print(f"\nDONE  {len(results)} 处存量文档回写完成（另有 2 份新增文档单独落盘）")
