*** Settings ***
Documentation      RPT-06/07 样本：解析时只有 4 项 metadata，六项靠补录补齐
Metadata           CASE_ID       RPT07-PARTIAL-001
Metadata           RUN_ID        RPT07-EXPORT-20261106-110417
Metadata           QA_PROJECT    robot-autotest
Metadata           OPERATOR      evergreen

*** Test Cases ***
样本通过用例
    [Tags]    rpt07
    Log    缺六项 metadata，解析后不可对外导出
    Should Be Equal    1    1
