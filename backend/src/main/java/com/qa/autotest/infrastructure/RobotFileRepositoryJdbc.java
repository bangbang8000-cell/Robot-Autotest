package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.RobotFileRepository;
import com.qa.autotest.common.tenant.TenantGuard;
import com.qa.autotest.domain.caseasset.FileNode;
import com.qa.autotest.domain.caseasset.RobotFile;
import com.qa.autotest.domain.caseasset.RobotFileVersion;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** 脚本文件仓储实现（ADR-0005：content 唯一真源）。 */
@Repository
public class RobotFileRepositoryJdbc implements RobotFileRepository {

    private static final RowMapper<FileNode> NODE_MAPPER = (rs, rowNum) -> new FileNode(
            rs.getString("path"), false,
            rs.getObject("size") == null ? 0 : rs.getInt("size"),
            JdbcSupport.instant(rs.getTimestamp("updated_at")));

    private static final RowMapper<RobotFile> MAPPER = (rs, rowNum) -> new RobotFile(
            rs.getLong("id"), rs.getLong("project_id"), rs.getString("path"),
            rs.getString("content"), rs.getString("content_sha256"),
            rs.getInt("current_version"));

    private final JdbcTemplate jdbc;

    public RobotFileRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<FileNode> nodes(long projectId) {
        String sql = """
                SELECT path, CHAR_LENGTH(COALESCE(content, '')) AS size, updated_at
                  FROM robot_file WHERE project_id = ? ORDER BY path
                """;
        TenantGuard.requireProjectScope(sql, "robot_file");
        return jdbc.query(sql, NODE_MAPPER, projectId);
    }

    @Override
    public Optional<RobotFile> findByPath(long projectId, String path) {
        String sql = """
                SELECT id, project_id, path, content, content_sha256, current_version
                  FROM robot_file WHERE project_id = ? AND path = ?
                """;
        TenantGuard.requireProjectScope(sql, "robot_file");
        return JdbcSupport.first(jdbc.query(sql, MAPPER, projectId, path));
    }

    @Override
    public Optional<RobotFile> find(long projectId, long id) {
        String sql = """
                SELECT id, project_id, path, content, content_sha256, current_version
                  FROM robot_file WHERE project_id = ? AND id = ?
                """;
        TenantGuard.requireProjectScope(sql, "robot_file");
        return JdbcSupport.first(jdbc.query(sql, MAPPER, projectId, id));
    }

    @Override
    public long insert(long projectId, String path, String content, String sha256,
                       Long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO robot_file
                      (project_id, path, content, content_sha256, current_version, created_by, updated_by)
                    VALUES (?, ?, ?, ?, 1, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, projectId);
            ps.setString(2, path);
            ps.setString(3, content);
            ps.setString(4, sha256);
            ps.setObject(5, operatorId, java.sql.Types.BIGINT);
            ps.setObject(6, operatorId, java.sql.Types.BIGINT);
            return ps;
        }, keyHolder);
        long id = keyHolder.getKey() == null ? 0 : keyHolder.getKey().longValue();
        if (id > 0 && content != null && !content.isEmpty()) {
            insertVersion(id, 1, content, sha256, "初始版本", operatorId);
        }
        return id;
    }

    @Override
    public void updateContent(long projectId, long id, String content, String sha256,
                              String comment, Long operatorId) {
        String sql = """
                UPDATE robot_file SET content = ?, content_sha256 = ?,
                       current_version = current_version + 1, updated_by = ?
                 WHERE project_id = ? AND id = ?
                """;
        TenantGuard.requireProjectScope(sql, "robot_file");
        Integer nextVersion = jdbc.queryForObject("""
                SELECT current_version + 1 FROM robot_file WHERE project_id = ? AND id = ?
                """, Integer.class, projectId, id);
        jdbc.update(sql, content, sha256, operatorId, projectId, id);
        if (nextVersion != null) {
            insertVersion(id, nextVersion, content, sha256, comment, operatorId);
            String updateVersionSql = """
                    UPDATE robot_file_version SET updated_by = ?
                     WHERE robot_file_id = ? AND version_no = ?
                    """;
            jdbc.update(updateVersionSql, operatorId, id, nextVersion);
        }
    }

    @Override
    public boolean updateContentIfVersion(long projectId, long id, int expectedVersion,
                                          String content, String sha256, String comment,
                                          Long operatorId) {
        String sql = """
                UPDATE robot_file SET content = ?, content_sha256 = ?,
                       current_version = current_version + 1, updated_by = ?
                 WHERE project_id = ? AND id = ? AND current_version = ?
                """;
        TenantGuard.requireProjectScope(sql, "robot_file");
        int affected = jdbc.update(sql, content, sha256, operatorId, projectId, id, expectedVersion);
        if (affected == 0) {
            return false;
        }
        // 只有写成功后才落版本快照，避免并发失败留下孤儿版本。
        insertVersion(id, expectedVersion + 1, content, sha256, comment, operatorId);
        return true;
    }

    private void insertVersion(long fileId, int versionNo, String content, String sha256,
                               String comment, Long operatorId) {
        jdbc.update("""
                INSERT INTO robot_file_version
                  (robot_file_id, version_no, content, content_sha256, content_size, comment,
                   created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, fileId, versionNo, content == null ? "" : content,
                sha256 == null ? "" : sha256,
                content == null ? 0 : content.length(), comment, operatorId, operatorId);
    }

    @Override
    public void delete(long projectId, long id) {
        String delVersions = """
                DELETE FROM robot_file_version WHERE robot_file_id = ?
                  AND robot_file_id IN (SELECT id FROM robot_file WHERE project_id = ? AND id = ?)
                """;
        TenantGuard.requireProjectScope(delVersions, "robot_file_version");
        jdbc.update(delVersions, id, projectId, id);
        String delFile = "DELETE FROM robot_file WHERE project_id = ? AND id = ?";
        TenantGuard.requireProjectScope(delFile, "robot_file");
        jdbc.update(delFile, projectId, id);
    }

    @Override
    public void deleteByPrefix(long projectId, String prefix) {
        String pattern = prefix + "%";
        String delVersions = """
                DELETE FROM robot_file_version
                 WHERE robot_file_id IN (
                   SELECT id FROM robot_file WHERE project_id = ? AND path LIKE ?
                 )
                """;
        TenantGuard.requireProjectScope(delVersions, "robot_file_version");
        jdbc.update(delVersions, projectId, pattern);
        String delFiles = "DELETE FROM robot_file WHERE project_id = ? AND path LIKE ?";
        TenantGuard.requireProjectScope(delFiles, "robot_file");
        jdbc.update(delFiles, projectId, pattern);
    }

    @Override
    public void move(long projectId, long id, String newPath, Long operatorId) {
        String sql = """
                UPDATE robot_file SET path = ?, updated_by = ? WHERE project_id = ? AND id = ?
                """;
        TenantGuard.requireProjectScope(sql, "robot_file");
        jdbc.update(sql, newPath, operatorId, projectId, id);
    }

    @Override
    public boolean pathExists(long projectId, String path, Long excludeId) {
        String sql = """
                SELECT COUNT(*) FROM robot_file WHERE project_id = ? AND path = ? AND id <> ?
                """;
        TenantGuard.requireProjectScope(sql, "robot_file");
        Integer count = jdbc.queryForObject(sql, Integer.class, projectId, path,
                excludeId == null ? -1L : excludeId);
        return count != null && count > 0;
    }

    @Override
    public List<RobotFileVersion> versions(long projectId, long fileId) {
        String sql = """
                SELECT v.id, f.path, v.version_no, v.content, v.content_sha256, v.comment,
                       v.created_at
                  FROM robot_file_version v JOIN robot_file f ON f.id = v.robot_file_id
                 WHERE f.project_id = ? AND v.robot_file_id = ?
                 ORDER BY v.version_no DESC
                """;
        TenantGuard.requireProjectScope(sql, "robot_file_version");
        return jdbc.query(sql, (rs, rowNum) -> new RobotFileVersion(
                rs.getLong("id"), rs.getString("path"), rs.getInt("version_no"),
                rs.getString("content"), rs.getString("content_sha256"),
                rs.getString("comment"), JdbcSupport.instant(rs.getTimestamp("created_at"))),
                projectId, fileId);
    }
}
