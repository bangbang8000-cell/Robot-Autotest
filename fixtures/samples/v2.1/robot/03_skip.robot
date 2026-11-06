*** Settings ***
Documentation     示例用例（v2.1 / SMPL-01）：确定跳过的取值。
...               定位：验证平台把 SKIP 与 PASS/FAIL 正确区分——跳过**不计入失败数**，
...               也不应被失败聚类收进来。
Metadata          CASE_ID      SMPL-RF-SKIP
Metadata          RUN_ID       %{RUN_ID=LOCAL-SMPL-0001}
Metadata          QA_PROJECT   Robot-Autotest
Metadata          DUT          none-synthetic
Metadata          LINK         loopback-local
Metadata          SW_BUILD     %{SW_BUILD=v2.1-sample}
Metadata          BASELINE     sample-v2.1
Metadata          OPERATOR     %{OPERATOR=local}
Metadata          STARTED_AT   %{STARTED_AT=1970-01-01T00:00:00Z}
Metadata          CASE_KIND    sample

*** Test Cases ***
SMPL-RF-SKIP-01 主动跳过
    [Documentation]    无条件 Skip。跳过原因是常量，便于断言解析结果。
    [Tags]    sample    L0    skip
    Skip    示例用例：主动跳过，不执行任何动作

SMPL-RF-SKIP-02 条件不满足而跳过
    [Documentation]    通过 IF 分支跳过，验证「非首行 Skip」同样被识别为 SKIP。
    [Tags]    sample    L0    skip
    ${condition}=    Set Variable    ${False}
    IF    not ${condition}
        Skip    示例用例：条件不满足，跳过
    END
    Fail    不应执行到这里
