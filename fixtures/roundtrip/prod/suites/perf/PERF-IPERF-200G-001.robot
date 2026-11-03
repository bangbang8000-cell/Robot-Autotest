*** Settings ***
Documentation     PERF-IPERF-200G-001 CX-6 200G 链路 TCP 多流吞吐测试
...               本文件由 tools/assemble.py 从 cases/perf/PERF-IPERF-200G-001.yaml 自动生成，请勿手工修改。
...               修改用例请编辑 YAML 规格后重新装配。
Library           ../../libraries/IperfLoad.py
Library           OperatingSystem
Library           Collections
Suite Setup       准备运行标识    PERF-IPERF-200G-001
Metadata    CASE_ID       PERF-IPERF-200G-001
Metadata    QA_PROJECT    gpu-nic-qa
Metadata    DUT           p1+p2 R750/CX-6 200G
Metadata    LINK          p1.enp23s0np0 -> p2.enp23s0np0
Metadata    SW_BUILD      Ubuntu 22.04.5 / iperf3 3.9
Metadata    BASELINE      188~195 Gbps
Metadata    OPERATOR      性能测试组
Metadata    CASE_KIND     perf-throughput

*** Variables ***
${CASE_ID}        PERF-IPERF-200G-001
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
${METRICS_FILE}   results/PERF-IPERF-200G-001_metrics.jsonl
${FLOWS}           8
${PROCS}          8
${STREAMS_PER_PROC}    1

*** Test Cases ***
PERF-IPERF-200G-001 CX-6 200G 链路 TCP 多流吞吐测试
    [Documentation]    验证两台 Dell PowerEdge R750 之间 ConnectX-6 200G 链路在 8 条并发 TCP 流下的实际吞吐能力。以「流」为度量单位，考核链路带宽、转发路径与主机侧协议栈能否支撑线速转发，并确认 8 条流之间的吞吐相对均衡、无持续重传。
    [Tags]    perf    iperf3    tcp    multistream    cx6    200g    smoke    PERF-IPERF-200G-001
    [Timeout]    15 minutes

    # ---------- 步骤 1 前置检查 A：链路状态与连通性 ----------
    ${srv_link}=    采集端口信息    ${SRV_HOST}    ${IFACE}
    ${cli_link}=    采集端口信息    ${CLI_HOST}    ${IFACE}
    Should Be True    ${srv_link}[link_detected]    msg=服务端 ${IFACE} Link Down
    Should Be True    ${cli_link}[link_detected]    msg=客户端 ${IFACE} Link Down
    Should Be Equal As Integers    ${srv_link}[speed_mbps]    200000
    ...    msg=服务端协商速率 ${srv_link}[speed_mbps] Mb/s, 期望 200000 Mb/s
    Should Be Equal As Integers    ${cli_link}[speed_mbps]    200000
    ...    msg=客户端协商速率 ${cli_link}[speed_mbps] Mb/s, 期望 200000 Mb/s
    ${rtt}=    采集链路 RTT    ${CLI_HOST}    ${SRV_IP}    5
    Should Be True    ${rtt}[loss_percent] < 1
    ...    msg=客户端到服务端丢包 ${rtt}[loss_percent]%, 前置条件不满足

    # ---------- 步骤 2 前置检查 B：主机侧调优门槛 ----------
    # 这三项不达标时吞吐会被系统性压低（实测 powersave 下掉到 150 Gbps 量级），
    # 因此不做「软告警」而是硬断，让环境问题在用例开头就暴露，避免污染结论。
    ${srv_perf}=    核查性能前置条件    ${SRV_HOST}    ${IFACE}
    ${cli_perf}=    核查性能前置条件    ${CLI_HOST}    ${IFACE}
    ${ok}=    Evaluate    $srv_perf['governor'] == $GOV_GATE
    Should Be True    ${ok}
    ...    msg=服务端 governor=${srv_perf}[governor] 期望 ${GOV_GATE}；请先执行 tools/push_perf_tune.py 持久化调优
    ${ok}=    Evaluate    $cli_perf['governor'] == $GOV_GATE
    Should Be True    ${ok}
    ...    msg=客户端 governor=${cli_perf}[governor] 期望 ${GOV_GATE}；请先执行 tools/push_perf_tune.py 持久化调优
    Should Be True    ${srv_perf}[ring_rx] >= ${MIN_RING}
    ...    msg=服务端环形缓冲区 RX=${srv_perf}[ring_rx] 低于门槛 ${MIN_RING}；请先执行 tools/push_perf_tune.py
    Should Be True    ${cli_perf}[ring_rx] >= ${MIN_RING}
    ...    msg=客户端环形缓冲区 RX=${cli_perf}[ring_rx] 低于门槛 ${MIN_RING}；请先执行 tools/push_perf_tune.py
    Should Be Equal As Integers    ${srv_link}[mtu]    ${MTU_GATE}
    ...    msg=服务端 MTU=${srv_link}[mtu] 期望 ${MTU_GATE}，两端不一致会导致分片或超长丢包

    # ---------- 步骤 3 启动服务端 ----------
    ${listening}=    启动 Iperf3 服务端    ${SRV_HOST}    ${PORT_BASE}    ${PROCS}
    Should Be Equal As Integers    ${listening}    ${PROCS}
    ...    msg=期望 ${PROCS} 个监听端口, 实际 ${listening} 个（端口不足会导致静默少流）

    # ---------- 步骤 4 多流打流 ----------
    # flows 是被测对象，procs 只是承载；每进程 -P ${STREAMS_PER_PROC} 条流
    ${tp}=    运行 Iperf3 多流吞吐    ${CLI_HOST}    ${SRV_IP}    ${PORT_BASE}
    ...    ${PROCS}    ${STREAMS_PER_PROC}    ${DURATION}    ${BLOCK_SIZE}
    Should Be Equal As Integers    ${tp}[procs]    ${PROCS}
    ...    msg=实际只有 ${tp}[procs] 个进程产出结果，期望 ${PROCS}

    # ---------- 步骤 5 判据校验 ----------
    ${CHECKS}=    Evaluate    __import__('json').loads('[{"metric": "bandwidth_gbps", "operator": ">=", "value": 150, "unit": "Gbps", "severity": "关键", "description": "8 条并发流合计吞吐不低于 150 Gbps（实测区间 187.9~194.9 Gbps 的 80% 以下取整）"}, {"metric": "flows", "operator": "==", "value": 8, "unit": "条", "severity": "关键", "description": "实际建立的并发流数须为 8 条，防止进程或端口缺失导致少流"}, {"metric": "flow_balance_cv", "operator": "<=", "value": 0.35, "unit": "无量纲", "severity": "重要", "description": "流间不均衡度不高于 0.35（实测 0.033~0.269，逐流 18.48~30.77 Gbps），避免个别流长期饥饿"}, {"metric": "retrans_per_gb", "operator": "<=", "value": 50, "unit": "次/GB", "severity": "重要", "description": "每 GB 发送量的重传次数不超过 50（实测 0~1.09）。按吞吐归一化，避免绝对次数随带宽线性增长而误判为丢包"}, {"metric": "link_speed_mbps", "operator": "==", "value": 200000, "unit": "Mb/s", "severity": "关键", "description": "端口协商速率须达 200 Gbps"}]')
    ${ok}=    Evaluate    ${tp}[total_gbps] >= 150
    Should Be True    ${ok}
    ...    [关键/bandwidth_gbps] 8 条并发流合计吞吐不低于 150 Gbps（实测区间 187.9~194.9 Gbps 的 80% 以下取整）（实测 ${tp}[total_gbps] Gbps）
    ${ok}=    Evaluate    ${tp}[flows] == 8
    Should Be True    ${ok}
    ...    [关键/flows] 实际建立的并发流数须为 8 条，防止进程或端口缺失导致少流（实测 ${tp}[flows] 条）
    ${ok}=    Evaluate    ${tp}[flow_balance_cv] <= 0.35
    Should Be True    ${ok}
    ...    [重要/flow_balance_cv] 流间不均衡度不高于 0.35（实测 0.033~0.269，逐流 18.48~30.77 Gbps），避免个别流长期饥饿（实测 ${tp}[flow_balance_cv] 无量纲）
    ${ok}=    Evaluate    ${tp}[retrans_per_gb] <= 50
    Should Be True    ${ok}
    ...    [重要/retrans_per_gb] 每 GB 发送量的重传次数不超过 50（实测 0~1.09）。按吞吐归一化，避免绝对次数随带宽线性增长而误判为丢包（实测 ${tp}[retrans_per_gb] 次/GB）
    ${ok}=    Evaluate    ${srv_link}[speed_mbps] == 200000
    Should Be True    ${ok}
    ...    [关键/link_speed_mbps] 端口协商速率须达 200 Gbps（实测 ${srv_link}[speed_mbps] Mb/s）

    # ---------- 步骤 6 指标落盘 ----------
    ${payload}=    Evaluate    __import__('json').dumps(dict($tp, run_id=$RUN_ID, case_id=$CASE_ID, criteria=$CHECKS, link_speed_mbps=$srv_link['speed_mbps'], mtu=$srv_link['mtu'], rtt_ms=$rtt['rtt_avg_ms'], loss_percent=$rtt['loss_percent'], governor=$srv_perf['governor'], ring_rx=$srv_perf['ring_rx']), ensure_ascii=False)
    Append To File    ${METRICS_FILE}    ${payload}\n
    Set Test Message
    ...    ${FLOWS} 流 / ${tp}[total_gbps] Gbps / 每流 ${tp}[per_flow_avg_gbps] Gbps / CV ${tp}[flow_balance_cv] / 重传 ${tp}[retransmits] 次 / 链路 ${srv_link}[speed_mbps] Mb/s

    [Teardown]    Run Keywords
    ...    清理 Iperf3 进程    ${SRV_HOST}
    ...    AND    清理 Iperf3 进程    ${CLI_HOST}
