package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.SystemSettingRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

/** 设置 KV 仓储的 JDBC 实现（NAV-02）。 */
@Repository
public class SystemSettingRepositoryJdbc implements SystemSettingRepository {

    private final JdbcTemplate jdbc;

    public SystemSettingRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, String> findAllSystem() {
        return pairs("SELECT setting_key, setting_value FROM system_setting ORDER BY setting_key");
    }

    @Override
    public Optional<String> findSystem(String key) {
        return JdbcSupport.first(jdbc.query(
                "SELECT setting_value FROM system_setting WHERE setting_key = ?",
                (rs, rowNum) -> rs.getString(1), key));
    }

    @Override
    public void upsertSystem(String key, String value, Long actorId) {
        // ON DUPLICATE KEY UPDATE：并发改同一 key 不会出现两个 INSERT 撞唯一键。
        jdbc.update("INSERT INTO system_setting (setting_key, setting_value, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value), "
                        + "updated_by = VALUES(updated_by)",
                key, value, actorId, actorId);
    }

    @Override
    public Map<String, String> findAllProject(long projectId) {
        Map<String, String> result = new LinkedHashMap<>();
        // 显式声明回调类型：lambda 直接传参会在 ResultSetExtractor / RowCallbackHandler 之间歧义
        RowCallbackHandler handler = rs -> result.put(rs.getString(1), rs.getString(2));
        jdbc.query("SELECT setting_key, setting_value FROM project_setting "
                        + "WHERE project_id = ? ORDER BY setting_key",
                handler, projectId);
        return result;
    }

    @Override
    public Optional<String> findProject(long projectId, String key) {
        return JdbcSupport.first(jdbc.query(
                "SELECT setting_value FROM project_setting WHERE project_id = ? AND setting_key = ?",
                (rs, rowNum) -> rs.getString(1), projectId, key));
    }

    @Override
    public void upsertProject(long projectId, String key, String value, Long actorId) {
        jdbc.update("INSERT INTO project_setting "
                        + "(project_id, setting_key, setting_value, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value), "
                        + "updated_by = VALUES(updated_by)",
                projectId, key, value, actorId, actorId);
    }

    private Map<String, String> pairs(String sql) {
        Map<String, String> result = new LinkedHashMap<>();
        RowCallbackHandler handler = rs -> result.put(rs.getString(1), rs.getString(2));
        jdbc.query(sql, handler);
        return result;
    }
}
