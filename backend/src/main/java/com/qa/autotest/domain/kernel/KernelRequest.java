package com.qa.autotest.domain.kernel;

import java.util.List;

/** 内核执行请求。两套内核共用同一个入参，避免«按内核各写一套 DTO»。 */
public record KernelRequest(long projectId, String runId, int shardNo, int processes,
                            List<String> suitePaths, List<String> testNames,
                            String includeTag, String displayName) {

    public KernelRequest {
        suitePaths = suitePaths == null ? List.of() : List.copyOf(suitePaths);
        testNames = testNames == null ? List.of() : List.copyOf(testNames);
    }
}
