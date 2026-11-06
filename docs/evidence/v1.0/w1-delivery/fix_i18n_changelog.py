#!/usr/bin/env python3
"""06-lint 最后一处失败：i18n 守门拦下 Changelog 页 5 处硬编码中文。

不改守门规则，改设计
--------------------
`CHANGELOG.md` 是中文数据真源，四类小节标题本来就该是中文（给人读）。问题在于
`ChangelogView.vue` 把「中文标题 → i18n key」的映射写死在代码里，于是 check_i18n.py
正确地把它们判成 UI 硬编码文案。

正解是让数据格式自带**语言无关的锚点**：
    ### 新增 <!-- added -->
    ## v2.0（...）<!-- current -->
HTML 注释在 Markdown 渲染时不可见，人读到的仍是中文标题；解析器只读锚点，
代码里因此一条中文都不用写，界面文案全部由 t('changelog.*') 从语言包取。
解析器保留「无锚点回退到标题文字」的旧格式兼容。

幂等：每处替换带命中次数断言 + 完成标记，重复执行只打印 SKIP。
"""
from __future__ import annotations

import pathlib
import shutil
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[4]

ANCHORS = {
    "新增": "added",
    "变更": "changed",
    "修复": "fixed",
    "破坏性变更": "breaking",
}


def patch(rel: str, old: str, new: str, label: str, marker: str) -> None:
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    if marker in text:
        print(f"SKIP  {label}")
        return
    hits = text.count(old)
    if hits != 1:
        raise SystemExit(f"FAIL  {label}：期望命中 1 次，实际 {hits} 次")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"PASS  {label}")


def fix_parser() -> None:
    rel = "frontend/src/config/changelog.ts"
    patch(
        rel,
        "        title: text.slice(3).trim(),",
        "        title: text.slice(3).replace(/<!--.*?-->/g, '').trim(),",
        "解析器：标题剥掉机器锚点",
        "replace(/<!--.*?-->/g, '')",
    )
    patch(
        rel,
        "        current: text.includes('（当前）') || text.includes('(current)'),",
        "        current: /<!--\\s*current\\s*-->/.test(text) || text.includes('(current)'),",
        "解析器：当前版本改判锚点",
        "current\\s*-->",
    )
    patch(
        rel,
        "      section = { key: text.slice(4).trim(), items: [] }",
        "      const anchor = /<!--\\s*([a-z]+)\\s*-->/.exec(text)\n"
        "      section = {\n"
        "        key: anchor ? anchor[1] : text.slice(4).replace(/<!--.*?-->/g, '').trim(),\n"
        "        items: [],\n"
        "      }",
        "解析器：小节 key 改读锚点（无锚点回退旧格式）",
        "const anchor =",
    )


def fix_view() -> None:
    old = """const SECTION_LABELS: Record<string, string> = {
  '新增': 'changelog.added',
  '变更': 'changelog.changed',
  '修复': 'changelog.fixed',
  '破坏性变更': 'changelog.breaking',
  Added: 'changelog.added',
  Changed: 'changelog.changed',
  Fixed: 'changelog.fixed',
  'Breaking changes': 'changelog.breaking',
}"""
    new = """// CHANGELOG.md 的小节标题用 <!-- added --> 这类锚点标记，故此处只需要语言无关的
// key；中文/英文显示文案一律由 t() 从语言包取，代码里不写死任何自然语言。
const SECTION_LABELS: Record<string, string> = {
  added: 'changelog.added',
  changed: 'changelog.changed',
  fixed: 'changelog.fixed',
  breaking: 'changelog.breaking',
}"""
    patch(
        "frontend/src/views/ChangelogView.vue", old, new,
        "视图：小节映射改为语言无关 key", "代码里不写死任何自然语言",
    )


def fix_spec() -> None:
    rel = "frontend/src/config/changelog.spec.ts"
    patch(
        rel,
        """const MARKDOWN = `## v2.0（2026-11-06）（当前）
### 新增
- 统一入口
### 变更
- 菜单 IA
### 修复
- 顶栏选择器
### 破坏性变更
- 无

## v1.0（2026-11-05）
### 新增
- 执行域
### 变更
- 口径
### 修复
- 表数
### 破坏性变更
- 无
`""",
        """const MARKDOWN = `## v2.0（2026-11-06）<!-- current -->
### 新增 <!-- added -->
- 统一入口
### 变更 <!-- changed -->
- 菜单 IA
### 修复 <!-- fixed -->
- 顶栏选择器
### 破坏性变更 <!-- breaking -->
- 无

## v1.0（2026-11-05）
### 新增 <!-- added -->
- 执行域
### 变更 <!-- changed -->
- 口径
### 修复 <!-- fixed -->
- 表数
### 破坏性变更 <!-- breaking -->
- 无
`""",
        "单测：样例数据加锚点", "<!-- current -->",
    )
    patch(
        rel,
        "expect(sections.map((s) => s.key)).toEqual(['新增', '变更', '修复', '破坏性变更'])",
        "expect(sections.map((s) => s.key)).toEqual(['added', 'changed', 'fixed', 'breaking'])",
        "单测：断言改为锚点 key", "toEqual(['added', 'changed', 'fixed', 'breaking'])",
    )
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    if "无锚点的旧格式回退" in text:
        print("SKIP  单测：旧格式回退用例")
    else:
        anchor = "  it('空输入返回空数组', () => {"
        if text.count(anchor) != 1:
            raise SystemExit("FAIL  单测插入点命中次数不为 1")
        addition = """  it('无锚点的旧格式回退到标题文字', () => {
    const sections = parseChangelog('## v1.0（2026-11-05）(current)\\n### 新增\\n- 旧条目\\n')[0].sections
    expect(sections[0].key).toBe('新增')
  })

"""
        path.write_text(text.replace(anchor, addition + anchor, 1), encoding="utf-8")
        print("PASS  单测：旧格式回退用例")


def fix_changelog() -> None:
    path = ROOT / "CHANGELOG.md"
    text = path.read_text(encoding="utf-8")
    if "机器锚点" in text:
        print("SKIP  数据真源 CHANGELOG.md（已加锚点）")
    else:
        old_head = (
            "> `frontend/public/CHANGELOG.md`。四类小标题（新增 / 变更 / 修复 / 破坏性变更）必须齐全，\n"
            "> 版本按倒序排列；带 `（当前）` 的版本在页面高亮。"
        )
        new_head = (
            "> `frontend/public/CHANGELOG.md`。四类小标题（新增 / 变更 / 修复 / 破坏性变更）必须齐全，\n"
            "> 版本按倒序排列。\n"
            ">\n"
            "> 机器锚点：小节标题后的 `<!-- added|changed|fixed|breaking -->` 与当前版本标题后的\n"
            "> `<!-- current -->` 是给解析器用的语言无关标识（Markdown 渲染时不可见）。\n"
            "> 因此代码里不需要硬编码中文，界面显示文案一律走 i18n 语言包 `changelog.*`。"
        )
        if text.count(old_head) != 1:
            raise SystemExit("FAIL  CHANGELOG 头部说明命中次数不为 1")
        text = text.replace(old_head, new_head, 1)

        lines = text.split("\n")
        out: list[str] = []
        current_done = False
        anchored = 0
        for line in lines:
            if line.startswith("## ") and "<!--" not in line and not current_done:
                line = line.replace("（当前）", "") + " <!-- current -->"
                current_done = True
            elif line.startswith("### ") and "<!--" not in line:
                heading = line[4:].strip()
                anchor = ANCHORS.get(heading)
                if anchor:
                    line = f"### {heading} <!-- {anchor} -->"
                    anchored += 1
            out.append(line)
        if not current_done or anchored != 12:
            raise SystemExit(f"FAIL  锚点数量异常：current={current_done} sections={anchored}（期望 12）")
        path.write_text("\n".join(out), encoding="utf-8")
        print(f"PASS  数据真源：1 个 current + {anchored} 个小节锚点")

    shutil.copyfile(path, ROOT / "frontend/public/CHANGELOG.md")
    print("PASS  同步 frontend/public/CHANGELOG.md")


def main() -> int:
    fix_parser()
    fix_view()
    fix_spec()
    fix_changelog()
    check = subprocess.run([sys.executable, "tools/check_i18n.py"], cwd=ROOT,
                           capture_output=True, text=True)
    print(check.stdout.strip().splitlines()[-6:] if check.stdout else "")
    if check.returncode != 0:
        print(check.stdout, check.stderr, file=sys.stderr)
        raise SystemExit("FAIL  i18n 守门仍未通过")
    print("DONE  i18n 守门通过；请复跑 gates.sh")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
