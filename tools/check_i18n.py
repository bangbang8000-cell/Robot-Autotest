#!/usr/bin/env python3
"""国际化守门脚本（FR-v0.2-37 / UX-03）。

三项硬检查，任一不过退出码 1：

1. **键集相等**——`zh-CN.ts` 与 `en-US.ts` 的键集合必须完全一致。
   少一个键，`te()` 返回 false，界面就静默回落到原始值（裸 `draft`、裸 `FINISHED`），
   这种退化在中文界面里几乎看不出来，只能靠脚本拦。

2. **零硬编码文案**——`frontend/src/**/*.{vue,ts}`（语言包本身除外）的
   `<template>` 与 `<script>` 里不得出现中文字面量。注释不算（注释是给维护者看的）。

3. **无同值漏译**——中文值里含汉字、却和英文值一模一样的键，视为漏译。
   反过来「P0」「CI」这类本身不含汉字的值同值是**正确**的，不报。

外加两项自检，防止脚本自身假装通过：

- 解析出的键少于 100 个 → 直接失败（说明解析逻辑坏了，而不是"真的没有键"）。
- 代码里用到的**字面量键**在语言包里必须存在。删键后忘了改调用点时，靠这条拦住。

用法：
    python tools/check_i18n.py          # 检查
    python tools/check_i18n.py -v       # 打印全部命中项
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "frontend" / "src"
LOCALES = SRC / "locales"


def is_test_file(path: Path) -> bool:
    """单测与测试基建文件：用例描述合法含中文，不参与 UI 文案守门。"""
    return path.name.endswith(".spec.ts") or path.name == "test-setup.ts"
ZH = LOCALES / "zh-CN.ts"
EN = LOCALES / "en-US.ts"

CJK = re.compile(r"[\u4e00-\u9fff]")
# 有意识地用中文书写的字面量：语言选择器的选项名按惯例用该语言自身书写
ALLOW_LITERALS = {
    ("frontend/src/App.vue", "中文"): "语言选择器选项用该语言自身书写（行业通行做法）",
    ("frontend/src/App.vue", "English"): "同上",
}
MIN_KEYS = 100
VERBOSE = "-v" in sys.argv or "--verbose" in sys.argv


# --------------------------------------------------------------------------- 解析语言包


def parse_locale_object(text: str) -> dict:
    """把 `export default { ... }` 解析成嵌套 dict。

    不引入第三方解析器（仓库不许加 JS 依赖给 Python 脚本用），手写一个小状态机。
    语言包只允许「标识符键 + 字符串值 + 嵌套对象」三种形态，所以够用；
    遇到别的形态直接抛错——**宁可脚本崩，也不要静默跳过一堆键**。
    """
    n = len(text)
    i = text.index("export default")
    i = text.index("{", i)

    def skip_ws(pos: int) -> int:
        while pos < n:
            ch = text[pos]
            if ch in " \t\r\n":
                pos += 1
            elif text.startswith("//", pos):
                end = text.find("\n", pos)
                pos = n if end < 0 else end + 1
            elif text.startswith("/*", pos):
                end = text.find("*/", pos)
                pos = n if end < 0 else end + 2
            else:
                break
        return pos

    def read_string(pos: int) -> tuple[str, int]:
        quote = text[pos]
        pos += 1
        buf: list[str] = []
        while pos < n:
            ch = text[pos]
            if ch == "\\":
                buf.append(text[pos + 1])
                pos += 2
            elif ch == quote:
                return "".join(buf), pos + 1
            else:
                buf.append(ch)
                pos += 1
        raise ValueError("字符串未闭合")

    def read_key(pos: int) -> tuple[str, int]:
        if text[pos] in "\"'":
            return read_string(pos)
        start = pos
        while pos < n and (text[pos].isalnum() or text[pos] in "_-$"):
            pos += 1
        if start == pos:
            raise ValueError(f"无法识别的键，位置 {pos}: {text[pos:pos + 30]!r}")
        return text[start:pos], pos

    def read_object(pos: int) -> tuple[dict, int]:
        if text[pos] != "{":
            raise ValueError(f"期望 {{，位置 {pos}: {text[pos:pos + 30]!r}")
        pos += 1
        out: dict = {}
        while True:
            pos = skip_ws(pos)
            if text[pos] == "}":
                return out, pos + 1
            key, pos = read_key(pos)
            pos = skip_ws(pos)
            if text[pos] != ":":
                raise ValueError(f"期望冒号，位置 {pos}: {text[pos:pos + 30]!r}")
            pos = skip_ws(pos + 1)
            if text[pos] == "{":
                value, pos = read_object(pos)
            elif text[pos] in "\"'":
                value, pos = read_string(pos)
            else:
                raise ValueError(f"不支持的值形态，位置 {pos}: {text[pos:pos + 30]!r}")
            if key in out:
                raise ValueError(f"重复键 {key}")
            out[key] = value
            pos = skip_ws(pos)
            if pos < n and text[pos] == ",":
                pos += 1

    obj, _ = read_object(i)
    return obj


def flatten(obj: dict, prefix: str = "") -> dict[str, str]:
    flat: dict[str, str] = {}
    for key, value in obj.items():
        path = f"{prefix}.{key}" if prefix else key
        if isinstance(value, dict):
            flat.update(flatten(value, path))
        else:
            flat[path] = value
    return flat


# --------------------------------------------------------------------------- 源码扫描


def strip_html_comments(text: str) -> str:
    return re.sub(r"<!--.*?-->", "", text, flags=re.S)


def strip_js_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"^\s*//.*$", "", text, flags=re.M)


def scan_template(template: str) -> list[tuple[str, str]]:
    """返回 [(类别, 片段)]；类别用于报告，便于定位。"""
    hits: list[tuple[str, str]] = []
    # 文字节点：去掉标签后剩下的内容
    for segment in re.sub(r"<[^>]*>", "\x00", template).split("\x00"):
        cleaned = segment.strip()
        if cleaned and CJK.search(cleaned):
            hits.append(("text", cleaned[:80]))
    # 插值表达式里的中文字面量
    for match in re.finditer(r"\{\{(.*?)\}\}", template, re.S):
        inner = match.group(1)
        if CJK.search(inner):
            hits.append(("expr", inner.strip()[:100]))
    # 静态属性
    for match in re.finditer(r"(?<![:@])\b([a-zA-Z-]+)=\"([^\"]*" + CJK.pattern + r"[^\"]*)\"", template):
        hits.append(("attr", f'{match.group(1)}="{match.group(2)}"'))
    # 绑定表达式里的中文字面量
    for match in re.finditer(r":[a-zA-Z-]+=\"([^\"]*" + CJK.pattern + r"[^\"]*)\"", template):
        hits.append(("bind", match.group(1)[:100]))
    return hits


def scan_script(script: str) -> list[tuple[str, str]]:
    hits: list[tuple[str, str]] = []
    for match in re.finditer(r"(['\"`])((?:(?!\1).)*)" + CJK.pattern + r"((?:(?!\1).)*)\1", script):
        hits.append(("script", (match.group(2) + match.group(3))[:100]))
    return hits


def scan_file(path: Path) -> list[tuple[str, str]]:
    rel = path.relative_to(ROOT).as_posix()
    text = path.read_text(encoding="utf-8")
    hits: list[tuple[str, str]] = []

    if path.suffix == ".vue":
        block = re.search(r"<template>(.*)</template>\s*(?:\n<style|\Z)", text, re.S)
        template = strip_html_comments(block.group(1)) if block else ""
        if template:
            hits.extend(scan_template(template))
        for script_match in re.finditer(r"<script[^>]*>(.*?)</script>", text, re.S):
            hits.extend(scan_script(strip_js_comments(script_match.group(1))))
    elif path.suffix == ".ts":
        hits.extend(scan_script(strip_js_comments(text)))

    kept = [item for item in hits if (rel, item[1]) not in ALLOW_LITERALS]
    return kept


def literal_keys(path: Path) -> set[str]:
    """收集代码里出现的字面量 i18n 键（只认带点、且不含插值的写法）。"""
    text = path.read_text(encoding="utf-8")
    found: set[str] = set()
    for match in re.finditer(r"\b(?:t|te)\(\s*'([A-Za-z][\w.]*)'", text):
        if "." in match.group(1):
            found.add(match.group(1))
    return found


STATUS_LABELS = SRC / "utils" / "statusLabels.ts"


def enum_group_keys() -> list[str]:
    """从 `utils/statusLabels.ts` 的 `ENUM_GROUPS` 推导出**全部应存在的键**。

    动态拼键（`enum.${group}.${value}`）是上面「字面量键」检查的盲区：
    键被误删时 `te()` 返回 false，界面静默回落成裸 `approved`，
    中文界面下几乎看不出来。本项目真的发生过一次，所以单列一条检查。
    """
    if not STATUS_LABELS.exists():
        return []
    text = STATUS_LABELS.read_text(encoding="utf-8")

    arrays: dict[str, list[str]] = {}
    for match in re.finditer(r"export const ([A-Z_]+)\s*=\s*\[([^\]]*)\]", text):
        values = re.findall(r"'([^']+)'", match.group(2))
        arrays[match.group(1)] = values

    block = re.search(r"export const ENUM_GROUPS\s*=\s*\{(.*?)\}\s*as const", text, re.S)
    if not block:
        return []
    keys: list[str] = []
    for match in re.finditer(r"(\w+)\s*:\s*([A-Z_]+)", block.group(1)):
        group, array_name = match.group(1), match.group(2)
        for value in arrays.get(array_name, []):
            keys.append(f"enum.{group}.{value.lower()}")
    return keys


# --------------------------------------------------------------------------- 主流程


def main() -> int:
    problems: list[str] = []

    zh = flatten(parse_locale_object(ZH.read_text(encoding="utf-8")))
    en = flatten(parse_locale_object(EN.read_text(encoding="utf-8")))

    # 自检 0：解析出的键数量下限。解析挂了却"零问题"是最危险的假绿。
    if len(zh) < MIN_KEYS or len(en) < MIN_KEYS:
        print(f"[FAIL] 自检：解析到的键太少（zh={len(zh)} en={len(en)}，下限 {MIN_KEYS}）")
        print("       说明语言包解析逻辑失效，本次结果不可信。")
        return 2
    print(f"[1/5] 语言包解析：zh-CN {len(zh)} 键 / en-US {len(en)} 键")

    # 检查 1：键集相等
    only_zh = sorted(set(zh) - set(en))
    only_en = sorted(set(en) - set(zh))
    if only_zh or only_en:
        problems.append("键集不相等")
        for key in only_zh:
            problems.append(f"  en-US 缺少键：{key}（zh 值「{zh[key]}」）")
        for key in only_en:
            problems.append(f"  zh-CN 缺少键：{key}（en 值「{en[key]}」）")
    else:
        print("[2/5] 键集相等：通过")

    # 检查 2：源码零硬编码中文
    # 测试文件（*.spec.ts / test-setup.ts）豁免：用例描述与注释合法含中文，
    # 不是 UI 字符串；UI 文案守门只针对运行时代码（UX-02 引入 vitest 时补的口径）。
    scanned = 0
    hardcoded = 0
    for path in sorted(list(SRC.rglob("*.vue")) + list(SRC.rglob("*.ts"))):
        if LOCALES in path.parents or is_test_file(path):
            continue
        scanned += 1
        for kind, snippet in scan_file(path):
            hardcoded += 1
            problems.append(f"{path.relative_to(ROOT).as_posix()} [{kind}] 硬编码中文：{snippet}")
    print(f"[3/5] 源码扫描：{scanned} 个文件，硬编码中文 {hardcoded} 处")

    # 检查 3：同值漏译（只对含汉字的中文值判定）
    for key, value in sorted(zh.items()):
        if CJK.search(value) and en.get(key) == value:
            problems.append(f"同值漏译：{key} = 「{value}」（中英一致）")

    # 自检 4：代码里用到的字面量键必须存在
    used: set[str] = set()
    for path in list(SRC.rglob("*.vue")) + list(SRC.rglob("*.ts")):
        if LOCALES in path.parents or is_test_file(path):
            continue
        used |= literal_keys(path)
    missing = sorted(key for key in used if key not in zh or key not in en)
    for key in missing:
        problems.append(f"代码引用了不存在的键：{key}")

    # 检查 5：受控词表逐值核对（动态拼键的盲区）
    enum_keys = enum_group_keys()
    if not enum_keys:
        problems.append("自检失败：没能从 utils/statusLabels.ts 解析出 ENUM_GROUPS，本项检查形同虚设")
    for key in enum_keys:
        if key not in zh:
            problems.append(f"枚举缺键（zh-CN）：{key}")
        if key not in en:
            problems.append(f"枚举缺键（en-US）：{key}")

    if problems:
        print("")
        for line in problems:
            print(("[FAIL] " if not line.startswith("  ") else "       ") + line)
        print(f"\n[RESULT] 失败：{len(problems)} 项问题")
        return 1

    if VERBOSE:
        print(f"      代码引用字面量键 {len(used)} 个，全部存在")
    print("[4/5] 同值漏译与键引用：通过")
    print(f"[5/5] 受控词表：{len(enum_keys)} 个值逐个核对，全部有键")
    print("[RESULT] 通过：键集相等 / 无硬编码中文 / 无同值漏译 / 键引用完整 / 枚举全覆盖")
    return 0


if __name__ == "__main__":
    sys.exit(main())
