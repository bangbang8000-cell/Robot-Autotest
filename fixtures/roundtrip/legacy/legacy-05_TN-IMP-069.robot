*** Settings ***
Documentation     8001 台账抽取：TN-IMP-069 TSO功能关闭多次抓包测试（MTU1500）
...               技术域：TSO功能测试
Force Tags        legacy    legacy-05
Test Timeout      30 minutes

*** Test Cases ***
TN-IMP-069 TSO功能关闭多次抓包测试（MTU1500）
    [Documentation]    1. GPU01回读TSO=off，两端MTU=1500，三轮TCP发送均成功。 2. GPU02生成3份有目标TCP报文的有效pcap，能够核对接收侧分段长度；与同MTU的反向TSO状态对照，结合实际存在的TSO专有计数判断卸载效果。 3
    [Tags]    TN-IMP-069    legacy    legacy-05
    ${A_CMD}=    Set Variable    sudo ip link set dev ens17np0 mtu 1500 sudo ethtool -K ens17np0 tso off ethtool -k ens17np0 sudo timeout 15 tcpdump -i ens17np0 -s 0 -U -w /tmp/TN-IMP-069_1.pcap "tcp and host 172.
    Log    ${A_CMD}
    Log Many    A 端命令原文    sudo ip link set dev ens17np0 mtu 1500 sudo ethtool -K ens17np0 tso off ethtool -k ens17np0 sudo timeout 15 tcpdump -i ens17np0 -s 0 -U -w /tmp/TN-IMP-069_1.pcap "tcp and host 172.
    ...    B 端命令原文    sudo ip link set dev ens17np0 mtu 1500 iperf3 -s -B 172.30.1.2 -p 5201 -1 sudo ip link set dev ens17np0 mtu 9000
    IF    "${A_CMD}" != ""
        Log    已记录 A 端命令原文
    ELSE
        Log    无 A 端命令，按台账保留手工判据
    END
