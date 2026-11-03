*** Settings ***
Library           SSHLibrary
Library           OperatingSystem
Library           Collections
Library           ../libraries/metrics_collector.py
Documentation     公共资源: 服务器清单、登录凭据、可复用关键字 + 指标采集
...               学习要点: 被所有套件 Resource 引用, 实现变量与关键字复用
...               整改: 新增 Ping 统计/指标登记/主机名登记, 数值直出测试消息与 metrics.json
...               整改: 统一"执行命令并记录原始输出"入口, 所有远程命令都留命令原文+原始输出

*** Variables ***
# ========== 登录凭据 (先定义, 后面的变量才能引用) ==========
${INSPUR_USER}    inspur
${INSPUR_PWD}     Eht_lab2026!
${EHT_USER}       eht
${EHT_PWD}        Eht@2025

# ========== 服务器清单 (外部 IP, 测试机可直连) ==========
@{GPU_SERVERS}      10.202.54.101    10.202.54.102    10.202.54.103    10.202.54.104
@{STORAGE_SERVERS}  10.202.54.105
@{R750_SERVERS}     10.202.54.106    10.202.54.107    10.202.54.108

# ========== 内网 IP (测试机不可达, 需经跳板探测) ==========
@{INTERNAL_IPS}     172.31.20.101    172.31.20.102    172.31.20.103    172.31.20.104
...                 172.31.20.105    172.31.20.106    172.31.20.107    172.31.20.108

# ========== 跳板服务器 (内网互通性从这里发起) ==========
${JUMP_HOST}      10.202.54.101
${JUMP_USER}      ${INSPUR_USER}
${JUMP_PWD}       ${INSPUR_PWD}

# ========== 检查阈值 (改这里即可调整全局标准) ==========
${LOAD_THRESHOLD}     16
${MEM_THRESHOLD}      90
${DISK_THRESHOLD}     90
${SSH_TIMEOUT}        10s
${PING_COUNT}         2

*** Keywords ***
执行命令并记录原始输出
    [Documentation]    在当前已登录的 SSH 会话执行命令, 把"命令原文 + 命令原始输出"完整写进日志。
    ...                [Arguments] cmd=要执行的命令, label=可选的场景标签(如目标 IP / 采集项名)
    ...                所有远程命令都必须走这个入口, 保证 log.html 与平台 Detailed Report 可回溯
    [Arguments]    ${cmd}    ${label}=
    IF    $label
        Log    [${label}] 执行命令: ${cmd}
    ELSE
        Log    执行命令: ${cmd}
    END
    ${out}=    Execute Command    ${cmd}
    Log    命令原始输出:\n${out}
    RETURN    ${out}

SSH 执行命令
    [Documentation]    登录服务器执行单条命令并返回输出 (自带 命令原文+原始输出 记录)
    [Arguments]    ${ip}    ${user}    ${pwd}    ${cmd}
    Open Connection    ${ip}    timeout=${SSH_TIMEOUT}
    Login    ${user}    ${pwd}
    ${out}=    执行命令并记录原始输出    ${cmd}    ${ip}
    Close Connection
    RETURN    ${out}

SSH 执行多条命令
    [Documentation]    单次登录执行多条命令, 逐条记录 命令原文+原始输出, 返回输出列表
    ...                [Arguments] cmds=命令列表 (Create List 传入), 返回与 cmds 等长的输出列表
    [Arguments]    ${ip}    ${user}    ${pwd}    ${cmds}
    Open Connection    ${ip}    timeout=${SSH_TIMEOUT}
    Login    ${user}    ${pwd}
    ${outs}=    Create List
    FOR    ${cmd}    IN    @{cmds}
        ${out}=    执行命令并记录原始输出    ${cmd}    ${ip}
        Append To List    ${outs}    ${out}
    END
    Close Connection
    RETURN    ${outs}

采集资源指标
    [Documentation]    单次 SSH 会话采集 负载/内存/磁盘, 逐条记录原始输出, 返回数值并登记 metrics.json
    [Arguments]    ${ip}    ${user}    ${pwd}
    ${cmds}=    Create List
    ...    awk '{print $1}' /proc/loadavg
    ...    free | awk 'NR==2{printf "%.1f", $3/$2*100}'
    ...    df -P / | awk 'NR==2{gsub("%","");print $5}'
    ${outs}=    SSH 执行多条命令    ${ip}    ${user}    ${pwd}    ${cmds}
    ${load}=    Set Variable    ${outs}[0]
    ${mem}=     Set Variable    ${outs}[1]
    ${disk}=    Set Variable    ${outs}[2]
    ${load_f}=    Evaluate    float($load.strip() or 0)
    ${mem_f}=     Evaluate    float($mem.strip() or 0)
    ${disk_f}=    Evaluate    float($disk.strip() or 0)
    登记指标    metric=cpu_load_1m    value=${load_f}    target=${ip}    threshold=<${LOAD_THRESHOLD}
    登记指标    metric=mem_used_pct    value=${mem_f}    unit=%    target=${ip}    threshold=<${MEM_THRESHOLD}
    登记指标    metric=root_disk_pct    value=${disk_f}    unit=%    target=${ip}    threshold=<${DISK_THRESHOLD}
    RETURN    ${load_f}    ${mem_f}    ${disk_f}

采集 GPU 指标
    [Documentation]    nvidia-smi 采集每卡 利用率/显存/温度 并登记; 无 GPU 返回空串(不判失败)
    [Arguments]    ${ip}    ${user}    ${pwd}
    ${cmd}=    Set Variable
    ...    nvidia-smi --query-gpu=index,name,utilization.gpu,memory.used,memory.total,temperature.gpu --format=csv,noheader,nounits
    ${out}=    SSH 执行命令    ${ip}    ${user}    ${pwd}    ${cmd}
    ${summary}=    登记硬件指标    ${out}    ${ip}
    RETURN    ${summary}

主机名登记
    [Documentation]    采集主机名并作为文本指标登记, 返回主机名
    [Arguments]    ${ip}    ${user}    ${pwd}
    ${out}=    SSH 执行命令    ${ip}    ${user}    ${pwd}    hostname
    登记指标    metric=hostname    value=${out.strip()}    target=${ip}
    RETURN    ${out.strip()}

Ping From Local
    [Documentation]    从测试机 ping 目标, 返回退出码 (0=可达); 原始输出同样入日志
    [Arguments]    ${ip}
    Log    执行命令: ping -c ${PING_COUNT} -W 2 ${ip} [本机]
    ${rc}    ${out}=    Run And Return Rc And Output    ping -c ${PING_COUNT} -W 2 ${ip}
    Log    命令原始输出:\n${out}
    RETURN    ${rc}

Ping 统计 (从测试机)
    [Documentation]    本机 ping 并解析 丢包率/延迟, 登记 metrics.json, 返回 丢包率% 平均延迟ms
    [Arguments]    ${ip}
    Log    执行命令: ping -c ${PING_COUNT} -W 2 ${ip} [本机]
    ${rc}    ${out}=    Run And Return Rc And Output    ping -c ${PING_COUNT} -W 2 ${ip}
    Log    命令原始输出:\n${out}
    ${loss}    ${avg}=    解析 Ping 输出    ${out}
    登记指标    metric=ping_loss_pct    value=${loss}    unit=%    target=${ip}    threshold=<100
    登记指标    metric=ping_avg_ms    value=${avg}    unit=ms    target=${ip}    threshold=<50
    RETURN    ${loss}    ${avg}

Ping 统计 (经跳板)
    [Documentation]    在已登录的跳板 SSH 会话内 ping 内网 IP 并解析 (需先 Open Connection+Login)
    [Arguments]    ${ip}
    ${out}=    执行命令并记录原始输出    ping -c ${PING_COUNT} -W 2 ${ip}    跳板 ping ${ip}
    ${loss}    ${avg}=    解析 Ping 输出    ${out}
    登记指标    metric=ping_loss_pct    value=${loss}    unit=%    target=${ip}    threshold=<100
    登记指标    metric=ping_avg_ms    value=${avg}    unit=ms    target=${ip}    threshold=<50
    RETURN    ${loss}    ${avg}
