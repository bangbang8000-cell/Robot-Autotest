#!/usr/bin/env bash
# EXEC-12 残余核实（2026-11-06）：.resource 能否承载 Test Timeout、用例级 [Timeout] 优先级
# 用法：bash docs/evidence/v0.4/exec12/rf-timeout-probe-20261106.sh
set -u
PY=/home/evergreen/rf-stack/.venv/bin/python
RF=/home/evergreen/rf-stack/.venv/bin/robot
WORK=/tmp/exec12-probe
rm -rf "$WORK"; mkdir -p "$WORK"; cd "$WORK" || exit 1

echo "===== RF 版本 ====="
"$PY" -c "import robot; print('Robot Framework', robot.version.VERSION)"

echo
echo "===== 探针 1：.resource 的 *** Settings *** 里写 Test Timeout ====="
mkdir -p p1
cat > p1/lib.resource <<'EOF'
*** Settings ***
Documentation    资源文件探针
Test Timeout    5s

*** Keywords ***
长睡关键字
    Sleep    2s
EOF
cat > p1/suite.robot <<'EOF'
*** Settings ***
Resource    lib.resource

*** Test Cases ***
用例一
    长睡关键字
EOF
"$RF" --output NONE --log NONE --report NONE p1 > p1.log 2>&1
echo "robot exit=$?"
cat p1.log

echo
echo "===== 探针 2：套件级 Test Timeout 1s + 用例级 [Timeout] 10s ====="
mkdir -p p2
cat > p2/suite.robot <<'EOF'
*** Settings ***
Test Timeout    1s

*** Test Cases ***
无覆盖的用例
    Sleep    3s

有覆盖的用例
    [Timeout]    10s
    Sleep    3s
EOF
"$RF" --output p2/output.xml --log NONE --report NONE p2 > p2.log 2>&1
echo "robot exit=$?"
cat p2.log
"$PY" - <<'PY'
import xml.etree.ElementTree as ET
root = ET.parse('/tmp/exec12-probe/p2/output.xml').getroot()
for test in root.iter('test'):
    st = test.find('status')
    print('  用例:', test.get('name'), '->', st.get('status'), '|', (st.text or '').strip()[:100])
PY

echo
echo "===== 探针 3：只有用例级 [Timeout]，无套件级设置 ====="
mkdir -p p3
cat > p3/suite.robot <<'EOF'
*** Test Cases ***
仅用例级超时
    [Timeout]    1s
    Sleep    3s
EOF
"$RF" --output p3/output.xml --log NONE --report NONE p3 > p3.log 2>&1
echo "robot exit=$?"
cat p3.log
"$PY" - <<'PY'
import xml.etree.ElementTree as ET
root = ET.parse('/tmp/exec12-probe/p3/output.xml').getroot()
for test in root.iter('test'):
    st = test.find('status')
    print('  用例:', test.get('name'), '->', st.get('status'), '|', (st.text or '').strip()[:100])
PY

echo
echo "===== 结论速览 ====="
echo "1) .resource 写 Test Timeout -> RF 报错 Setting 'Test Timeout' is not allowed in resource file（不注入是约束）"
echo "2) 套件级 1s + 用例级 10s -> 无覆盖用例超时失败，有覆盖用例通过（用例级优先）"
echo "3) 仅用例级 [Timeout] -> 生效（逐用例配置可完全由脚本承担）"
