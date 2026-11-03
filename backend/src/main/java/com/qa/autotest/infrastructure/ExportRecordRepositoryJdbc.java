package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.ExportRecordRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantGuard;
import com.qa.autotest.domain.report.ExportFormat;
import com.qa.autotest.domain.report.ExportMode;
import com.qa.autotest.domain.report.ExportRecord;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** 导出记录 JDBC 实现（RPT-05）：所有查询带 project_id 租户条件。 */
@Repository
public class ExportRecordRepositoryJdbc implements ExportRecordRepository {

    private static final String COLUMNS = """
            SELECT id, project_id, run_key, export_format, export_mode, file_name, file_path,
                   sha256, size_bytes, export_status, requested_by, created_at
              FROM export_record
            """;

    private final JdbcTemplate jdbc;
    private final RowMapper<ExportRecord> rowMapper = this::mapRow;

    public ExportRecordRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long insert(long projectId, String runId, ExportFormat format, ExportMode mode,
                       String fileName, String filePath, String sha256, long sizeBytes,
                       Long requestedBy, String status) {
        String sql = """
                INSERT INTO export_record (project_id, run_key, export_format, export_mode,
                                           file_path, file_name, sha256, size_bytes,
                                           export_status, requested_by, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        TenantGuard.requireProjectScope(sql, "export_record");
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, projectId);
            ps.setString(2, runId);
            ps.setString(3, format.wireName());
            ps.setString(4, mode.wireName());
            ps.setString(5, filePath);
            ps.setString(6, fileName);
            ps.setString(7, sha256);
            ps.setLong(8, sizeBytes);
            ps.setString(9, status);
            if (requestedBy == null) {
                ps.setNull(10, java.sql.Types.BIGINT);
                ps.setNull(11, java.sql.Types.BIGINT);
            } else {
                ps.setLong(10, requestedBy);
                ps.setLong(11, requestedBy);
            }
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导出记录写入失败：未取得主键");
        }
        return key.longValue();
    }

    @Override
    public List<ExportRecord> list(long projectId, String runId) {
        String sql = COLUMNS + """
                 WHERE project_id = ? AND run_key = ?
                 ORDER BY created_at DESC, id DESC
                """;
        TenantGuard.requireProjectScope(sql, "export_record");
        return jdbc.query(sql, rowMapper, projectId, runId);
    }

    @Override
    public Optional<ExportRecord> find(long projectId, long exportId) {
        String sql = COLUMNS + """
                 WHERE project_id = ? AND id = ?
                """;
        TenantGuard.requireProjectScope(sql, "export_record");
        List<ExportRecord> rows = jdbc.query(sql, rowMapper, projectId, exportId);
        return rows.stream().findFirst();
    }

    private ExportRecord mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        long requestedBy = rs.getLong("requested_by");
        Long requested = rs.wasNull() ? null : requestedBy;
        return new ExportRecord(
                rs.getLong("id"),
                rs.getLong("project_id"),
                rs.getString("run_key"),
                ExportFormat.fromWire(rs.getString("export_format")).orElse(ExportFormat.DOCX),
                ExportMode.fromWire(rs.getString("export_mode")).orElse(ExportMode.EXTERNAL),
                rs.getString("file_name"),
                rs.getString("file_path"),
                rs.getString("sha256"),
                rs.getLong("size_bytes"),
                rs.getString("export_status"),
                requested,
                instant(rs.getTimestamp("created_at")));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
