package com.qa.autotest.domain.report;

import java.time.Instant;

/**
 * 保留策略的候选运行（RPT-06）：一次运行及其产物占用、三类保护标记。
 *
 * <p>数据库记录与产物文件是两件事：这里只描述「产物是否可清理」，
 * 清理永远不删除 {@code run} / {@code test_result} 行。
 *
 * @param runDbId        run.id（产物外键）
 * @param runId          RUN_ID
 * @param startedAt      开始时间（null 视为最旧）
 * @param bytes          可清理产物的占用字节数（仅 availability='ok'）
 * @param artifactCount  可清理产物条数（0 表示没有文件可删）
 * @param pinned         是否被手动标记保留（run_artifact.pinned=1）
 * @param p0Evidence     note 是否标记为 p0_evidence
 * @param exported       是否已有导出记录（export_record）
 */
public record RetentionCandidate(long runDbId,
                                 String runId,
                                 Instant startedAt,
                                 long bytes,
                                 int artifactCount,
                                 boolean pinned,
                                 boolean p0Evidence,
                                 boolean exported) {

    /** 有可清理的产物文件。 */
    public boolean hasArtifacts() {
        return artifactCount > 0;
    }

    /** 三类保护中的任意一类命中即不清理。 */
    public boolean protectedRun() {
        return exported || pinned || p0Evidence;
    }
}
