*** Settings ***
Documentation    一个简单的 Robot Framework 测试示例
Library           OperatingSystem
Library           BuiltIn
Library           Collections
Library           String
Library           SeleniumLibrary

*** Test Cases ***
测试字符串操作
    [Documentation]    测试字符串拼接和转换
    ${name} =          Set Variable      Robot
    ${greeting} =      Set Variable      Hello ${name}!
    Should Be Equal    ${greeting}       Hello Robot!
    
    ${upper} =         Convert To Upper Case    ${greeting}
    Should Be Equal    ${upper}                 HELLO ROBOT!

测试数学运算
    [Documentation]    测试基本数学运算
    ${result} =        Evaluate    5 + 7
    Should Be Equal    ${result}   ${12}
    
    ${result} =        Evaluate    10 - 3
    Should Be Equal    ${result}   ${7}
    
    ${result} =        Evaluate    6 * 9
    Should Be Equal    ${result}   ${54}

测试列表操作
    [Documentation]    测试列表操作
    @{fruits} =        Create List    Apple    Banana    Orange
    Length Should Be    ${fruits}      3
    Should Contain     ${fruits}       Banana
    
    Append To List     ${fruits}       Grape
    Length Should Be    ${fruits}      4

测试文件系统操作
    [Documentation]    测试文件操作
    Create File        test.txt        This is a test file
    File Should Exist  test.txt
    
    ${content} =       Get File        test.txt
    Should Contain     ${content}      test file
    
    Remove File        test.txt
    File Should Not Exist  test.txt

测试网页操作 (需要浏览器)
    [Documentation]    打开百度并验证标题
    [Tags]             web
    Open Browser       https://www.baidu.com    browser=chrome
    Title Should Be    百度一下，你就知道
    Capture Page Screenshot    baidu_homepage.png
    Close Browser

