#!/usr/bin/env python3
"""给 19 处 DataTable 壳接入空态语义：出错 / 搜不到 / 本来没有。

三件事一起做，因为它们拆开做必然漏：
1. 每处表格接 `:error`（表格区域在加载失败时不能显示「暂无数据」）；
2. 有搜索框的页面的主表接 `:empty-illustration="keyword ? 'no-result' : 'no-data'"`；
3. 顺序敏感 —— 按块序号映射到对应的 error 变量（一个视图里有多个 error ref）。

映射表写死并在此审计（而不是按名字猜）：猜错的后果是 A 表的错误显示到 B 表上。
"""
from __future__ import annotations

import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[4]
VIEWS = ROOT / "frontend" / "src" / "views"
# 注意：`computed<DataTableColumns<UserSummary>>` 里也有 `<DataTable` 字样。
# 必须要求后面紧跟空白 / `/` / `>`，否则会把这个类型表达式当成标签开始（第一版就踩了）。
OPEN_RE = re.compile(r"<DataTable(?=[\s/>])")
OPEN = "<DataTable"

# 视图 → 按块出现顺序的 error 变量名；None 表示该表不接错误态
ERROR_BY_BLOCK: dict[str, list[str | None]] = {
    "AdminView.vue": ["error", "error", "error", "error"],
    "AssetsView.vue": ["error", "error", "error", "error"],
    "AuditView.vue": ["error"],
    "CasesView.vue": ["error"],
    "OverviewView.vue": ["digestError", "digestError", "error"],
    "PlansView.vue": ["error"],
    "ReportDetailView.vue": ["exportHistoryError", "compareError"],
    "ReportsView.vue": ["retentionError", "error"],
    "RunsView.vue": ["error"],
}

# 视图 → 按块顺序，该表是否受搜索关键词影响（滤空时用 no-result）
SEARCH_BLOCKS: dict[str, set[int]] = {
    "AdminView.vue": {0},
    "AssetsView.vue": {0, 1, 2, 3},
    "CasesView.vue": {0},
}


def block_spans(text: str) -> list[tuple[int, int]]:
    """返回每个 `<DataTable .../>` 或 `<DataTable ...>…</DataTable>` 的 (start, end)。"""
    spans: list[tuple[int, int]] = []
    cursor = 0
    while True:
        match = OPEN_RE.search(text, cursor)
        if not match:
            break
        start = match.start()
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
        if j >= len(text):
            print(f"  ! 未闭合的 <DataTable（偏移 {start}），停止扫描")
            break
        self_closing = text[j - 1] == "/"
        if self_closing:
            end = j + 1
        else:
            close = text.find("</DataTable>", j)
            if close < 0:
                print(f"  ! 找不到 </DataTable>（偏移 {start}），停止扫描")
                break
            end = close + len("</DataTable>")
        if end <= start:  # 防御：偏移不前进就是死循环
            print(f"  ! 偏移未前进（start={start} end={end}），停止扫描")
            break
        spans.append((start, end))
        cursor = end
    return spans


def insert_props(text: str, start: int, end: int, props: list[str]) -> str:
    block = text[start:end]
    lines = block.split("\n")
    # 最后一行是 `/>` 或 `>` / `</DataTable>`：属性插在它前面
    tail_index = len(lines) - 1
    while tail_index > 0 and lines[tail_index].strip() in ("/>", ">", "</DataTable>"):
        tail_index -= 1
    indent = lines[tail_index][: len(lines[tail_index]) - len(lines[tail_index].lstrip())]
    new_lines = lines[: tail_index + 1] + [f"{indent}{prop}" for prop in props] + lines[tail_index + 1 :]
    return "\n".join(new_lines)


def main() -> int:
    changed = 0
    for name, errors in ERROR_BY_BLOCK.items():
        path = VIEWS / name
        text = path.read_text(encoding="utf-8")
        spans = block_spans(text)
        if len(spans) != len(errors):
            print(f"[SKIP] {name}: 找到 {len(spans)} 个 DataTable，映射表有 {len(errors)} 项")
            continue

        search = SEARCH_BLOCKS.get(name, set())
        for index in reversed(range(len(spans))):  # 从后往前插，避免偏移
            props: list[str] = []
            error_var = errors[index]
            if error_var:
                props.append(f':error="Boolean({error_var})"')
            if index in search:
                props.append(':empty-illustration="keyword ? \'no-result\' : \'no-data\'"')
            if not props:
                continue
            start, end = spans[index]
            text = text[:start] + insert_props(text, start, end, props) + text[end:]

        path.write_text(text, encoding="utf-8")
        changed += 1
        print(f"接线 {name}：{len(spans)} 处表格")
    print(f"共 {changed} 个视图")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
