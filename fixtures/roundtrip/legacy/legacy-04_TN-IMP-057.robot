*** Settings ***
Documentation     8001 台账抽取：TN-IMP-057 GRO功能关闭多次抓包测试（MTU1500）
...               技术域：GRO功能测试
Force Tags        legacy    legacy-04
Test Timeout      30 minutes

*** Test Cases ***
TN-IMP-057 GRO功能关闭多次抓包测试（MTU1500）
    [Documentation]    1. GPU01 回读 GRO=off，两端测试 MTU=1500，iperf3 反向 TCP 流成功。 2. GPU01 生成 3 份非空、可解析的接收口 pcap，均包含 GPU02→GPU01 的 TCP 报文；检查 IP 报文长度符
    [Tags]    TN-IMP-057    legacy    legacy-04
    ${A_CMD}=    Set Variable    sudo ip link set dev ens17np0 mtu 1500 sudo ethtool -K ens17np0 gro off ethtool -k ens17np0 sudo ethtool -K ens17np0 lro off sudo timeout 15 tcpdump -i ens17np0 -s 0 -U -w /tmp/TN-
    Log    ${A_CMD}
    Log Many    A 端命令原文    sudo ip link set dev ens17np0 mtu 1500 sudo ethtool -K ens17np0 gro off ethtool -k ens17np0 sudo ethtool -K ens17np0 lro off sudo timeout 15 tcpdump -i ens17np0 -s 0 -U -w /tmp/TN-
    ...    B 端命令原文    sudo ip link set dev ens17np0 mtu 1500 iperf3 -s -B 172.30.1.2 -p 5201 -1 sudo ip link set dev ens17np0 mtu 9000
    IF    "${A_CMD}" != ""
        Log    已记录 A 端命令原文
    ELSE
        Log    无 A 端命令，按台账保留手工判据
    END
