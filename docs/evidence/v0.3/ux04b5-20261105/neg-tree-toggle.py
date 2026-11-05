"""负向自检：剥掉 `.tree-toggle` 的 hover 后，`check_states.py` 必须报 FAIL。

为什么单独留一份：本批把守门的扫描范围从 `styles/*.css` 扩到 `.vue` 的 `<style>`，
「扩了范围」这件事本身没有任何可观测产出 —— 如果新扫描没真正生效，
结果依然是 PASS，和扩之前长得一模一样。所以必须有一条**能被证伪**的证据：
把缺陷重新注入，看它是否红。

用法（在项目根）：`python3 docs/evidence/v0.3/ux04b5-20261105/neg-tree-toggle.py`
退出码：0 = 守门确实报出了缺项；1 = 没报出来（扩范围无效）。
"""

from __future__ import annotations

import sys

sys.path.insert(0, "tools")

import check_states as cs  # noqa: E402


def main() -> int:
    base = cs.compile_css()
    # 把真实样式里的 `.tree-toggle:hover` 改成一个没人引用的选择器，等价于「没写 hover」
    stripped = base.replace(".tree-toggle:hover", ".tree-toggle--stripped")
    if stripped == base:
        print("[FAIL] 自检失效：样式里找不到 .tree-toggle:hover，替换没生效")
        return 1

    problems = cs.audit(stripped)
    hit = [p for p in problems if ".tree-toggle" in p]
    for p in hit:
        print(f"  命中: {p}")

    if not hit:
        print("[FAIL] 剥掉 hover 后守门没报 —— 扫描范围扩大没生效，属于假绿")
        return 1

    print("[OK] 负向自检通过：剥掉 .tree-toggle 的 hover 即报 FAIL")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
