#!/usr/bin/env python3
"""四格式字段集合一致性校验（TPLX-03）。

为什么需要这个工具
------------------
`ReportExportService` 的四条格式分支各自拼字符串/开 workbook，最容易出的错不是「崩了」，
而是**某一分支少写了一个字段**：HTML 里 21 行、xlsx 里 20 行，导出记录照样 200，
文件照样能打开，只有把两份文件摆在一起数才知道。人眼数 21 行不现实，所以做成脚本。

四种格式的「机读锚点」
----------------------
`ReportExportService` 的约定是「每一行都以字段 key 起头」，各格式取 key 的方式不同：

| 格式 | 取法 |
| --- | --- |
| HTML | `<th ...>fieldKey</th>` 的文本 |
| DOCX | `word/document.xml` 段落文本，取第一个 ` | ` 之前 |
| XLSX | A 列（跳过首行表头），单元格可能是共享字符串或内联字符串 |
| PDF  | 内容流里 `(...) Tj` 的字符串，取第一个 ` | ` 之前；非 ASCII 会被降级成 `?`，但 key 是 ASCII，不受影响 |

只用标准库：门禁里跑的东西不该多一个 pip 依赖。XLSX 因此自己解 `sharedStrings.xml` +
`sheet1.xml`，不依赖 openpyxl。

用法
----
    python3 tools/check_report_consistency.py a.html b.docx c.xlsx d.pdf

- 参数至少 2 个，按扩展名识别格式（顺序随意）；
- 全部一致 → 退出码 0；
- 有不一致 → 逐个格式列出「缺哪些字段 / 多哪些字段 / 顺序是否不同」，退出码 1；
- 文件缺失或格式不认识 → 退出码 2（与「不一致」区分开，避免 CI 里误判）。
"""
from __future__ import annotations

import html as html_mod
import pathlib
import re
import sys
import zipfile

KEY_SEPARATOR = " | "


class UsageError(Exception):
    """用法/环境问题（文件缺失、格式不认识、解析不了）。

    刻意与「不一致」分开：退出码 2 表示「这次没法判」，1 表示「判过了，不一致」。
    两者混成一个码，CI 里就分不清「导出器坏了」和「样本没生成」。
    """


# ------------------------------------------------------------------ HTML
def keys_from_html(path: pathlib.Path) -> list[str]:
    text = path.read_text(encoding="utf-8", errors="replace")
    # 只看表格行里的 th：`<tr><th title="标题">fieldKey</th><td>值</td></tr>`
    rows = re.findall(r"<tr>(.*?)</tr>", text, re.S)
    keys = []
    for row in rows:
        th = re.search(r"<th[^>]*>(.*?)</th>", row, re.S)
        if th:
            keys.append(html_mod.unescape(re.sub(r"<[^>]+>", "", th.group(1))).strip())
    return keys


# ------------------------------------------------------------------ DOCX
def _docx_paragraphs(path: pathlib.Path) -> list[str]:
    with zipfile.ZipFile(path) as archive:
        xml = archive.read("word/document.xml").decode("utf-8", "replace")
    paragraphs = []
    for block in re.findall(r"<w:p[ >].*?</w:p>", xml, re.S):
        pieces = re.findall(r"<w:t[^>]*>(.*?)</w:t>", block, re.S)
        paragraphs.append(html_mod.unescape("".join(pieces)))
    return paragraphs


def keys_from_docx(path: pathlib.Path) -> list[str]:
    keys = []
    for line in _docx_paragraphs(path):
        if KEY_SEPARATOR in line:
            keys.append(line.split(KEY_SEPARATOR, 1)[0].strip())
    return keys


# ------------------------------------------------------------------ XLSX
def _xlsx_shared_strings(archive: zipfile.ZipFile) -> list[str]:
    try:
        xml = archive.read("xl/sharedStrings.xml").decode("utf-8", "replace")
    except KeyError:
        return []
    values = []
    for item in re.findall(r"<si>(.*?)</si>", xml, re.S):
        values.append(html_mod.unescape("".join(re.findall(r"<t[^>]*>(.*?)</t>", item, re.S))))
    return values


def _xlsx_first_sheet(archive: zipfile.ZipFile) -> str:
    names = [n for n in archive.namelist() if re.match(r"xl/worksheets/sheet\d+\.xml$", n)]
    if not names:
        raise ValueError("xlsx 里没有 worksheet")
    return sorted(names)[0]


def keys_from_xlsx(path: pathlib.Path) -> list[str]:
    with zipfile.ZipFile(path) as archive:
        shared = _xlsx_shared_strings(archive)
        sheet = archive.read(_xlsx_first_sheet(archive)).decode("utf-8", "replace")
    keys = []
    for row_attr, body in re.findall(r"<row[^>]*r=\"(\d+)\"[^>]*>(.*?)</row>", sheet, re.S):
        if int(row_attr) == 1:  # 首行是 FIELD_KEY / TITLE / VALUE 表头
            continue
        cell = re.search(r"<c[^>]*r=\"A\d+\"[^>]*>(.*?)</c>", body, re.S)
        if not cell:
            continue
        body_xml = cell.group(1)
        inline = re.search(r"<is>.*?<t[^>]*>(.*?)</t>", body_xml, re.S)
        if inline:
            keys.append(html_mod.unescape(inline.group(1)).strip())
            continue
        value = re.search(r"<v>(.*?)</v>", body_xml, re.S)
        if not value:
            continue
        raw = value.group(1)
        if raw.isdigit() and int(raw) < len(shared):
            keys.append(shared[int(raw)].strip())
        else:
            keys.append(html_mod.unescape(raw).strip())
    return keys


# ------------------------------------------------------------------ PDF
PDF_TEXT = re.compile(r"\((?:\\.|[^()\\])*\)\s*Tj")


def keys_from_pdf(path: pathlib.Path) -> list[str]:
    raw = path.read_bytes().decode("latin-1", "replace")
    keys = []
    for chunk in PDF_TEXT.findall(raw):
        text = chunk[:chunk.rindex(")")].lstrip("(")
        text = text.replace("\\(", "(").replace("\\)", ")").replace("\\\\", "\\")
        if KEY_SEPARATOR in text:
            keys.append(text.split(KEY_SEPARATOR, 1)[0].strip())
    return keys


READERS = {
    ".html": ("HTML", keys_from_html),
    ".htm": ("HTML", keys_from_html),
    ".docx": ("DOCX", keys_from_docx),
    ".xlsx": ("XLSX", keys_from_xlsx),
    ".pdf": ("PDF", keys_from_pdf),
}


def read_keys(path: pathlib.Path) -> tuple[str, list[str]]:
    reader = READERS.get(path.suffix.lower())
    if reader is None:
        raise UsageError(f"不认识的格式: {path.name}（支持 .html/.docx/.xlsx/.pdf）")
    if not path.is_file():
        raise UsageError(f"文件不存在: {path}")
    label, func = reader
    try:
        return label, func(path)
    except (zipfile.BadZipFile, ValueError, KeyError) as exc:
        raise UsageError(f"{label} 解析失败 {path.name}: {exc}") from exc


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__.strip().splitlines()[0])
        print("用法: python3 tools/check_report_consistency.py <a.html> <b.docx> ...（至少 2 个）")
        return 2

    parsed = []
    for arg in argv:
        try:
            parsed.append(read_keys(pathlib.Path(arg)))
        except UsageError as exc:
            print(f"[ERR] {exc}")
            return 2
    labels = [item[0] for item in parsed]
    if len(set(labels)) != len(labels):
        print(f"[ERR] 同一格式传了多次: {labels}")
        return 2

    baseline_label, baseline = parsed[0]
    baseline_set = set(baseline)
    print(f"基准：{baseline_label} {baseline_label and argv[0]}，共 {len(baseline)} 个字段")
    print(f"  字段：{', '.join(baseline)}")

    failed = False
    for label, keys in parsed[1:]:
        keys_set = set(keys)
        missing = [key for key in baseline if key not in keys_set]
        extra = [key for key in keys if key not in baseline_set]
        order_diff = not missing and not extra and keys != baseline
        if missing or extra:
            failed = True
            print(f"[FAIL] {label}: 共 {len(keys)} 个字段"
                  f"{'，缺 ' + ', '.join(missing) if missing else ''}"
                  f"{'，多 ' + ', '.join(extra) if extra else ''}")
        elif order_diff:
            failed = True
            print(f"[FAIL] {label}: 字段集合一致但**顺序**不同")
            for index, (left, right) in enumerate(zip(baseline, keys)):
                if left != right:
                    print(f"       第 {index + 1} 项：{baseline_label}={left} / {label}={right}")
        else:
            print(f"[PASS] {label}: {len(keys)} 个字段，集合与顺序都与 {baseline_label} 一致")

    if failed:
        print("\n结论：四格式字段不一致（导出器某条分支漏写或多写了字段）")
        return 1
    print(f"\n结论：{len(parsed)} 种格式字段集合与顺序完全一致，每种 {len(baseline)} 个字段")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
