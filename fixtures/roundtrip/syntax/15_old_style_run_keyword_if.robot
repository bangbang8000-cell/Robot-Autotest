*** Settings ***
Documentation     语法边界 15：旧式 Run Keyword If 单行写法
*** Test Cases ***
Old Style Run Keyword If
    ${cond}=    Set Variable    ${True}
    Run Keyword If    ${cond}    Log    yes
    ...    ELSE    Log    no
    Run Keyword If    ${cond}    Log    with else
    ...    ELSE IF    ${cond} == ${False}    Log    elif
    ...    ELSE    Log    else
