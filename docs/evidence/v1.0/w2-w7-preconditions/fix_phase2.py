# -*- coding: utf-8 -*-
"""把 C5 扫描器的 RULES 块规范化为「不含裸引号 / 不含歧义反斜杠」版本；幂等。

坑：raw string 用 r"..." 界定时，字符类里出现未转义的 " 会提前终止字符串；
    Windows 路径分支写成 [A-Za-z]:\\) 又会让 ) 被当成转义字面量而分组不闭合。
改法：边界用 [^\\w]，Windows 分支直接匹配盘符冒号后跟非空白字符。
"""
import pathlib
import py_compile
import subprocess
import sys

RULES_BLOCK = '''    ("IP", re.compile(r"\\b(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\b")),
    ("COMMAND", re.compile(r"(?m)(?:^|[^\\w])(?:robot|pabot|pytest|iperf3|ssh|mysql|curl|bash|sh)\\s+-{1,2}[A-Za-z]")),
    ("CREDENTIAL", re.compile(r"(?i)\\b(?:password|passwd|pwd|api[_-]?key|secret|token|authorization)\\b\\s*[:=]|Bearer\\s+[A-Za-z0-9._-]{8,}")),
    ("ABS_PATH", re.compile(r"(?:^|[^\\w])(?:/home/|/root/|/mnt/|/var/|[A-Za-z]:)[^\\s<]{2,}")),
    ("USER", re.compile(r"(?i)\\b(?:user|username|login|account)\\s*[:=]\\s*[A-Za-z0-9._@-]{2,}")),
    ("RAW_OUTPUT", re.compile(r"Traceback \\(most recent call last\\)|^\\[ OUT \\]|^\\[ ERR \\]")),'''

p = pathlib.Path("tools/check_report_sensitive.py")
lines = p.read_text(encoding="utf-8").splitlines()
start = next(i for i, l in enumerate(lines) if l.startswith("RULES:"))
end = next(i for i in range(start + 1, len(lines)) if lines[i].strip() == "]")
if "\n".join(lines[start + 1:end]) == RULES_BLOCK:
    print("[SKIP] RULES 已是规范版")
else:
    lines[start + 1:end] = RULES_BLOCK.splitlines()
    p.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("[PASS] RULES 规范化：6 条规则去掉裸引号与歧义反斜杠")

py_compile.compile(str(p), doraise=True)
print("[PASS] 语法编译")
r = subprocess.run([sys.executable, str(p), "--self-check"], capture_output=True, text=True)
print((r.stdout + r.stderr).strip())
if r.returncode:
    raise SystemExit("FAIL self-check")
print("[PASS] 自校验")

# 生成器与修复器构成一组可复跑补丁序列，写进证据 README 说明执行顺序。
readme = pathlib.Path("docs/evidence/v1.0/w2-w7-preconditions/README.md")
t = readme.read_text(encoding="utf-8")
note = ("\n**可复跑顺序**：`python3 docs/evidence/v1.0/w2-w7-preconditions/phase2_kickoff.py`\n"
        "→ `python3 docs/evidence/v1.0/w2-w7-preconditions/fix_phase2.py`。\n"
        "前者负责回写与生成，后者把 C5 扫描器的正则块规范化（剥离裸引号）；两步都幂等。\n")
if "可复跑顺序" in t:
    print("[SKIP] README 已注明顺序")
else:
    readme.write_text(t.rstrip() + "\n" + note, encoding="utf-8")
    print("[PASS] README 补可复跑顺序")
