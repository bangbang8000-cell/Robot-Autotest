*** Settings ***
Documentation     8001 台账抽取：TN-IMP-136 测试Vlan模式下网卡的连通性
...               技术域：VLAN功能测试
Force Tags        legacy    legacy-13
Test Timeout      30 minutes

*** Test Cases ***
TN-IMP-136 测试Vlan模式下网卡的连通性
    [Documentation]    1. 两端实际具有802.1Q VID1101子接口，配置了规划地址。 2. GPU01以VLAN接口发送的4个ICMP Echo全部收到应答（4/4），接口收发计数有业务增长。 3. 双端测试接口均能在结束后移除；若现网未放通VID110
    [Tags]    TN-IMP-136    legacy    legacy-13
    ${A_CMD}=    Set Variable    sudo ip link add link ens17np0 name tnv1101 type vlan id 1101 sudo ip addr add 198.19.11.1/30 dev tnv1101 sudo ip link set tnv1101 up ip -d link show dev tnv1101 ping -c 4 -I tnv11
    Log    ${A_CMD}
    Log Many    A 端命令原文    sudo ip link add link ens17np0 name tnv1101 type vlan id 1101 sudo ip addr add 198.19.11.1/30 dev tnv1101 sudo ip link set tnv1101 up ip -d link show dev tnv1101 ping -c 4 -I tnv11
    ...    B 端命令原文    sudo ip link add link ens17np0 name tnv1101 type vlan id 1101 sudo ip addr add 198.19.11.2/30 dev tnv1101 sudo ip link set tnv1101 up ip -s link show dev tnv1101 sudo ip link delet
    IF    "${A_CMD}" != ""
        Log    已记录 A 端命令原文
    ELSE
        Log    无 A 端命令，按台账保留手工判据
    END
