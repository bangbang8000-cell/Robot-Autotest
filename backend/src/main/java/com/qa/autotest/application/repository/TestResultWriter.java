package com.qa.autotest.application.repository;

/**
 * 用例结果写入。runDbId 是 run 表主键（不是 RUN_ID 字符串）。
 */
public interface TestResultWriter {

    void write(long projectId, long runDbId, String caseKey, String verdict,
               long durationMs, String failureMsg, int roundNo);
}
