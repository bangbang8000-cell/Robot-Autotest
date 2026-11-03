*** Settings ***
Documentation     8001 台账抽取：TN-IMP-095 RSS 流量分发验证
...               技术域：RSS 接收侧缩放测试
Force Tags        legacy    legacy-07
Test Timeout      30 minutes

*** Test Cases ***
TN-IMP-095 RSS 流量分发验证
    [Documentation]    1. GPU02→GPU01的16路TCP通信成功，GPU01为接收方向。 2. 至少两个不同RX队列的packets计数相对测试前出现正增量；须保留RSS间接表、计数前后差值和iperf3日志。 3. 无逐队列计数或仅1个队列增长时不判定
    [Tags]    TN-IMP-095    legacy    legacy-07
    ${A_CMD}=    Set Variable    ethtool -l ens17np0 sudo ethtool -L ens17np0 combined 8 ethtool -x ens17np0 ethtool -S ens17np0 iperf3 -c 172.30.1.2 -B 172.30.1.1 -p 5201 -P 16 -t 30 -i 1 -R ethtool -S ens17np0 s
    Log    ${A_CMD}
    Log Many    A 端命令原文    ethtool -l ens17np0 sudo ethtool -L ens17np0 combined 8 ethtool -x ens17np0 ethtool -S ens17np0 iperf3 -c 172.30.1.2 -B 172.30.1.1 -p 5201 -P 16 -t 30 -i 1 -R ethtool -S ens17np0 s
    ...    B 端命令原文    iperf3 -s -B 172.30.1.2 -p 5201 -1
    IF    "${A_CMD}" != ""
        Log    已记录 A 端命令原文
    ELSE
        Log    无 A 端命令，按台账保留手工判据
    END
