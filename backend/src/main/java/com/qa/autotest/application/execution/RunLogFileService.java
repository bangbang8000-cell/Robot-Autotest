package com.qa.autotest.application.execution;

import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.execution.RunRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * 运行全量日志文件（EXEC-06 / FR-v0.4-21）。
 *
 * <p>实时日志频道 {@link RunLogService} 只保留最近 2000 行，完整日志由
 * {@link PabotOrchestrator} 全量写在 run 产物目录下的 {@code run.log}。
 * 本服务负责把「某次 run 的完整日志在哪」收敛到一处，并提供下载资源；
 * 找不到时给可读 404，不猜历史目录（与 RPT-02 的 {@code ReportFileLocator} 同一原则）。
 */
@Service
public class RunLogFileService {

    /** 与 {@link PabotOrchestrator} 的落盘位置保持一致：run 产物目录根下。 */
    public static final String LOG_FILE = "run.log";

    /** 定位并校验可读的全量日志文件。 */
    public Path locate(RunRecord run) {
        if (run.artifactsDir() == null || run.artifactsDir().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "该运行没有产物目录，无法下载完整日志：" + run.runId());
        }
        Path log = ExecutionPaths.resolve(run.artifactsDir()).resolve(LOG_FILE);
        if (!Files.isRegularFile(log)) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "该运行还没有完整日志文件（" + LOG_FILE + " 不存在）：" + log
                            + "。日志在运行开始后才会落盘；尚未开始或产物目录已清理的运行无文件可下");
        }
        return log;
    }

    /** 下载资源；存在性校验在 {@link #locate(RunRecord)} 完成。 */
    public Resource download(RunRecord run) {
        return new FileSystemResource(locate(run));
    }
}
