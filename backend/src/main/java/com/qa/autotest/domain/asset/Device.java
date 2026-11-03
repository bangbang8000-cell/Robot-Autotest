package com.qa.autotest.domain.asset;

import java.time.Instant;

/** 设备台账（FR-v0.2-09）。 */
public record Device(
        Long id,
        Long projectId,
        String name,
        String deviceType,
        String model,
        String vendor,
        String serialNo,
        String managementIp,
        String location,
        String rackNo,
        String domainTag,
        boolean enabled,
        Long ownerId,
        Instant lastCheckedAt) {
}
