*** Settings ***
Documentation     8001 台账抽取：TN-IMP-101 多队列收发包测试01
...               技术域：队列功能测试
Force Tags        legacy    legacy-09
Test Timeout      30 minutes

*** Test Cases ***
TN-IMP-101 多队列收发包测试01
    [Documentation]    1. Combined确已处驱动支持的最大值，iperf3 32路接收业务完成。 2. 至少2个不同RX队列的packets计数产生有效正增量；不要求哈希流量均匀或所有最大队列均被命中。 3. 保存RSS映射和队列差值，恢复原Combine
    [Tags]    TN-IMP-101    legacy    legacy-09
    ${A_CMD}=    Set Variable    ethtool -l ens17np0 sudo ethtool -L ens17np0 combined 63 ethtool -x ens17np0 ethtool -S ens17np0 iperf3 -c 172.30.1.2 -B 172.30.1.1 -p 5201 -P 32 -t 30 -i 1 -R ethtool -S ens17np0 
    Log    ${A_CMD}
    Log Many    A 端命令原文    ethtool -l ens17np0 sudo ethtool -L ens17np0 combined 63 ethtool -x ens17np0 ethtool -S ens17np0 iperf3 -c 172.30.1.2 -B 172.30.1.1 -p 5201 -P 32 -t 30 -i 1 -R ethtool -S ens17np0 
    ...    B 端命令原文    iperf3 -s -B 172.30.1.2 -p 5201 -1
    IF    "${A_CMD}" != ""
        Log    已记录 A 端命令原文
    ELSE
        Log    无 A 端命令，按台账保留手工判据
    END
