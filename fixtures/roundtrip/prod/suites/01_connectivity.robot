*** Settings ***
Documentation     连通性测试: 外部 IP 可达性 + 内网互通性(经 GPU-01 跳板)
...               整改: ping 解析丢包率/延迟并登记 metrics.json, 数值写入测试消息
Resource          ../resources/common.robot

*** Test Cases ***
外部 IP 可达性检查 (从测试机)
    [Documentation]    学习要点: FOR 循环 + 列表变量, 逐台 ping 并解析统计
    [Tags]    connectivity    ping    external
    ${lines}=    Create List
    FOR    ${ip}    IN    @{GPU_SERVERS}    @{STORAGE_SERVERS}    @{R750_SERVERS}
        ${loss}    ${avg}=    Ping 统计 (从测试机)    ${ip}
        ${avg_disp}=    Set Variable If    $avg is None    N/A    ${avg}
        Should Be Equal As Numbers    ${loss}    0    msg=外部 IP ${ip} 丢包率 ${loss}% 不可达
        Append To List    ${lines}    ${ip} 丢包=${loss}% 延迟=${avg_disp}ms
    END
    ${msg}=    Evaluate    ' | '.join($lines)
    Set Test Message    外部连通性: ${msg}    append=True

内网互通性检查 (从 GPU-01 跳板)
    [Documentation]    学习要点: SSHLibrary 跳板模式, 登录后从内网侧逐台 ping 并解析
    [Tags]    connectivity    ping    internal
    Open Connection    ${JUMP_HOST}    timeout=${SSH_TIMEOUT}
    Login    ${JUMP_USER}    ${JUMP_PWD}
    ${lines}=    Create List
    FOR    ${ip}    IN    @{INTERNAL_IPS}
        ${loss}    ${avg}=    Ping 统计 (经跳板)    ${ip}
        ${avg_disp}=    Set Variable If    $avg is None    N/A    ${avg}
        Should Be True    ${loss} < 100    msg=内网 IP ${ip} 从 GPU-01 不可达 (丢包 ${loss}%)
        Append To List    ${lines}    ${ip} 丢包=${loss}% 延迟=${avg_disp}ms
    END
    Close Connection
    ${msg}=    Evaluate    ' | '.join($lines)
    Set Test Message    内网互通性: ${msg}    append=True
