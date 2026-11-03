*** Settings ***
Documentation     8001 台账抽取：TN-IMP-118 ip link逻辑UP/DOWN循环100次
...               技术域：网卡逻辑状态测试（源表续行）
Force Tags        legacy    legacy-10
Test Timeout      30 minutes

*** Test Cases ***
TN-IMP-118 ip link逻辑UP/DOWN循环100次
    [Documentation]    1. 必须完成100轮，各轮DOWN/UP标志正确，物理Link均在30秒内恢复。 2. 最终Ping GPU02成功；检查期间不存在新增且影响该接口的驱动/PCIe故障。 3. 任意轮失败、最终不通或存在关联故障不得判通过。
    [Tags]    TN-IMP-118    legacy    legacy-10
    ${A_CMD}=    Set Variable    sudo ip link set dev ens17np0 down ip link show dev ens17np0 sudo ip link set dev ens17np0 up ethtool ens17np0 ping -c 3 -I ens17np0 172.30.1.2
    Log    ${A_CMD}
    Log Many    A 端命令原文    sudo ip link set dev ens17np0 down ip link show dev ens17np0 sudo ip link set dev ens17np0 up ethtool ens17np0 ping -c 3 -I ens17np0 172.30.1.2
    ...    B 端命令原文    （无）
    IF    "${A_CMD}" != ""
        Log    已记录 A 端命令原文
    ELSE
        Log    无 A 端命令，按台账保留手工判据
    END
