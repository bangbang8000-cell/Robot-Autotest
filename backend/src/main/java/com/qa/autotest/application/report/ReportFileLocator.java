package com.qa.autotest.application.report;

import com.qa.autotest.application.execution.ExecutionPaths;
import com.qa.autotest.application.execution.RunArtifact;
import com.qa.autotest.application.repository.RunArtifactRepository;
import com.qa.autotest.application.repository.RunRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.execution.RunRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * 报告产物定位（RPT-02）：把「某次运行的 output.xml 在哪」收敛到一处。
 *
 * <p>顺序与 RPT-01 入库时的推导保持一致：run_artifact(kind=output_xml) → artifacts_dir 下的
 * {@code shard-0/output.xml} / {@code output.xml}。找不到时给可读 404，不猜历史目录。
 */
@Component
public final class ReportFileLocator implements ReportArtifactLocator {

    private final RunRepository runs;
    private final RunArtifactRepository artifacts;

    public ReportFileLocator(RunRepository runs, RunArtifactRepository artifacts) {
        this.runs = runs;
        this.artifacts = artifacts;
    }

    /** 定位并校验可读的 output.xml。 */
    @Override
    public Path locateOutputXml(long projectId, String runId) {
        RunRecord run = runs.findByRunId(projectId, runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "运行不存在: " + runId));
        for (RunArtifact artifact : artifacts.list(projectId, run.id())) {
            if (!"output_xml".equals(artifact.kind()) || artifact.path() == null
                    || artifact.path().isBlank()) {
                continue;
            }
            Path candidate = ExecutionPaths.resolve(artifact.path());
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        if (run.artifactsDir() != null && !run.artifactsDir().isBlank()) {
            Path runDir = ExecutionPaths.resolve(run.artifactsDir());
            Path shard = runDir.resolve("shard-0").resolve("output.xml");
            if (Files.isRegularFile(shard)) {
                return shard;
            }
            Path direct = runDir.resolve("output.xml");
            if (Files.isRegularFile(direct)) {
                return direct;
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND,
                "未找到 output.xml：run_artifact 未登记且产物目录不存在，无法读取日志");
    }
}
