*** Settings ***
Documentation     PERF-IPERF-200G-002 TCP 多流承载方式对比（单进程 vs 多进程）
...               本文件由 tools/assemble.py 从 cases/perf/PERF-IPERF-200G-002.yaml 自动生成，请勿手工修改。
...               修改用例请编辑 YAML 规格后重新装配。
Library           ../../libraries/IperfLoad.py
Library           OperatingSystem
Library           Collections
Suite Setup       准备运行标识    PERF-IPERF-200G-002
Metadata    CASE_ID       PERF-IPERF-200G-002
Metadata    QA_PROJECT    gpu-nic-qa
Metadata    DUT           p1+p2 R750/CX-6 200G
Metadata    LINK          p1.enp23s0np0 -> p2.enp23s0np0
Metadata    SW_BUILD      Ubuntu 22.04.5 / iperf3 3.9
Metadata    BASELINE      单 22.7 / 多 187.9 Gbps
Metadata    OPERATOR      性能测试组
Metadata    CASE_KIND     perf-carrier-compare

*** Variables ***
${CASE_ID}        PERF-IPERF-200G-002
${SRV_HOST}       10.202.54.106
${CLI_HOST}       10.202.54.107
${SRV_IP}         172.30.11.5
${CLI_IP}         172.30.11.6
${IFACE}          enp23s0np0
${PORT_BASE}      5201
${DURATION}       8
${BLOCK_SIZE}     1M
${GOV_GATE}       performance
${MIN_RING}       4096
${MTU_GATE}       5000
${METRICS_FILE}   results/PERF-IPERF-200G-002_metrics.jsonl
${FLOWS}           8
${SINGLE_PROCS}    1
${SINGLE_STREAMS}    8
${MULTI_PROCS}     8
${MULTI_STREAMS}    1

*** Test Cases ***
PERF-IPERF-200G-002 TCP 多流承载方式对比（单进程 vs 多进程）
    [Documentation]    在同一环境、同一块 200G 网卡上，分别用「1 个 iperf3 进程开 8 条流」与「8 个 iperf3 进程各开 1 条流」施加 8 条并发 TCP 流，对比两种承载方式的聚合吞吐，定量证明 iperf3 单线程用户态循环是单进程多流的天花板，多流吞吐必须由多进程承载。
    [Tags]    perf    iperf3    tcp    multistream    compare    cx6    200g    PERF-IPERF-200G-002
    [Timeout]    20 minutes

    # ---------- 步骤 1 前置检查：链路 + 主机侧调优 ----------
    ${srv_link}=    采集端口信息    ${SRV_HOST}    ${IFACE}
    ${cli_link}=    采集端口信息    ${CLI_HOST}    ${IFACE}
    Should Be True    ${srv_link}[link_detected]    msg=服务端 ${IFACE} Link Down
    Should Be True    ${cli_link}[link_detected]    msg=客户端 ${IFACE} Link Down
    Should Be Equal As Integers    ${srv_link}[speed_mbps]    200000
    ...    msg=服务端协商速率 ${srv_link}[speed_mbps] Mb/s, 期望 200000 Mb/s
    ${rtt}=    采集链路 RTT    ${CLI_HOST}    ${SRV_IP}    5
    Should Be True    ${rtt}[loss_percent] < 1
    ...    msg=客户端到服务端丢包 ${rtt}[loss_percent]%, 前置条件不满足
    ${srv_perf}=    核查性能前置条件    ${SRV_HOST}    ${IFACE}
    ${cli_perf}=    核查性能前置条件    ${CLI_HOST}    ${IFACE}
    ${ok}=    Evaluate    $srv_perf['governor'] == $GOV_GATE
    Should Be True    ${ok}
    ...    msg=服务端 governor=${srv_perf}[governor] 期望 ${GOV_GATE}；请先执行 tools/push_perf_tune.py
    ${ok}=    Evaluate    $cli_perf['governor'] == $GOV_GATE
    Should Be True    ${ok}
    ...    msg=客户端 governor=${cli_perf}[governor] 期望 ${GOV_GATE}；请先执行 tools/push_perf_tune.py

    # ---------- 步骤 2 相位一：单进程多流 ----------
    ${listening}=    启动 Iperf3 服务端    ${SRV_HOST}    ${PORT_BASE}    ${SINGLE_PROCS}
    Should Be Equal As Integers    ${listening}    ${SINGLE_PROCS}
    ...    msg=相位一期望 ${SINGLE_PROCS} 个监听端口, 实际 ${listening} 个
    ${r1}=    运行 Iperf3 多流吞吐    ${CLI_HOST}    ${SRV_IP}    ${PORT_BASE}
    ...    ${SINGLE_PROCS}    ${SINGLE_STREAMS}    ${DURATION}    ${BLOCK_SIZE}
    清理 Iperf3 进程    ${SRV_HOST}
    清理 Iperf3 进程    ${CLI_HOST}
    Sleep    2s

    # ---------- 步骤 3 相位二：多进程单流 ----------
    ${listening}=    启动 Iperf3 服务端    ${SRV_HOST}    ${PORT_BASE}    ${MULTI_PROCS}
    Should Be Equal As Integers    ${listening}    ${MULTI_PROCS}
    ...    msg=相位二期望 ${MULTI_PROCS} 个监听端口, 实际 ${listening} 个
    ${r2}=    运行 Iperf3 多流吞吐    ${CLI_HOST}    ${SRV_IP}    ${PORT_BASE}
    ...    ${MULTI_PROCS}    ${MULTI_STREAMS}    ${DURATION}    ${BLOCK_SIZE}

    # ---------- 步骤 4 汇总与判据 ----------
    ${ratio}=    Evaluate    round($r2['total_gbps'] / $r1['total_gbps'], 2) if $r1['total_gbps'] else 0
    ${cmp}=    Evaluate    dict(single_proc_gbps=$r1['total_gbps'], multi_proc_gbps=$r2['total_gbps'], carrier_ratio=$ratio, flows=$r1['flows'], flows_single=$r1['flows'], flows_multi=$r2['flows'], retransmits=$r2['retransmits'])
    ${CHECKS}=    Evaluate    __import__('json').loads('[{"metric": "carrier_ratio", "operator": ">=", "value": 3.0, "unit": "倍", "severity": "关键", "description": "多进程承载吞吐须不低于单进程的 3 倍（实测 8.3~9.3 倍），否则说明单进程多流已不再是瓶颈，本用例的设计前提需重新评估"}, {"metric": "multi_proc_gbps", "operator": ">=", "value": 150, "unit": "Gbps", "severity": "关键", "description": "多进程承载 8 流吞吐不低于 150 Gbps，确认对照基线本身有效"}, {"metric": "single_proc_gbps", "operator": "<=", "value": 45, "unit": "Gbps", "severity": "重要", "description": "单进程 8 流吞吐不高于 45 Gbps（实测 20.75~22.66），用于锁定 iperf3 单线程上限；一旦超过说明承载模型已变化，必须重新评估验收口径"}]')
    ${ok}=    Evaluate    ${cmp}[carrier_ratio] >= 3.0
    Should Be True    ${ok}
    ...    [关键/carrier_ratio] 多进程承载吞吐须不低于单进程的 3 倍（实测 8.3~9.3 倍），否则说明单进程多流已不再是瓶颈，本用例的设计前提需重新评估（实测 ${cmp}[carrier_ratio] 倍）
    ${ok}=    Evaluate    ${cmp}[multi_proc_gbps] >= 150
    Should Be True    ${ok}
    ...    [关键/multi_proc_gbps] 多进程承载 8 流吞吐不低于 150 Gbps，确认对照基线本身有效（实测 ${cmp}[multi_proc_gbps] Gbps）
    ${ok}=    Evaluate    ${cmp}[single_proc_gbps] <= 45
    Should Be True    ${ok}
    ...    [重要/single_proc_gbps] 单进程 8 流吞吐不高于 45 Gbps（实测 20.75~22.66），用于锁定 iperf3 单线程上限；一旦超过说明承载模型已变化，必须重新评估验收口径（实测 ${cmp}[single_proc_gbps] Gbps）

    # ---------- 步骤 5 指标落盘 ----------
    ${payload}=    Evaluate    __import__('json').dumps(dict($cmp, run_id=$RUN_ID, case_id=$CASE_ID, criteria=$CHECKS, link_speed_mbps=$srv_link['speed_mbps'], rtt_ms=$rtt['rtt_avg_ms'], loss_percent=$rtt['loss_percent'], governor=$srv_perf['governor'], single_detail=dict(total=$r1['total_gbps'], flows=$r1['flows'], per_flow_avg=$r1['per_flow_avg_gbps'], cv=$r1['flow_balance_cv']), multi_detail=dict(total=$r2['total_gbps'], flows=$r2['flows'], per_flow_avg=$r2['per_flow_avg_gbps'], cv=$r2['flow_balance_cv']), streams=$r2['streams'], raw_command=$r2['raw_command']), ensure_ascii=False)
    Append To File    ${METRICS_FILE}    ${payload}\n
    Set Test Message
    ...    单进程 ${r1}[total_gbps] Gbps vs 多进程 ${r2}[total_gbps] Gbps = ${ratio} 倍

    [Teardown]    Run Keywords
    ...    清理 Iperf3 进程    ${SRV_HOST}
    ...    AND    清理 Iperf3 进程    ${CLI_HOST}
