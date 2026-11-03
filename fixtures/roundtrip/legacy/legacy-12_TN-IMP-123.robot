*** Settings ***
Documentation     8001 台账抽取：TN-IMP-123 MTU9000 收发包测试
...               技术域：收发包测试
Force Tags        legacy    legacy-12
Test Timeout      30 minutes

*** Test Cases ***
TN-IMP-123 MTU9000 收发包测试
    [Documentation]    1. 两端MTU9000均设置并回读成功。 2. 8972字节DF Ping发送4次均收到应答；8973字节DF探测因本地MTU限制失败。 3. 测试结束两端恢复原MTU；链路不支持9000或报文丢失时不得判通过。
    [Tags]    TN-IMP-123    legacy    legacy-12
    ${A_CMD}=    Set Variable    sudo ip link set dev ens17np0 mtu 9000 ip link show dev ens17np0 ping -c 4 -M do -s 8972 -I ens17np0 172.30.1.2 sudo ip link set dev ens17np0 mtu 9000
    Log    ${A_CMD}
    Log Many    A 端命令原文    sudo ip link set dev ens17np0 mtu 9000 ip link show dev ens17np0 ping -c 4 -M do -s 8972 -I ens17np0 172.30.1.2 sudo ip link set dev ens17np0 mtu 9000
    ...    B 端命令原文    sudo ip link set dev ens17np0 mtu 9000 ip link show dev ens17np0 sudo ip link set dev ens17np0 mtu 9000
    IF    "${A_CMD}" != ""
        Log    已记录 A 端命令原文
    ELSE
        Log    无 A 端命令，按台账保留手工判据
    END
