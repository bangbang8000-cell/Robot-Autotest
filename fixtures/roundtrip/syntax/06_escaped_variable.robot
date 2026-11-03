*** Settings ***
Documentation     语法边界 06：转义 \${not_a_var} 必须保持字面量
*** Test Cases ***
Escaped Variable
    Log    \${not_a_var} 是字面量
    Log    \@{not_a_list}
    Log    \%{NOT_AN_ENV}
