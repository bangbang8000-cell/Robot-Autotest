*** Settings ***
Documentation     示例套件 4/6：字符串处理。
Metadata           CASE_ID    SAMPLE-04-STRING
Metadata           CASE_KIND  sample-basic
Force Tags         sample
Library            String

*** Test Cases ***
子串与替换
    ${text}=    Set Variable    robot-autotest
    Should Contain    ${text}    auto
    ${replaced}=    Replace String    ${text}    -    _
    Should Be Equal    ${replaced}    robot_autotest

大小写转换
    ${upper}=    Convert To Upper Case    robot
    Should Be Equal    ${upper}    ROBOT
