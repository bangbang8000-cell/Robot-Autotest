package com.qa.autotest.application.execution;

/**
 * 运行派发端口（EXEC-01）：从计划域提升为执行域公共组件。
 *
 * <p>调度器只负责「到点、判重、建 run」，真正的 pabot 执行统一经此端口排队；
 * 生产实现按项目分道、单项目并发上限 {@code execution.max-parallel}（EXEC-10），
 * 单测用内存实现即可，不碰真实进程。
 *
 * <p><b>语义约定</b>（FR-v0.4-03 / 05）：同一项目内按 {@code priority} 降序、再按提交先后排队，
 * 达到并发上限只排队不拒绝；不同项目互不阻塞；同一个 {@code runId} 只允许一次在途派发
 * （重复提交直接丢弃并告警），避免幂等重放把同一 run 执行两遍。
 */
public interface RunDispatcher {

    /**
     * 提交一次已创建的 run 执行。
     *
     * @param projectId 租户上下文（执行线程需用它 runAs 包裹）
     * @param planId    触发计划；手工/CI 触发为 null，便于日志反查
     * @param runId     RUN_ID 字符串，同时作为在途去重键
     * @param priority  优先级 0~9，数值越大越先执行
     * @param task      实际执行动作（内部调用 RunExecutionService）
     */
    void submit(long projectId, Long planId, String runId, int priority, Runnable task);
}
