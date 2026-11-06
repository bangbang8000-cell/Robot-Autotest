package com.qa.autotest.application.repository;

import com.qa.autotest.domain.report.ReportSnapshot;
import com.qa.autotest.domain.report.ReportState;
import com.qa.autotest.domain.report.TestReport;
import com.qa.autotest.domain.report.TestReportExport;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 测试报告仓储（RPTX-01）。渲染路径只读本仓储，不回查 run 表。 */
public interface TestReportRepository {

    Optional<TestReport> find(long id);

    List<TestReport> listByProject(long projectId);

    int maxVersion(long projectId, String runId);

    long insert(long projectId, String runId, String title, int version, ReportSnapshot snapshot);

    void freeze(long id, ReportSnapshot snapshot);

    void changeState(long id, ReportState state);

    long insertExport(long reportId, String format, String fileName, long sizeBytes, String watermark);

    List<TestReportExport> exports(long reportId);

    int purgeExportsBefore(Instant cutoff);

    void audit(long reportId, String action, String fromState, String toState, Long actorId);
}
