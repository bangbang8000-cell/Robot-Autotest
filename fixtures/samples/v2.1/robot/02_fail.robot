*** Settings ***
Documentation     示例用例（v2.1 / SMPL-01）：确定失败的取值。
...               定位：给「失败聚合、失败聚类、重跑清单」提供**稳定的失败样本**——
...               失败信息是写死的常量，不随环境变化，故可作为簇键归一化的对照基准。
...               本套件**故意失败**，不得当作产品缺陷。
Metadata          CASE_ID      SMPL-RF-FAIL
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
SMPL-RF-FAIL-01 断言不等（固定失败）
    [Documentation]    失败信息固定为「3 != 5」形状，供聚类归一化（数字被归一）做对照。
    [Tags]    sample    L0    fail
    Should Be Equal    3    5    AssertionError: 3 != 5

SMPL-RF-FAIL-02 期望抛错但没有抛（固定失败）
    [Documentation]    失败原因落在「预期异常未发生」这一类，供聚类一级分类（ASSERT）做对照。
    [Tags]    sample    L0    fail
    Run Keyword And Expect Error    ValueError*    Log    这里不会抛错
