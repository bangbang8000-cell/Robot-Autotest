*** Settings ***
Documentation     GPU 业务服务器健康检查 (GPU-01 ~ GPU-04)
...               整改: 指标登记 metrics.json + 测试消息带数值
Resource          ../resources/common.robot

*** Test Cases ***
GPU-01 (10.202.54.101) SSH 登录与主机名
    [Tags]    health    ssh    gpu
    ${hn}=    主机名登记    10.202.54.101    ${INSPUR_USER}    ${INSPUR_PWD}
    Should Not Be Empty    ${hn}    msg=SSH 登录或执行失败
    Set Test Message    GPU-01 主机名: ${hn}    append=True

GPU-01 (10.202.54.101) 系统资源检查
    [Documentation]    学习要点: 采集关键字返回数值并登记; 断言阈值参数化
    [Tags]    health    resource    gpu
    ${load}    ${mem}    ${disk}=    采集资源指标    10.202.54.101    ${INSPUR_USER}    ${INSPUR_PWD}
    Should Be True    $load < ${LOAD_THRESHOLD}    msg=CPU 负载过高: ${load}
    Should Be True    $mem < ${MEM_THRESHOLD}      msg=内存使用率过高: ${mem}%
    Should Be True    $disk < ${DISK_THRESHOLD}    msg=根分区使用率过高: ${disk}%
    Set Test Message    GPU-01 资源: 负载=${load} 内存=${mem}% 磁盘=${disk}%    append=True

GPU-01 (10.202.54.101) GPU 状态检查
    [Tags]    health    gpu    info
    ${summary}=    采集 GPU 指标    10.202.54.101    ${INSPUR_USER}    ${INSPUR_PWD}
    IF    $summary
        Log    GPU 状态: ${summary}
        Set Test Message    ${summary}    append=True
    ELSE
        Log    未检测到 nvidia-smi 或无 GPU (不判失败)
    END

GPU-02 (10.202.54.102) SSH 登录与主机名
    [Tags]    health    ssh    gpu
    ${hn}=    主机名登记    10.202.54.102    ${INSPUR_USER}    ${INSPUR_PWD}
    Should Not Be Empty    ${hn}    msg=SSH 登录或执行失败
    Set Test Message    GPU-02 主机名: ${hn}    append=True

GPU-02 (10.202.54.102) 系统资源检查
    [Tags]    health    resource    gpu
    ${load}    ${mem}    ${disk}=    采集资源指标    10.202.54.102    ${INSPUR_USER}    ${INSPUR_PWD}
    Should Be True    $load < ${LOAD_THRESHOLD}    msg=CPU 负载过高: ${load}
    Should Be True    $mem < ${MEM_THRESHOLD}      msg=内存使用率过高: ${mem}%
    Should Be True    $disk < ${DISK_THRESHOLD}    msg=根分区使用率过高: ${disk}%
    Set Test Message    GPU-02 资源: 负载=${load} 内存=${mem}% 磁盘=${disk}%    append=True

GPU-02 (10.202.54.102) GPU 状态检查
    [Tags]    health    gpu    info
    ${summary}=    采集 GPU 指标    10.202.54.102    ${INSPUR_USER}    ${INSPUR_PWD}
    IF    $summary
        Log    GPU 状态: ${summary}
        Set Test Message    ${summary}    append=True
    ELSE
        Log    未检测到 nvidia-smi 或无 GPU (不判失败)
    END

GPU-03 (10.202.54.103) SSH 登录与主机名
    [Tags]    health    ssh    gpu
    ${hn}=    主机名登记    10.202.54.103    ${INSPUR_USER}    ${INSPUR_PWD}
    Should Not Be Empty    ${hn}    msg=SSH 登录或执行失败
    Set Test Message    GPU-03 主机名: ${hn}    append=True

GPU-03 (10.202.54.103) 系统资源检查
    [Tags]    health    resource    gpu
    ${load}    ${mem}    ${disk}=    采集资源指标    10.202.54.103    ${INSPUR_USER}    ${INSPUR_PWD}
    Should Be True    $load < ${LOAD_THRESHOLD}    msg=CPU 负载过高: ${load}
    Should Be True    $mem < ${MEM_THRESHOLD}      msg=内存使用率过高: ${mem}%
    Should Be True    $disk < ${DISK_THRESHOLD}    msg=根分区使用率过高: ${disk}%
    Set Test Message    GPU-03 资源: 负载=${load} 内存=${mem}% 磁盘=${disk}%    append=True

GPU-03 (10.202.54.103) GPU 状态检查
    [Tags]    health    gpu    info
    ${summary}=    采集 GPU 指标    10.202.54.103    ${INSPUR_USER}    ${INSPUR_PWD}
    IF    $summary
        Log    GPU 状态: ${summary}
        Set Test Message    ${summary}    append=True
    ELSE
        Log    未检测到 nvidia-smi 或无 GPU (不判失败)
    END

GPU-04 (10.202.54.104) SSH 登录与主机名
    [Tags]    health    ssh    gpu
    ${hn}=    主机名登记    10.202.54.104    ${INSPUR_USER}    ${INSPUR_PWD}
    Should Not Be Empty    ${hn}    msg=SSH 登录或执行失败
    Set Test Message    GPU-04 主机名: ${hn}    append=True

GPU-04 (10.202.54.104) 系统资源检查
    [Tags]    health    resource    gpu
    ${load}    ${mem}    ${disk}=    采集资源指标    10.202.54.104    ${INSPUR_USER}    ${INSPUR_PWD}
    Should Be True    $load < ${LOAD_THRESHOLD}    msg=CPU 负载过高: ${load}
    Should Be True    $mem < ${MEM_THRESHOLD}      msg=内存使用率过高: ${mem}%
    Should Be True    $disk < ${DISK_THRESHOLD}    msg=根分区使用率过高: ${disk}%
    Set Test Message    GPU-04 资源: 负载=${load} 内存=${mem}% 磁盘=${disk}%    append=True

GPU-04 (10.202.54.104) GPU 状态检查
    [Tags]    health    gpu    info
    ${summary}=    采集 GPU 指标    10.202.54.104    ${INSPUR_USER}    ${INSPUR_PWD}
    IF    $summary
        Log    GPU 状态: ${summary}
        Set Test Message    ${summary}    append=True
    ELSE
        Log    未检测到 nvidia-smi 或无 GPU (不判失败)
    END
