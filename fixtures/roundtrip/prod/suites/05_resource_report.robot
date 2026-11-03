*** Settings ***
Documentation     资源指标汇总与完整性检查 (01~04 采集完后的收口)
...               作用: 回读本次运行的 metrics.json, 在报告中给出全局指标视图
...               注意: 按文件名排序, 本套件在 01~04 之后执行, 因此能读到完整指标
Resource          ../resources/common.robot

*** Variables ***
# 期望至少采集到的指标类别 (缺任何一类说明采集链路断了)
@{EXPECTED_METRICS}    hostname    cpu_load_1m    mem_used_pct    root_disk_pct    ping_loss_pct    ping_avg_ms

*** Test Cases ***
资源指标汇总 (本次运行)
    [Documentation]    学习要点: 运行期产物 ${OUTPUT_DIR}/metrics.json 可被后续套件回读,
    ...                形成 "采集 → 登记 → 汇总" 闭环; 汇总结果直接进报告消息
    [Tags]    report    metrics    summary
    ${path}=    Set Variable    ${OUTPUT_DIR}/metrics.json
    ${ok}=    Run Keyword And Return Status    OperatingSystem.File Should Exist    ${path}
    Should Be True    ${ok}    msg=metrics.json 未生成: 检查 libraries/metrics_collector.py 是否被正确引用
    ${summary}=    汇总指标    ${path}
    Log    指标文件: ${path}
    Set Test Message    ${summary}    append=True

指标覆盖完整性检查
    [Documentation]    学习要点: 用数据完整性断言防止"用例全绿但指标静默丢失";
    ...                这类断言失败时报告里会直接列出缺失的指标名
    [Tags]    report    metrics    quality
    ${path}=    Set Variable    ${OUTPUT_DIR}/metrics.json
    ${ok}=    Run Keyword And Return Status    OperatingSystem.File Should Exist    ${path}
    Should Be True    ${ok}    msg=metrics.json 未生成
    ${missing}=    检查指标覆盖    ${EXPECTED_METRICS}    ${path}
    Should Be Empty    ${missing}    msg=以下指标未采集到: ${missing}
    ${names}=    指标名称列表    ${path}
    ${cnt}=    Get Length    ${names}
    ${joined}=    Evaluate    " / ".join($names)
    Set Test Message    覆盖 ${cnt} 类指标全部命中: ${joined}    append=True
