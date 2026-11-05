package com.qa.autotest.application.repository;

import com.qa.autotest.domain.execution.RunState;

import java.time.Instant;

/**
 * 上一次尝试的可读记录（FR-v0.4-29）。
 *
 * <p>{@code run.retry_of_run_id} 只存了一个编号，页面拿它回答不了「为什么重试」；
 * 这里按它自连接 {@code run} 表，把原运行的状态与失败结论一并带出来。
 *
 * @param runId          上一次尝试的 run_id（本次运行的 {@code retry_of_run_id}）
 * @param state          上一次尝试的终态
 * @param failureClass   失败分类（如 {@code ENV} / {@code CASE}），成功时为 null
 * @param failureReason  失败原因（结构化短语），成功时为 null
 * @param failureMessage 失败详情，成功时为 null
 * @param finishedAt     上一次尝试的结束时间
 */
public record RunRetryOrigin(String runId,
                             RunState state,
                             String failureClass,
                             String failureReason,
                             String failureMessage,
                             Instant finishedAt) {
}
