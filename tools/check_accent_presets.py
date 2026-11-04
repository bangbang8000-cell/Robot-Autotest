#!/usr/bin/env python3
"""六预设强调色清单一致性守门（UX-04 步骤 6 的配套检查）。

为什么需要它
------------
「六预设」不是一处定义，而是**七处**必须同时知道同一张清单：

    1. frontend/src/composables/useAccent.ts          —— 唯一真源（ACCENTS + LEGACY_ALIAS）
    2. frontend/index.html                            —— 首帧内联脚本（不能 import，只能抄）
    3. backend/.../UserPreferenceService.java         —— 服务端偏好白名单
    4. frontend/src/styles/theme.css                  —— 12 个变体块 + 6 个 --preset-*
    5. frontend/src/components/AppearanceDialog.vue    —— 色板 SWATCH_VARS
    6. frontend/src/locales/zh-CN.ts                  —— 预设名
    7. frontend/src/locales/en-US.ts                  —— 预设名

2026-11-05 换色板（blue/cyan/indigo/violet/teal/fuchsia →
新年红 crimson / 科技蓝 blue / 土豪金 gold / 活力橙 orange / 魅力紫 violet / 翡翠绿 jade）
时，这七处靠人脑同步：漏一处的结果**不是报错**，而是
 - 漏 Java 白名单 → 前端 PUT 400、静默不同步（本项目已经踩过一次）；
 - 漏 index.html → 首帧闪一帧蓝；
 - 漏 one locale → 面板上出现键名原文 `appearance.presets.crimson`；
 - 漏 theme.css 变体块 → 该预设选了没反应，页面还是上一个色。

所以这里做机械比对：**以 useAccent.ts 的 ACCENTS 为准**，其余六处必须与之一致
（列表类要求顺序一致，因为面板按顺序渲染；集合类只比集合）。

用法
----
    python3 tools/check_accent_presets.py
    python3 tools/check_accent_presets.py --root /tmp/fake   # 自检用
    python3 tools/check_accent_presets.py --self-test        # 破坏性自检（在临时目录注入违规）

退出码：0 = 七处一致；1 = 有漂移（逐条列出）；2 = 文件缺失 / 解析失败（不是"没问题"）。
"""

from __future__ import annotations

import argparse
import re
import shutil
import sys
import tempfile
from pathlib import Path

PATHS = {
    "ts": "frontend/src/composables/useAccent.ts",
    "html": "frontend/index.html",
    "java": "backend/src/main/java/com/qa/autotest/application/iam/UserPreferenceService.java",
    "css": "frontend/src/styles/theme.css",
    "dialog": "frontend/src/components/AppearanceDialog.vue",
    "zh": "frontend/src/locales/zh-CN.ts",
    "en": "frontend/src/locales/en-US.ts",
}

ACCENT_LIST_RE = re.compile(r"export const ACCENTS\s*=\s*\[([^\]]*)\]")
LEGACY_TS_RE = re.compile(r"export const LEGACY_ALIAS\s*=\s*\{([^}]*)\}")
HTML_LIST_RE = re.compile(r"var ACCENTS\s*=\s*\[([^\]]*)\]")
HTML_LEGACY_RE = re.compile(r"var LEGACY\s*=\s*\{([^}]*)\}")
JAVA_SET_RE = re.compile(r'"accent"\s*,\s*Set\.of\(([^)]*)\)')
SWATCH_RE = re.compile(r"SWATCH_VARS[^=]*=\s*\{([^}]*)\}")
LOCALE_PRESETS_RE = re.compile(r"appearance:\s*\{.*?presets:\s*\{([^}]*)\}", re.S)


def _names(raw: str) -> list[str]:
    return re.findall(r"['\"]([\w-]+)['\"]", raw)


def _pairs(raw: str) -> dict[str, str]:
    return dict(re.findall(r"([\w-]+)\s*:\s*['\"]([\w-]+)['\"]", raw))


def read_text(root: Path, key: str) -> str | None:
    path = root / PATHS[key]
    if not path.is_file():
        return None
    return path.read_text(encoding="utf-8")


def collect(root: Path) -> tuple[dict[str, object], list[str]]:
    """返回 (解析结果, 硬错误)。硬错误 = 文件缺失或某处解析不出清单。"""
    errors: list[str] = []
    data: dict[str, object] = {}
    texts: dict[str, str] = {}
    for key, rel in PATHS.items():
        text = read_text(root, key)
        if text is None:
            errors.append(f"文件缺失：{rel}")
        else:
            texts[key] = text

    if "ts" in texts:
        match = ACCENT_LIST_RE.search(texts["ts"])
        if match:
            data["ts"] = _names(match.group(1))
        else:
            errors.append(f"{PATHS['ts']}：找不到 export const ACCENTS = [...]")
        legacy = LEGACY_TS_RE.search(texts["ts"])
        if legacy:
            data["ts_legacy"] = _pairs(legacy.group(1))
        else:
            errors.append(f"{PATHS['ts']}：找不到 export const LEGACY_ALIAS = {{...}}")

    if "html" in texts:
        match = HTML_LIST_RE.search(texts["html"])
        if match:
            data["html"] = _names(match.group(1))
        else:
            errors.append(f"{PATHS['html']}：找不到 var ACCENTS = [...]")
        legacy = HTML_LEGACY_RE.search(texts["html"])
        if legacy:
            data["html_legacy"] = _pairs(legacy.group(1))
        else:
            errors.append(f"{PATHS['html']}：找不到 var LEGACY = {{...}}")

    if "java" in texts:
        match = JAVA_SET_RE.search(texts["java"])
        if match:
            # Set.of(...) 无序，用集合比较（顺序由前端决定）
            data["java"] = sorted(set(_names(match.group(1))))
        else:
            errors.append(f"{PATHS['java']}：找不到 \"accent\", Set.of(...)")

    if "css" in texts:
        css = texts["css"]
        data["css_blocks"] = {}
        for name in re.findall(r"data-accent\s*=\s*['\"]([\w-]+)['\"]", css):
            data["css_blocks"][name] = data["css_blocks"].get(name, 0) + 1  # type: ignore[index]
        data["css_presets"] = sorted(set(re.findall(r"--preset-([\w-]+)\s*:", css)))

    if "dialog" in texts:
        match = SWATCH_RE.search(texts["dialog"])
        if match:
            data["dialog"] = sorted(set(re.findall(r"^\s*([\w-]+)\s*:", match.group(1), re.M)))
        else:
            errors.append(f"{PATHS['dialog']}：找不到 SWATCH_VARS = {{...}}")

    for key in ("zh", "en"):
        if key not in texts:
            continue
        match = LOCALE_PRESETS_RE.search(texts[key])
        if match:
            data[key] = sorted(set(re.findall(r"^\s*([\w-]+)\s*:", match.group(1), re.M)))
        else:
            errors.append(f"{PATHS[key]}：找不到 appearance.presets = {{...}}")

    return data, errors


def validate(data: dict[str, object], errors: list[str]) -> tuple[list[str], list[str]]:
    """返回 (问题清单, 说明清单)。有硬错误时问题清单里也带上，一并判红。"""
    issues: list[str] = list(errors)
    notes: list[str] = []
    if errors:
        return issues, notes

    base = data.get("ts") or []
    if not base:
        issues.append("useAccent.ts 的 ACCENTS 解析为空（规则失效，不是没有预设）")
        return issues, notes

    data["accent_names"] = base
    notes.append(f"真源 useAccent.ts ACCENTS = {base}（{len(base)} 个）")

    if list(data.get("html") or []) != base:
        issues.append(
            f"index.html 的 var ACCENTS 与真源不一致：{data.get('html')} ≠ {base}"
            "（顺序也要一致，面板按顺序渲染；不一致会让首帧闪一下默认色）"
        )

    legacy_ts = dict(data.get("ts_legacy") or {})
    legacy_html = dict(data.get("html_legacy") or {})
    if legacy_ts != legacy_html:
        issues.append(
            f"LEGACY 映射两侧不一致：useAccent.ts={legacy_ts} index.html={legacy_html}"
            "（旧键名归一漏一处，升级用户就会被打回默认蓝）"
        )
    for old, new in legacy_ts.items():
        if new not in base:
            issues.append(f"LEGACY_ALIAS 把 {old} 映射到了不存在的预设 {new}")
        if old in base:
            issues.append(f"LEGACY_ALIAS 的旧键 {old} 同时又是新预设名，归一语义会自相矛盾")

    java = list(data.get("java") or [])
    if java != sorted(base):
        issues.append(f"后端白名单与真源不一致：UserPreferenceService={java} ≠ {sorted(base)}")

    blocks = dict(data.get("css_blocks") or {})
    for name in base:
        count = blocks.get(name, 0)
        if count != 2:
            issues.append(
                f"theme.css 里 data-accent='{name}' 出现 {count} 次，应为 2（light + dark）"
            )
    extra_blocks = sorted(set(blocks) - set(base))
    if extra_blocks:
        issues.append(f"theme.css 里有真源没有的变体块：{extra_blocks}")

    presets = list(data.get("css_presets") or [])
    if presets != sorted(base):
        issues.append(f"theme.css 的 --preset-* 与真源不一致：{presets} ≠ {sorted(base)}")

    dialog = list(data.get("dialog") or [])
    if dialog != sorted(base):
        issues.append(f"AppearanceDialog 的 SWATCH_VARS 与真源不一致：{dialog} ≠ {sorted(base)}")

    for key, label in (("zh", "zh-CN"), ("en", "en-US")):
        keys = list(data.get(key) or [])
        if keys != sorted(base):
            issues.append(f"{label} 的 appearance.presets 键与真源不一致：{keys} ≠ {sorted(base)}")

    notes.append(
        f"theme.css 变体块 {sum(blocks.values())} 个 / --preset-* {len(presets)} 个；"
        f"dialog {len(dialog)} 键；zh/en presets {len(data.get('zh') or [])}/"
        f"{len(data.get('en') or [])} 键"
    )
    return issues, notes


def run(root: Path) -> int:
    data, errors = collect(root)
    issues, notes = validate(data, errors)
    for note in notes:
        print(f"  [INFO] {note}")
    if issues:
        for issue in issues:
            print(f"  [FAIL] {issue}", file=sys.stderr)
        print(f"[FAIL] 六预设清单有 {len(issues)} 处不一致", file=sys.stderr)
        return 1
    print(f"[PASS] 六预设清单七处一致（{len(data.get('accent_names') or [])} 个预设）")
    return 0


def self_test() -> int:
    """在临时目录里改一处制造违规，确认每种违规都能被拦。不改仓库文件。"""
    real_root = Path(__file__).resolve().parents[1]
    ok = True

    def case(label: str, mutate) -> None:
        nonlocal ok
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for rel in PATHS.values():
                src = real_root / rel
                if not src.is_file():
                    print(f"[SELFTEST][FAIL] 真实文件缺失，无法自检：{rel}", file=sys.stderr)
                    ok = False
                    return
                dst = root / rel
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)
            baseline = run(root)
            if baseline != 0:
                print(f"[SELFTEST][FAIL] {label}：基线本该 PASS，实际 rc={baseline}", file=sys.stderr)
                ok = False
                return
            mutate(root)
            changed = run(root)
            if changed != 1:
                print(f"[SELFTEST][FAIL] {label}：注入违规后 rc={changed}，应被拦为 1", file=sys.stderr)
                ok = False
            else:
                print(f"[SELFTEST][OK] {label}：已拦")

    def sub(root: Path, key: str, old: str, new: str) -> None:
        path = root / PATHS[key]
        text = path.read_text(encoding="utf-8")
        assert old in text, f"自检目标文本不存在：{key} {old!r}"
        path.write_text(text.replace(old, new, 1), encoding="utf-8")

    # 1) index.html 首帧脚本漏改一个预设（最典型的"改了一半"）
    case("首帧脚本清单漂移", lambda root: sub(root, "html", "'jade']", "'jade', 'teal']"))
    # 2) 后端白名单漏改
    case("后端白名单漂移", lambda root: sub(root, "java", '"jade"', '"teal"'))
    # 3) theme.css 缺一个暗色块
    case(
        "变体块缺失",
        lambda root: sub(root, "css", "[data-accent='jade'][data-theme='dark']", "[data-accent='jade-x'][data-theme='dark']"),
    )
    # 4) 语言包漏一个键
    case("中文预设名缺失", lambda root: sub(root, "zh", "      jade: '翡翠绿',\n", ""))
    # 5) LEGACY 映射两侧不一致
    case("LEGACY 映射漂移", lambda root: sub(root, "html", "teal: 'jade'", "teal: 'blue'"))

    print("[SELFTEST] " + ("通过：五类漂移均可被拦截" if ok else "存在未拦截场景"))
    return 0 if ok else 1


def main() -> int:
    parser = argparse.ArgumentParser(description="校验六预设强调色清单七处一致")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    return run(args.root)


if __name__ == "__main__":
    raise SystemExit(main())
