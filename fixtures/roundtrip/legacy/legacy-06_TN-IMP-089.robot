*** Settings ***
Documentation     8001 台账抽取：TN-IMP-089 校验和卸载功能验证
...               技术域：硬件校验和卸载测试
Force Tags        legacy    legacy-06
Test Timeout      30 minutes

*** Test Cases ***
TN-IMP-089 校验和卸载功能验证
    [Documentation]    1. GPU01的TX/RX校验和卸载确实能够分别达到on及off，且结束时恢复原始全部卸载状态。 2. 开启/关闭两组模式下，双向TCP/UDP实际通信成功；iperf3无异常连接失败，UDP丢包和接口错误计数无异常增长；异常须保留日志分
    [Tags]    TN-IMP-089    legacy    legacy-06
    ${A_CMD}=    Set Variable    ethtool -k ens17np0 ethtool -S ens17np0 sudo ethtool -K ens17np0 tx off rx off iperf3 -c 172.30.1.2 -B 172.30.1.1 -p 5201 -P 8 -t 30 -i 1 --bidir iperf3 -c 172.30.1.2 -B 172.30.1.1
    Log    ${A_CMD}
    Log Many    A 端命令原文    ethtool -k ens17np0 ethtool -S ens17np0 sudo ethtool -K ens17np0 tx off rx off iperf3 -c 172.30.1.2 -B 172.30.1.1 -p 5201 -P 8 -t 30 -i 1 --bidir iperf3 -c 172.30.1.2 -B 172.30.1.1
    ...    B 端命令原文    iperf3 -s -B 172.30.1.2 -p 5201 -1
    IF    "${A_CMD}" != ""
        Log    已记录 A 端命令原文
    ELSE
        Log    无 A 端命令，按台账保留手工判据
    END
