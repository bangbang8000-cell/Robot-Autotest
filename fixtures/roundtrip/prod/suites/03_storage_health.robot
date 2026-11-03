*** Settings ***
Documentation     存储服务器健康检查 (Storage-01)
...               整改: 指标登记 metrics.json + 测试消息带数值
Resource          ../resources/common.robot

*** Test Cases ***
Storage-01 (10.202.54.105) SSH 登录与主机名
    [Tags]    health    ssh    storage
    ${hn}=    主机名登记    10.202.54.105    ${INSPUR_USER}    ${INSPUR_PWD}
    Should Not Be Empty    ${hn}    msg=SSH 登录或执行失败
    Set Test Message    Storage-01 主机名: ${hn}    append=True

Storage-01 (10.202.54.105) 系统资源检查
    [Documentation]    学习要点: 采集与断言分离, 阈值改 common.robot 即全局生效
    [Tags]    health    resource    storage
    ${load}    ${mem}    ${disk}=    采集资源指标    10.202.54.105    ${INSPUR_USER}    ${INSPUR_PWD}
    Should Be True    $load < ${LOAD_THRESHOLD}    msg=CPU 负载过高: ${load}
    Should Be True    $mem < ${MEM_THRESHOLD}      msg=内存使用率过高: ${mem}%
    Should Be True    $disk < ${DISK_THRESHOLD}    msg=根分区使用率过高: ${disk}%
    Set Test Message    Storage-01 资源: 负载=${load} 内存=${mem}% 磁盘=${disk}%    append=True

Storage-01 (10.202.54.105) 磁盘挂载信息
    [Documentation]    学习要点: 信息采集型用例, 只记录不判失败
    [Tags]    health    storage    info
    ${out}=    SSH 执行命令    10.202.54.105    ${INSPUR_USER}    ${INSPUR_PWD}
    ...    timeout 15 df -h -x nfs -x nfs4 -x cifs | tail -n +2 | awk '{print $1, $2, $5, $6}'; echo '--- 网络挂载 ---'; mount | grep -iE 'nfs|cifs' | awk '{print $1, $3}'
    Log    挂载点 (设备/容量/使用率/挂载目录):\n${out.strip()}
    ${mount}=    汇总挂载    ${out}
    Set Test Message    ${mount}    append=True
