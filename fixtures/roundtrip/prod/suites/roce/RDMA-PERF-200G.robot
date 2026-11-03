*** Settings ***
Documentation       ConnectX-6 200G RoCEv2 基础性能测试
...
...                 用例编号：RDMA-PERF-200G
...                 目的：验证两台 Dell PowerEdge R750 之间 ConnectX-6 200G 端口在 RoCEv2 协议下的 带宽、时延与稳定性是否达到该速率等级应有的水平，并确认链路协商、GID 配置与 MTU 等前置条件正确，为后续 RDMA 业务（分布式存储、集合通信）提供性能基线。
Library             Collections
Library             OperatingSystem
Library             ${CURDIR}/../../libraries/RdmaPerf.py    WITH NAME    Rdma
Suite Setup         Rdma.套件初始化    ${OUTPUT DIR}    RDMA-PERF-200G
Suite Teardown      Rdma.清理全部
Force Tags          roce    perftest    RDMA-PERF-200G
Test Timeout        15 minutes
Metadata    CASE_ID       RDMA-PERF-200G
Metadata    QA_PROJECT    gpu-nic-qa
Metadata    DUT           p1+p2 R750/CX-6 200G
Metadata    LINK          p1.mlx5_0 -> p2.mlx5_0
Metadata    SW_BUILD      Ubuntu 22.04.5 / perftest 6.23
Metadata    BASELINE      194 Gbps / 3.14 us
Metadata    OPERATOR      性能测试组
Metadata    CASE_KIND     rdma-perf-baseline

*** Variables ***
${SRV_HOST}         10.202.54.106
${CLI_HOST}         10.202.54.107
${SRV_IP}           172.30.11.5
${DEVICE}           mlx5_0
${GID_INDEX}        3
${SSH_USER}         eht

*** Test Cases ***
RDMA-PRE-001 RoCEv2 前置条件核查
    [Tags]    RDMA-PRE-001    precheck    p0    rdma
    [Documentation]    确认两端链路层、协商速率、端口状态、GID 索引与 MTU 均满足 RoCEv2 打流条件， 避免后续性能用例因环境不成立而产生无效数据。
    ${r}=    核查两端前置条件    ${SRV_HOST}    ${CLI_HOST}
    ...    device=mlx5_0
    ...    gid_index=3
    ...    min_mtu=4096
    Rdma.记录用例指标    RDMA-PRE-001    ${r}
    Should Be True    ${r}[link_speed_mbps] >= 200000
    ...    [关键/link_speed_mbps] 两端端口协商速率不低于 200 Gbps（实测 ${r}[link_speed_mbps] Mb/s）
    Should Be True    ${r}[all_active] == True
    ...    [关键/all_active] 两端 RDMA 端口状态均为 ACTIVE（实测 ${r}[all_active] -）
    Should Be True    ${r}[all_gid_ready] == True
    ...    [关键/all_gid_ready] 两端 GID index 3 均为 RoCEv2 IPv4 且地址有效（实测 ${r}[all_gid_ready] -）
    Should Be True    ${r}[mtu] >= 4096
    ...    [重要/mtu] 链路 MTU 不低于 4096，保证大包不被分片（实测 ${r}[mtu] Byte）

RDMA-BW-001 Write 带宽 64KB 单 QP
    [Tags]    RDMA-BW-001    bw    p0    rdma
    [Documentation]    测量单向 RDMA Write 在 64KB 消息下的饱和带宽，作为 200G 链路的基础吞吐基线。
    ${r}=    执行 Perftest 带宽测试    ${SRV_HOST}    ${CLI_HOST}    ${SRV_IP}
    ...    tool=ib_write_bw
    ...    device=${DEVICE}
    ...    gid_index=${GID_INDEX}
    ...    size=65536
    ...    qp=1
    ...    duration=5
    Rdma.记录用例指标    RDMA-BW-001    ${r}
    Should Be True    ${r}[bandwidth_gbps] >= 185
    ...    [关键/bandwidth_gbps] 单向 Write 带宽不低于 185 Gbps（实测基线 194.05 的 95%）（实测 ${r}[bandwidth_gbps] Gbps）

RDMA-BW-002 Send 带宽 64KB 单 QP
    [Tags]    RDMA-BW-002    bw    p1    rdma
    [Documentation]    测量单向 RDMA Send 在 64KB 消息下的饱和带宽，与 Write 结果互相印证， 区分单边写与双边收发两条路径的实现差异。
    ${r}=    执行 Perftest 带宽测试    ${SRV_HOST}    ${CLI_HOST}    ${SRV_IP}
    ...    tool=ib_send_bw
    ...    device=${DEVICE}
    ...    gid_index=${GID_INDEX}
    ...    size=65536
    ...    qp=1
    ...    duration=5
    Rdma.记录用例指标    RDMA-BW-002    ${r}
    Should Be True    ${r}[bandwidth_gbps] >= 185
    ...    [关键/bandwidth_gbps] 单向 Send 带宽不低于 185 Gbps（实测基线 194.09）（实测 ${r}[bandwidth_gbps] Gbps）

RDMA-BW-003 Read 带宽 64KB 单 QP
    [Tags]    RDMA-BW-003    bw    p0    rdma
    [Documentation]    测量单向 RDMA Read 带宽。Read 由请求端主动拉取对端内存，路径与 Write 不同， 是分布式存储类负载的关键指标，本用例用于识别读写路径的性能不对称。
    ${r}=    执行 Perftest 带宽测试    ${SRV_HOST}    ${CLI_HOST}    ${SRV_IP}
    ...    tool=ib_read_bw
    ...    device=${DEVICE}
    ...    gid_index=${GID_INDEX}
    ...    size=65536
    ...    qp=1
    ...    duration=5
    Rdma.记录用例指标    RDMA-BW-003    ${r}
    Should Be True    ${r}[bandwidth_gbps] >= 150
    ...    [关键/bandwidth_gbps] 单向 Read 带宽不低于 150 Gbps（同速率等级应有水平）（实测 ${r}[bandwidth_gbps] Gbps）

RDMA-BW-004 Write 带宽 64KB 4 QP 扩展
    [Tags]    RDMA-BW-004    bw    p1    rdma
    [Documentation]    验证多队列并发下带宽能否随 QP 数保持或提升，确认单 QP 未触达主机侧瓶颈。
    ${r}=    执行 Perftest 带宽测试    ${SRV_HOST}    ${CLI_HOST}    ${SRV_IP}
    ...    tool=ib_write_bw
    ...    device=${DEVICE}
    ...    gid_index=${GID_INDEX}
    ...    size=65536
    ...    qp=4
    ...    duration=5
    Rdma.记录用例指标    RDMA-BW-004    ${r}
    Should Be True    ${r}[bandwidth_gbps] >= 190
    ...    [重要/bandwidth_gbps] 4 QP 聚合带宽不低于 190 Gbps（实测 196.04）（实测 ${r}[bandwidth_gbps] Gbps）

RDMA-BW-005 Write 带宽 64KB 双向
    [Tags]    RDMA-BW-005    bw    p1    rdma
    [Documentation]    验证双向同时收发时的总吞吐，检查链路在全双工压力下的表现。
    ${r}=    执行 Perftest 带宽测试    ${SRV_HOST}    ${CLI_HOST}    ${SRV_IP}
    ...    tool=ib_write_bw
    ...    device=${DEVICE}
    ...    gid_index=${GID_INDEX}
    ...    size=65536
    ...    qp=1
    ...    duration=5
    ...    bidirectional=True
    Rdma.记录用例指标    RDMA-BW-005    ${r}
    Should Be True    ${r}[bandwidth_gbps] >= 190
    ...    [重要/bandwidth_gbps] 双向合计带宽不低于 190 Gbps（实测 198.49）（实测 ${r}[bandwidth_gbps] Gbps）

RDMA-BW-006 消息尺寸带宽扫描
    [Tags]    RDMA-BW-006    scan    p1    rdma
    [Documentation]    逐尺寸测量 Write 带宽，刻画带宽随消息尺寸的变化曲线，识别小包场景的 性能塌陷点，为业务侧消息尺寸选型提供依据。
    @{SIZES}=    Create List    2    64    256    1024    4096    16384    65536    262144    1048576    8388608
    ${r}=    执行消息尺寸扫描    ${SRV_HOST}    ${CLI_HOST}    ${SRV_IP}
    ...    sizes=@{SIZES}
    ...    tool=ib_write_bw
    ...    device=${DEVICE}
    ...    gid_index=${GID_INDEX}
    ...    duration=5
    Rdma.记录用例指标    RDMA-BW-006    ${r}
    Should Be True    ${r}[bw_at_64k] >= 185
    ...    [关键/bw_at_64k] 64KB 尺寸带宽不低于 185 Gbps（实测 ${r}[bw_at_64k] Gbps）
    Should Be True    ${r}[bw_at_1m] >= 150
    ...    [重要/bw_at_1m] 1MB 尺寸带宽不低于 150 Gbps（实测 ${r}[bw_at_1m] Gbps）

RDMA-LAT-001 Write 时延 2B 单 QP
    [Tags]    RDMA-LAT-001    lat    p0    rdma
    [Documentation]    测量最小消息下的 Write 往返时延，反映网卡与交换机转发路径的固有开销。
    ${r}=    执行 Perftest 延迟测试    ${SRV_HOST}    ${CLI_HOST}    ${SRV_IP}
    ...    tool=ib_write_lat
    ...    device=${DEVICE}
    ...    gid_index=${GID_INDEX}
    ...    size=2
    ...    duration=5
    Rdma.记录用例指标    RDMA-LAT-001    ${r}
    Should Be True    ${r}[latency_us] <= 4.0
    ...    [关键/latency_us] Write 平均时延不高于 4.0 us（实测 3.14）（实测 ${r}[latency_us] us）

RDMA-LAT-002 Read 时延 2B 单 QP
    [Tags]    RDMA-LAT-002    lat    p1    rdma
    [Documentation]    测量最小消息下的 Read 往返时延，与 Write 时延对比可判断读写路径开销差异。
    ${r}=    执行 Perftest 延迟测试    ${SRV_HOST}    ${CLI_HOST}    ${SRV_IP}
    ...    tool=ib_read_lat
    ...    device=${DEVICE}
    ...    gid_index=${GID_INDEX}
    ...    size=2
    ...    duration=5
    Rdma.记录用例指标    RDMA-LAT-002    ${r}
    Should Be True    ${r}[latency_us] <= 7.0
    ...    [重要/latency_us] Read 平均时延不高于 7.0 us（实测 5.46）（实测 ${r}[latency_us] us）

RDMA-LAT-003 Send 时延 2B 单 QP
    [Tags]    RDMA-LAT-003    lat    p1    rdma
    [Documentation]    测量最小消息下的 Send 往返时延，覆盖双边通信路径。
    ${r}=    执行 Perftest 延迟测试    ${SRV_HOST}    ${CLI_HOST}    ${SRV_IP}
    ...    tool=ib_send_lat
    ...    device=${DEVICE}
    ...    gid_index=${GID_INDEX}
    ...    size=2
    ...    duration=5
    Rdma.记录用例指标    RDMA-LAT-003    ${r}
    Should Be True    ${r}[latency_us] <= 4.5
    ...    [重要/latency_us] Send 平均时延不高于 4.5 us（实测 3.45）（实测 ${r}[latency_us] us）

RDMA-STA-001 Write 带宽持续稳定性
    [Tags]    RDMA-STA-001    stability    p2    rdma
    [Documentation]    连续多轮定长打流，用轮间离散度衡量带宽稳定性，识别温升、降频或链路偶发 丢包导致的长时性能劣化。
    ${r}=    执行稳定性测试    ${SRV_HOST}    ${CLI_HOST}    ${SRV_IP}
    ...    tool=ib_write_bw
    ...    device=${DEVICE}
    ...    gid_index=${GID_INDEX}
    ...    size=65536
    ...    rounds=3
    ...    duration=30
    Rdma.记录用例指标    RDMA-STA-001    ${r}
    Should Be True    ${r}[bandwidth_gbps] >= 185
    ...    [关键/bandwidth_gbps] 三轮平均带宽不低于 185 Gbps（实测 ${r}[bandwidth_gbps] Gbps）
    Should Be True    ${r}[spread_pct] <= 5.0
    ...    [重要/spread_pct] 轮间离散度不高于 5%，反映无持续劣化（实测 ${r}[spread_pct] %）
