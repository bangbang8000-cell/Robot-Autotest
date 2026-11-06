package com.qa.autotest.domain.kernel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 归一后的整轮结果（KRN-01）。counts 由 cases 现场统计，不接受外部传入的第二套数字。 */
public record NormalizedRun(String kernel, String runId, List<NormalizedCaseResult> cases) {

    public NormalizedRun {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }

    public Map<String, Integer> counts() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (CaseStatus status : CaseStatus.values()) {
            result.put(status.name(), 0);
        }
        for (NormalizedCaseResult item : cases) {
            result.merge(item.status().name(), 1, Integer::sum);
        }
        return result;
    }

    public int total() {
        return cases.size();
    }

    public int failures() {
        return (int) cases.stream().filter(c -> c.status().isFailure()).count();
    }

    public boolean complete() {
        return !cases.isEmpty();
    }
}
