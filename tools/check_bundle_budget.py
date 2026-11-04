#!/usr/bin/env python3
"""构建产物体积守门（UX-04 美术 S1 / 《美术体系-开发与测试计划-20261104.md》§2.2、§4）。

为什么需要这个工具
------------------
UX-04 S1 会给主仓引入 Naive UI、lucide-vue-next、@fontsource/noto-sans-sc、
@fontsource/jetbrains-mono，并新增一个**仅 DEV 挂载**的 /dev/styleguide 路由页。
这几样东西都会实打实地改变 frontend/dist 的体积和内容，但「页面看起来没问题」
完全掩盖得了体积超标和 DEV-only 页面泄漏。本脚本直接扫 Vite 产物，把计划里的
判据做成 CI 守门（由 scripts/lint.sh 接线）。

判据来源（数字照抄计划与实测，不另行发明）
--------------------------------------------
- §2.2 步骤 1：图标 chunk ≤ 15 KB（TC-S1-01「图标 chunk gzip ≤ 15 KB」）。
- §2.2 步骤 2：首屏字体增量目标 ≤ 800 KB gzip；font-display: swap 不阻塞首帧
  （TC-S1-02）。Vite 打包后 CSS 里应有 font-display: swap 声明。
- §2.2 步骤 8：组件库 chunk 预算在试点实测后定，> 预算报警（TC-S1-09）。
- §4：styleguide 泄漏到生产 —— 路由挂载条件 `import.meta.env.DEV || 显式开关`，
  **构建产物 grep 验证**。

两页试点实测（docs/evidence/v0.3/ux04-pilot-measure-20261104.tsv）：
  - Naive UI 2.45.3 独立试点：JS gzip 153,376 B，合计 gzip 154,779 B；
  - Element Plus 2.14.7：合计 174,484 B。
  注意：这两个数**含 vue runtime**，是「独立入口完整产物」，
  **不是接入主仓后的净增量**。

三档预算（统一按 1 KiB = 1024 B 换算，与既定口径「200 KiB = 204800 B」一致）：
- 图标 chunk：15 KiB = 15360 B（gzip）。
- 组件库 vendor chunk：200 KiB = 204800 B（gzip）。
  定档理由：试点 Naive UI 合计 154,779 B 含 vue runtime；主仓本就复用同一个
  runtime，接入后的组件库净增量应明显小于该独立入口完整产物。取整到 200 KiB
  既高于实测值、又留出余量，避免把「复用 runtime 后本应更小」的产物误判超标。
  （Element Plus 对照 174,484 B 同样落在该预算内。）
- 字体：800 KiB = 819200 B（gzip），对应步骤 2 的「≤ 800 KB gzip」。

gzip 数值由本脚本对产物文件即时计算（gzip.compress level=9、mtime=0 固定，
保证可复现），与试点 TSV 的「gzip 后字节」口径一致；Vite 默认不产出 .gz 文件，
所以不依赖 .gz 是否存在。

styleguide 标记约定（§4「构建产物 grep 验证」）
------------------------------------------------
用户会在 styleguide 组件里放一个稳定字符串 __STYLEGUIDE_DEV_ONLY__（本脚本
只认这一个常量）。/dev/styleguide 仅 DEV 挂载，生产构建里**不应**出现它：
- 命中   → 违规（DEV 页面泄漏到生产）；
- 找不到 → 不能假绿：脚本不假设该标记一定存在（组件还没写、字符串被换名、
  或产物被整体裁剪都可能），此时显式打印「未找到标记」并计入「未触发项」，
  让守门结果如实反映「这一项本轮其实没被验证到」，而不是闷头 PASS。

关于「当前产物还没有新资产」的处理
------------------------------------
本轮改造落地前，dist 里既没有 lucide / naive-ui 标识，也没有 woff2 / 标记。
判据是**上限**，用量为 0 天然不违规，因此这些情况一律不判 FAIL（不假红）；
但每一项都显式 [INFO] 报告「未检测到目标 / 检查未触发」，并在末尾 [PASS] 里
标出未触发项数（不假绿）。依赖与 styleguide 落地后，各项自动转为实测判定。

用法
----
    python3 tools/check_bundle_budget.py                  # 扫 frontend/dist
    python3 tools/check_bundle_budget.py --dist PATH      # 扫指定产物目录
    python3 tools/check_bundle_budget.py --self-test      # 破坏性自检（不碰 frontend/dist）

退出码（与其它 tools/check_*.py 一致）：
    0 = 通过（含 dist 缺失 SKIP、含未触发项如实报告）；
    1 = 有违规（自检模式下 = 自检失败）；
    2 = 扫描本身失败（dist 损坏 / 计数下限不满足 / 读文件出错，疑似假绿）。
"""

from __future__ import annotations

import argparse
import gzip
import os
import re
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DIST = ROOT / "frontend" / "dist"

# ---- 预算：统一按 1 KiB = 1024 B 换算（与既定口径「200 KiB = 204800 B」一致） ----
ICON_BUDGET = 15 * 1024        # 15360 B —— §2.2 步骤 1「图标 chunk ≤ 15 KB」gzip
VENDOR_BUDGET = 200 * 1024     # 204800 B —— §2.2 步骤 8，试点实测后定
FONT_BUDGET = 800 * 1024       # 819200 B —— §2.2 步骤 2「首屏字体增量 ≤ 800 KB gzip」

# 标识是启发式：文件名或内容命中即认为属于该类 chunk（大小写不敏感）。
# 若 Vite 产物里的标识形态变化（例如只留 n-* 类名、不再出现包名），需同步更新
# 这里的常量；否则会如实走「未找到独立 chunk」分支并给出全量 JS gzip 数，
# 不会静默放过。
ICON_MARKER = b"lucide"                          # lucide-vue-next
VENDOR_MARKER = b"naive-ui"                      # Naive UI
STYLEGUIDE_MARKER = b"__STYLEGUIDE_DEV_ONLY__"   # 由 styleguide 组件提供，见上方约定
# 标记的正向来源：约定字符串必须真实存在于这个源文件里，否则「产物中没找到」不能
# 说明没泄漏（可能只是被改名）。这个文件由 router 的 DEV 条件挂载引用。
VERIFY_MARKER_SOURCE = ROOT / "frontend" / "src" / "views" / "StyleguideView.vue"
FONT_DISPLAY_RE = re.compile(r"font-display\s*:\s*swap", re.I)

# 计数下限：dist 存在但文件太少时，多半是产物没构建全 / 路径变了，而不是
# 「项目变干净了」。宁可报扫描失败（2），也不能让空产物一路 PASS 成假绿。
MIN_JS_FILES = 1
MIN_CSS_FILES = 1


@dataclass
class Entry:
    path: Path
    rel: str          # 相对 dist 的路径（即输出里的「文件名」）
    data: bytes
    raw: int
    gz: int
    suffix: str


@dataclass
class Bundle:
    dist: Path
    entries: list[Entry]

    @property
    def js(self) -> list[Entry]:
        return [e for e in self.entries if e.suffix == ".js"]

    @property
    def css(self) -> list[Entry]:
        return [e for e in self.entries if e.suffix == ".css"]

    @property
    def fonts(self) -> list[Entry]:
        return [e for e in self.entries if e.suffix == ".woff2"]

    @property
    def css_text(self) -> str:
        return "\n".join(e.data.decode("utf-8", "ignore") for e in self.css)


@dataclass
class Result:
    violations: list[str] = field(default_factory=list)
    infos: list[str] = field(default_factory=list)
    untriggered: list[str] = field(default_factory=list)
    scan_error: str | None = None


def gzip_len(data: bytes) -> int:
    # mtime=0 让结果可复现（gzip 头的时间戳不参与压缩体大小，但固定它更干净）。
    return len(gzip.compress(data, compresslevel=9, mtime=0))


def read_bundle(dist: Path) -> Bundle:
    """读取整个 dist；OSError 交给 main 转成退出码 2（扫描本身失败）。"""
    entries: list[Entry] = []
    for path in sorted(dist.rglob("*")):
        if not path.is_file():
            continue
        data = path.read_bytes()
        entries.append(Entry(
            path=path,
            rel=str(path.relative_to(dist)),
            data=data,
            raw=len(data),
            gz=gzip_len(data),
            suffix=path.suffix.lower(),
        ))
    return Bundle(dist=dist, entries=entries)


def has_marker(e: Entry, marker: bytes) -> bool:
    """文件名或内容含标识（大小写不敏感）。"""
    return marker in e.path.name.lower().encode() or marker in e.data.lower()


def evaluate(bundle: Bundle) -> Result:
    r = Result()

    # ---- 自检 0：计数下限（防「dist 存在但几乎没文件」的假绿） ----
    if len(bundle.js) < MIN_JS_FILES or len(bundle.css) < MIN_CSS_FILES:
        r.scan_error = (
            f"dist 存在但文件数过少：JS {len(bundle.js)} 个（需 ≥{MIN_JS_FILES}）、"
            f"CSS {len(bundle.css)} 个（需 ≥{MIN_CSS_FILES}）；共 {len(bundle.entries)} 个文件，"
            f"疑似产物未完整生成或目录结构变更"
        )
        return r

    # ---- (a) 图标 chunk ≤ 15 KiB gzip（§2.2 步骤 1 / TC-S1-01） ----
    # 只按**文件名**认定独立图标 chunk。曾经按「文件内容含 lucide 标识」认定，
    # 结果把含 vue runtime + naive-ui 的整个主包（138 KB gzip）当成图标 chunk 判超预算 ——
    # 守门自己造假红。现在：内容里有 lucide 但文件名没有 → 说明图标没独立分包，
    # 明确报「无法按 chunk 判预算」（vite.config.ts 的 manualChunks 负责拆出来）。
    icon_named = [e for e in bundle.js if "lucide" in e.path.name.lower()]
    icon_any = [e for e in bundle.js if has_marker(e, ICON_MARKER)]
    if not icon_any:
        r.untriggered.append("图标 chunk（lucide 标识）")
        r.infos.append(
            "未检测到 lucide 图标 chunk（文件名/内容均无 lucide 标识）；"
            "图标 ≤ 15 KiB 检查未触发"
        )
    elif not icon_named:
        r.violations.append(
            "图标未独立分包：lucide 代码混在 "
            + ", ".join(e.rel for e in icon_any)
            + " 中，无法按 chunk 判预算（§2.2 步骤 1 要求独立图标 chunk）；"
            "请在 vite.config.ts 的 manualChunks 拆出 lucide"
        )
    else:
        total_gz = sum(e.gz for e in icon_named)
        over = [e for e in icon_named if e.gz > ICON_BUDGET]
        for e in over:
            r.violations.append(
                f"图标 chunk 超预算：{e.rel} gzip {e.gz} B > {ICON_BUDGET} B（15 KiB）"
            )
        if not over:
            r.infos.append(
                f"图标 chunk {len(icon_named)} 个，gzip 合计 {total_gz} B ≤ "
                f"{ICON_BUDGET} B（15 KiB），达标"
            )

    # ---- (b) 组件库 vendor chunk ≤ 200 KiB gzip（§2.2 步骤 8 / TC-S1-09） ----
    vendor_chunks = [e for e in bundle.js if has_marker(e, VENDOR_MARKER)]
    if not vendor_chunks:
        all_js_gz = sum(e.gz for e in bundle.js)
        all_js_raw = sum(e.raw for e in bundle.js)
        r.untriggered.append("组件库 vendor chunk（naive-ui 标识）")
        r.infos.append(
            "未找到独立的 naive-ui vendor chunk（文件名/内容均无 naive-ui 标识），"
            "无法单独衡量组件库体积；200 KiB 判据本轮不适用。"
            f"全量 JS gzip 合计 {all_js_gz} B（原始 {all_js_raw} B，{len(bundle.js)} 个 .js 文件），"
            "仅作参考、不参与判据"
        )
    else:
        total_gz = sum(e.gz for e in vendor_chunks)
        over = [e for e in vendor_chunks if e.gz > VENDOR_BUDGET]
        for e in over:
            r.violations.append(
                f"组件库 vendor chunk 超预算：{e.rel} gzip {e.gz} B > "
                f"{VENDOR_BUDGET} B（200 KiB；试点实测 154,779 B 取整定档）"
            )
        if not over:
            r.infos.append(
                f"组件库 vendor chunk {len(vendor_chunks)} 个，gzip 合计 {total_gz} B ≤ "
                f"{VENDOR_BUDGET} B（200 KiB），达标"
            )

    # ---- (c) 字体 ≤ 800 KiB + font-display: swap（§2.2 步骤 2 / TC-S1-02） ----
    # 以 dist 内全部 woff2 之和作首屏增量的保守上界（不细分 unicode-range 分包，
    # 避免漏报）；原始总和与 gzip 后总和都报告，取较大者与预算比较。
    fonts = bundle.fonts
    raw_total = sum(e.raw for e in fonts)
    gz_total = sum(e.gz for e in fonts)
    if not fonts:
        r.untriggered.append("字体资产（woff2 总和 / font-display: swap）")
        r.infos.append("未检测到 *.woff2 字体资产；字体 ≤ 800 KiB 检查未触发")
        if FONT_DISPLAY_RE.search(bundle.css_text):
            r.infos.append("CSS 中存在 font-display: swap 声明（但当前未引入 woff2 字体资产，该项未触发判定）")
        else:
            r.infos.append("CSS 中未找到 font-display: swap 声明（当前产物未引入打包字体，该项未触发）")
    else:
        worst = max(raw_total, gz_total)
        if worst > FONT_BUDGET:
            r.violations.append(
                f"字体超预算：{len(fonts)} 个 woff2 原始合计 {raw_total} B、"
                f"gzip 合计 {gz_total} B，较大者 {worst} B > {FONT_BUDGET} B（800 KiB）"
            )
        else:
            r.infos.append(
                f"字体 {len(fonts)} 个 woff2：原始合计 {raw_total} B、gzip 合计 "
                f"{gz_total} B ≤ {FONT_BUDGET} B（800 KiB），达标"
            )
        # 有字体资产时才强制要求 swap：font-display 缺失会阻塞首帧。
        if FONT_DISPLAY_RE.search(bundle.css_text):
            r.infos.append("CSS 中存在 font-display: swap，首帧不被字体阻塞，达标")
        else:
            r.violations.append(
                "检测到 woff2 字体资产，但 CSS 中未找到 font-display: swap 声明"
                "（会阻塞首帧，违反 §2.2 步骤 2）"
            )

    # ---- (d) styleguide 标记不得出现在生产产物（§4 / 构建产物 grep 验证） ----
    # 先做**正向**校验：约定字符串必须还在源码里被真实使用（`StyleguideView.vue` 的
    # `:data-marker="MARKER"`）。没有这一步，「产物里没找到标记」就有第二种解释——
    # 有人把标记改名或删了，于是这项检查永远绿。守门必须能区分「没泄漏」和「根本没测」。
    marker_src = VERIFY_MARKER_SOURCE
    if marker_src.is_file():
        src_text = marker_src.read_text(encoding="utf-8", errors="replace")
        if STYLEGUIDE_MARKER.decode() not in src_text:
            r.violations.append(
                f"styleguide 标记约定失效：{marker_src} 里找不到 "
                f"{STYLEGUIDE_MARKER.decode()}。约定字符串被改名/删除，"
                "「产物无泄漏」的检查将失去意义（改回该字符串，或同步改本脚本）"
            )
        else:
            r.infos.append(
                f"styleguide 标记在源码中存在（{marker_src.name}），"
                "产物侧 grep 因此具备判定力"
            )
    else:
        # 源码路径都不在了，说明目录结构变了：不静默跳过，计入未触发并说明原因。
        r.untriggered.append("styleguide 源码标记（源码文件不存在）")
        r.infos.append(
            f"[INFO] 未找到 {marker_src}，无法做标记正向校验；"
            "产物侧仍继续 grep，但结论强度下降"
        )

    # 对全部产物文件（含 .html 等）做字节级 grep，等价于 grep -r。
    hits = [e for e in bundle.entries if STYLEGUIDE_MARKER in e.data]
    if hits:
        for e in hits:
            r.violations.append(
                f"styleguide 标记泄漏到生产：{e.rel} 含 __STYLEGUIDE_DEV_ONLY__"
                "（§4 要求构建产物 grep 为空）"
            )
    elif marker_src.is_file() and STYLEGUIDE_MARKER.decode() in marker_src.read_text(
            encoding="utf-8", errors="replace"):
        # 正向校验通过 + 产物侧 0 命中 = 真的没泄漏，不再挂「未触发」。
        r.infos.append(
            "生产产物中未找到 __STYLEGUIDE_DEV_ONLY__ 标记，且源码侧标记确认存在"
            "（DEV 条件挂载 + tree-shaking 生效），本项已被反向验证"
        )
    else:
        r.untriggered.append("styleguide 标记 grep（未检出标记）")
        r.infos.append(
            "生产产物中未找到 __STYLEGUIDE_DEV_ONLY__ 标记（无泄漏）；但源码侧标记缺失，"
            "无法排除「字符串被换名」，故如实计入『未触发』，不假绿"
        )

    return r


def report(bundle: Bundle, r: Result) -> None:
    js, css, fonts = bundle.js, bundle.css, bundle.fonts
    other = len(bundle.entries) - len(js) - len(css) - len(fonts)
    print(f"扫描产物目录：{bundle.dist}")
    print(
        f"产物概览：共 {len(bundle.entries)} 个文件"
        f"（JS {len(js)} / CSS {len(css)} / woff2 {len(fonts)} / 其它 {other}）"
    )
    print(
        f"全量 JS ：原始 {sum(e.raw for e in js):>9} B / gzip "
        f"{sum(e.gz for e in js):>9} B（{len(js)} 个文件）"
    )
    print(
        f"全量 CSS：原始 {sum(e.raw for e in css):>9} B / gzip "
        f"{sum(e.gz for e in css):>9} B（{len(css)} 个文件）"
    )

    print("\n=== 产物文件清单（原始字节 / gzip 字节 / 文件名）===")
    for e in bundle.entries:
        if e.suffix in (".js", ".css", ".woff2"):
            print(f"  {e.raw:>9} B / gzip {e.gz:>9} B  {e.rel}")

    print("\n=== 各项判定 ===")
    for v in r.violations:
        print(f"[FAIL] {v}")
    for i in r.infos:
        print(f"[INFO] {i}")


# ---- 破坏性自检用的夹具：全部写在临时目录，不触碰 frontend/dist ----

def _make_baseline(dist: Path) -> Path:
    """构造满足计数下限、不含任何违规的最小 Vite 产物。"""
    assets = dist / "assets"
    assets.mkdir(parents=True)
    (dist / "index.html").write_text("<!doctype html><html></html>", encoding="utf-8")
    (assets / "index-AAAA.js").write_bytes(b"export const ok = 1;\n")
    # CSS 里放 swap：注入「字体超预算」用例时不会混入「缺 font-display」的额外违规
    (assets / "index-BBBB.css").write_bytes(b"body{color:#333;font-display:swap}\n")
    return dist


def _inject_icon_over(dist: Path) -> None:
    # 文件名 + 内容都带 lucide 标识；不可压缩随机字节确保 gzip 后 > 15 KiB
    payload = b"/* lucide icon chunk */\n" + os.urandom(20000)
    (dist / "assets" / "lucide-CCCC.js").write_bytes(payload)


def _inject_font_over(dist: Path) -> None:
    # 随机字节 gzip 后几乎不缩，稳超 800 KiB
    (dist / "assets" / "noto-sans-sc-DDDD.woff2").write_bytes(
        b"wOF2" + os.urandom(820000)
    )


def _inject_styleguide_leak(dist: Path) -> None:
    (dist / "assets" / "styleguide-EEEE.js").write_bytes(
        b"export const devOnly = '__STYLEGUIDE_DEV_ONLY__';\n"
    )


def self_test() -> int:
    """破坏性自检：在临时目录伪造 dist，注入三类违规，确认 evaluate 真能拦截。

    不污染 frontend/dist：每个用例一个 TemporaryDirectory，dist 通过参数传给
    read_bundle/evaluate；不改任何全局常量（无需 monkeypatch），上下文退出即
    删除临时目录 —— 自检结束天然还原。
    """
    failures = 0

    # 用例 0：干净基线必须 PASS（验证「没引入新资产时不假红」）
    with tempfile.TemporaryDirectory(prefix="bundle-budget-selftest-") as tmp:
        r = evaluate(read_bundle(_make_baseline(Path(tmp))))
        if r.scan_error or r.violations:
            print(
                f"[FAIL] 自检基线：干净产物未被判通过"
                f"（scan_error={r.scan_error}, violations={r.violations}）",
                file=sys.stderr,
            )
            failures += 1
        else:
            print("[SELFTEST] 通过：干净基线产物不假红（应返回 PASS）")

    cases = [
        ("图标超预算", _inject_icon_over, "图标"),
        ("字体超预算", _inject_font_over, "字体"),
        ("styleguide 标记泄漏", _inject_styleguide_leak, "styleguide"),
    ]
    for label, inject, keyword in cases:
        with tempfile.TemporaryDirectory(prefix="bundle-budget-selftest-") as tmp:
            dist = _make_baseline(Path(tmp))
            inject(dist)
            r = evaluate(read_bundle(dist))
            matched = [v for v in r.violations if keyword in v]
            if matched:
                print(f"[SELFTEST] 通过：注入「{label}」后按预期报 FAIL —— {matched[0]}")
            else:
                print(
                    f"[FAIL] 自检：「{label}」未被拦截"
                    f"（scan_error={r.scan_error}, violations={r.violations}）",
                    file=sys.stderr,
                )
                failures += 1

    if failures:
        print(f"[FAIL] 自检失败：{failures} 个用例未按预期工作", file=sys.stderr)
        return 1
    print(
        "[SELFTEST] 通过：图标 / 字体 / styleguide 三类违规均可被拦截；"
        "临时目录已随上下文销毁，frontend/dist 未被触碰"
    )
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="UX-04 S1 构建产物体积 / styleguide 泄漏守门")
    parser.add_argument(
        "--dist", type=Path, default=DEFAULT_DIST,
        help="Vite 产物目录（默认 frontend/dist）",
    )
    parser.add_argument(
        "--self-test", action="store_true",
        help="破坏性自检：注入三类违规验证拦截，不碰 frontend/dist",
    )
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    dist: Path = args.dist
    if not dist.exists():
        print("[SKIP] 未找到构建产物，请先 npm run build")
        return 0
    if not dist.is_dir():
        print(f"[FAIL] 扫描失败：{dist} 存在但不是目录", file=sys.stderr)
        return 2

    try:
        bundle = read_bundle(dist)
    except OSError as exc:
        print(f"[FAIL] 扫描失败：读取产物出错：{exc}", file=sys.stderr)
        return 2

    r = evaluate(bundle)
    if r.scan_error:
        print(f"[FAIL] 扫描失败：{r.scan_error}", file=sys.stderr)
        return 2

    report(bundle, r)
    if r.violations:
        print(f"\n[FAIL] 共 {len(r.violations)} 项违规（预算超标 / 标记泄漏）", file=sys.stderr)
        return 1

    extra = (
        f"；4 项守门中 {len(r.untriggered)} 项因产物未检出目标资产/标记而未被实际触发验证"
        "（详见上方 [INFO]）"
        if r.untriggered else ""
    )
    print(
        "\n[PASS] 构建产物体积守门通过：图标 ≤15 KiB / 组件库 ≤200 KiB / "
        f"字体 ≤800 KiB / 无 styleguide 标记泄漏{extra}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
