*** Settings ***
Documentation     示例套件 1/6：最小可执行套件，不依赖任何外部主机与凭据。
...                用途：验证执行引擎、报告入库与时间线展示的「最短闭环」。
Metadata           CASE_ID    SAMPLE-01-QUICK
Metadata           CASE_KIND  sample-smoke
Force Tags         sample    smoke

*** Test Cases ***
算术自检
    [Documentation]    2+2 必须等于 4；失败时只可能是执行环境问题。
    ${sum}=    Evaluate    2 + 2
    Should Be Equal As Integers    ${sum}    4

字符串自检
    [Documentation]    空串拼接后仍为空串。
    ${joined}=    Set Variable    ${EMPTY}${EMPTY}
    Should Be Equal    ${joined}    ${EMPTY}
