*** Settings ***
Documentation     语法边界 14：Resource 相对路径导入
Resource          syntax_helper.resource
*** Test Cases ***
Resource Import
    辅助打印    from resource
