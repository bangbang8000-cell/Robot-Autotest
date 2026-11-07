*** Settings ***
Documentation     示例套件 2/6：列表操作。
Metadata           CASE_ID    SAMPLE-02-LIST
Metadata           CASE_KIND  sample-basic
Force Tags         sample
Library            Collections

*** Test Cases ***
列表追加与长度
    [Documentation]    追加 3 个元素后长度必须为 3，最后一项必须是最新追加的值。
    ${items}=    Create List    alpha
    Append To List    ${items}    beta    gamma
    Length Should Be    ${items}    3
    ${last}=    Get From List    ${items}    -1
    Should Be Equal    ${last}    gamma

列表包含判断
    ${items}=    Create List    red    green    blue
    List Should Contain Value    ${items}    green
