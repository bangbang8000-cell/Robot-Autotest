#!/usr/bin/env python3
"""UX-04 步骤 2：打包字体子集（Noto Sans SC + JetBrains Mono）与 fonts.css 生成器。

为什么必须子集化
----------------
`@fontsource/noto-sans-sc` 的 `chinese-simplified-400-normal.woff2` 是 **1.1 MiB 整包**，
直接 `@import` 会让中文首屏多下载 1 MiB（计划判据：首屏字体增量 ≤ 800 KiB gzip）。
本项目是内网测试工具，界面文案是**编译期已知**的，所以按实际字符集做静态子集，
再按 unicode-range 拆成「拉丁 / 汉字」两片：浏览器只在页面出现对应字符时才取那一篇。

受限说明（不假装做到）：动态数据里的冷僻汉字不在子集里，会由 font-family 栈
回退到系统中文字体（'Microsoft YaHei' / system-ui），不破版但字形略有差异。
这正是 TC-S1-02「断网时回落系统栈不破版」的同一条路径；要覆盖全部汉字需改回整包，
与 800 KiB 预算冲突，故不这么做。

用法
----
    python3 tools/subset_font.py            # 从 node_modules 生成字体 + fonts.css
    python3 tools/subset_font.py --check   # 只校验已有产物（CI / lint 可接）

依赖：fonttools、brotli（见 tools/requirements.txt）；字体源文件来自 frontend/node_modules。
"""

from __future__ import annotations

import argparse
import gzip
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "frontend"
FONTS_DIR = FRONTEND / "src" / "assets" / "fonts"
FONTS_CSS = FRONTEND / "src" / "styles" / "fonts.css"

SC_SIMPLE = (
    FRONTEND
    / "node_modules/@fontsource/noto-sans-sc/files/noto-sans-sc-chinese-simplified-400-normal.woff2"
)
SC_LATIN = (
    FRONTEND / "node_modules/@fontsource/noto-sans-sc/files/noto-sans-sc-latin-400-normal.woff2"
)
MONO_LATIN = (
    FRONTEND
    / "node_modules/@fontsource/jetbrains-mono/files/jetbrains-mono-latin-400-normal.woff2"
)

SCAN_ROOTS = [FRONTEND / "src", ROOT / "packages" / "editor" / "src"]
SCAN_SUFFIXES = {".vue", ".ts", ".css", ".html"}

# 扫描到的字之外再兜一组「运行时才出现」的高频字，避免首屏几个字回退得刺眼。
FALLBACK_CHARS = set(
    "零一二三四五六七八九十百千万亿个年月日时分秒次条项已未无有是否成功失败跳过错误告警"
    "自动化测试平台项目用例执行报告资产设备管理审计登录退出设置主题语言亮暗保存取消删除"
    "确认返回上一下一页共条选择全部搜索筛选刷新下载上传新增编辑查看详情运行状态结果"
    "项目编号名称类型备注创建修改操作用户角色权限所有者成员管理员工程师访客查看只读"
    "排队准备中完成已取消手动计划日志证据基线对比版本环境设备链路凭据"
)

LATIN_RANGE = (
    "U+0000-00FF, U+0131, U+0152-0153, U+02BB-02BC, U+02C6, U+02DA, U+02DC, "
    "U+0304, U+0308, U+0329, U+2000-206F, U+20AC, U+2122, U+2191, U+2193, "
    "U+2212, U+2215, U+FEFF, U+FFFD"
)
CJK_RANGE = (
    "U+2E80-2EFF, U+3000-303F, U+3400-4DBF, U+4E00-9FFF, "
    "U+F900-FAFF, U+FF00-FFEF"
)

MIN_CHARS = 200  # 低于这个数说明扫描路径失效，不能当成「字很少」（防假绿）
FONT_BUDGET = 800 * 1024  # 计划 §2.2 步骤 2：首屏字体增量 ≤ 800 KiB


def is_cjk(ch: str) -> bool:
    cp = ord(ch)
    return (
        0x2E80 <= cp <= 0x2EFF
        or 0x3000 <= cp <= 0x303F
        or 0x3400 <= cp <= 0x4DBF
        or 0x4E00 <= cp <= 0x9FFF
        or 0xF900 <= cp <= 0xFAFF
        or 0xFF00 <= cp <= 0xFFEF
    )


def collect_chars() -> set[str]:
    chars: set[str] = set(FALLBACK_CHARS)
    for root in SCAN_ROOTS:
        if not root.is_dir():
            continue
        for path in root.rglob("*"):
            if path.suffix not in SCAN_SUFFIXES or not path.is_file():
                continue
            chars.update(c for c in path.read_text(encoding="utf-8", errors="ignore") if is_cjk(c))
    return chars


def subset(source: Path, target: Path, chars: set[str]) -> None:
    from fontTools import subset as ft_subset
    from fontTools.ttLib import TTFont

    options = ft_subset.Options()
    options.flavor = "woff2"
    options.notdef_outline = True
    options.layout_features = ["*"]
    font = TTFont(source)
    sub = ft_subset.Subsetter(options=options)
    sub.populate(unicodes=sorted({ord(c) for c in chars}))
    sub.subset(font)
    font.flavor = "woff2"
    font.save(target)


CSS_TEMPLATE = """/*
 * 由 `python3 tools/subset_font.py` 生成，勿手改。
 * 两片 Noto Sans SC（拉丁 / 汉字）+ 一片 JetBrains Mono，全部 font-display: swap：
 * 字体没到时先用系统栈渲染首帧，不阻塞；断网时直接停在回退栈上，不破版。
 */
@font-face {
  font-family: 'Noto Sans SC';
  font-style: normal;
  font-weight: 400;
  font-display: swap;
  src: url('../assets/fonts/noto-sans-sc-latin.woff2') format('woff2');
  unicode-range: {latin_range};
}

@font-face {
  font-family: 'Noto Sans SC';
  font-style: normal;
  font-weight: 400;
  font-display: swap;
  src: url('../assets/fonts/noto-sans-sc-ui.woff2') format('woff2');
  unicode-range: {cjk_range};
}

@font-face {
  font-family: 'JetBrains Mono';
  font-style: normal;
  font-weight: 400;
  font-display: swap;
  src: url('../assets/fonts/jetbrains-mono-latin.woff2') format('woff2');
  unicode-range: {latin_range};
}
"""


def gzip_size(path: Path) -> int:
    return len(gzip.compress(path.read_bytes(), compresslevel=9))


def report(chars: set[str]) -> int:
    paths = [
        FONTS_DIR / "noto-sans-sc-latin.woff2",
        FONTS_DIR / "noto-sans-sc-ui.woff2",
        FONTS_DIR / "jetbrains-mono-latin.woff2",
    ]
    total = 0
    print(f"扫描到界面汉字 {len(chars)} 个（下限 {MIN_CHARS}）")
    for path in paths:
        if not path.is_file():
            print(f"[FAIL] 缺产物 {path}", file=sys.stderr)
            return 2
        raw, gz = path.stat().st_size, gzip_size(path)
        total += gz
        print(f"  {path.name:36s} {raw:9d} B  gzip {gz:9d} B")
    print(f"  {'合计（gzip）':36s} {'':9s}   {total:9d} B  / 预算 {FONT_BUDGET} B")
    css = FONTS_CSS.read_text(encoding="utf-8") if FONTS_CSS.is_file() else ""
    if css.count("font-display: swap") < 3:
        print("[FAIL] fonts.css 缺少 3 条 font-display: swap", file=sys.stderr)
        return 1
    if CJK_RANGE.split(",")[0].strip() not in css:
        print("[FAIL] fonts.css 缺少汉字 unicode-range 分片", file=sys.stderr)
        return 1
    if total > FONT_BUDGET:
        print("[FAIL] 字体首屏增量超预算", file=sys.stderr)
        return 1
    if len(chars) < MIN_CHARS:
        print("[FAIL] 扫描字符数过少，疑似扫描路径失效", file=sys.stderr)
        return 2
    print(f"[PASS] 字体子集 {len(chars)} 字 / {total} B gzip，未超 {FONT_BUDGET // 1024} KiB 预算")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="生成/校验打包字体子集")
    parser.add_argument("--check", action="store_true", help="只校验已有产物，不重新生成")
    args = parser.parse_args()

    chars = collect_chars()
    if not args.check:
        for src in (SC_SIMPLE, SC_LATIN, MONO_LATIN):
            if not src.is_file():
                print(f"[FAIL] 找不到字体源文件 {src}；请先 npm ci", file=sys.stderr)
                return 2
        try:
            FONTS_DIR.mkdir(parents=True, exist_ok=True)
            subset(SC_SIMPLE, FONTS_DIR / "noto-sans-sc-ui.woff2", chars)
            (FONTS_DIR / "noto-sans-sc-latin.woff2").write_bytes(SC_LATIN.read_bytes())
            (FONTS_DIR / "jetbrains-mono-latin.woff2").write_bytes(MONO_LATIN.read_bytes())
            # 不能用 str.format：CSS 本身的 { } 会被当成格式化字段（KeyError: '\n  font-family'）
            FONTS_CSS.write_text(
                CSS_TEMPLATE.replace("{latin_range}", LATIN_RANGE).replace(
                    "{cjk_range}", CJK_RANGE
                ),
                encoding="utf-8",
            )
            print(f"[OK] 已生成 {FONTS_DIR} 与 {FONTS_CSS}")
        except Exception as exc:  # noqa: BLE001 - 字体工具链失败要明确报错而不是半产物
            print(f"[FAIL] 子集化失败：{exc}", file=sys.stderr)
            return 2
    return report(chars)


if __name__ == "__main__":
    raise SystemExit(main())
