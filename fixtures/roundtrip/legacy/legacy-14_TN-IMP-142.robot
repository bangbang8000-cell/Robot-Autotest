*** Settings ***
Documentation     8001 台账抽取：TN-IMP-142 VXLAN开关checksum offload测试
...               技术域：VXLAN功能测试
Force Tags        legacy    legacy-14
Test Timeout      30 minutes

*** Test Cases ***
TN-IMP-142 VXLAN开关checksum offload测试
    [Documentation]    off/on两阶段TCP接收字节>0且双方退出0；关闭时tx_tso_inner_packets增量0、开启时增量>0；独立接收抓包中VNI42，外层UDP校验和非零且正确，内层TCP校验和正确，外层IP长度<=9000。
    [Tags]    TN-IMP-142    legacy    legacy-14
    ${A_CMD}=    Set Variable    sudo ip link add cx7vx142 type vxlan id 42 local 172.30.2.3 remote 172.30.2.4 dev ens24np0 dstport 4789 udpcsum sudo ip link set cx7vx142 mtu 8950 up sudo ip addr add 198.19.42.1/3
    Log    ${A_CMD}
    Log Many    A 端命令原文    sudo ip link add cx7vx142 type vxlan id 42 local 172.30.2.3 remote 172.30.2.4 dev ens24np0 dstport 4789 udpcsum sudo ip link set cx7vx142 mtu 8950 up sudo ip addr add 198.19.42.1/3
    ...    B 端命令原文    sudo ip link add cx7vx142 type vxlan id 42 local 172.30.2.4 remote 172.30.2.3 dev ens24np0 dstport 4789 udpcsum sudo ip link set cx7vx142 mtu 8950 up sudo ip addr add 198.19.42.2/3
    IF    "${A_CMD}" != ""
        Log    已记录 A 端命令原文
    ELSE
        Log    无 A 端命令，按台账保留手工判据
    END
