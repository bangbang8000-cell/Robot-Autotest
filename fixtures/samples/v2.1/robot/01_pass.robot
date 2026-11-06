*** Settings ***
Documentation     示例用例（v2.1 / SMPL-01）：全部通过的取值。
...               定位：**平台自身的回归底座**——只验证执行、解析、报告三条管道能把 pass 态
...               正确送到底，不映射任何业务场景，不碰 DUT，不联网。
...               跑法：robot fixtures/samples/v2.1/robot/01_pass.robot
Metadata          CASE_ID      SMPL-RF-PASS
Metadata          RUN_ID       %{RUN_ID=LOCAL-SMPL-0001}
Metadata          QA_PROJECT   Robot-Autotest
Metadata          DUT          none-synthetic
Metadata          LINK         loopback-local
Metadata          SW_BUILD     %{SW_BUILD=v2.1-sample}
Metadata          BASELINE     sample-v2.1
Metadata          OPERATOR     %{OPERATOR=local}
Metadata          STARTED_AT   %{STARTED_AT=1970-01-01T00:00:00Z}
Metadata          CASE_KIND    sample

*** Variables ***
${LEFT}      alpha
${RIGHT}     alpha

*** Test Cases ***
SMPL-RF-PASS-01 字符串相等
    [Documentation]    最朴素的通过路径：不依赖外部资源，任何环境都应为 PASS。
    [Tags]    sample    L0    pass
    Should Be Equal    ${LEFT}    ${RIGHT}
    Log    通过态样例 1：两个变量相等

SMPL-RF-PASS-02 列表长度与成员
    [Documentation]    验证断言库在容器类型上的行为，仍不触外部。
    [Tags]    sample    L0    pass
    ${items}=    Create List    1    2    3
    Length Should Be    ${items}    3
    Should Contain    ${items}    2
    Log    通过态样例 2：列表长度与成员均符合预期
