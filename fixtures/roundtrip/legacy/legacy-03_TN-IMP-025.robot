*** Settings ***
Documentation     8001 台账抽取：TN-IMP-025 光模块数字诊断指标验证
...               技术域：光模块 DDM 指标监控测试
Force Tags        legacy    legacy-03
Test Timeout      30 minutes

*** Test Cases ***
TN-IMP-025 光模块数字诊断指标验证
    [Documentation]    1. 能正常读取光模块DOM/DDM数据，显示温度、电压及模块实际支持的TX/RX光功率指标。 2. 数值有效且未出现模块报告的异常告警；不支持的单项指标如实记录。 3. 若当前为不支持DOM的无源DAC，不得判为验证通过。
    [Tags]    TN-IMP-025    legacy    legacy-03
    ${A_CMD}=    Set Variable    sudo ethtool -m ens17np0
    Log    ${A_CMD}
    Log Many    A 端命令原文    sudo ethtool -m ens17np0
    ...    B 端命令原文    （无）
    IF    "${A_CMD}" != ""
        Log    已记录 A 端命令原文
    ELSE
        Log    无 A 端命令，按台账保留手工判据
    END
