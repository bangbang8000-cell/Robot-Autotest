*** Settings ***
Documentation    回归测试套件 - 含故意失败项用于演示

*** Test Cases ***
User Registration Flow
    [Tags]    regression    ui
    Sleep    1.2s
    Log    注册流程通过

Search Functionality
    [Tags]    regression    ui
    Sleep    0.9s
    Log    搜索功能正常

Payment Gateway
    [Tags]    regression    api    critical
    Sleep    1.5s
    Fail    模拟失败: 支付网关超时

Email Notification
    [Tags]    regression    api
    Sleep    0.7s
    Log    邮件通知正常

Report Generation
    [Tags]    regression    report
    Sleep    1.1s
    Should Be Equal    actual_data    expected_data    模拟失败: 报表数据不一致

Logout Flow
    [Tags]    regression    ui
    Sleep    0.4s
    Log    登出流程正常
