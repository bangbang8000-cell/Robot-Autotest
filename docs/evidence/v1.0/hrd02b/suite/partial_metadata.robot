*** Settings ***
Documentation      HRD-02 补录样本：解析时只有 4 项 metadata，六项靠补录补齐
Metadata           CASE_ID       HRD02-PATCH-001
Metadata           RUN_ID        RUN-HRD02B-20261106-104200
Metadata           QA_PROJECT    robot-autotest
Metadata           OPERATOR      evergreen

*** Test Cases ***
补录前不可对外导出的样本
    [Tags]    hrd02
    Log    该套件故意缺六项 metadata，验证补录能把门禁扳绿
    Should Be Equal    1    1

第二条通过用例
    [Tags]    hrd02
    Log    用于验证补录后用例结论仍可读
    Should Contain    robot-autotest    robot

故意失败用例
    [Tags]    hrd02    known-fail
    Log    故意失败，验证补录不影响用例结论
    Should Be Equal    1    2    msg=故意失败：补录场景样本
