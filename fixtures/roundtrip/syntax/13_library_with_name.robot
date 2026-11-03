*** Settings ***
Documentation     语法边界 13：Library 带 WITH NAME 与导入参数
Library           Collections    WITH NAME    Coll
Library           String    WITH NAME    Str    # 行尾注释
*** Test Cases ***
Library Import
    ${l}=    Create List    a    b
    Log    ${l}
