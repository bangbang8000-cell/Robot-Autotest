*** Settings ***
Documentation     语法边界 05：内嵌变量 ${var_${i}} 与列表索引
*** Variables ***
${i}              1
${var_1}          值一
@{LIST}           a    b    c
*** Test Cases ***
Nested Variable
    Log    ${var_${i}}
    Log    ${LIST}[0]
    Log    ${var_${i}}_后缀
