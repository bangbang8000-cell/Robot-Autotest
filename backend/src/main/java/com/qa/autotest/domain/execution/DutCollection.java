package com.qa.autotest.domain.execution;

import java.time.Instant;
import java.util.List;

/**
 * DUT 采集结果。targetLabel 只含 user@host，不含口令。
 */
public record DutCollection(String targetLabel,
                            List<CommandOutput> outputs,
                            Instant startedAt,
                            Instant finishedAt) {

    public record CommandOutput(String name,
                                int exitCode,
                                String stdout,
                                String stderr) {
    }
}
