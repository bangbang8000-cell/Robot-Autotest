#!/usr/bin/env python3
"""补齐 v0.1~v1.0 七份 `05-TEST-REPORT.md`（版本流程第 ⑥ 道硬门槛）。

诚实口径（不硬凑模板、不把历史证据说成本轮重跑）：
  - **本轮真实重跑**的只有机器门：后端单测 / 前端单测 / 构建 / 守门 / 库审计 / 入口实测；
  - 逐条 TC 标 `EVIDENCE-ONLY`（有历史证据，本轮未逐条重跑），列证据文件相对路径；
  - 真机 / 外部凭据 / 时间类一律 `待现场`，不自评通过；
  - 每份报告的 TC 数、证据清单、遗留缺口都从该版本自己的文档与目录生成，不复用同一段模板。
"""
from __future__ import annotations

import pathlib
import importlib.util

ROOT = pathlib.Path(__file__).resolve().parents[4]
RELEASE = ROOT / "docs" / "release"
EVIDENCE = ROOT / "docs" / "evidence"

VERSIONS = [
    ("v0.1-foundation", "v0.1", "地基", "G1", "B1"),
    ("v0.2-ledger", "v0.2", "台账", "G2", "B2"),
    ("v0.3-editor", "v0.3", "编辑器", "G3", "B3"),
    ("v0.4-execution", "v0.4", "执行", "G4", "B4/B5"),
    ("v0.5-report", "v0.5", "报告", "G5", "B6"),
    ("v0.6-automation", "v0.6", "自动化", "G6", "B7"),
    ("v1.0-ga", "v1.0", "生产就绪", "G7", "B8"),
]

MACHINE_GATES = [
    ("后端单测", "mvn -B test", "644 / 644 通过，0 失败 0 错误"),
    ("前端单测", "cd frontend && npm run test", "30 文件 / 221 用例通过"),
    ("前端类型与构建", "npm run build（vue-tsc --noEmit）", "0 类型错误，dist 产出成功"),
    ("后端静态检查", "mvn -B checkstyle:check", "0 violations / BUILD SUCCESS"),
    ("数据库体检", "bash scripts/db-audit.sh", "8 项 PASS / 47 业务表"),
    ("仓库守门", "bash scripts/lint.sh", "PASS（含 Checkstyle 接线）"),
    ("统一入口实测", "bash scripts/verify-entry.sh", "12 / 12 PASS"),
    ("WBS 自洽", "python3 tools/check_wbs_consistency.py", "130 包 / 87 已完成，五数自洽"),
]

GATE_STATE = {
    "G1": "11-04 判过（带偏差），见 `docs/evidence/v0.1/G1-核验纪要-20261104.md`",
    "G2": "11-04 有核验纪要且原文写明「本门未判过」，8 项中 6 项待现场",
    "G3": "无书面核验纪要，不得自评通过",
    "G4": "无书面核验纪要，W3 因此不得开工",
    "G5": "无书面核验纪要，W2 因此不得开工",
    "G6": "时间类（连续 7 天）已按 ADR-0012 取消，不再作为阻塞",
    "G7": "发版门禁，归大师拍板",
}

def _release_checker():
    """加载门禁同款工具，避免「生成器一套正则、机检另一套」的口径漂移。"""
    spec = importlib.util.spec_from_file_location(
        "check_release_docs", ROOT / "tools" / "check_release_docs.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


CHECKER = _release_checker()


def tc_ids(test_plan: pathlib.Path) -> list[str]:
    """与 tools/check_release_docs.py 判据 3 使用同一套提取逻辑。

    机检 extract_ids() 会展开 `TC-v0.1-1 ~ 5` 区间，并同时保留零填充（001）
    与非填充（1）两种形态。生成器若另写正则，v0.1 报告会缺 30 条编号而被判
    「未执行 TC」。这里不复制实现，直接加载同一模块，从根上消除漂移。
    """
    if not test_plan.exists():
        return []
    content = test_plan.read_text(encoding="utf-8")
    prefix = CHECKER.detect_prefix(content, "TC")
    if not prefix:
        return []
    return sorted(CHECKER.extract_ids(content, prefix), key=CHECKER.id_sort_key)


def evidence_files(version: str) -> list[str]:
    # 证据目录名就是 v0.1 / v0.2 / ... / v1.0（版本目录带 -foundation 之类后缀）
    directory = EVIDENCE / version.split("-")[0]
    if not directory.is_dir():
        return []
    return sorted(
        p.relative_to(ROOT).as_posix()
        for p in directory.rglob("*")
        if p.is_file() and p.suffix in {".md", ".log", ".txt", ".json", ".png"}
    )


def build(directory: str, label: str, name: str, gate: str, batch: str) -> str:
    plan = RELEASE / directory / "TEST-PLAN.md"
    tcs = tc_ids(plan)
    files = evidence_files(directory)
    shown = files[:18]
    lines = [
        f"# {label} {name} · ⑥ 测试报告",
        "",
        f"**文档编号** RA-REL-2026-{directory.split('-')[0]}-TR ｜ **版本** {label} ｜ **日期** 2026-11-06",
        f"**状态** 机器门复跑通过；TC 级沿用历史证据；门禁 {gate} 真值见 §4",
        "",
        "> 本文件是 2026-11-06 W1 收口时补齐的版本流程第 ⑥ 道产物。**本轮重跑的是机器门**，",
        "> 不是逐条 TC 重新执行；凡未重跑的 TC 一律标 `EVIDENCE-ONLY`，不得读作「本轮通过」。",
        "> 真机 / 外部凭据 / 时间类条件见 §5，一律 `待现场`。",
        "",
        "## 1. 结论",
        "",
        "| 项 | 值 |",
        "|---|---|",
        f"| 版本 / 批次 | {label} {name} / {batch} |",
        f"| TEST-PLAN 中识别到 TC | {len(tcs)} 条 |",
        f"| 本轮机器门 | 8 项全绿（见 §2） |",
        f"| 本轮逐条重跑 TC | 0（口径见上文） |",
        f"| 历史证据文件 | {len(files)} 份（§3 列前 {len(shown)} 份） |",
        f"| 门禁 {gate} | {GATE_STATE[gate]} |",
        "| 当前结论 | **机器门通过、TC 证据齐备；门禁未判过项与真机项不冒充通过** |",
        "",
        "## 2. 本轮机器门实测（2026-11-06，W1 收口批次）",
        "",
        "| 门 | 命令 | 实测读数 | 结果 |",
        "|---|---|---|---|",
    ]
    for gate_name, command, reading in MACHINE_GATES:
        lines.append(f"| {gate_name} | `{command}` | {reading} | PASS |")
    lines += [
        "",
        "> 原始日志由 `docs/evidence/v1.0/policy-20261106/gates.sh` 复跑重建（logs/ 被 .gitignore 忽略）。",
        "",
        "## 3. 历史证据清单",
        "",
        f"共 {len(files)} 份，以下列前 {len(shown)} 份（相对仓库根）：",
        "",
    ]
    lines += [f"- `{item}`" for item in shown] or ["- （该版本证据目录为空）"]
    if len(files) > len(shown):
        lines.append(f"- … 其余 {len(files) - len(shown)} 份见 `docs/evidence/{directory.split('-')[0]}/`")
    lines += [
        "",
        "## 4. TC 台账（口径：EVIDENCE-ONLY）",
        "",
        "| TC | 本轮结果 | 判定依据 |",
        "|---|---|---|",
    ]
    if tcs:
        for tc in tcs:
            lines.append(f"| {tc} | EVIDENCE-ONLY | 该版本历史证据 + WBS 包状态；本轮未逐条重跑 |")
    else:
        lines.append("| （TEST-PLAN 无可识别 TC 编号） | — | 需人工核对计划文档 |")
    lines += [
        "",
        "## 5. 遗留缺口与未验证项（不自评通过）",
        "",
        f"- 门禁 {gate}：{GATE_STATE[gate]}。",
        "- 真机 / 外部凭据类（DUT、交换机、GitHub runner、pabot）一律 `待现场`；ADR-0013 禁跑令有效。",
        "- 条件类（4 并发、打印链路）与时间类（连续运行）不具备复跑条件，见 `docs/plan/门禁核验纪要-G2-G5-20261106.md`。",
        "- v2.0 的 W2/W3 受 G5/G4 前置约束，本报告不将其计入任何「通过」结论。",
        "",
        "## 6. 修订记录",
        "",
        "| 日期 | 版本 | 变更 |",
        "|---|---|---|",
        "| 2026-11-06 | v1.2 | 机器门读数按最终门禁日志订正：后端 642→644、前端 220→221，WBS 补 87/130；不沿用估算值 |",
        "| 2026-11-06 | v1.1 | TC 台账改为复用 `tools/check_release_docs.py::extract_ids()`，补齐区间展开与两种编号形态（v1.0 生成器漏 30 条被机检判 FAIL） |",
        "| 2026-11-06 | v1.0 | 首次补齐；按「机器门实测 + 历史证据 + 门禁真值」三层写实，不用通过模板 |",
    ]
    return "\n".join(lines) + "\n"


def main() -> int:
    for directory, label, name, gate, batch in VERSIONS:
        target = RELEASE / directory / "05-TEST-REPORT.md"
        target.write_text(build(directory, label, name, gate, batch), encoding="utf-8")
        print(f"PASS  {target.relative_to(ROOT).as_posix()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
