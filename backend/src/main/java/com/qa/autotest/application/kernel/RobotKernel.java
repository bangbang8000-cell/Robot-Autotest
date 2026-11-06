package com.qa.autotest.application.kernel;

import com.qa.autotest.application.execution.ExecutionProperties;
import com.qa.autotest.application.execution.PabotCommandBuilder;
import com.qa.autotest.domain.kernel.ExecutionKernel;
import com.qa.autotest.domain.kernel.KernelRequest;
import com.qa.autotest.domain.kernel.NormalizedRun;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * RF 内核（KRN-02）：命令装配<b>直接委托</b>既有 {@link PabotCommandBuilder}，
 * 不做任何字符串重排，保证 v1.0 的 RF 命令逐字等价（G8 第 3 条）。
 *
 * <p>结果归一复用既有 output.xml 解析器；本内核不引入第二套 RF 判定逻辑。
 */
@Service
public class RobotKernel implements ExecutionKernel {

    public static final String NAME = "robot";

    private final PabotCommandBuilder builder = new PabotCommandBuilder();
    private final ExecutionProperties properties;
    private final OutputXmlNormalizer normalizer;

    public RobotKernel(ExecutionProperties properties, OutputXmlNormalizer normalizer) {
        this.properties = properties;
        this.normalizer = normalizer;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<String> command(KernelRequest request, Path artifactsRoot) {
        PabotCommandBuilder.ShardSpec spec = new PabotCommandBuilder.ShardSpec(
                request.shardNo(), request.suitePaths(), request.processes(),
                request.includeTag(), request.displayName(), request.testNames());
        return builder.build(spec, properties, artifactsRoot).argv();
    }

    @Override
    public NormalizedRun normalize(Path artifactDir, String runId) {
        Path output = artifactDir.resolve("output.xml");
        if (!Files.isRegularFile(output)) {
            throw new IllegalArgumentException("RF 产物缺少 output.xml: " + artifactDir);
        }
        return normalizer.fromOutputXml(output, runId);
    }
}
