package com.qa.autotest.domain.kernel;

import java.nio.file.Path;
import java.util.List;

/** 执行内核 SPI（KRN-01）。domain 层接口，实现不得反向依赖 domain 之外的上层。 */
public interface ExecutionKernel {

    String name();

    /** 由产物目录解析归一结果；找不到主通道时自动走兜底通道。 */
    NormalizedRun normalize(Path artifactDir, String runId);

    /** 生成本内核的执行命令；同一请求下 RF 命令必须与旧路径逐字等价。 */
    List<String> command(KernelRequest request, Path artifactsRoot);
}
