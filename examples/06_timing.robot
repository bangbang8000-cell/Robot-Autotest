*** Settings ***
Documentation     示例套件 6/6：带日志与短等待，用于观察时间线/报告消息。
Metadata           CASE_ID    SAMPLE-06-TIMING
Metadata           CASE_KIND  sample-timing
Force Tags         sample

*** Test Cases ***
日志与短等待
    [Documentation]    故意产生两条带时间差的日志，便于在报告与时间线里看到顺序。
    Log    步骤 1：开始
    Sleep    0.2s
    Log    步骤 2：等待结束
    Should Be True    ${True}
