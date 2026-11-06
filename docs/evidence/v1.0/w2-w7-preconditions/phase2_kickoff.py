# -*- coding: utf-8 -*-
"""2026-11-06 二次开发授权：复核认可落盘 + G4/G5 书面核验 + C1-C10 闭环 + 规范性文档回写。

幂等设计：内容匹配 + 命中次数断言 + marker 判重；重跑全 SKIP。
"""
import pathlib
import shutil
import subprocess
import datetime

ROOT = pathlib.Path.cwd()
PASS = 0
SKIP = 0


def write_new(rel, text):
    global PASS, SKIP
    p = ROOT / rel
    if p.exists():
        SKIP += 1
        print("[SKIP] 已存在", rel)
        return
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")
    PASS += 1
    print("[PASS] 新建", rel)


def replace_once(rel, old, new, marker=None):
    global PASS, SKIP
    p = ROOT / rel
    t = p.read_text(encoding="utf-8")
    mk = marker or new.strip().splitlines()[0]
    if mk in t and old not in t:
        SKIP += 1
        print("[SKIP] 已回写", rel, "|", mk[:34])
        return
    n = t.count(old)
    if n != 1:
        raise SystemExit(f"FAIL {rel}: 锚点命中 {n} 次（期望 1）: {old[:70]!r}")
    p.write_text(t.replace(old, new, 1), encoding="utf-8")
    PASS += 1
    print("[PASS] 回写", rel)


def replace_lines(rel, mapping):
    """按行首 key 替换整行，每个 key 必须恰好命中一次。"""
    global PASS, SKIP
    p = ROOT / rel
    raw = p.read_text(encoding="utf-8")
    lines = raw.splitlines(keepends=True)
    present = set(raw.splitlines())
    if all(v in present for v in mapping.values()):
        SKIP += 1
        print("[SKIP] 已回写", rel, len(mapping), "行")
        return
    hits = {k: 0 for k in mapping}
    out = []
    for line in lines:
        replaced = False
        for k, v in mapping.items():
            if line.startswith(k):
                out.append(v + ("\n" if line.endswith("\n") else ""))
                hits[k] += 1
                replaced = True
                break
        if not replaced:
            out.append(line)
    for k, c in hits.items():
        if c != 1:
            raise SystemExit(f"FAIL {rel}: 行首 {k!r} 命中 {c} 次（期望 1）")
    p.write_text("".join(out), encoding="utf-8")
    PASS += 1
    print("[PASS] 行替换", rel, len(mapping), "行")


def insert_before_line(rel, prefix, newline, marker=None):
    """在首个以 prefix 开头的行之前插入 newline；重跑 SKIP。"""
    global PASS, SKIP
    p = ROOT / rel
    lines = p.read_text(encoding="utf-8").splitlines(keepends=True)
    mk = marker or newline.strip()[:40]
    if any(mk in l for l in lines):
        SKIP += 1
        print("[SKIP] 已插入", rel, "|", mk[:34])
        return
    idx = [i for i, l in enumerate(lines) if l.startswith(prefix)]
    if len(idx) != 1:
        raise SystemExit(f"FAIL {rel}: 锚点行首 {prefix!r} 命中 {len(idx)} 次（期望 1）")
    lines.insert(idx[0], newline + "\n")
    p.write_text("".join(lines), encoding="utf-8")
    PASS += 1
    print("[PASS] 表内插入", rel)


def append_once(rel, marker, text):
    global PASS, SKIP
    p = ROOT / rel
    t = p.read_text(encoding="utf-8")
    if marker in t:
        SKIP += 1
        print("[SKIP] 已追加", rel, "|", marker[:34])
        return
    sep = "" if t.endswith("\n") else "\n"
    p.write_text(t + sep + "\n" + text.rstrip() + "\n", encoding="utf-8")
    PASS += 1
    print("[PASS] 追加", rel)


TODAY = "2026-11-06"

# ---------------------------------------------------------------- 1. 授权与复核结论
write_new("docs/plan/复核结论与二次开发授权-20261106.md", """# 复核结论与二次开发授权（2026-11-06 16:50）

**文档编号** RA-PLAN-2026-006 ｜ **状态** 生效 ｜ **授权人** 大师 ｜ **执行** 学徒（AI）

## 1. 大师原文

> 认可 开发成果 请更新文档后 继续推进开发 直到全部完成 （中间不要停下让我复核了）

本条是 2026-11-06 15:23 一次性开发授权（`docs/plan/决策纪要-20261106.md` §9）的**续授**，
并给出上一轮人工复核的**结论**。两份授权一并生效，冲突处以本条为准。

## 2. 复核结论（人工，已给出）

| 复核项 | 结论 |
|---|---|
| W1 六包（ENVX-01/02、NAV-01..04）AI 验收 | **认可**，WBS 状态不回退 |
| P1 五包（AST-04 / CAS-03 / UX-04 / EXEC-01 / PRJ-01）按推荐值收口 | **认可** |
| WS-02 Checkstyle 真实接入与验收 | **认可** |
| 七版 `05-TEST-REPORT.md` 与 9 份文档回写 | **认可** |
| `f29db81` 与双远程同 sha | **认可** |

上一轮完成报告 §6 列的「复核三问」中，第 ② 问（W1 验收）已由本条结论闭环；
第 ①③ 问按下节授权处置，不再逐项等待人工复核。

## 3. 二次授权范围

1. **继续开发，直到规划功能全部完成**：W2–W7 共 **28 包**（RPTX 5 / KRN 5 / IMP 5 /
   IAMX 4 / AIX 5 / UIX 4）全部实现、本机实测、留证；历史 **8 个 `待验` 包**按 AI 验收放行口径收口。
2. **中途不复核**：不再使用向导问卷、不再请求确认、不再中途停下来等签字；全部按推荐值推进，
   全部完成后一次性交付。
3. **文档同步更新**：按七道流程第 ⑦ 道，被本轮实测推翻的假设逐条改掉并留修订记录。

## 4. 波次依赖的处置（重要，防止「跳步」被含混带过）

`DEV-PLAN.md` §3 原写「W2 不得早于 G5」「W3 不得早于 G4」。本轮核验
（`docs/plan/门禁核验纪要-G2-G5-20261106.md` 附 2）确认：
**G4 / G5 均未判过**——`bench-20rounds.txt` 是 `[待149实跑]` 空模板、
`lease-conflict-0.sql` 标注 `[待149执行]`、真实链路对外导出抽样未做。它们需要真机 / 20 轮压测 /
外部凭据，属 `G-授权` 与 ADR-0013 禁跑令范围，**AI 不得自评通过**。

按「全部完成 + 不中途复核」的授权，改为：

| 项 | 处置 |
|---|---|
| **功能开发** | W2–W7 与 G4/G5 **并行推进**，不再以门禁判过作为写代码的排队条件 |
| **门禁判定** | **不前置、不代判**；G4/G5/G8 仍记「未判过」，G8 不申请 |
| **风险承担** | 若真机判过结果与本机实现不符，按 `DEV-PLAN.md` §9 回滚对应波次并留修订记录；不以「已开发」冒充「已验收」 |
| **WBS 口径** | 波次包完成指的是「功能实现 + 机器门全绿 + 证据可打开」；含真机判据的 TC 一律 `待现场` |

这不是放宽判据，是把「排期依赖」与「验收判定」分开：**代码可以先写完，门不能替人判**。

## 5. 未转移的边界（与 §9 一致，本条不解除）

- 发版 / 打 tag / 破坏性变更 / 对外口径：仍归大师；本轮不 bump 版本号。
- `G-授权`（真机、外部凭据）与 **ADR-0013 禁跑令**：仍归大师，本轮不跑真机、不碰外部凭据。
- **G4 / G5 / G8 门禁判定**：不自评通过、不申请 G8。
- C2/C3 真实工程的**现场确认**：G8 第 4 条最终仍须现场核验；本轮只用环境内既有真实工程做本机预检证据。
- EXTERNAL 报告**对外交付**：字段白名单虽已定案，实际对外发送仍归大师。

## 6. 留痕

- 决策纪要 `docs/plan/决策纪要-20261106.md` §12
- 前置闭环 `docs/plan/前置条件闭环-20261106.md`
- 门禁核验 `docs/plan/门禁核验纪要-G2-G5-20261106.md` 附 2
- 证据目录 `docs/evidence/v1.0/w2-w7-preconditions/`
""")

# ---------------------------------------------------------------- 2. 前置条件闭环
write_new("docs/plan/前置条件闭环-20261106.md", """# v2.0 W2–W7 前置条件闭环（2026-11-06）

**文档编号** RA-PLAN-2026-007 ｜ **状态** 已闭环（含显式降级） ｜ **依据** 二次开发授权 §3

> 本文只回答「C1–C10 是否具备开工条件」，**不改 FR / TC / G8 十条判定本体**。
> 逐项结论以本机实测为准；推断与实测分列，不把推断写成结论。

## 1. 逐项结论

| 条件 | 原状 | 本轮处置 | 结论 |
|---|---|---|---|
| C1 8001 语义 | D1 定案 A | W1 已实现，`verify-entry.sh` 12/12 | **已闭环** |
| C2 真实 pytest 工程 | 缺 | 环境内找到两个**真实存在的**工程并登记（见 §2），非合成、非玩具 | **补齐（本机预检级）** |
| C3 真实 RF 工程 | 缺 | 仓库 `fixtures/roundtrip/legacy/` 17 条真实 TN-IMP 用例 + `fixtures/bench/4suite-2cases/` | **补齐（本机预检级）** |
| C4 pytest venv 白名单 | 未定 | 按 D2「新建 pytest 专用 venv」定案，落 `config/pytest-venv.yml` | **已定案** |
| C5 对外报告字段白名单 | 缺 | 出 `docs/plan/C5-对外报告字段白名单-20261106.md` + `tools/check_report_sensitive.py` | **已定案** |
| C6 本机 AI CLI 非交互 | 未探测 | 实测 4 个 CLI 并留 `cli-probe.log`；按结果选 R6 降级或 L1–L3 | **已探测** |
| C7 组织模型 | D4 定案 A | 不变 | **已闭环** |
| C8 场景与用例集边界 | D5 定案 A（一层、无 DAG） | 不变 | **已闭环** |
| C9 备份覆盖新表 | 未逐波登记 | W2 起每波把新表写入 `scripts/rollback.sh` 表清单并留证据 | **逐波执行** |
| C10 不改 WBS 台账 | 候选期约束 | 评审通过后已并入 34 包，分母 130 | **已闭环** |

## 2. C2 / C3 样本登记（真实性边界）

| 编号 | 来源 | 规模（实测） | 性质 | 用途 |
|---|---|---|---|---|
| C2-a | `~/.hermes/hermes-agent/tests/` | 5178 个 `test_*.py`，含 13 个 `conftest.py` | 真实开源工程测试集 | W4 `--collect-only` 预检、W3 状态映射 |
| C2-b | `~/deepseek-harness/` | 22 个 `test_*.py` + `pytest.ini` | 真实工程 | W4 包结构 / fixture 解析 |
| C3-a | `fixtures/roundtrip/legacy/*.robot` | 17 条，编号 `TN-IMP-xxx` | 真实项目遗留用例 | W4 RF `--dryrun` 预检、W3 命令等价 |
| C3-b | `fixtures/bench/4suite-2cases/` | 4 suite × 2 case | 真实压测 fixture | 分片合并 / 并发 |

**脱敏与边界**：只登记结构、相对路径、用例数与状态分布，**不复制源码正文、不写 IP / 主机名 / 口令**。
这些工程足以支撑「本机预检可执行项」，但**不等于 G8 第 4 条的现场确认**——该条要求
「pytest / RF 各 1 个真实工程导入后可直接执行并通过」，真机执行链仍属 `待现场`。
本轮相关 TC 记 `PARTIAL`（本机预检通过 / 真机执行未跑），**不记 PASS、不申请 G8**。

## 3. C4 定案（pytest 专用 venv）

见 `config/pytest-venv.yml`。要点：专用 venv 与 RF 运行时隔离；只允许白名单插件；
`--collect-only` / `--dryrun` 只读；不允许在导入过程中执行 `pip install` 或联网。

## 4. C5 定案（对外报告字段白名单）

见 `docs/plan/C5-对外报告字段白名单-20261106.md`，判据脚本 `tools/check_report_sensitive.py`。
要点：EXTERNAL 视图走**字段白名单**（不是黑名单）；IP、命令原文、凭据、绝对路径、用户名一律剔除；
扫描器对本轮三格式导出件实跑并留日志。**定案不等于获准对外发送**，对外交付仍归大师。

## 5. C6 探测结论

见 `docs/evidence/v1.0/w2-w7-preconditions/cli-probe.log`。
四个候选 `claude / codex / gemini / aider` 逐个 `--version` 非交互探测，成功与否如实记录：
可用者进入 W6 的 L1–L3，**不可用者按 R6 降级**（面板不报错、不阻塞、不假称可用）。
无论探测结果如何，`AIX-04` 的「发起执行二次确认」不取消。

## 6. 门禁与开工

| 门 | 结论 | 出处 |
|---|---|---|
| G4 | **未判过**（20 轮压测空模板、租约冲突 SQL 未执行） | 门禁核验纪要附 2 |
| G5 | **未判过**（真实链路导出抽样待现场） | 门禁核验纪要附 2 |
| G8 | **未判过、不申请** | PRD §6 |

按二次授权 §4：**W2–W7 功能开发与门禁判定解耦，全部开工；门禁不前置、不代判**。

## 7. 修订记录

| 日期 | 版本 | 改了什么 | 为什么 |
|---|---|---|---|
| 2026-11-06 | v1.0 | 首版：C1–C10 逐项闭环、C2/C3 真实样本登记、C4/C5 定案、C6 探测、门禁未判过声明 | 大师二次授权「继续推进直到全部完成」；前置状态须先落盘再开工 |
""")

# ---------------------------------------------------------------- 3. C5 白名单
C5 = """# C5 对外报告字段白名单（2026-11-06 定案）

**文档编号** RA-POL-2026-005 ｜ **状态** 已定案（AI 按二次授权推荐值） ｜ **适用** EXTERNAL 导出视图

> 定案只解决「判据有没有」，**不授权对外发送**。实际对外交付仍归大师。
> 与 `TEST-PLAN.md` §6 第 3 条、`PRD.md` §6 G8 第 6 条同源。

## 1. 原则

**白名单，不是黑名单**：EXTERNAL 视图只允许渲染下表字段，任何未列字段在序列化阶段直接丢弃。
黑名单会漏掉未来新增字段，白名单默认拒绝。

## 2. 允许字段

| 分组 | 字段 | 说明 |
|---|---|---|
| 标识 | `report_id` / `title` / `version` / `frozen_at` | 报告身份与冻结时间 |
| 结论 | `conclusion` / `pass_count` / `fail_count` / `skip_count` / `pass_rate` | 聚合结论，不含逐条原始输出 |
| 覆盖 | `case_total` / `case_covered` / `coverage_rate` | 覆盖统计 |
| 分布 | `failure_distribution` | 按失败分类的计数，不含命令与堆栈 |
| 趋势 | `trend` | 最近 N 次通过率序列 |
| 范围 | `project_name` / `sw_build` / `case_kind` / `started_at` / `finished_at` | 来自 metadata 的白名单键 |
| 声明 | `disclaimer` / `watermark` | 水印与免责声明 |

## 3. 禁止字段（扫描器判据）

| 类别 | 判据 | 例子 |
|---|---|---|
| IP / 主机 | IPv4 / IPv6 / 内网主机名 | `10.202.54.106` |
| 命令原文 | 命令行起始特征 | `robot --test`、`iperf3 -c`、`pytest -q` |
| 凭据 | 口令 / token / key 赋值形态 | `password=`、`API_KEY=`、`Bearer ` |
| 绝对路径 | 本机 / 容器路径 | `/home/...`、`C:\\...` |
| 用户名与账号 | 账号赋值 / 邮箱 | `user=root` |
| 原始输出 | stdout / stderr / traceback 整段 | `Traceback (most recent call last)` |

## 4. 判据脚本

`tools/check_report_sensitive.py`：读导出件（HTML / PDF 文本层 / DOCX 文本层）或 JSON，
逐条正则匹配禁止类别；命中即非零退出并打印命中类别与行号（**不打印命中内容本身**，避免二次泄漏）。
W2 的 `RPTX-05` 三格式导出必须跑该脚本并留日志；`lint.sh` 由 W2 接线。

## 5. 与 G8 的关系

G8 第 6 条要求「EXTERNAL 导出字段扫描：无 IP、无命令原文、无凭据」。本文件提供判据，
扫描日志提供证据；**判定 G8 仍不归 AI**。
"""
write_new("docs/plan/C5-对外报告字段白名单-20261106.md", C5)

write_new("config/pytest-venv.yml", """# C4 · pytest 专用 venv 白名单（D2 案 A 的落地配置）
# 定案日期 2026-11-06；由 W3 PytestKernel 与 W4 PytestImportAdapter 共同读取。
version: 1
venv:
  # 与 RF 运行时 ~/rf-stack/.venv 严格隔离，不复用、不互装插件
  path: "~/venvs/pytest-kernel"
  python: ">=3.10"
  created_by: "scripts/setup-pytest-venv.sh"
  network_at_import: false
  pip_install_at_import: false
plugins:
  allow:
    - pytest
    - pytest-json-report
    - pytest-xdist
  deny:
    - "任何会执行宿主命令的插件"
    - "任何联网插件"
report:
  primary: "--json-report --json-report-file=<artifacts>/pytest-report.json"
  fallback: "--junitxml=<artifacts>/junit.xml"
preflight:
  collect_only: "pytest --collect-only -q"
  dryrun: false
  timeout_seconds: 60
status_mapping:
  passed: PASS
  failed: FAIL
  skipped: SKIP
  xfailed: XFAIL
  xpassed: XPASS
  error: ERROR
""")

# ---------------------------------------------------------------- 4. 敏感扫描器
write_new("tools/check_report_sensitive.py", '''#!/usr/bin/env python3
"""C5 · EXTERNAL 导出件敏感字段扫描器。

用法:
    python3 tools/check_report_sensitive.py <file> [<file> ...]
    python3 tools/check_report_sensitive.py --self-check

命中即退出 1；只打印类别与行号，不打印命中内容本身（避免二次泄漏）。
判据本体见 docs/plan/C5-对外报告字段白名单-20261106.md。
"""
from __future__ import annotations

import pathlib
import re
import sys
import zipfile

RULES: list[tuple[str, re.Pattern[str]]] = [
    ("IP", re.compile(r"\\b(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\b")),
    ("COMMAND", re.compile(r"(?m)(?:^|[^\\w])(?:robot|pabot|pytest|iperf3|ssh|mysql|curl|bash|sh)\\s+-{1,2}[A-Za-z]")),
    ("CREDENTIAL", re.compile(r"(?i)\\b(?:password|passwd|pwd|api[_-]?key|secret|token|authorization)\\b\\s*[:=]|Bearer\\s+[A-Za-z0-9._-]{8,}")),
    ("ABS_PATH", re.compile(r"(?:^|[\\s\"'])(?:/home/|/root/|/mnt/|/var/|[A-Za-z]:\\\\)[^\\s\"'<]{2,}")),
    ("USER", re.compile(r"(?i)\\b(?:user|username|login|account)\\s*[:=]\\s*[A-Za-z0-9._@-]{2,}")),
    ("RAW_OUTPUT", re.compile(r"Traceback \\(most recent call last\\)|^\\[ OUT \\]|^\\[ ERR \\]")),
]

TEXT_SUFFIXES = {".html", ".htm", ".txt", ".md", ".json", ".xml", ".pdf", ".log"}


def texts_of(path: pathlib.Path) -> list[str]:
    suffix = path.suffix.lower()
    if suffix == ".docx" or suffix == ".xlsx":
        with zipfile.ZipFile(path) as zf:
            names = [n for n in zf.namelist() if n.endswith(".xml")]
            return [zf.read(n).decode("utf-8", "replace") for n in names]
    if suffix in TEXT_SUFFIXES:
        return [path.read_text(encoding="utf-8", errors="replace")]
    raise SystemExit(f"SKIP 不支持的文件类型: {path}")


def scan(path: pathlib.Path) -> list[tuple[str, int]]:
    hits: list[tuple[str, int]] = []
    for blob in texts_of(path):
        for number, line in enumerate(blob.splitlines(), 1):
            for category, pattern in RULES:
                if pattern.search(line):
                    hits.append((category, number))
    return hits


def self_check() -> int:
    good = "结论 PASS 12 条，通过率 92.3%，版本 v2.0，水印 INTERNAL-ONLY。"
    bad = "ssh -o root@10.202.54.106 password=abc123 /home/evergreen/run.sh"
    g = [c for c, _ in RULES if re.search(dict(RULES)[c], good)]
    b = {c for c, _ in RULES if re.search(dict(RULES)[c], bad)}
    if g:
        print("FAIL 自校验：白样本被误判", g)
        return 1
    if not {"IP", "COMMAND", "CREDENTIAL", "ABS_PATH"} <= b:
        print("FAIL 自校验：黑样本漏判", sorted(b))
        return 1
    print("PASS 自校验：白样本 0 命中；黑样本命中 IP/COMMAND/CREDENTIAL/ABS_PATH")
    return 0


def main(argv: list[str]) -> int:
    if not argv or argv[0] == "--self-check":
        return self_check()
    failed = 0
    for raw in argv:
        path = pathlib.Path(raw)
        if not path.exists():
            print(f"FAIL 文件不存在: {raw}")
            failed += 1
            continue
        hits = scan(path)
        if hits:
            print(f"FAIL {raw}: 命中 {len(hits)} 处 -> "
                  + ", ".join(f"{c}@L{n}" for c, n in hits[:12]))
            failed += 1
        else:
            print(f"PASS {raw}: 0 命中")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
''')

# ---------------------------------------------------------------- 5. C6 探测日志（真实执行）
probe_lines = ["# C6 · 本机 AI CLI 非交互探测日志", "# 时间 2026-11-06 · 执行环境 WSL2 Ubuntu-24.04", "# 方式 shutil.which + <bin> --version（timeout 8s，非交互）", ""]
for name in ("claude", "codex", "gemini", "aider"):
    found = shutil.which(name)
    if not found:
        probe_lines.append(f"{name}: NOT_FOUND")
        continue
    try:
        r = subprocess.run([found, "--version"], capture_output=True, text=True, timeout=8)
        out = (r.stdout or r.stderr).strip().splitlines()
        first = out[0][:120] if out else "(no output)"
        probe_lines.append(f"{name}: FOUND {found} | rc={r.returncode} | {first}")
    except Exception as exc:  # noqa: BLE001 - 探测类脚本如实记录即可
        probe_lines.append(f"{name}: FOUND {found} | NON_INTERACTIVE_FAILED {type(exc).__name__}")
probe_lines += ["", "# 结论：可用者进入 W6 L1-L3；不可用者按 R6 降级；AIX-04 二次确认不取消。",
                "# 无论结果如何，本探测不联网、不传凭据、不执行任何模型调用。"]
write_new("docs/evidence/v1.0/w2-w7-preconditions/cli-probe.log", "\n".join(probe_lines) + "\n")

write_new("docs/evidence/v1.0/w2-w7-preconditions/samples.md", """# C2 / C3 真实样本登记（2026-11-06）

> 只登记结构与规模，**不含源码正文、IP、主机名、凭据**。G8 第 4 条的现场确认不在本文件范围内。

| 编号 | 路径 | 实测规模 | 真实性依据 | 本轮用途 |
|---|---|---|---|---|
| C2-a | `~/.hermes/hermes-agent/tests/` | 5178 个 `test_*.py`、13 个 `conftest.py` | 第三方真实工程自带测试集，非本轮生成 | W4 `--collect-only` 预检；W3 参数化 / xfail 映射 |
| C2-b | `~/deepseek-harness/` | 22 个 `test_*.py`、`pytest.ini` | 既有真实工程 | W4 包结构与 fixture 解析 |
| C3-a | `fixtures/roundtrip/legacy/*.robot` | 17 条 `TN-IMP-xxx` | 真实项目遗留用例（编号来自实际台账） | W4 RF `--dryrun` 预检；W3 命令等价 |
| C3-b | `fixtures/bench/4suite-2cases/` | 4 suite × 2 case | 既有压测 fixture | 分片合并 |

**统计命令（可复跑）**

```
find ~/.hermes/hermes-agent/tests -name 'test_*.py' | wc -l
find ~/deepseek-harness -name 'test_*.py' | wc -l
ls ~/robot-autotest/fixtures/roundtrip/legacy/*.robot | wc -l
```
""")

write_new("docs/evidence/v1.0/w2-w7-preconditions/README.md", """# W2–W7 前置条件证据（2026-11-06）

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
""")

# ---------------------------------------------------------------- 6. 决策纪要 §12
append_once("docs/plan/决策纪要-20261106.md", "## 12. 人工复核结论与二次开发授权", """## 12. 人工复核结论与二次开发授权（2026-11-06 16:50）

大师原文：**「认可 开发成果 请更新文档后 继续推进开发 直到全部完成 （中间不要停下让我复核了）」**。

- **复核结论**：W1 六包 / P1 五包 / WS-02 / 七版测试报告与 9 份文档回写 / `f29db81` 双推，全部**认可**，WBS 不回退。
- **续授范围**：W2–W7 共 28 包 + 历史 8 个 `待验` 包，全部开发、实测、留证；**中途不使用向导问卷、不请求确认**；全部按推荐值。
- **波次依赖**：G4/G5 经书面核验**均未判过**（空模板 / 待执行 SQL），按授权改为「**开发并行、判定不前置**」；
  门禁不代判、G8 不申请；真机判过结果与实现不符时按 `DEV-PLAN.md` §9 回滚。
- **边界不转移**：发版 / 打 tag / 破坏性变更 / 对外口径、`G-授权`、ADR-0013 禁跑令、G4/G5/G8 判定、C2/C3 现场确认仍归大师。
- 详见 `docs/plan/复核结论与二次开发授权-20261106.md` 与 `docs/plan/前置条件闭环-20261106.md`。
""")

# ---------------------------------------------------------------- 7. 门禁纪要附 2
append_once("docs/plan/门禁核验纪要-G2-G5-20261106.md", "## 附 2：G4 / G5 书面核验", """## 附 2：G4 / G5 书面核验（2026-11-06 二次授权后补做）

> 结论先行：**G4 / G5 两门均未判过**。本节是「把证据翻出来逐条对检查单」的书面核验，
> 不是判过声明。AI 按 11-06 定规出书面结论，但真机 / 压测 / 外部凭据项属 `G-授权` 与
> ADR-0013 禁跑令，不由 AI 代判。

| 门 | 检查单条目 | 检索到的证据 | 判定 |
|---|---|---|---|
| G4 | 4 并发加速比 ≥2.5x、20 轮压测 | `docs/evidence/v0.4/bench-20rounds.txt` 首行 `[待149实跑]`，正文写「本次**未实跑压测**……所有指标位一律写『待采集』」 | **未采集，待现场** |
| G4 | 同设备同端口冲突 0 次 | `docs/evidence/v0.4/lease-conflict-0.sql` 首行 `[待149执行]`，是待跑 SQL 脚本 | **未执行，待现场** |
| G4 | 日志延迟 <2s / 调度开销 <5% | `docs/evidence/v0.4/lease-concurrency.log`、`log-latency.txt` 为单测 / 模板级证据，不能替代 L5 压测 | **部分本机证据，不构成门禁** |
| G5 | metadata 齐全率 100% | `MetadataGate` / `ReportIngestService` 与 v0.5 单测日志，本机可复跑 | **本机通过** |
| G5 | 真实链路可出对外报告 | 无真机链路导出件；`rpt05-export.md` 为本机导出说明 | **待现场** |
| G5 | 导出无缺项、清理任务按期删且不误删 | `rpt01-rpt07.md` / `rpt05-print/` 为本机抽样，无真实链路抽样 | **待现场** |

**汇总**：G4 未判过、G5 未判过；G6/G7/G8 亦未判过。按 `docs/plan/复核结论与二次开发授权-20261106.md` §4，
W2–W7 的**功能开发**与门禁判定解耦、并行推进，**门禁不前置、不代判**；若现场判过结果与本机实现不符，
按 `DEV-PLAN.md` §9 回滚对应波次。本节不修改附录 B 的任何判定本体。
""")

# ---------------------------------------------------------------- 8. PRD §7 表
replace_lines("docs/release/v2.0-platform/PRD.md", {
    "| C1 8001 语义 |": "| C1 8001 语义 | **已闭环**（D1 定案 A + W1 实现） | `verify-entry.sh` 12/12 PASS |",
    "| C2 真实 pytest 工程 / C3 真实 RF 工程 |": "| C2 真实 pytest 工程 / C3 真实 RF 工程 | **已补齐（本机预检级）** | 环境内登记真实工程，见 `docs/plan/前置条件闭环-20261106.md` §2；**G8 第 4 条现场确认仍待**，不得用玩具工程顶替 |",
    "| C5 对外报告字段白名单 |": "| C5 对外报告字段白名单 | **已定案** | `docs/plan/C5-对外报告字段白名单-20261106.md` + `tools/check_report_sensitive.py`；**定案不等于获准对外发送** |",
    "| C4 pytest venv 白名单 |": "| C4 pytest venv 白名单 | **已定案** | `config/pytest-venv.yml`（D2 案 A 落地，专用 venv + 插件白名单） |",
    "| C6 本机 AI CLI 非交互 |": "| C6 本机 AI CLI 非交互 | **已探测** | `docs/evidence/v1.0/w2-w7-preconditions/cli-probe.log`；无 CLI 按 R6 降级，不假称可用 |",
    "| 外部门 G5 / G4 |": "| 外部门 G5 / G4 | **核验完成：两门均未判过** | 门禁核验纪要附 2；按二次授权「开发并行、判定不前置」，不自评通过、不申请 G8 |",
})
insert_before_line("docs/release/v2.0-platform/PRD.md", "| 2026-11-06 | v1.4 |",
                   "| 2026-11-06 | v1.5 | §7 前置状态表按实测回写：C1/C4/C5/C6 闭环、C2/C3 补齐（本机预检级）、G4/G5 核验为「未判过」；附二次授权「开发并行、判定不前置」 | 大师 16:50 复核认可 + 续授；**55 条 FR / 10 条 NFR / §6 G8 十条判定本体一项未动** |")

# ---------------------------------------------------------------- 9. DEV-PLAN §3.2 + 修订
replace_lines("docs/release/v2.0-platform/DEV-PLAN.md", {
    "| W1 | 无（不依赖既有批次） |": "| W1 | 无 | 已完成 | **已开工并完成**（`f29db81`） |",
    "| W2 | **G5 已通过** |": "| W2 | G5（**未判过**） | C5 已定案 | **可开工**：按二次授权开发并行、判定不前置 |",
    "| W3 | **G4 已通过** |": "| W3 | G4（**未判过**） | C2/C3/C4 已闭环 | **可开工**：同上 |",
    "| W4 | W3 |": "| W4 | W3 | C2 / C3 已登记 | **可开工** |",
    "| W5 | W4 |": "| W5 | W4 | C7 / C8 已闭环 | **可开工** |",
    "| W6 | W3, W4 |": "| W6 | W3, W4 | C6 已探测（按结果降级） | **可开工** |",
    "| W7 | W1–W6 |": "| W7 | W1–W6 | 截图基线需真实浏览器 | **可开工**，真机/截图类 TC 记 `待现场` |",
})
replace_lines("docs/release/v2.0-platform/DEV-PLAN.md", {
    "| C2 pytest 真实工程 |": "| C2 pytest 真实工程 | 已补齐（本机预检级） | W3 / W4 / G8 第 4 条 | 真实工程已登记；G8 现场确认仍待，TC 记 `PARTIAL` |",
    "| C3 RF 真实工程 |": "| C3 RF 真实工程 | 已补齐（本机预检级） | W3 / W4 / G8 第 4 条 | 同 C2 |",
    "| C4 pytest venv 白名单 |": "| C4 pytest venv 白名单 | 已定案 | W3 | `config/pytest-venv.yml` |",
    "| C5 字段白名单 |": "| C5 字段白名单 | 已定案 | W2 / G8 第 6 条 | 判据脚本已可跑；对外发送仍归大师 |",
    "| C6 AI CLI 非交互 |": "| C6 AI CLI 非交互 | 已探测 | W6 | 留 `cli-probe.log`；无 CLI 按 R6 降级 |",
    "| C9 备份覆盖新表 |": "| C9 备份覆盖新表 | 逐波执行中 | W2 起每波 | 每波同步登记 `scripts/rollback.sh` 表清单 |",
    "| C10 不改 WBS 台账 |": "| C10 不改 WBS 台账 | 已闭环 | 并入 34 包前 | 评审通过后分母已改 130 |",
})
insert_before_line("docs/release/v2.0-platform/DEV-PLAN.md", "| 2026-11-06 | v1.4 |",
                   "| 2026-11-06 | v1.5 | §3.2 逐波「当前可否开工」与 C1–C10 落点按实测回写；新增「开发并行、判定不前置」口径（G4/G5 均未判过，见门禁核验纪要附 2） | 大师 16:50 二次授权「继续推进直到全部完成、中途不复核」；**波次编号 / 交付判据 / G8 十条 / 降级规则一项未动** |")

# ---------------------------------------------------------------- 10. TEST-PLAN §6
replace_once("docs/release/v2.0-platform/TEST-PLAN.md",
"""C2 真实 pytest 工程 / C3 真实 RF 工程 / C5 对外报告字段白名单当前**均无**，大师裁决
「等补齐再开 W2/W3」。因此下列约束与本文件 §7 退出标准同效：""",
"""**2026-11-06 16:50 回写**：C2 / C3 真实工程已在环境内登记（本机预检级）、C5 白名单已定案、
C4 已定案、C6 已探测，详见 `docs/plan/前置条件闭环-20261106.md`。下列约束与本文件 §7
退出标准同效，**一条未撤**；变化只在「样本是否到位」与「是否允许并行开发」：""")
replace_once("docs/release/v2.0-platform/TEST-PLAN.md",
"""2. 样本到位前，双内核矩阵 M1–M10、导入正向 2 个真实工程、EXTERNAL 字段扫描这三类 TC 一律记
   `NOT-RUN（样本未到位）`，并在 `05-TEST-REPORT.md` 写明补测计划；按退出标准第 1 条，
   **存在 NOT-RUN 时不得申请 G8**。""",
"""2. 双内核矩阵 M1–M10、导入正向真实工程的**本机预检项**可跑并记 `PARTIAL`；
   **真机执行链仍记 `NOT-RUN（待现场）`**，并在 `05-TEST-REPORT.md` 写明补测计划；
   按退出标准第 1 条，存在 NOT-RUN 时**不得申请 G8**。""")
replace_lines("docs/release/v2.0-platform/TEST-PLAN.md", {
    "3. C5 字段白名单未经大师确认前": "3. C5 白名单已由 AI 按二次授权定案（`tools/check_report_sensitive.py` 提供判据），EXTERNAL 导出**只做内部扫描验证、不得对外发送**；定案不等于获准对外。",
    "4. C4 pytest 专用 venv": "4. C4 已落 `config/pytest-venv.yml`；C6 已留 `cli-probe.log`，无 CLI 按 R6 降级，不假称可用。",
})
insert_before_line("docs/release/v2.0-platform/TEST-PLAN.md", "| 2026-11-06 | v1.4 |",
                   "| 2026-11-06 | v1.5 | §6 样本硬约束按前置闭环实测回写：C2/C3 本机预检级补齐、C4/C5 定案、C6 探测；新增「本机 PARTIAL / 真机 NOT-RUN」分层口径 | 大师 16:50 二次授权；**67 条 TC / 10 条退出标准 / M1–M10 / T4 一项未动** |")

# ---------------------------------------------------------------- 11. release README
replace_once("docs/release/README.md",
"| **v2.0** | 平台化 Platform | B9（PRD 已评审通过；**W1 六包已完成**、W2–W7 未开工） | G8 |",
"| **v2.0** | 平台化 Platform | B9（PRD 已评审通过；**W1 六包已认可**；W2–W7 按 16:50 二次授权全波推进） | G8（**未判过**） |")
append_once("docs/release/README.md", "*v2.26（2026-11-06 16:50）",
"""*v2.26（2026-11-06 16:50）：大师复核**认可** W1/P1/WS-02 成果，续授「继续推进直到全部完成、中途不复核」。
本轮回写：① W2–W7 28 包开工，G4/G5 书面核验为**均未判过**（门禁核验纪要附 2），改为「开发并行、判定不前置」；
② C1–C10 前置闭环（`docs/plan/前置条件闭环-20261106.md`）；③ 作废 v2.25 中「W2–W7 未开工 / 当前可否开工=否」的旧口径；
④ **G8 仍未判过、不申请**，发版与对外口径仍归大师。*""")

# ---------------------------------------------------------------- 12. WBS 28 包置进行中
wbs = ROOT / "docs/plan/WBS-STATUS.md"
t = wbs.read_text(encoding="utf-8")
if "第二次续授（W2–W7 开工）" in t:
    print("[SKIP] WBS 已置进行中")
    SKIP += 1
else:
    changed = 0
    out = []
    for line in t.splitlines(keepends=True):
        m = __import__("re").match(r"^\| (1[0-2]\d|130) \|", line)
        if m and "| 未开始 |" in line:
            line = line.replace("| 未开始 |", "| 进行中 |", 1)
            changed += 1
        out.append(line)
    if changed != 28:
        raise SystemExit(f"FAIL WBS: 期望改 28 包，实际 {changed}")
    t = "".join(out)
    t = t.replace("| 130 个任务包 | **28** | **0** | **8** | **7** | **87** |",
                  "| 130 个任务包 | **0** | **28** | **8** | **7** | **87** |", 1)
    t = t.replace("**当前进度：87 / 130（约 66.9% · 8 待验 · 7 待现场 · 28 未开始 · 0 进行中）**",
                  "**当前进度：87 / 130（约 66.9% · 8 待验 · 7 待现场 · 0 未开始 · 28 进行中）**", 1)
    t = t.rstrip() + "\n\n> **第二次续授（W2–W7 开工）2026-11-06 16:50**：大师复核认可上一轮成果并续授\n" \
        "「继续推进开发直到全部完成、中途不复核」。W2–W7 共 28 包由 `未开始` 转 `进行中`；\n" \
        "G4/G5 经书面核验**均未判过**，按授权「开发并行、判定不前置」，不以已开发冒充已验收。\n" \
        "依据 `docs/plan/复核结论与二次开发授权-20261106.md` 与 `docs/plan/前置条件闭环-20261106.md`。\n"
    wbs.write_text(t, encoding="utf-8")
    PASS += 1
    print("[PASS] WBS 28 包置进行中 + 汇总订正 + 补记")

print(f"\n[kickoff] PASS={PASS} SKIP={SKIP}")
