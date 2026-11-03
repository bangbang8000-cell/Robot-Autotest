package com.qa.autotest.application.repository;

import com.qa.autotest.domain.report.RetentionArtifact;
import com.qa.autotest.domain.report.RetentionCandidate;
import java.util.List;

/**
 * 保留策略仓储端口（RPT-06）。
 *
 * <p>只提供「查候选 / 查产物行 / 标记 purged / 查项目总占用」四类能力，
 * <b>不提供任何删除 run / test_result 的方法</b>——库记录必须永久保留。
 */
public interface RetentionRepository {

    /** 项目下的运行候选（含产物占用与三类保护标记），按开始时间倒序。 */
    List<RetentionCandidate> listCandidates(long projectId);

    /** 某运行的待清理产物行（仅 availability='ok'）。 */
    List<RetentionArtifact> listArtifacts(long projectId, long runDbId);

    /** 项目当前仍可读产物的总占用字节数（仅 availability='ok'）。 */
    long totalBytes(long projectId);

    /** 把指定产物行标记为 purged（文件已删除后调用）。 */
    void markPurged(long projectId, long runDbId, List<Long> artifactIds);

    /** 启用状态的项目 id（定时清理遍历用）。 */
    List<Long> listProjectIds();
}
