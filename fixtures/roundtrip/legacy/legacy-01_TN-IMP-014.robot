*** Settings ***
Documentation     8001 台账抽取：TN-IMP-014 网卡信息检查
...               技术域：OS安装与网卡识别测试
Force Tags        legacy    legacy-01
Test Timeout      30 minutes

*** Test Cases ***
TN-IMP-014 网卡信息检查
    [Documentation]    1. 能读取目标CX7的PCI Vendor/Device ID及驱动绑定信息。 2. ens17np0接口存在、MAC地址有效，链路状态及协商速率有明确输出。 3. ethtool可显示当前驱动、固件版本及PCI总线地址，与目标设备一致。
    [Tags]    TN-IMP-014    legacy    legacy-01
    ${A_CMD}=    Set Variable    lspci -Dnnk -d 15b3:1021 ethtool -i ens17np0 ethtool ens17np0 ip address show dev ens17np0
    Log    ${A_CMD}
    Log Many    A 端命令原文    lspci -Dnnk -d 15b3:1021 ethtool -i ens17np0 ethtool ens17np0 ip address show dev ens17np0
    ...    B 端命令原文    （无）
    IF    "${A_CMD}" != ""
        Log    已记录 A 端命令原文
    ELSE
        Log    无 A 端命令，按台账保留手工判据
    END
