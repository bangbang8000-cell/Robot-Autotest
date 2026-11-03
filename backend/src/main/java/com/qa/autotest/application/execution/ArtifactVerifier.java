package com.qa.autotest.application.execution;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 产物校验：退出码不可信，必须满足「存在 + size>1024 + 可解析 + totalTests>0」四条件。
 */
public final class ArtifactVerifier {

    private final OutputXmlProbe probe;

    public ArtifactVerifier(OutputXmlProbe probe) {
        this.probe = probe;
    }

    public VerifyResult verify(Path outputXml) {
        if (outputXml == null || !Files.exists(outputXml)) {
            return new VerifyResult(false, "missing", 0, 0);
        }
        long size;
        try {
            size = Files.size(outputXml);
        } catch (Exception ex) {
            return new VerifyResult(false, "missing", 0, 0);
        }
        if (size <= 1024) {
            return new VerifyResult(false, "too_small", 0, size);
        }
        OutputXmlProbe.Probe probeResult = probe.probe(outputXml);
        if (!probeResult.parsed()) {
            return new VerifyResult(false, "parse_failed", 0, size);
        }
        if (probeResult.totalTests() <= 0) {
            return new VerifyResult(false, "no_tests_executed", 0, size);
        }
        return new VerifyResult(true, "ok", probeResult.totalTests(), size);
    }

    public record VerifyResult(boolean success, String reason, int totalTests, long sizeBytes) {
    }
}
