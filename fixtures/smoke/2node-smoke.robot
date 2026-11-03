*** Settings ***
Documentation     两台服务器轻量冒烟：健康检查 → ping → iperf3 → perftest。
...               定位是**开发期验证平台执行链路**的轻量用例，不是产品验收。
...               安全口径见 docs/adr/ADR-0013：全部动作 L0/L1，不改配置、不装软件、不碰交换机。
...               跑法：set_dut.sh 或直接 -v DUT_A:10.202.54.106 -v DUT_B:10.202.54.107 -v NIC_A:ens17np0
Library           SSHLibrary
Library           OperatingSystem
Library           String
Library           DateTime
Suite Setup       准备运行标识
Suite Teardown    收尾清理
Metadata          CASE_ID      SMOKE-2NODE-001
Metadata          QA_PROJECT   Robot-Autotest
Metadata          DUT          ${DUT_A}+${DUT_B}
Metadata          LINK         ${NIC_A}<->${NIC_B}
Metadata          SW_BUILD     ${SW_BUILD}
Metadata          CASE_KIND    smoke
Metadata          OPERATOR     ${OPERATOR}
Metadata          BASELINE     develop-smoke

*** Variables ***
# 一律从环境变量取，**不硬编码 IP 与凭据**（仓库里已有明文密码的历史问题，见 docs 记录）
${DUT_A}      %{DUT_A=}
${DUT_B}      %{DUT_B=}
${NIC_A}      %{NIC_A=}
${NIC_B}      %{NIC_B=}
${DUT_USER}   %{DUT_USER=}
${DUT_PASS}   %{DUT_PASS=}
${SW_BUILD}   %{SW_BUILD=dev}
${OPERATOR}   %{OPERATOR=unknown}
${SSH_TIMEOUT}     10s
${CMD_TIMEOUT}     30s
${IPERF_SECS}      10
${PERFTEST_SECS}   5
${RDMA_DEV}        %{RDMA_DEV=mlx5_0}

*** Test Cases ***
TC-01 A 端基础健康检查
    [Documentation]    纯只读采集：主机/OS/内核/负载/内存/磁盘/网卡链路/驱动固件/时钟。
    [Tags]    smoke    L0    health
    ${summary}=    健康检查    A    ${DUT_A}    ${NIC_A}
    Set Test Message    ${summary}

TC-02 B 端基础健康检查
    [Documentation]    同 TC-01，对象为 B 端。
    [Tags]    smoke    L0    health
    ${summary}=    健康检查    B    ${DUT_B}    ${NIC_B}
    Set Test Message    ${summary}

TC-03 双向 ping 连通性
    [Documentation]    两向各 20 包，判据：丢包 0%。丢包 0<x<1% 记告警不判失败。
    [Tags]    smoke    L0    ping
    ${fwd}=    单向 Ping    A→B    ${DUT_A}    ${DUT_B}
    ${rev}=    单向 Ping    B→A    ${DUT_B}    ${DUT_A}
    Set Test Message    ${fwd} | ${rev}

TC-04 iperf3 TCP 单流
    [Documentation]    B 端起一次性服务端（-1），A 端打流 ${IPERF_SECS}s。只记录吞吐，不设通过阈值。
    [Tags]    smoke    L1    throughput
    ${gbps}=    Iperf3 打流    1
    Should Not Be Empty    ${gbps}    msg=iperf3 未取到吞吐（服务端未起或连接失败）
    Set Test Message    TCP 单流 ${gbps} Gbps

TC-05 iperf3 TCP 8 流
    [Documentation]    同 TC-04，-P 8。用于看流数对吞吐的影响（多进程 vs 多流的口径差异见 bench 记录）。
    [Tags]    smoke    L1    throughput
    ${gbps}=    Iperf3 打流    8
    Should Not Be Empty    ${gbps}    msg=iperf3 8 流未取到吞吐
    Set Test Message    TCP 8 流 ${gbps} Gbps

TC-06 perftest ib_write_bw
    [Documentation]    RDMA 写带宽，${PERFTEST_SECS}s。设备名默认 ${RDMA_DEV}，可用 -v RDMA_DEV:xxx 覆盖。
    [Tags]    smoke    L1    rdma
    ${bw}=    Perftest 写带宽
    Should Not Be Empty    ${bw}    msg=perftest 未取到带宽（设备名可能不对或 RDMA 未就绪）
    Set Test Message    ib_write_bw ${bw} Gb/s

*** Keywords ***
准备运行标识
    [Documentation]    项目硬规矩：6 必填 + 4 推荐 metadata，值随本次运行变化。
    ${run_id}=    Get Current Date    result_format=%Y%m%dT%H%M%S
    Set Suite Metadata    RUN_ID    smoke-${run_id}
    Set Suite Metadata    STARTED_AT    ${run_id}
    Should Not Be Empty    ${DUT_A}    msg=缺 DUT_A（export DUT_A 或 -v DUT_A:IP）
    Should Not Be Empty    ${DUT_B}    msg=缺 DUT_B
    Should Not Be Empty    ${DUT_USER}    msg=缺 DUT_USER

收尾清理
    [Documentation]    只杀本套件起的临时进程，不动其他任何东西。
    Run Keyword And Ignore Error    远端执行    ${DUT_B}    pkill -f 'iperf3 -s -p 5201'
    Run Keyword And Ignore Error    远端执行    ${DUT_B}    pkill -f ib_write_bw

远端执行
    [Arguments]    ${host}    ${cmd}    ${timeout}=${CMD_TIMEOUT}
    [Documentation]    一次性 SSH：登录→执行→断开。所有远程命令都经过这里，保证原文入日志。
    ...    超时用 SSHLibrary 自己的 timeout 参数（不包 `bash -lc`，否则命令里的空格/引号会被二次解析）。
    Log    执行命令 @${host}: ${cmd}
    Open Connection    ${host}    timeout=${SSH_TIMEOUT}
    Login    ${DUT_USER}    ${DUT_PASS}
    ${out}=    Execute Command    ${cmd}    timeout=${timeout}
    Close All Connections
    Log    原始输出:\n${out}
    RETURN    ${out}

健康检查
    [Arguments]    ${label}    ${host}    ${nic}
    [Documentation]    只读采集。任一项取不到只记「?」，不判失败——健康检查看现状，不卡门槛。
    ${hn}=    远端执行    ${host}    hostname
    ${os}=    远端执行    ${host}    grep -m1 PRETTY_NAME /etc/os-release
    ${kr}=    远端执行    ${host}    uname -r
    ${cpu}=    远端执行    ${host}    nproc
    ${load}=    远端执行    ${host}    cat /proc/loadavg
    ${mem}=    远端执行    ${host}    free -m
    ${disk}=    远端执行    ${host}    df -P /
    Log    主机 ${hn} | ${os} | kernel ${kr}\nCPU ${cpu}\nload ${load}\nfree ${mem}\ndf ${disk}
    ${summary}=    Set Variable    ${label}(${host}) ${hn} kernel=${kr}
    IF    $nic != ''
        ${state}=    远端执行    ${host}    cat /sys/class/net/${nic}/operstate
        ${speed}=    远端执行    ${host}    cat /sys/class/net/${nic}/speed
        ${drv}=    远端执行    ${host}    ethtool -i ${nic}
        Should Be Equal As Strings    ${state.strip()}    up    msg=${label} 网卡 ${nic} 未 up，打流必失败
        Log    网卡 ${nic} state=${state} speed=${speed}\n${drv}
        ${summary}=    Set Variable    ${summary} nic=${nic} up/${speed}Mb
    END
    RETURN    ${summary}

单向 Ping
    [Arguments]    ${label}    ${src}    ${dst}
    ${out}=    远端执行    ${src}    ping -c 20 -W 2 -q ${dst}    60
    ${loss}=    Get Regexp Matches    ${out}    (\\d+(?:\\.\\d+)?)% packet loss    1
    ${avg}=    Get Regexp Matches    ${out}    min/avg/max/mdev = \\S+/(\\S+)/    1
    Should Not Be Empty    ${loss}    msg=${label} ping 无输出（未安装或不可达）\n${out}
    ${l}=    Set Variable    ${loss}[0]
    ${a}=    Set Variable    N/A
    IF    len($avg) > 0
        ${a}=    Set Variable    ${avg}[0]
    END
    # 判据：丢包必须 <1%。iperf/RDMA 前留这条，是因为"能 ssh"不等于"这条网卡链路通"
    Should Be True    float($l) < 1    msg=${label} 丢包 ${l}%（>1% 判失败）
    RETURN    ${label} 丢包=${l}% RTT平均=${a}ms

Iperf3 打流
    [Arguments]    ${streams}
    [Documentation]    -1 服务端一次即退，故每组流数前都要重启服务端。
    ${起}=    远端执行    ${DUT_B}    pkill -f 'iperf3 -s -p 5201' 2>/dev/null; sleep 1; nohup iperf3 -s -p 5201 -1 >/tmp/iperf3-srv.log 2>&1 & sleep 2; pgrep -f 'iperf3 -s -p 5201' >/dev/null && echo SRV_UP
    Should Contain    ${起}    SRV_UP    msg=B 端 iperf3 服务端未起
    ${out}=    远端执行    ${DUT_A}    iperf3 -c ${DUT_B} -p 5201 -t ${IPERF_SECS} -P ${streams} -f g    ${IPERF_SECS + 20}
    Log    原始输出:\n${out}
    # 多流时有多行 receiver，优先取 [SUM] 汇总行；取不到再取最后一行
    ${lines}=    Get Lines Matching Regexp    ${out}    .*(receiver|sender).*(Gbits/sec|Mbits/sec)
    Should Not Be Empty    ${lines}    msg=未解析到吞吐行\n${out}
    ${sumline}=    Get Lines Matching Regexp    ${out}    .*SUM.*(receiver|sender)
    ${target}=    Set Variable    ${lines}[-1]
    IF    len($sumline) > 0
        ${target}=    Set Variable    ${sumline}[-1]
    END
    ${val}=    Get Regexp Matches    ${target}    (\\d+\\.?\\d*)\\s+(G|M)bits/sec    1
    ${unit}=    Get Regexp Matches    ${target}    (\\d+\\.?\\d*)\\s+(G|M)bits/sec    2
    Should Not Be Empty    ${val}    msg=吞吐行里取不到数值\n${target}
    ${gbps}=    Evaluate    float(${val}[0]) * (1.0 if '${unit}[0]' == 'G' else 0.001)
    RETURN    ${gbps}

Perftest 写带宽
    ${起}=    远端执行    ${DUT_B}    pkill -f ib_write_bw 2>/dev/null; sleep 1; nohup ib_write_bw -d ${RDMA_DEV} -D ${PERFTEST_SECS} >/tmp/perftest-srv.log 2>&1 & sleep 3; pgrep -f ib_write_bw >/dev/null && echo SRV_UP
    Should Contain    ${起}    SRV_UP    msg=B 端 ib_write_bw 服务端未起
    ${out}=    远端执行    ${DUT_A}    ib_write_bw -d ${RDMA_DEV} -D ${PERFTEST_SECS} -F ${DUT_B}    ${PERFTEST_SECS + 30}
    Log    原始输出:\n${out}
    # perftest 表头是「#bytes #iterations BW peak BW average MsgRate」，数字在下一行：
    #   65536  20000  194.50  187.90  0.356461
    # 故匹配「4 个数字的数据行」并取第 4 个，不能 grep 'BW average'（那行是表头）。
    ${m}=    Get Regexp Matches    ${out}    (?m)^[ \\t]*\\d+[ \\t]+\\d+[ \\t]+[\\d.]+[ \\t]+([\\d.]+)    1
    Should Not Be Empty    ${m}    msg=未解析到 BW average 数据行\n${out}
    RETURN    ${m}[-1]
