package com.qa.autotest.application.repository;

import com.qa.autotest.domain.asset.EnvSnapshot;
import java.util.List;
import java.util.Optional;

/** 环境画像与快照仓储。 */
public interface EnvProfileRepository {

    long insertSnapshot(long projectId, String name, String profileType, String description,
                        String snapshotJson, Long operatorId);

    List<EnvSnapshot> listSnapshots(long projectId, String name);

    Optional<EnvSnapshot> latestSnapshot(long projectId, String name);
}
