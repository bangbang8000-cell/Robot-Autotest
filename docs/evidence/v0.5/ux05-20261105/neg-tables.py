#!/usr/bin/env python3
"""负向证据：往真视图里塞一处裸 `NDataTable`，`check_tables.py` 必须 FAIL。

守门「扩了范围」本身没有可观测产出 —— 不注入缺陷就无法证明它真的在看。
本次注入的是最容易复发的那种写法：直接 `import { NDataTable }` 并用起来，
页面照样能跑（naive-ui 类名不变、单测仍然绿），只是绕过了整套表格规范。

脚本自己备份与还原，跑完把文件恢复到字节一致；还原失败会以 rc=2 结束，
避免把注入物留在工作区。
"""
from __future__ import annotations

import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
VIEW = ROOT / "frontend" / "src" / "views" / "RunsView.vue"
CHECK = ROOT / "tools" / "check_tables.py"

INJECT_IMPORT = "import { NDataTable } from 'naive-ui'\n"
INJECT_TAG = '<NDataTable :columns="columns" :data="runs" />\n'


def main() -> int:
    original = VIEW.read_text(encoding="utf-8")
    if INJECT_TAG.strip() in original:
        print("[NEG] 注入点已被占用，先清掉再跑", file=sys.stderr)
        return 2

    # 插在 <template> 之后，保证落在视图模板里
    pos = original.find("<template>")
    if pos < 0:
        print("[NEG] 找不到 <template>，注入失败", file=sys.stderr)
        return 2
    injected = (
        original[: pos + len("<template>")]
        + "\n"
        + INJECT_TAG
        + original[pos + len("<template>") :]
    )
    # import 放在 <script setup> 第一行之后
    injected = injected.replace("<script setup lang=\"ts\">\n", "<script setup lang=\"ts\">\n" + INJECT_IMPORT, 1)

    backup = Path(tempfile.mkstemp(suffix=".vue")[1])
    try:
        shutil.copy2(VIEW, backup)
        VIEW.write_text(injected, encoding="utf-8")

        proc = subprocess.run(
            [sys.executable, str(CHECK)], capture_output=True, text=True, check=False
        )
        out = (proc.stdout or "") + (proc.stderr or "")
        print("[NEG] 注入后 check_tables rc =", proc.returncode)
        print(out.strip()[-800:])

        if proc.returncode != 1:
            print(
                f"[NEG] FAIL：注入裸 NDataTable 后守门仍 rc={proc.returncode}，"
                "说明它没有真的扫到视图",
                file=sys.stderr,
            )
            return 1
        if "RunsView.vue" not in out:
            print("[NEG] FAIL：报错没指向被注入的文件，判定力可疑", file=sys.stderr)
            return 1
        print("[NEG] 通过：注入后被拦，且报错指向 RunsView.vue")
        return 0
    finally:
        VIEW.write_text(original, encoding="utf-8")
        restored = VIEW.read_text(encoding="utf-8")
        backup.unlink(missing_ok=True)
        if restored != original:
            print("[NEG] 严重：文件未恢复到注入前状态", file=sys.stderr)
            raise SystemExit(2)
        print("[NEG] 已还原 RunsView.vue")


if __name__ == "__main__":
    raise SystemExit(main())
