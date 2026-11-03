*** Settings ***
Documentation     R750 业务服务器健康检查 (R750-01 ~ R750-03, eht 账号)
...               整改: 指标登记 metrics.json + 测试消息带数值
Resource          ../resources/common.robot

*** Test Cases ***
R750-01 (10.202.54.106) SSH 登录与主机名
    [Tags]    health    ssh    r750
    ${hn}=    主机名登记    10.202.54.106    ${EHT_USER}    ${EHT_PWD}
    Should Not Be Empty    ${hn}    msg=SSH 登录或执行失败
    Set Test Message    R750-01 主机名: ${hn}    append=True

R750-01 (10.202.54.106) 系统资源检查
    [Tags]    health    resource    r750
    ${load}    ${mem}    ${disk}=    采集资源指标    10.202.54.106    ${EHT_USER}    ${EHT_PWD}
    Should Be True    $load < ${LOAD_THRESHOLD}    msg=CPU 负载过高: ${load}
    Should Be True    $mem < ${MEM_THRESHOLD}      msg=内存使用率过高: ${mem}%
    Should Be True    $disk < ${DISK_THRESHOLD}    msg=根分区使用率过高: ${disk}%
    Set Test Message    R750-01 资源: 负载=${load} 内存=${mem}% 磁盘=${disk}%    append=True

R750-01 (10.202.54.106) 磁盘挂载信息
    [Tags]    health    r750    info
    ${out}=    SSH 执行命令    10.202.54.106    ${EHT_USER}    ${EHT_PWD}
    ...    timeout 15 df -h -x nfs -x nfs4 -x cifs | tail -n +2 | awk '{print $1, $2, $5, $6}'; echo '--- 网络挂载 ---'; mount | grep -iE 'nfs|cifs' | awk '{print $1, $3}'
    Log    挂载点 (设备/容量/使用率/挂载目录):\n${out.strip()}
    ${mount}=    汇总挂载    ${out}
    Set Test Message    ${mount}    append=True

R750-02 (10.202.54.107) SSH 登录与主机名
    [Tags]    health    ssh    r750
    ${hn}=    主机名登记    10.202.54.107    ${EHT_USER}    ${EHT_PWD}
    Should Not Be Empty    ${hn}    msg=SSH 登录或执行失败
    Set Test Message    R750-02 主机名: ${hn}    append=True

R750-02 (10.202.54.107) 系统资源检查
    [Tags]    health    resource    r750
    ${load}    ${mem}    ${disk}=    采集资源指标    10.202.54.107    ${EHT_USER}    ${EHT_PWD}
    Should Be True    $load < ${LOAD_THRESHOLD}    msg=CPU 负载过高: ${load}
    Should Be True    $mem < ${MEM_THRESHOLD}      msg=内存使用率过高: ${mem}%
    Should Be True    $disk < ${DISK_THRESHOLD}    msg=根分区使用率过高: ${disk}%
    Set Test Message    R750-02 资源: 负载=${load} 内存=${mem}% 磁盘=${disk}%    append=True

R750-02 (10.202.54.107) 磁盘挂载信息
    [Tags]    health    r750    info
    ${out}=    SSH 执行命令    10.202.54.107    ${EHT_USER}    ${EHT_PWD}
    ...    timeout 15 df -h -x nfs -x nfs4 -x cifs | tail -n +2 | awk '{print $1, $2, $5, $6}'; echo '--- 网络挂载 ---'; mount | grep -iE 'nfs|cifs' | awk '{print $1, $3}'
    Log    挂载点 (设备/容量/使用率/挂载目录):\n${out.strip()}
    ${mount}=    汇总挂载    ${out}
    Set Test Message    ${mount}    append=True

R750-03 (10.202.54.108) SSH 登录与主机名
    [Tags]    health    ssh    r750
    ${hn}=    主机名登记    10.202.54.108    ${EHT_USER}    ${EHT_PWD}
    Should Not Be Empty    ${hn}    msg=SSH 登录或执行失败
    Set Test Message    R750-03 主机名: ${hn}    append=True

R750-03 (10.202.54.108) 系统资源检查
    [Tags]    health    resource    r750
    ${load}    ${mem}    ${disk}=    采集资源指标    10.202.54.108    ${EHT_USER}    ${EHT_PWD}
    Should Be True    $load < ${LOAD_THRESHOLD}    msg=CPU 负载过高: ${load}
    Should Be True    $mem < ${MEM_THRESHOLD}      msg=内存使用率过高: ${mem}%
    Should Be True    $disk < ${DISK_THRESHOLD}    msg=根分区使用率过高: ${disk}%
    Set Test Message    R750-03 资源: 负载=${load} 内存=${mem}% 磁盘=${disk}%    append=True

R750-03 (10.202.54.108) 磁盘挂载信息
    [Tags]    health    r750    info
    ${out}=    SSH 执行命令    10.202.54.108    ${EHT_USER}    ${EHT_PWD}
    ...    timeout 15 df -h -x nfs -x nfs4 -x cifs | tail -n +2 | awk '{print $1, $2, $5, $6}'; echo '--- 网络挂载 ---'; mount | grep -iE 'nfs|cifs' | awk '{print $1, $3}'
    Log    挂载点 (设备/容量/使用率/挂载目录):\n${out.strip()}
    ${mount}=    汇总挂载    ${out}
    Set Test Message    ${mount}    append=True
