*** Settings ***
Documentation    冒烟测试套件 - 演示量化看板

*** Test Cases ***
Login Page Loads
    [Tags]    smoke    ui
    Sleep    1s
    Log    登录页面加载正常

API Health Check
    [Tags]    smoke    api
    Sleep    0.5s
    Should Be Equal As Integers    200    200

Database Connection
    [Tags]    smoke    db
    Sleep    0.8s
    Log    数据库连接正常

Config File Valid
    [Tags]    smoke    config
    Sleep    0.3s
    Should Not Be Empty    config_value
