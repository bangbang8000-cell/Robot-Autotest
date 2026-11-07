package com.qa.autotest.domain.kernel;

import com.qa.autotest.domain.report.GateResult;
import com.qa.autotest.domain.report.MetadataGate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 归一后的整轮结果（KRN-01）。counts 由 cases 现场统计，不接受外部传入的第二套数字。
 *
 * <p>metadata 与 cases 同样**只由内核产物解析得到**，不走外部补录：
 * 这样「同一份产物归一两次结果一致」对 metadata 也成立（N3，v2.1 欠账）。
 * 十项门禁复用 {@link MetadataGate}，与 RF 报告入库共享同一口径——
 * 两个内核的「能不能对外交付」不应有两套判定。
 */
public record NormalizedRun(String kernel, String runId, List<NormalizedCaseResult> cases,
                            Map<String, String> metadata) {

    public NormalizedRun {
        cases = cases == null ? List.of() : List.copyOf(cases);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** 兼容旧调用：不带 metadata 的归一结果（metadata 为空，门禁必然不通过）。 */
    public NormalizedRun(String kernel, String runId, List<NormalizedCaseResult> cases) {
        this(kernel, runId, cases, Map.of());
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

    /** metadata 十项门禁结果（N3）。 */
    public GateResult metadataGate() {
        return MetadataGate.check(metadata);
    }

    /**
     * 是否可作为对外交付依据：十项齐全且无超长。
     *
     * <p>与 RF 报告里 {@code exportable} 同一语义：缺项时**不是报错**，
     * 而是降级为「内部可见、不可导出」。
     */
    public boolean exportable() {
        return metadataGate().exportable();
    }
}
