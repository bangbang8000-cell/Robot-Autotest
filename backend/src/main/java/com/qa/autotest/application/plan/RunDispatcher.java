package com.qa.autotest.application.plan;

/**
 * 计划触发后的运行派发端口（PLN-01）。
 *
 * <p>调度器只负责「到点、判重、建 run」，真正的 pabot 执行必须串行；这里把执行派发抽成端口，
 * 生产实现是单线程 TaskExecutor（max=1），单测用内存实现即可，不碰真实进程。
 */
public interface RunDispatcher {

    /**
     * 提交一次已创建的 run 执行。实现必须保证串行（max=1）。
     *
     * @param projectId 租户上下文（执行线程需用它 runAs 包裹）
     * @param planId    触发计划；手工触发也带，便于日志反查
     * @param runId     RUN_ID 字符串
     * @param task      实际执行动作（内部调用 RunExecutionService）
     */
    void submit(long projectId, Long planId, String runId, Runnable task);
}
