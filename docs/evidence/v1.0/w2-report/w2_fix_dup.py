# -*- coding: utf-8 -*-
"""修 w2_frontend.py 的追加型替换幂等 bug，并清掉已产生的重复插入。"""
import pathlib

# ---- 1. 生成器改为 delta 判重 ----
gen = pathlib.Path("docs/evidence/v1.0/w2-w7-preconditions/w2_frontend.py")
g = gen.read_text(encoding="utf-8")
old = '''    mk = marker or new.strip().splitlines()[0]
    if mk in t and old not in t:
        SKIP += 1
        print("[SKIP] 已回写", rel)
        return'''
new = '''    # 追加型替换（new = delta + old）时，old 依然在文件里，不能用「old 不在 t」判重。
    # 改为检查「新增部分 delta 是否已在文件中」——这是项目里踩过两次的坑。
    delta = new.replace(old, "", 1).strip()
    if delta and delta in t:
        SKIP += 1
        print("[SKIP] 已回写", rel)
        return'''
if new in g:
    print("[SKIP] 生成器已是 delta 判重版")
else:
    if g.count(old) != 1:
        raise SystemExit(f"FAIL 生成器锚点命中 {g.count(old)} 次")
    gen.write_text(g.replace(old, new, 1), encoding="utf-8")
    print("[PASS] 生成器改为 delta 判重")

# ---- 2. 清理重复插入（只保留第一份） ----
BLOCKS = {
    "frontend/src/router/index.ts": (
        "    {\n"
        "      path: '/test-reports',\n"
        "      name: 'test-reports',\n"
        "      component: () => import('@/views/TestReportsView.vue'),\n"
        "    },\n"),
    "scripts/lint.sh": (
        'echo "[lint]   - tools/check_report_sensitive.py（C5 EXTERNAL 字段判据自校验）"\n'
        'python3 tools/check_report_sensitive.py --self-check\n'),
    "scripts/rollback.sh": (
        "# C9（2026-11-06 实测订正）：备份走 mysqldump 全库，新增表自动纳入，\n"
        "# 不存在「逐表登记清单」；W2 起每波在证据 README 登记新表供人工核对。\n"),
}
for rel, block in BLOCKS.items():
    p = pathlib.Path(rel)
    t = p.read_text(encoding="utf-8")
    n = t.count(block)
    if n == 0:
        raise SystemExit(f"FAIL {rel}: 找不到目标块")
    if n == 1:
        print("[SKIP]", rel, "块唯一")
        continue
    parts = t.split(block)
    p.write_text(parts[0] + block + "".join(parts[2:]), encoding="utf-8")
    print(f"[PASS] {rel}: 重复块 {n} -> 1")
