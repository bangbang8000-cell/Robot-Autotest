package com.qa.autotest.application.repository;

import java.util.Collection;
import java.util.Map;

/**
 * 运行记录补录 metadata（HRD-02：{@code run:metadata-patch}）。
 *
 * <p>键值对按 run 维度存放；所有实现必须带 project_id 条件（租户守卫不做例外）。
 */
public interface RunMetadataRepository {

    /** 读取某次 run 的全部补录项，按写入顺序返回。 */
    Map<String, String> findAll(long projectId, String runId);

    /**
     * 落库一次补录：{@code values} 覆盖写，{@code removed} 删除。
     *
     * <p>由服务层在同一事务里调用，避免「先删后写」被并发读到中间态。
     */
    void save(long projectId, String runId, Map<String, String> values,
              Collection<String> removed, Long operatorId);
}
