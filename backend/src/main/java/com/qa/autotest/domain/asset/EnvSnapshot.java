package com.qa.autotest.domain.asset;

import java.time.Instant;

/** 环境快照（FR-v0.2-12）：某时点的拓扑与关键参数，可 diff 出变化项。 */
public record EnvSnapshot(
        Long id,
        String name,
        String profileType,
        String description,
        String snapshotJson,
        Instant snapshotAt) {
}
