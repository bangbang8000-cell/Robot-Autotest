*** Settings ***
Documentation     语法边界 04：WHILE 循环 + limit=
*** Test Cases ***
While With Limit
    ${i}=    Set Variable    ${0}
    WHILE    ${i} < 3    limit=10
        ${i}=    Evaluate    ${i} + 1
        Log    轮次 ${i}
    END
