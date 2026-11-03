*** Settings ***
Documentation     语法边界 07：续行 ... 跨行参数
*** Test Cases ***
Continuation
    Log Many    one
    ...    two
    ...    three
    [Documentation]    第一行
    ...    第二行
    ...    第三行
    Log    done
