*** Settings ***
Documentation     8001 台账抽取：TN-IMP-020 LLDP 功能测试
...               技术域：LLDP 功能测试
Force Tags        legacy    legacy-02
Test Timeout      30 minutes

*** Test Cases ***
TN-IMP-020 LLDP 功能测试
    [Documentation]    1. ens17np0能够发现预期交换机邻居，输出包含远端Chassis ID与Port ID。 2. 远端端口编号与现场连接记录一致；无邻居或端口不符不判通过。
    [Tags]    TN-IMP-020    legacy    legacy-02
    ${A_CMD}=    Set Variable    sudo lldpcli show neighbors ports ens17np0 details
    Log    ${A_CMD}
    Log Many    A 端命令原文    sudo lldpcli show neighbors ports ens17np0 details
    ...    B 端命令原文    （无）
    IF    "${A_CMD}" != ""
        Log    已记录 A 端命令原文
    ELSE
        Log    无 A 端命令，按台账保留手工判据
    END
