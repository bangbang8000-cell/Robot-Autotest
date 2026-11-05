#!/usr/bin/env python3
"""UX-05 品牌守门：favicon 可辨性 / 品牌区挂载 / 版本号只在「关于」。

为什么需要这条守门
------------------
三件事都属「不生成长什么样」的类别，人眼一次验收之后就没人再看了：

1. **favicon 存在** —— 缺了不会报错，浏览器只是退回默认地球图标，而 99% 的时间
   没人看标签页；等到对外交付截图才发现没有品牌。
2. **favicon 在 16px 下可辨** —— SVG favicon 由 UA 缩放到 16×16，`<text>`、
   细于 2px 的描边、十几个图元在 16px 下全部糊成一团。这条只能近似机检，
   但「有没有文字 / 最细描边多少 / 图元几个」能拦住绝大多数退化。
3. **版本号只在「关于」里** —— 版本号原先写在顶部副标题（`app.subtitle`）里，
   每发一版要改 i18n 文案、忘了就对外报旧版本；现在副标题只放定位语，
   版本从后端 `/_ping` 读。

守什么
------
A. `frontend/public/favicon.svg` 存在，且 `index.html` 里有 `rel="icon"` 指向它；
B. favicon：viewBox 为 `0 0 32 32`、无 `<text>` / `font-`、`stroke-width` 全 ≥ 2、图元数 ≤ 8；
C. `components/AppLogo.vue` 存在，且 `App.vue`（侧栏）与 `LoginView.vue`（登录页）都挂了；
D. 语言包里 `app.subtitle` 不含版本号（`v数字`），且 `AboutDialog.vue` 从 `healthPing` 读版本。

用法
----
    python3 tools/check_brand.py               # 常规检查
    python3 tools/check_brand.py --self-test   # 破坏性自检：注入四类退化，必须被拦

退出码：0 = 通过；1 = 有违规；2 = 扫描本身失败。
"""

from __future__ import annotations

import argparse
import re
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "frontend"
PUBLIC_FAVICON = FRONTEND / "public" / "favicon.svg"
INDEX_HTML = FRONTEND / "index.html"
APP_LOGO = FRONTEND / "src" / "components" / "AppLogo.vue"
APP_VUE = FRONTEND / "src" / "App.vue"
LOGIN = FRONTEND / "src" / "views" / "LoginView.vue"
ABOUT = FRONTEND / "src" / "components" / "AboutDialog.vue"
LOCALES = [
    FRONTEND / "src" / "locales" / "zh-CN.ts",
    FRONTEND / "src" / "locales" / "en-US.ts",
]

MAX_SHAPES = 8
MIN_STROKE = 2.0
VERSION_IN_TEXT_RE = re.compile(r"\bv\d+(?:\.\d+)+", re.I)


@dataclass
class Result:
    ok: list[str] = field(default_factory=list)
    violations: list[str] = field(default_factory=list)


def check_favicon(path: Path, index: Path) -> Result:
    r = Result()
    if not path.is_file():
        r.violations.append(f"找不到 favicon：{path}（期望 frontend/public/favicon.svg）")
        return r

    svg = path.read_text(encoding="utf-8", errors="replace")
    if "<text" in svg or "font-" in svg:
        r.violations.append(
            "favicon.svg 里有 <text> 或 font-*：16px 下文字会糊成一团，标记必须是几何形"
        )

    box = re.search(r'viewBox="([^"]+)"', svg)
    if not box:
        r.violations.append("favicon.svg 缺 viewBox：没有它 UA 无法等比缩放")
    elif box.group(1).strip() != "0 0 32 32":
        r.violations.append(
            f"favicon.svg 的 viewBox 是 `{box.group(1)}`，期望 `0 0 32 32`"
            "（与 AppLogo 同坐标系，便于肉眼对照与后续改形）"
        )

    strokes = [float(value) for value in re.findall(r'stroke-width="([\d.]+)"', svg)]
    thin = [value for value in strokes if value < MIN_STROKE]
    if thin:
        r.violations.append(
            f"favicon.svg 有描边细于 {MIN_STROKE:g}px（{thin}）：16px 缩放下会消失或变灰"
        )
    if not strokes:
        r.violations.append(
            "favicon.svg 里没有任何 stroke-width：纯填充形在 16px 下容易变成一块实心色"
        )

    shapes = len(re.findall(r"<(?:rect|circle|line|path|polyline|polygon)\b", svg))
    if shapes > MAX_SHAPES:
        r.violations.append(
            f"favicon.svg 有 {shapes} 个图元（上限 {MAX_SHAPES}）：细节越多，16px 下越糊"
        )

    if index.is_file():
        html = index.read_text(encoding="utf-8", errors="replace")
        if not re.search(r'<link[^>]+rel="icon"[^>]+href="/favicon\.svg"', html):
            r.violations.append(
                "index.html 没有 <link rel=\"icon\" href=\"/favicon.svg\">：favicon 不会被加载"
            )
    else:
        r.violations.append(f"找不到 {index}")

    r.ok.append(f"favicon：{shapes} 个图元 / 最细描边 {min(strokes):g}px / viewBox 32×32")
    return r


def check_mounting(logo: Path, app: Path, login: Path) -> Result:
    r = Result()
    if not logo.is_file():
        r.violations.append(f"找不到品牌组件：{logo}")
        return r

    for path, label in ((app, "App.vue（侧栏品牌区）"), (login, "LoginView.vue（登录页品牌区）")):
        if not path.is_file():
            r.violations.append(f"找不到 {path}")
            continue
        if "<AppLogo" not in path.read_text(encoding="utf-8", errors="replace"):
            r.violations.append(f"{label} 没有挂 <AppLogo>：品牌标记只在设计稿里，界面看不到")

    r.ok.append("品牌区：App.vue 与 LoginView.vue 均挂载 AppLogo")
    return r


def check_version_placement(locales: list[Path], about: Path) -> Result:
    r = Result()
    for path in locales:
        if not path.is_file():
            r.violations.append(f"找不到语言包 {path}")
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        match = re.search(r"subtitle:\s*'([^']*)'", text)
        if not match:
            r.violations.append(f"{path.name} 里找不到 app.subtitle")
            continue
        if VERSION_IN_TEXT_RE.search(match.group(1)):
            r.violations.append(
                f"{path.name} 的 app.subtitle 含版本号（`{match.group(1)}`）："
                "版本号只在「关于」里，且从 /_ping 读，副标题只放定位语"
            )
        if not re.search(r"subtitle:\s*'\S", text):
            r.violations.append(f"{path.name} 的 app.subtitle 为空")

    if not about.is_file():
        r.violations.append(f"找不到 {about}")
    else:
        text = about.read_text(encoding="utf-8", errors="replace")
        if "healthPing" not in text:
            r.violations.append(
                "AboutDialog.vue 没有从 healthPing 读版本：手写版本号会与服务端实际版本脱节"
            )
        r.ok.append("版本号：副标题无版本，关于面板从 /_ping 取")

    return r


def evaluate() -> Result:
    merged = Result()
    for part in (
        check_favicon(PUBLIC_FAVICON, INDEX_HTML),
        check_mounting(APP_LOGO, APP_VUE, LOGIN),
        check_version_placement(LOCALES, ABOUT),
    ):
        merged.ok.extend(part.ok)
        merged.violations.extend(part.violations)
    return merged


def self_test() -> int:
    print("[SELFTEST] 开始：临时目录内构造最小树，不触碰真实 frontend/")
    ok = True

    def build(tmp: Path, favicon: str, index: str, subtitle: str, with_about: bool = True,
              with_mount: bool = True) -> dict[str, Path]:
        root = tmp / "frontend"
        (root / "public").mkdir(parents=True, exist_ok=True)
        (root / "src" / "components").mkdir(parents=True, exist_ok=True)
        (root / "src" / "views").mkdir(parents=True, exist_ok=True)
        (root / "src" / "locales").mkdir(parents=True, exist_ok=True)
        fav = root / "public" / "favicon.svg"
        fav.write_text(favicon, encoding="utf-8")
        idx = root / "index.html"
        idx.write_text(index, encoding="utf-8")
        logo = root / "src" / "components" / "AppLogo.vue"
        logo.write_text("<template><svg /></template>", encoding="utf-8")
        app = root / "src" / "App.vue"
        app.write_text("<template><AppLogo /></template>" if with_mount else "<template />", encoding="utf-8")
        login = root / "src" / "views" / "LoginView.vue"
        login.write_text("<template><AppLogo /></template>", encoding="utf-8")
        about = root / "src" / "components" / "AboutDialog.vue"
        about.write_text("import { healthPing } from '@/api/http'" if with_about else "// none", encoding="utf-8")
        paths = {}
        for name in ("zh-CN", "en-US"):
            loc = root / "src" / "locales" / f"{name}.ts"
            loc.write_text(f"app: {{\n  subtitle: '{subtitle}',\n}}", encoding="utf-8")
            paths[name] = loc
        return {"favicon": fav, "index": idx, "logo": logo, "app": app, "login": login,
                "about": about, "zh": paths["zh-CN"], "en": paths["en-US"]}

    good_svg = (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">'
        '<rect x="0.5" y="0.5" width="31" height="31" rx="9" fill="#1567c9"/>'
        '<g stroke="#ffffff" stroke-width="2.6"><line x1="9" y1="10" x2="23" y2="10"/></g>'
        "</svg>"
    )
    good_index = '<link rel="icon" type="image/svg+xml" href="/favicon.svg" />'

    def run(paths: dict[str, Path]) -> Result:
        merged = Result()
        for part in (
            check_favicon(paths["favicon"], paths["index"]),
            check_mounting(paths["logo"], paths["app"], paths["login"]),
            check_version_placement([paths["zh"], paths["en"]], paths["about"]),
        ):
            merged.ok.extend(part.ok)
            merged.violations.extend(part.violations)
        return merged

    # 1) 干净基线
    with tempfile.TemporaryDirectory() as d:
        r = run(build(Path(d), good_svg, good_index, "AIDC 自动化测试平台"))
        if r.violations:
            ok = False
            print(f"[SELFTEST] FAIL 干净基线被误判：{r.violations}")
        else:
            print(f"[SELFTEST] 通过：干净基线不假红（{len(r.ok)} 项判定）")

    # 2) favicon 带文字 → FAIL
    with tempfile.TemporaryDirectory() as d:
        bad = good_svg.replace("</svg>", '<text x="4" y="20">A</text></svg>')
        r = run(build(Path(d), bad, good_index, "定位语"))
        if any("<text>" in v for v in r.violations):
            print("[SELFTEST] 通过：favicon 含文字被拦截")
        else:
            ok = False
            print(f"[SELFTEST] FAIL favicon 文字没被拦：{r.violations}")

    # 3) 细描边 → FAIL
    with tempfile.TemporaryDirectory() as d:
        bad = good_svg.replace('stroke-width="2.6"', 'stroke-width="0.8"')
        r = run(build(Path(d), bad, good_index, "定位语"))
        if any("细于" in v for v in r.violations):
            print("[SELFTEST] 通过：细描边被拦截")
        else:
            ok = False
            print(f"[SELFTEST] FAIL 细描边没被拦：{r.violations}")

    # 4) index.html 缺 link → FAIL
    with tempfile.TemporaryDirectory() as d:
        r = run(build(Path(d), good_svg, "<html></html>", "定位语"))
        if any("rel=\"icon\"" in v for v in r.violations):
            print("[SELFTEST] 通过：index.html 缺 favicon 引用被拦截")
        else:
            ok = False
            print(f"[SELFTEST] FAIL 缺 link 没被拦：{r.violations}")

    # 5) 版本号写回副标题 → FAIL
    with tempfile.TemporaryDirectory() as d:
        r = run(build(Path(d), good_svg, good_index, "v0.2 台账版"))
        if any("含版本号" in v for v in r.violations):
            print("[SELFTEST] 通过：版本号写回副标题被拦截")
        else:
            ok = False
            print(f"[SELFTEST] FAIL 版本号回退没被拦：{r.violations}")

    # 6) 品牌区没挂 / 关于不读 /_ping → FAIL
    with tempfile.TemporaryDirectory() as d:
        r = run(build(Path(d), good_svg, good_index, "定位语", with_about=False, with_mount=False))
        if any("没有挂 <AppLogo>" in v for v in r.violations) and any(
            "healthPing" in v for v in r.violations
        ):
            print("[SELFTEST] 通过：品牌区未挂载 / 关于不读版本 都被拦截")
        else:
            ok = False
            print(f"[SELFTEST] FAIL 挂载问题没被拦：{r.violations}")

    print("[SELFTEST] " + ("通过：六类场景均符合预期" if ok else "存在未拦截场景"))
    return 0 if ok else 1


def main() -> int:
    parser = argparse.ArgumentParser(description="UX-05 品牌守门")
    parser.add_argument("--self-test", action="store_true", help="破坏性自检")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    try:
        r = evaluate()
    except OSError as exc:
        print(f"[FAIL] 扫描失败：{exc}", file=sys.stderr)
        return 2

    for line in r.ok:
        print(f"[INFO] {line}")
    if r.violations:
        for v in r.violations:
            print(f"[FAIL] {v}", file=sys.stderr)
        print(f"\n[FAIL] 共 {len(r.violations)} 项品牌问题", file=sys.stderr)
        return 1
    print(f"\n[PASS] 品牌：favicon 可辨 / 侧栏与登录页均有标记 / 版本号只在关于（取自 /_ping）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
