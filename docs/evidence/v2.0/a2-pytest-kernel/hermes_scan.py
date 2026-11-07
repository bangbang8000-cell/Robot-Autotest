#!/usr/bin/env python3
"""hermes-agent 规模样本的依赖缺口扫描（A2 佐证，非替代 collect）。

做两件事：
1. 用 AST 抽取 tests/ 下全部 test_*.py 的顶层 import，去重；
2. 在 A2 的专用 venv 里用 importlib.util.find_spec 判定每个顶层模块是否可导入，
   输出「可导入 / 缺失」两份清单与统计行。

不做的是：不推测 collect 条数、不伪造执行结果。缺依赖导致 collect 失败的规模
样本只能证明「规模存在」，不能证明「内核在真实项目上能跑通」——后者由
deepseek-harness 的 254 条真实 collect + 245 passed 证明。
"""
import ast
import importlib.util
import os
import sys
from collections import Counter

TESTS = os.path.expanduser("~/.hermes/hermes-agent")
STDLIB = set(sys.stdlib_module_names)
LOCAL_TOP = set()

imports = Counter()
files = 0

for root, _dirs, names in os.walk(TESTS):
    # 只在 tests/ 子树里扫；仓库根目录的 agent/、apps/ 等是被测对象，不是测试
    if "tests" not in root.split(os.sep):
        continue
    for name in names:
        if not (name.startswith("test_") and name.endswith(".py")):
            continue
        path = os.path.join(root, name)
        files += 1
        try:
            tree = ast.parse(open(path, encoding="utf-8", errors="replace").read())
        except SyntaxError:
            continue
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for a in node.names:
                    imports[a.name.split(".")[0]] += 1
            elif isinstance(node, ast.ImportFrom):
                if node.level == 0 and node.module:
                    imports[node.module.split(".")[0]] += 1

# 仓库自身的顶层包名，不算缺失
for entry in os.listdir(TESTS):
    if entry.endswith(".py"):
        LOCAL_TOP.add(entry[:-3])
    elif os.path.isdir(os.path.join(TESTS, entry)):
        LOCAL_TOP.add(entry)

missing = []
present = []
for mod, cnt in sorted(imports.items(), key=lambda kv: (-kv[1], kv[0])):
    if mod in STDLIB or mod in LOCAL_TOP or mod == "tests":
        present.append((mod, cnt, "stdlib/local"))
        continue
    try:
        found = importlib.util.find_spec(mod) is not None
    except (ImportError, ValueError, ModuleNotFoundError):
        found = False
    (present if found else missing).append((mod, cnt, "installed" if found else "MISSING"))

print(f"# hermes-agent 依赖缺口扫描")
print(f"# 扫描目录: {TESTS}")
print(f"# 测试文件: {files} 个 test_*.py")
print(f"# 顶层 import 去重后: {len(imports)} 个模块")
print(f"# 缺失: {len(missing)} 个 / 可导入(含 stdlib+本地): {len(present)} 个")
print()
print("## 缺失清单（模块, 引用次数, 状态）")
for mod, cnt, st in missing:
    print(f"{mod}\t{cnt}\t{st}")
print()
print("## 缺失按引用次数排序（前 20）")
for mod, cnt, _st in missing[:20]:
    print(f"{cnt:5d}  {mod}")
print()
print("## 结论")
print(f"缺失 {len(missing)} 个第三方/内部包 → 全仓 pytest collect 必然大面积报错，")
print("故规模样本只做『规模存在性』佐证；collect 计数一致性证据取自 deepseek-harness。")
sys.exit(0)
