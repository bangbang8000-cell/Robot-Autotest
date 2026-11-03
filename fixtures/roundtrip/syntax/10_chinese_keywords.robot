*** Settings ***
Documentation     语法边界 10：中文关键字与中文参数值
*** Test Cases ***
中文用例
    [Tags]    中文    编码
    打印信息    中文参数值，含全角标点
    ${结果}=    拼接    α    β
    Log    ${结果}
*** Keywords ***
打印信息
    [Arguments]    ${文本}
    Log    ${文本}
拼接
    [Arguments]    ${左}    ${右}
    ${x}=    Catenate    ${左}${右}
    RETURN    ${x}
