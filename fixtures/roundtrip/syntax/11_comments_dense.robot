*** Settings ***
Documentation     语法边界 11：空行与注释密集
# 顶部注释一
# 顶部注释二

*** Test Cases ***
注释密集
    # 用例内注释
    Log    a    # 行尾注释

    # 空行之后的注释
    Log    b
# 段末注释
