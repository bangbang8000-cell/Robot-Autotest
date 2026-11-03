# 固定压测形态：4 suite × 2 case，每条用例固定 Sleep 6s，理论串行总时长 48s。
# 本文件仅用于并发编排压测（EXE-07 / T7-1），不代表业务用例，不得当作功能验收依据。
*** Settings ***
Documentation    Bench Suite 4：压测固定套件，2 条用例，每条固定 6s。

*** Test Cases ***
Bench-4-A
    [Tags]    bench
    Sleep    6s
    Log    固定压测形态：套件 4 的 A 用例，固定 Sleep 6s，仅用于并发编排压测。

Bench-4-B
    [Tags]    bench
    Sleep    6s
    Log    固定压测形态：套件 4 的 B 用例，固定 Sleep 6s，仅用于并发编排压测。
