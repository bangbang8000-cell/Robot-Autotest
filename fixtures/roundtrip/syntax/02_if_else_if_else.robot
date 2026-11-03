*** Settings ***
Documentation     语法边界 02：IF / ELSE IF / ELSE 三层分支
*** Test Cases ***
If Else If Else
    ${rc}=    Set Variable    ${0}
    IF    ${rc} > 0
        Log    正
    ELSE IF    ${rc} == 0
        Log    零
    ELSE
        Log    负
    END
