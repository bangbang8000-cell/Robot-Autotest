#!/usr/bin/env python3
"""UX-05 表格规范壳迁移：把 views 里 19 处直接使用的 NDataTable 换成 DataTable 壳。

只做机械改写，不做判断：列定义、行键、加载态、`v-if` 一律原样保留；壳固定掉的
`size="small"` / `:bordered="false"` / `:scroll-x="N"` 三个属性删掉（横滚宽度改由列宽推导，
见 `frontend/src/utils/tableWidth.ts`）。空态从 `#empty` 插槽折成壳的三个 props；若空态不是
EmptyState（例如端口面板那行灰字），保留插槽不动 —— 壳对 `#empty` 是「有则用、无则给默认」。

为什么用扫描器而不是正则：属性值里有箭头函数（`:row-key="(row: X) => row.id"`），
`>` 会提前把标签截断，正则版本曾把后面一整个表格吞进来。

用法：
  python3 migrate_tables.py --dry     # 只打印将要生成的块，不落盘
  python3 migrate_tables.py           # 落盘
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

VIEWS = Path(__file__).resolve().parents[4] / "frontend" / "src" / "views"

OPEN = "<NDataTable"
CLOSE = "</NDataTable>"
DROP_KEY = re.compile(r"^\s*:?(scroll-x|bordered|size|striped)=")

EMPTY_PROPS = {
    ":title": ("empty-title", True),
    "title": ("empty-title", False),
    ":description": ("empty-description", True),
    "description": ("empty-description", False),
    ":icon": ("empty-icon", True),
    "icon": ("empty-icon", False),
}


def scan_blocks(text: str) -> list[tuple[int, int, str, str, bool]]:
    """返回 [(line_start, end, indent, attrs, self_closing)]。

    `line_start` 而不是标签的 `<` 位置：替换区间要连行首缩进一起吃掉，否则缩进会翻倍。
    """
    blocks: list[tuple[int, int, str, str, bool]] = []
    cursor = 0
    while True:
        start = text.find(OPEN, cursor)
        if start < 0:
            break
        j = start + len(OPEN)
        quote = ""
        while j < len(text):
            ch = text[j]
            if quote:
                if ch == quote:
                    quote = ""
            elif ch in "\"'":
                quote = ch
            elif ch == ">":
                break
            j += 1
        self_closing = text[j - 1] == "/"
        attrs = text[start + len(OPEN) : j - 1 if self_closing else j]
        line_start = text.rfind("\n", 0, start) + 1
        indent = text[line_start:start]
        if self_closing:
            end = j + 1
        else:
            close = text.find(CLOSE, j)
            end = close + len(CLOSE)
        blocks.append((line_start, end, indent, attrs, self_closing))
        cursor = end
    return blocks


def strip_empty_template(body: str) -> str | None:
    """从 `<NDataTable>…</NDataTable>` 之间取出 #empty 插槽内容（若没有返回 None）。"""
    match = re.search(r"<template #empty>([\s\S]*?)</template>", body)
    return match.group(1).strip() if match else None


def fold_empty(slot: str) -> tuple[list[str], str | None]:
    """把 EmptyState 折成壳 props；不是 EmptyState 就原样保留插槽。"""
    if not slot.startswith("<EmptyState"):
        return [], slot
    inner = re.match(r"<EmptyState\b([\s\S]*?)/?>", slot)
    attrs = inner.group(1) if inner else ""
    props: list[str] = []
    for name, value in re.findall(r'([:@\w-]+)="([^"]*)"', attrs):
        target = EMPTY_PROPS.get(name)
        if target is None:
            continue
        prop, is_expression = target
        if is_expression:
            props.append(f':{prop}="{value}"')
        else:
            # `icon="users"` 是字面量；写成 `:empty-icon="users"` 会变成「变量 users 未定义」
            props.append(f'{prop}="{value}"')
    return props, None


def rebuild(indent: str, attrs: str, self_closing: bool, slot: str | None) -> str:
    inline = ""
    rest = attrs
    first_break = attrs.find("\n")
    if first_break > 0:
        inline = attrs[:first_break].strip()
        rest = attrs[first_break:]

    kept: list[str] = []
    for line in rest.splitlines():
        if not line.strip():
            continue
        if DROP_KEY.match(line):
            continue
        kept.append(line.rstrip())

    # 属性行沿用原缩进；壳新增的 props 跟属性行同列
    attr_indent = ""
    for line in kept:
        attr_indent = line[: len(line) - len(line.lstrip())]
        break
    prop_indent = attr_indent or f"{indent}  "

    props, kept_slot = ([], None) if slot is None else fold_empty(slot)
    head = f"{indent}<DataTable" + (f" {inline}" if inline else "")
    lines = [head, *kept, *(f"{prop_indent}{prop}" for prop in props)]

    if kept_slot is None:
        return "\n".join([*lines, f"{indent}/>"])

    body = [
        f"{prop_indent}<template #empty>",
        *(f"{prop_indent}  {line}" for line in kept_slot.splitlines()),
        f"{prop_indent}</template>",
    ]
    return "\n".join([*lines, f"{indent}>", *body, f"{indent}</DataTable>"])


def migrate(text: str) -> str:
    out = []
    cursor = 0
    for start, end, indent, attrs, self_closing in scan_blocks(text):
        slot = None
        if not self_closing:
            body_start = text.find(">", start) + 1
            slot = strip_empty_template(text[body_start : end - len(CLOSE)])
        out.append(text[cursor:start])
        out.append(rebuild(indent, attrs, self_closing, slot))
        cursor = end
    out.append(text[cursor:])
    return "".join(out)


def fix_imports(text: str) -> str:
    """删掉不再需要的 NDataTable 导入项；需要时补 DataTable 导入。"""
    text = re.sub(r"\n[ \t]*NDataTable,\n", "\n", text)
    text = re.sub(r"(import \{[^}]*?)\bNDataTable,\s*", r"\1", text)
    text = re.sub(r",\s*NDataTable(\s*\})", r"\1", text)
    text = re.sub(r"import \{ NDataTable \} from 'naive-ui'\n", "", text)

    if "from '@/components/DataTable.vue'" not in text:
        anchor = re.search(r"^import .*?from '@/components/", text, re.M)
        line = "import DataTable from '@/components/DataTable.vue'\n"
        if anchor:
            text = text[: anchor.start()] + line + text[anchor.start() :]
        else:
            anchor = re.search(r"^import .*?from '@/", text, re.M)
            text = text[: anchor.start()] + line + text[anchor.start() :]
    return text


def main() -> int:
    dry = "--dry" in sys.argv
    total = 0
    for path in sorted(VIEWS.glob("*.vue")):
        original = path.read_text()
        if OPEN not in original:
            continue
        migrated = fix_imports(migrate(original))
        if migrated == original:
            continue
        total += 1
        if dry:
            print(f"=== {path.name} ===")
            print(migrated[migrated.find("<DataTable") :][:1200])
            print()
        else:
            path.write_text(migrated)
            print(f"改写 {path.name}")
    print(f"共 {total} 个视图")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
