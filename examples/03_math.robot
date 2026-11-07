*** Settings ***
Documentation     示例套件 3/6：数值计算与容差断言。
Metadata           CASE_ID    SAMPLE-03-MATH
Metadata           CASE_KIND  sample-basic
Force Tags         sample

*** Test Cases ***
绝对值
    ${value}=    Evaluate    abs(-7)
    Should Be Equal As Integers    ${value}    7

四舍五入
    ${value}=    Evaluate    round(3.14159, 2)
    Should Be Equal As Numbers    ${value}    3.14

容差比较
    ${value}=    Evaluate    0.1 + 0.2
    Should Be Equal As Numbers    ${value}    0.3    0.0001
