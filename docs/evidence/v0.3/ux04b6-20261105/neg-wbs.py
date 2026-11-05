"""负向自检：往 WBS 汇总行注入一处不一致，`check_wbs_consistency.py` 必须报 FAIL。

为什么需要：这条守门是为了拦「汇总数字与逐行状态不符」，而它的正常输出就是一行 PASS ——
如果检查逻辑写歪了（比如把单元格索引取错），依然会 PASS。只有**注入一个已知错误、
看它是否变红**，才能证明它有判定力。

做法：把真文档复制到临时文件，改掉汇总行的两个数字，把守门的 `DOC` 指向副本再跑；
真文档全程不动。

用法（项目根）：`python3 docs/evidence/v0.3/ux04b6-20261105/neg-wbs.py`
退出码：0 = 守门确实报出了不一致；1 = 没报出来（守门无效）。
"""

from __future__ import annotations

import importlib.util
import pathlib
import sys

TOOL = pathlib.Path("tools/check_wbs_consistency.py")
OLD = "| **3** | **0** | **66** | **0** | **13** |"
NEW = "| **3** | **1** | **65** | **0** | **13** |"


def load_tool():
    spec = importlib.util.spec_from_file_location("check_wbs", TOOL)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def main() -> int:
    if not TOOL.exists():
        print(f"[FAIL] 找不到 {TOOL}")
        return 1

    tool = load_tool()
    original = tool.DOC.read_text(encoding="utf-8")
    if OLD not in original:
        print("[FAIL] 汇总行锚点没找到，负向自检无法进行（先看文档是否改版）")
        return 1

    tmp = pathlib.Path("/tmp/wbs-status-negative.md")
    tmp.write_text(original.replace(OLD, NEW, 1), encoding="utf-8")
    try:
        tool.DOC = tmp
        # 守门自己的 argparse 会读 sys.argv；这里已无额外参数，行为等同直接执行
        rc = tool.main()
    finally:
        tool.DOC = pathlib.Path("docs/plan/WBS-STATUS.md")
        tmp.unlink(missing_ok=True)

    if rc == 1:
        print("[OK] 负向自检通过：注入不一致后守门报 FAIL")
        return 0

    print(f"[FAIL] 守门没报出来（rc={rc}），汇总与逐行不一致将无人拦截")
    return 1


if __name__ == "__main__":
    sys.exit(main())
