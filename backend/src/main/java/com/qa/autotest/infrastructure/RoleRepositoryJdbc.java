package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.RoleRepository;
import com.qa.autotest.domain.iam.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** 角色仓储实现。 */
@Repository
public class RoleRepositoryJdbc implements RoleRepository {

    private static final RowMapper<Role> MAPPER = (rs, rowNum) -> new Role(
            rs.getLong("id"), rs.getString("code"), rs.getString("name"),
            rs.getString("description"), rs.getInt("built_in") == 1, rs.getString("permissions"));

    private static final String SELECT =
            "SELECT id, code, name, description, built_in, permissions FROM role";

    private final JdbcTemplate jdbc;

    public RoleRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Role> findAll() {
        return jdbc.query(SELECT + " ORDER BY id", MAPPER);
    }

    @Override
    public Optional<Role> findById(long id) {
        return JdbcSupport.first(jdbc.query(SELECT + " WHERE id = ?", MAPPER, id));
    }

    @Override
    public Optional<Role> findByCode(String code) {
        return JdbcSupport.first(jdbc.query(SELECT + " WHERE code = ?", MAPPER, code));
    }

    @Override
    public long insert(Role role) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO role (code, name, description, built_in, permissions, created_by, updated_by)
                    VALUES (?, ?, ?, 0, ?, NULL, NULL)
                    """, new String[]{"id"});
            ps.setString(1, role.code());
            ps.setString(2, role.name());
            ps.setString(3, role.description());
            ps.setString(4, role.permissionsJson());
            return ps;
        }, keyHolder);
        return keyHolder.getKey() == null ? 0 : keyHolder.getKey().longValue();
    }

    @Override
    public void update(long id, String name, String description, String permissionsJson,
                       Long updatedBy) {
        jdbc.update("""
                UPDATE role SET name = ?, description = ?, permissions = ?, updated_by = ? WHERE id = ?
                """, name, description, permissionsJson, updatedBy, id);
    }

    @Override
    public void delete(long id) {
        jdbc.update("DELETE FROM user_role WHERE role_id = ?", id);
        jdbc.update("DELETE FROM role WHERE id = ?", id);
    }

    @Override
    public boolean codeExists(String code, Long excludeId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM role WHERE code = ? AND id <> ?
                """, Integer.class, code, excludeId == null ? -1L : excludeId);
        return count != null && count > 0;
    }
}
