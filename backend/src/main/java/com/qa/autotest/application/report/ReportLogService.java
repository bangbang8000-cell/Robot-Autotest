package com.qa.autotest.application.report;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.ReportReadRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.report.CaseLogRef;
import com.qa.autotest.domain.report.LogSpan;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 用例日志按需读取（RPT-02）。
 *
 * <p>请求带 projectId + testKey(caseKey) + offset + limit；服务层按入库时固化的
 * {@code log_offset / log_span} 定位区间，再从 output.xml <b>流式</b>读取，
 * 单次最多 {@link #MAX_LIMIT} 行，默认 {@link #DEFAULT_LIMIT}。绝不返回整文件。
 */
@Service
public class ReportLogService {

    public static final int DEFAULT_LIMIT = 200;
    public static final int MAX_LIMIT = 1000;

    private final ReportReadRepository read;
    private final ReportArtifactLocator locator;
    private final LogXmlReader reader;
    private final AccessControlService access;

    public ReportLogService(ReportReadRepository read, ReportArtifactLocator locator,
                            LogXmlReader reader, AccessControlService access) {
        this.read = read;
        this.locator = locator;
        this.reader = reader;
        this.access = access;
    }

    /** 日志行（seq 为该用例内的相对序号，0 基）。 */
    public record LogLineView(long seq, String level, String keyword, String message) {
    }

    /**
     * 日志分页结果。
     *
     * @param lines      本次返回的行
     * @param total      该用例日志总行数
     * @param nextOffset 下一次请求的 offset；已到末尾为 null
     */
    public record LogPage(List<LogLineView> lines, long total, Integer nextOffset) {
    }

    public LogPage logs(long projectId, String runId, String testKey, int offset, int limit) {
        if (projectId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "projectId 非法");
        }
        if (runId == null || runId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "runId 不能为空");
        }
        if (testKey == null || testKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "testKey 不能为空");
        }
        if (offset < 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "offset 不能为负数");
        }
        int effectiveLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.READ);
        if (read.findRun(projectId, runId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "运行不存在: " + runId);
        }
        CaseLogRef ref = read.findCaseLog(projectId, runId, testKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用例不存在: " + testKey));
        LogSpan span = LogSpan.parse(ref.logSpan());
        if (span.empty()) {
            return new LogPage(List.of(), 0L, null);
        }

        long total = span.length();
        long relativeStart = Math.min(offset, total);
        long relativeEnd = Math.min(relativeStart + effectiveLimit, total);
        if (relativeStart >= total) {
            return new LogPage(List.of(), total, null);
        }
        Path file = locator.locateOutputXml(projectId, runId);
        List<LogXmlReader.LogXmlLine> raw = reader.read(file,
                span.start() + relativeStart, span.start() + relativeEnd);
        List<LogLineView> lines = raw.stream()
                .map(line -> new LogLineView(line.seq() - span.start(),
                        line.level(), line.keyword(), line.message()))
                .toList();
        Integer nextOffset = relativeEnd >= total ? null : (int) relativeEnd;
        return new LogPage(lines, total, nextOffset);
    }
}
