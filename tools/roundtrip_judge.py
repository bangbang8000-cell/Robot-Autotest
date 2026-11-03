#!/usr/bin/env python3
"""EDT-06 往返保真的独立裁判（L1 可执行性 + L2 结构等价）。

按 DEV-PLAN §4 的两层判据，对语料原文与编辑器序列化产物分别打分：

  L1  `robot --dryrun`   退出码 0 —— 改完还能被 Robot Framework 正常解析与校验
  L2  `robot.api.get_model()` 归一化后 AST 逐节点比对 —— 语义一致

三条纪律（写在 DEV-PLAN 里，这里照做）：
  1. dryrun 通过 ≠ 语义一致，两层都要过；
  2. 归一化时忽略行号、缩进、注释、分隔符、方括号设置的先后顺序 —— 这些是允许变化的；
  3. 语料里必须有「故意难」的语法，否则 100% 通过没有意义（见 fixtures/roundtrip/syntax）。

为什么要独立裁判：序列化由编辑器（TypeScript）做，裁判必须由 Robot Framework 自己的
解析器（Python）做。两侧只通过 `.robot` 文件交接，编辑器不能自证。

两个实现上的坑（踩过，别改回去）：
  1. `Statement.get_tokens()` 在 RF 7.5 里传空参数会返回**空列表**，必须直接读 `.tokens`。
     用它取 token 会让所有样本归一化成空、L2 恒等于「一致」，等于给假保险。
  2. dryrun 在临时目录里执行，文件路径必须转**绝对路径**；相对路径会报
     「File or directory to execute does not exist.」

用法（需要装了 robotframework 的解释器，149 上用 ~/rf-stack/.venv/bin/python）：

    python tools/roundtrip_judge.py fixtures/roundtrip \
        --serialized artifacts/roundtrip/serialized \
        --report docs/evidence/v0.3/roundtrip.md

退出码：0 = 全部样本双层通过；1 = 有样本未通过。
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
import tempfile
from pathlib import Path

from robot.api import get_model

# 归一化时丢弃的 token 类型：分隔符、换行、结束标记、注释与续行标记
# （这些正是编辑器被允许改写的部分）。
IGNORED_TOKEN_TYPES = {
    "SEPARATOR",
    "EOL",
    "EOS",
    "COMMENT",
    "CONTINUATION",
    "EOL_COMMENT",
}

# AST 里承载子节点的属性。RF 的块结构各不相同：
#   File        → sections
#   Section/TC/KW/For/Try/If → body
#   If/Try      → orelse（元素是块）/ next（单个块）
CONTAINER_ATTRS = ("sections", "body", "orelse", "next")

# 只统计、不判定的「允许丢失」项：注释
COUNT_TOKEN_TYPES = {"COMMENT", "EOL_COMMENT"}

# 用例/关键字体内的方括号设置。RF 按类型收集这些设置，**先后顺序不参与语义**，
# 而编辑器序列化时固定按「Arguments → Documentation → Tags → Setup → Teardown →
# Timeout → Template」输出。所以比对前把它们按类型名排序，否则会把纯排版差异
# 当成语义变化（实测 7 个样本里有 5 个是这一类）。
SETTING_TOKEN_TYPES = {
    "DOCUMENTATION",
    "TAGS",
    "SETUP",
    "TEARDOWN",
    "TIMEOUT",
    "TEMPLATE",
    "ARGUMENTS",
    "RETURN",
}

_WS_RE = re.compile(r"\s+")


def _tokens(node) -> list[tuple[str, str]]:
    """取语义 token：直接读 `.tokens`（见模块 docstring 的坑 1）。"""
    raw = getattr(node, "tokens", None)
    if raw is None:
        return []
    out = []
    for tok in raw:
        ttype = str(tok.type)
        if ttype in IGNORED_TOKEN_TYPES:
            continue
        value = str(tok.value).strip()
        if ttype == "ASSIGN":
            # `${name} =` 与 `${name}=` 是同一个赋值，空格只是排版。
            value = _WS_RE.sub("", value)
        out.append((ttype, value))
    return out


def _children(node):
    """展开容器属性，把「块列表」与「单块」两种形态统一成节点的迭代。"""
    for attr in CONTAINER_ATTRS:
        seq = getattr(node, attr, None)
        if not seq:
            continue
        items = seq if isinstance(seq, list) else [seq]
        for child in items:
            if isinstance(child, list):
                for inner in child:
                    yield inner
            else:
                yield child


def _walk(node, depth: int, out: list) -> None:
    header = getattr(node, "header", None)
    if header is not None:
        # 块节点（File 的 section、TestCase、For、If、Try…）先记头部，再下钻一层。
        out.append((depth, "BLOCK", type(node).__name__, _tokens(header)))
        child_depth = depth + 1
    else:
        toks = _tokens(node)
        # 空行、整行注释归一化后为空，不参与比较（编辑器本来就允许丢空行/注释）。
        if toks:
            out.append((depth, "STMT", type(node).__name__, toks))
        child_depth = depth

    kids = list(_children(node))
    settings = []
    others = []
    for kid in kids:
        if getattr(kid, "header", None) is None:
            kid_toks = _tokens(kid)
            if kid_toks and kid_toks[0][0] in SETTING_TOKEN_TYPES:
                settings.append((kid_toks[0][0], kid))
                continue
        others.append(kid)

    # 方括号设置按类型排序后统一前置：位置与顺序都不影响 RF 语义。
    for _, kid in sorted(settings, key=lambda x: x[0]):
        _walk(kid, child_depth, out)
    for kid in others:
        _walk(kid, child_depth, out)


def normalize(path: Path) -> list:
    """只保留语义节点：套件/用例/关键字名、设置键值、关键字调用与参数、块结构。"""
    model = get_model(str(path))
    out: list = []
    _walk(model, 0, out)
    return out


def _first_diff(src: list, dst: list) -> str:
    """给出第一处差异，写进报告的备注列 —— 否则「不一致」只是个结论，没法复查。"""
    for i in range(min(len(src), len(dst))):
        if src[i] != dst[i]:
            return f"第 {i + 1} 个节点：原文 {src[i]} / 产物 {dst[i]}"
    if len(src) != len(dst):
        longer, tag = (src, "原文") if len(src) > len(dst) else (dst, "产物")
        extra = longer[min(len(src), len(dst))]
        return f"节点数 {len(src)} != {len(dst)}，{tag}多出 {extra}"
    return ""


def count_comments(path: Path) -> int:
    """注释 token 计数（只用于报告，不参与判定；DEV-PLAN 允许 ≤5% 丢失）。"""
    total = 0
    stack = [get_model(str(path))]
    while stack:
        node = stack.pop()
        for tok in getattr(node, "tokens", []) or []:
            if str(tok.type) in COUNT_TOKEN_TYPES:
                total += 1
        stack.extend(_children(node))
    return total


def has_test_cases(path: Path) -> bool:
    """RF 对「没有 Test Case 的文件」dryrun 会报 `contains no tests`。

    这不是语法问题：`common.robot` 这类资源文件本身就是 .robot 后缀却没有用例。
    所以按 AST 判断，而不是按后缀判断。
    """
    model = get_model(str(path))
    stack = [model]
    while stack:
        node = stack.pop()
        if type(node).__name__ in {"TestCaseSection", "TaskSection"}:
            return True
        stack.extend(_children(node))
    return False


def _error_line(text: str) -> str:
    """从 dryrun 输出里挑最有信息量的一行，别只取末行（末行常是 `Output:  NONE`）。"""
    lines = [ln.strip() for ln in text.splitlines() if ln.strip()]
    for ln in lines:
        low = ln.lower()
        if "no keyword" in low or "error" in low or "failed" in low or "unexpected" in low:
            return ln
    return lines[-1] if lines else ""


def dryrun(path: Path) -> tuple[bool, str]:
    """L1：robot --dryrun。不执行，只校验语法与关键字存在性。"""
    cmd = [
        sys.executable,
        "-m",
        "robot",
        "--dryrun",
        "--output",
        "NONE",
        "--log",
        "NONE",
        "--report",
        "NONE",
        "--consolecolors",
        "off",
        "--loglevel",
        "TRACE:ERROR",
        # 必须转绝对路径：dryrun 在临时目录里执行，相对路径会解析不到文件。
        str(Path(path).resolve()),
    ]
    with tempfile.TemporaryDirectory() as tmp:
        proc = subprocess.run(cmd, capture_output=True, text=True, cwd=tmp)
    return proc.returncode == 0, _error_line(proc.stdout + proc.stderr)


def collect(root: Path) -> list[Path]:
    files = []
    for path in root.rglob("*"):
        if path.suffix in (".robot", ".resource") and path.is_file():
            files.append(path)
    return sorted(files)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("corpus", type=Path)
    ap.add_argument("--serialized", type=Path, required=True)
    ap.add_argument("--report", type=Path, required=True)
    args = ap.parse_args()

    rows = []
    l1_pass = l1_fail = l1_skip = l2_pass = l2_fail = 0
    comments_src = comments_dst = 0

    for source in collect(args.corpus):
        rel = source.relative_to(args.corpus)
        mirror = args.serialized / rel

        if not mirror.exists():
            rows.append((str(rel), "MISSING", "-", "-", "镜像产物不存在"))
            l2_fail += 1
            continue

        try:
            norm_src = normalize(source)
            norm_dst = normalize(mirror)
            lex_equal = norm_src == norm_dst
        except Exception as err:  # noqa: BLE001 - 解析失败要进报告，不能静默
            rows.append((str(rel), "-", "-", "-", f"L2 解析失败：{err}"))
            l2_fail += 1
            continue

        comments_src += count_comments(source)
        comments_dst += count_comments(mirror)

        note = ""
        if not has_test_cases(source):
            # 没有用例的文件（.resource 与 common.robot 这类）dryrun 必然报
            # 「contains no tests」，只走 L2。
            l1_src = l1_out = "SKIP(无用例)"
            l1_skip += 1
            note = "资源文件，L1 不适用"
        else:
            ok_src, msg_src = dryrun(source)
            ok_out, msg_out = dryrun(mirror)
            l1_src = "PASS" if ok_src else f"FAIL({msg_src})"
            l1_out = "PASS" if ok_out else f"FAIL({msg_out})"
            l1_pass += int(ok_src) + int(ok_out)
            l1_fail += int(not ok_src) + int(not ok_out)

        if lex_equal:
            l2_pass += 1
        else:
            l2_fail += 1
            note = (note + "；" if note else "") + _first_diff(norm_src, norm_dst)
        rows.append((str(rel), l1_src, l1_out, "PASS" if lex_equal else "FAIL", note))

    total = len(rows)
    verdict = "PASS" if l1_fail == 0 and l2_fail == 0 else "FAIL"

    args.report.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "# EDT-06 往返保真报告（L1 dryrun + L2 AST）",
        "",
        f"- 语料根目录：`{args.corpus}`",
        f"- 序列化产物：`{args.serialized}`",
        f"- 样本数：**{total}**",
        f"- L1 `robot --dryrun`：原文/产物各跑一次，成功 **{l1_pass}** 次、"
        f"失败 **{l1_fail}** 次、跳过 **{l1_skip}** 个（无用例文件）",
        f"- L2 AST 归一化比对：一致 **{l2_pass}** 个、不一致 **{l2_fail}** 个",
        f"- 注释 token（允许丢失，DEV-PLAN §10 ≤5%）：原文 **{comments_src}** / "
        f"产物 **{comments_dst}**",
        f"- 总判定：**{verdict}**",
        "",
        "| 样本 | L1 原文 | L1 序列化产物 | L2 AST | 备注 |",
        "|---|---|---|---|---|",
    ]
    for rel, a, b, c, note in rows:
        # 备注里可能有原始 token 值，含 `|` 会截断 markdown 表格。
        safe_note = str(note).replace("|", "\\|")
        lines.append(f"| `{rel}` | {a} | {b} | {c} | {safe_note} |")
    args.report.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(
        f"样本 {total}；L1 成功 {l1_pass} / 失败 {l1_fail} / 跳过 {l1_skip}；"
        f"L2 一致 {l2_pass} / 不一致 {l2_fail}；判定 {verdict}"
    )
    return 0 if verdict == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
