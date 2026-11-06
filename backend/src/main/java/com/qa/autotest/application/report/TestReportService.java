package com.qa.autotest.application.report;

import com.qa.autotest.application.repository.TestReportRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.report.ReportSnapshot;
import com.qa.autotest.domain.report.ReportState;
import com.qa.autotest.domain.report.TestReport;
import com.qa.autotest.domain.report.TestReportExport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 测试报告服务（RPTX-01 状态机 / RPTX-02 冻结 / RPTX-05 导出与保留）。 */
@Service
public class TestReportService {

    private final TestReportRepository repository;
    private final ReportSnapshotService snapshots;
    private final ReportViewRenderer renderer;
    private final ReportExportService exports;
    private final ReportQueryService queries;

    public TestReportService(TestReportRepository repository, ReportSnapshotService snapshots,
                             ReportViewRenderer renderer, ReportExportService exports,
                             ReportQueryService queries) {
        this.repository = repository;
        this.snapshots = snapshots;
        this.renderer = renderer;
        this.exports = exports;
        this.queries = queries;
    }

    public TestReport find(long id) {
        return repository.find(id).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "测试报告不存在: " + id));
    }

    public List<TestReport> list(long projectId) {
        return repository.listByProject(projectId);
    }

    public TestReport create(long projectId, String runId, String title) {
        if (runId == null || runId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "runId 不能为空");
        }
        int version = repository.maxVersion(projectId, runId) + 1;
        ReportSnapshot snapshot = snapshots.build(queries.detail(projectId, runId), version);
        long id = repository.insert(projectId, runId,
                title == null || title.isBlank() ? "报告 " + runId : title, version, snapshot);
        repository.audit(id, "CREATE", null, ReportState.DRAFT.name(), null);
        return find(id);
    }

    public TestReport freeze(long id) {
        TestReport report = find(id);
        require(report.state().canTransitionTo(ReportState.FROZEN), "只有 DRAFT 可以冻结");
        int version = report.version() + 1;
        ReportSnapshot snapshot = snapshots.build(
                queries.detail(report.projectId(), report.runId()), version);
        repository.freeze(id, snapshot);
        repository.audit(id, "FREEZE", report.state().name(), ReportState.FROZEN.name(), null);
        return find(id);
    }

    public TestReport transition(long id, ReportState target) {
        TestReport report = find(id);
        require(report.state().canTransitionTo(target),
                "不允许的状态流转: " + report.state() + " -> " + target);
        repository.changeState(id, target);
        repository.audit(id, "TRANSITION", report.state().name(), target.name(), null);
        return find(id);
    }

    /** 渲染只读快照表，绝不回查 run —— 这是「删源 run 仍可渲染」的实现保证。 */
    public Map<String, Object> render(long id, boolean external) {
        TestReport report = find(id);
        if (report.snapshotJson() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "报告尚未物化快照");
        }
        ReportSnapshot snapshot = new ReportSnapshot(report.snapshotJson(), report.snapshotHtml(),
                report.snapshotHash(), report.version(), report.sourceSummary());
        if (!external) {
            return renderer.internal(snapshot);
        }
        return renderer.external(snapshot, report.id(), report.title(),
                report.frozenAt() == null ? "" : report.frozenAt().toString(),
                ReportExportService.WATERMARK);
    }

    public byte[] export(long id, String format) {
        Map<String, Object> view = render(id, true);
        byte[] bytes = exports.export(format, view);
        String ext = ReportExportService.extension(format);
        repository.insertExport(id, ext, "report-" + id + "." + ext, bytes.length,
                ReportExportService.WATERMARK);
        return bytes;
    }

    public List<TestReportExport> exports(long id) {
        find(id);
        return repository.exports(id);
    }

    public int purge(int retentionDays) {
        if (retentionDays < 1) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "保留天数必须 >= 1");
        }
        return repository.purgeExportsBefore(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(ErrorCode.CONFLICT, message);
        }
    }
}
