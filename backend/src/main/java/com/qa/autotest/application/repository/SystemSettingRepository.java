package com.qa.autotest.application.repository;

import java.util.Map;
import java.util.Optional;

/**
 * 平台级 / 项目级设置仓储（NAV-02 / FR-v2.0-10）。
 *
 * <p>两张表 V1 基线里就有（{@code system_setting} / {@code project_setting}），本轮不新增迁移：
 * 只是把「设置分级」这件事真正接进 API 与前端。表本身是 KV，白名单与校验全部在
 * {@code SystemSettingService}，这里不做任何业务判断。
 */
public interface SystemSettingRepository {

    Map<String, String> findAllSystem();

    Optional<String> findSystem(String key);

    void upsertSystem(String key, String value, Long actorId);

    Map<String, String> findAllProject(long projectId);

    Optional<String> findProject(long projectId, String key);

    void upsertProject(long projectId, String key, String value, Long actorId);
}
