package com.qa.autotest.application.repository;

import java.util.List;

/**
 * 失败行读取（ORCH-03，v2.1）。
 *
 * <p>只读、不建表：簇是**视图**，不落库。理由是簇键由归一化规则决定，
 * 规则一改历史簇就该重算——落库会让「旧规则算出的簇」与「新规则算出的簇」并存，
 * 反而说不清哪个对。
 */
public interface FailureResultRepository {

    /**
     * 读一轮里所有失败用例行。
     *
     * @param projectId 项目（租户条件）
     * @param runId     运行的业务 ID（{@code run.run_id}，不是自增主键）
     * @return 按 {@code test_result.id} 升序；没有失败时返回空列表
     */
    List<FailureRow> failedRows(long projectId, String runId);

    /** 一轮的判定上下文：run 表上的整体状态与失败分类，用于「整轮被判成什么」。 */
    RunFailureContext runContext(long projectId, String runId);

    /**
     * 一条失败用例。
     *
     * @param failureMsg 用例级失败文本（{@code test_result.failure_msg}）—— 簇键唯一来源
     * @param caseCode   用例台账编号，重跑与对外报告都用它
     */
    record FailureRow(long resultId, Long caseId, String caseCode, String caseTitle,
                      String verdict, String failureMsg) {
    }

    /** 运行级上下文；找不到运行时各字段为 null。 */
    record RunFailureContext(String runId, String runStatus, String failureReason,
                             String failureClass) {
    }
}
