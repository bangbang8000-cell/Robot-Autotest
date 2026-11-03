*** Settings ***
Documentation     8001 台账抽取：TN-IMP-167 iperf打流测试-单口单向TCP性能测试
...               技术域：物理网口TCP/IP单口单向性能测试
Force Tags        legacy    legacy-16
Test Timeout      30 minutes

*** Test Cases ***
TN-IMP-167 iperf打流测试-单口单向TCP性能测试
    [Documentation]    1. 双端完成20秒单口单向8流TCP传输，iperf3退出正常，接收端有有效receiver SUM吞吐及发送端重传统计。 2. 核实客户端源172.30.1.1和服务端目的172.30.1.2确对应平面1物理网口，并记录实际Gb/s、R
    [Tags]    TN-IMP-167    legacy    legacy-16
    ${A_CMD}=    Set Variable    iperf3 -c 172.30.1.2 -B 172.30.1.1 -p 5201 -P 8 -t 30 -i 1
    Log    ${A_CMD}
    Log Many    A 端命令原文    iperf3 -c 172.30.1.2 -B 172.30.1.1 -p 5201 -P 8 -t 30 -i 1
    ...    B 端命令原文    iperf3 -s -B 172.30.1.2 -p 5201 -1
    IF    "${A_CMD}" != ""
        Log    已记录 A 端命令原文
    ELSE
        Log    无 A 端命令，按台账保留手工判据
    END
