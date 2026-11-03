package com.qa.autotest.application.repository;

import com.qa.autotest.application.execution.RunArtifact;

import java.util.List;

/**
 * 运行产物登记。路径落库前必须已存在（由调用方校验）。
 */
public interface RunArtifactRepository {

    void upsert(long projectId, long runDbId, String kind, String path,
                String sha256, long sizeBytes, int shardNo, String note);

    List<RunArtifact> list(long projectId, long runDbId);
}
