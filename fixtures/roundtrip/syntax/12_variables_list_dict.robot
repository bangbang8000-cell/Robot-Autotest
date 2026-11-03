*** Settings ***
Documentation     语法边界 12：变量段含列表与字典
*** Variables ***
${SCALAR}         hello
@{LIST}           one    two    three
&{DICT}           k1=v1    k2=v2
${NESTED}         ${SCALAR}-suffix
*** Test Cases ***
Variables Shapes
    Log Many    @{LIST}
    Log    &{DICT}[k1]
