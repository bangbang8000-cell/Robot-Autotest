package com.qa.autotest.application.execution;

/**
 * 执行队列状态回写端口（EXEC-10 残余）。
 *
 * <p>派发器内存里的队列视图只对「本次进程」有效，重启即丢；落到 {@code execution_queue}
 * 表才让「排队中 / 执行中 / 已完成」跨进程可见（`V1__baseline.sql` 已有该表与
 * {@code queue_state} 字段，之前只有建表没有写入方）。
 *
 * <p>所有方法默认空实现：单测内存仓储与不需要队列持久化的场景不用改动；
 * JDBC 实现必须覆盖。
 */
public interface RunQueueRepository {

    /** 入队：写入 {@code waiting}，同一 job 重复提交按覆盖处理（幂等重放不报错）。 */
    default void enqueue(long projectId, String runId, int priority) {
    }

    /** 开始执行：{@code waiting -> running} 并记 {@code started_at}。 */
    default void markRunning(long projectId, String runId) {
    }

    /** 执行结束：{@code running -> done}；失败/取消同样落 {@code done}，原因由 run 状态承载。 */
    default void markDone(long projectId, String runId) {
    }
}
