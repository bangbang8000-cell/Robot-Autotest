package com.qa.autotest.application.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qa.autotest.application.execution.ExecutionProperties;
import com.qa.autotest.application.execution.PabotCommandBuilder;
import com.qa.autotest.domain.kernel.KernelRequest;
import com.qa.autotest.domain.kernel.NormalizedRun;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RobotKernelParityTest {

    private final RobotKernel kernel = new RobotKernel(new ExecutionProperties(),
            new OutputXmlNormalizer());

    private static KernelRequest request() {
        return new KernelRequest(1L, "R1", 1, 2,
                List.of("fixtures/smoke/2node-smoke.robot", "fixtures/smoke/extra.robot"),
                List.of("Case A", "Case B"), "smoke", "parity-check");
    }

    @Test
    void commandIsByteForByteEqualToLegacyBuilder() {
        KernelRequest request = request();
        Path artifacts = Path.of("artifacts");
        PabotCommandBuilder.ShardSpec spec = new PabotCommandBuilder.ShardSpec(
                request.shardNo(), request.suitePaths(), request.processes(),
                request.includeTag(), request.displayName(), request.testNames());
        List<String> legacy = new PabotCommandBuilder()
                .build(spec, new ExecutionProperties(), artifacts).argv();
        assertEquals(legacy, kernel.command(request, artifacts));
        assertTrue(legacy.contains("--test"));
    }

    @Test
    void outputXmlIsNormalizedWithoutRewritingRfSemantics(@TempDir Path dir) throws Exception {
        String xml = """
                <robot><suite name="S"><test name="A"><status status="PASS"/></test>
                <test name="B"><status status="FAIL"/></test></suite></robot>""";
        Files.writeString(dir.resolve("output.xml"), xml);
        NormalizedRun run = kernel.normalize(dir, "R1");
        assertEquals(2, run.total());
        assertEquals(1, run.failures());
        assertEquals("robot", run.kernel());
    }
}
