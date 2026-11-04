package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.UserPreferenceRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 用户偏好 KV 仓储（UX-01）。 */
@Repository
public class UserPreferenceRepositoryJdbc implements UserPreferenceRepository {

    private final JdbcTemplate jdbc;

    public UserPreferenceRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, String> findAll(long userId) {
        Map<String, String> result = new LinkedHashMap<>();
        jdbc.query("SELECT pref_key, pref_value FROM user_preference WHERE user_id = ? ORDER BY pref_key",
                        (rs, rowNum) -> new String[]{rs.getString(1), rs.getString(2)}, userId)
                .forEach(row -> result.put(row[0], row[1]));
        return result;
    }

    @Override
    public Optional<String> find(long userId, String key) {
        return JdbcSupport.first(jdbc.query(
                "SELECT pref_value FROM user_preference WHERE user_id = ? AND pref_key = ?",
                (rs, rowNum) -> rs.getString("pref_value"), userId, key));
    }

    @Override
    public void upsert(long userId, String key, String value) {
        // 用 ON DUPLICATE KEY UPDATE 而不是"先查再决定"：并发改同一 key 时不会有两个请求
        // 同时判断"不存在"然后都去 INSERT（那会撞唯一键）。
        // created_by/updated_by 填 userId：偏好是本人改的，审计链在这里只是"谁最后动的"。
        jdbc.update("INSERT INTO user_preference (user_id, pref_key, pref_value, created_by, updated_by) "
                + "VALUES (?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE pref_value = VALUES(pref_value), "
                + "updated_by = VALUES(updated_by)",
                userId, key, value, userId, userId);
    }
}
