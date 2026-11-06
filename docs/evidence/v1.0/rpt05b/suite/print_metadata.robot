*** Settings ***
Documentation      RPT-05 对外导出正样本：10 项 metadata 齐全
Metadata           CASE_ID       RPT05-EXT-001
Metadata           RUN_ID        RUN-RPT05EXT-20261106-101647
Metadata           QA_PROJECT    robot-autotest
Metadata           DUT           WSL-DEV-NODE-01
Metadata           LINK          wsl2-lo -> backend:8081
Metadata           SW_BUILD      Ubuntu 24.04 / RF 7.5.1
Metadata           BASELINE      2 passed 1 failed
Metadata           OPERATOR      evergreen
Metadata           STARTED_AT    2026-11-06T02:16:48Z
Metadata           CASE_KIND     report-export

*** Test Cases ***
元数据齐全的外部导出样本
    [Tags]    rpt05
    Log    这条用例用于 RPT-05 对外成功分支
    Should Be Equal    1    1
    Log    第二步：关键字步骤也要进对外报告

第二条通过用例
    [Tags]    rpt05
    Log    用于验证多用例的结论统计
    Should Contain    robot-autotest    robot

故意失败用例
    [Tags]    rpt05    known-fail
    Log    故意失败，验证失败结论在对外报告里仍可读
    Should Be Equal    1    2    msg=故意失败：验证失败用例渲染
