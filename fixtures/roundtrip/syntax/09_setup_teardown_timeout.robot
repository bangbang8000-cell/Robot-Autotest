*** Settings ***
Test Timeout      2 minutes
*** Test Cases ***
Setup Teardown Timeout
    [Setup]    Log    开始
    [Teardown]    Log    结束
    [Timeout]    30 seconds
    Log    主体
