*** Settings ***
Documentation     8001 台账抽取：TN-IMP-096 中断聚合功能测试01
...               技术域：中断功能测试
Force Tags        legacy    legacy-08
Test Timeout      30 minutes

*** Test Cases ***
TN-IMP-096 中断聚合功能测试01
    [Documentation]    1. rx-usecs发生真实变更并准确回读；不支持参数修改时记录设备能力受限。 2. GPU02向GPU01发流成功，GPU01 RX报文和该CX7对应MSI-X IRQ计数均有增长。 3. rx-usecs与adaptive-rx在测试
    [Tags]    TN-IMP-096    legacy    legacy-08
    ${A_CMD}=    Set Variable    ethtool -c ens17np0 sudo ethtool -C ens17np0 adaptive-rx off rx-usecs 56 ethtool -c ens17np0 ls /sys/bus/pci/devices/0000:45:00.0/msi_irqs cat /proc/interrupts ip -s link show dev 
    Log    ${A_CMD}
    Log Many    A 端命令原文    ethtool -c ens17np0 sudo ethtool -C ens17np0 adaptive-rx off rx-usecs 56 ethtool -c ens17np0 ls /sys/bus/pci/devices/0000:45:00.0/msi_irqs cat /proc/interrupts ip -s link show dev 
    ...    B 端命令原文    iperf3 -s -B 172.30.1.2 -p 5201 -1
    IF    "${A_CMD}" != ""
        Log    已记录 A 端命令原文
    ELSE
        Log    无 A 端命令，按台账保留手工判据
    END
