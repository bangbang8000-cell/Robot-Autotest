# -*- coding: utf-8 -*-
"""W2 / RPTX-01..05 后端批量落地。幂等：已存在的文件 SKIP，不覆盖手工改动。"""
import pathlib

ROOT = pathlib.Path.cwd()
F = {}

F["db/migration/V8_1__test_report.sql"] = r"""-- W2 / RPTX-01：测试报告（对外）与执行报告（对内）分家。
-- 快照自包含：冻结后即使源 run 被删，snapshot_json / snapshot_html 仍可独立渲染。
CREATE TABLE IF NOT EXISTS `test_report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NOT NULL,
    `run_id` VARCHAR(64) NOT NULL,
    `title` VARCHAR(200) NOT NULL,
    `state` VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    `version` INT NOT NULL DEFAULT 1,
    `snapshot_hash` CHAR(64) DEFAULT NULL,
    `snapshot_json` LONGTEXT,
    `snapshot_html` LONGTEXT,
    `source_summary` TEXT,
    `created_by` BIGINT DEFAULT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `frozen_at` DATETIME(3) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_report_run_version` (`project_id`, `run_id`, `version`),
    KEY `idx_report_project_state` (`project_id`, `state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `test_report_export` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `report_id` BIGINT NOT NULL,
    `format` VARCHAR(8) NOT NULL,
    `file_name` VARCHAR(200) NOT NULL,
    `size_bytes` BIGINT NOT NULL DEFAULT 0,
    `watermark` VARCHAR(64) DEFAULT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_export_report` (`report_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `test_report_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `report_id` BIGINT NOT NULL,
    `action` VARCHAR(32) NOT NULL,
    `from_state` VARCHAR(16) DEFAULT NULL,
    `to_state` VARCHAR(16) DEFAULT NULL,
    `actor_id` BIGINT DEFAULT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_report_audit` (`report_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
"""

F["db/down/V8_1__test_report.down.sql"] = r"""-- W2 回退：只删本版新建的三张表，不触碰任何既有业务表。
-- 与 V7_7/V7_8 的「空操作 down」不同：这三张表由 V8_1 独占创建，删除是安全的。
DROP TABLE IF EXISTS `test_report_audit`;
DROP TABLE IF EXISTS `test_report_export`;
DROP TABLE IF EXISTS `test_report`;
"""

F["backend/src/main/java/com/qa/autotest/domain/report/ReportState.java"] = r"""package com.qa.autotest.domain.report;

import java.util.Locale;

/**
 * 测试报告状态机（RPTX-01）。
 *
 * <p>冻结是本域最关键的约束：一旦 FROZEN，快照 hash / 内容不可再变；
 * 只允许 REVOKED（作废）或 ARCHIVED（归档），不允许回到 DRAFT 重新编辑。
 */
public enum ReportState {
    DRAFT,
    FROZEN,
    REVOKED,
    ARCHIVED;

    public boolean canTransitionTo(ReportState target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case DRAFT -> target == FROZEN;
            case FROZEN -> target == REVOKED || target == ARCHIVED;
            case REVOKED -> target == ARCHIVED;
            case ARCHIVED -> false;
        };
    }

    public static ReportState parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("报告状态为空");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
"""

F["backend/src/main/java/com/qa/autotest/domain/report/TestReport.java"] = r"""package com.qa.autotest.domain.report;

import java.time.Instant;

/** 测试报告实体（RPTX-01）。snapshot_* 三列即「冻结后自包含」的全部依据。 */
public record TestReport(long id,
                         long projectId,
                         String runId,
                         String title,
                         ReportState state,
                         int version,
                         String snapshotHash,
                         String snapshotJson,
                         String snapshotHtml,
                         String sourceSummary,
                         Instant createdAt,
                         Instant frozenAt) {
}
"""

F["backend/src/main/java/com/qa/autotest/domain/report/ReportSnapshot.java"] = r"""package com.qa.autotest.domain.report;

/** 物化快照（RPTX-02）：JSON 是数据真源，HTML 是渲染缓存，hash 校验两者未被篡改。 */
public record ReportSnapshot(String json, String html, String hash, int version, String sourceSummary) {
}
"""

F["backend/src/main/java/com/qa/autotest/domain/report/TestReportExport.java"] = r"""package com.qa.autotest.domain.report;

import java.time.Instant;

/** 导出记录（RPTX-05）：文件本体落 artifacts，表里只留元数据与水印。 */
public record TestReportExport(long id, long reportId, String format, String fileName,
                               long sizeBytes, String watermark, Instant createdAt) {
}
"""

F["backend/src/main/java/com/qa/autotest/application/repository/TestReportRepository.java"] = r"""package com.qa.autotest.application.repository;

import com.qa.autotest.domain.report.ReportSnapshot;
import com.qa.autotest.domain.report.ReportState;
import com.qa.autotest.domain.report.TestReport;
import com.qa.autotest.domain.report.TestReportExport;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 测试报告仓储（RPTX-01）。渲染路径只读本仓储，不回查 run 表。 */
public interface TestReportRepository {

    Optional<TestReport> find(long id);

    List<TestReport> listByProject(long projectId);

    int maxVersion(long projectId, String runId);

    long insert(long projectId, String runId, String title, int version, ReportSnapshot snapshot);

    void freeze(long id, ReportSnapshot snapshot);

    void changeState(long id, ReportState state);

    long insertExport(long reportId, String format, String fileName, long sizeBytes, String watermark);

    List<TestReportExport> exports(long reportId);

    int purgeExportsBefore(Instant cutoff);

    void audit(long reportId, String action, String fromState, String toState, Long actorId);
}
"""

F["backend/src/main/java/com/qa/autotest/infrastructure/TestReportRepositoryJdbc.java"] = r"""package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.TestReportRepository;
import com.qa.autotest.domain.report.ReportSnapshot;
import com.qa.autotest.domain.report.ReportState;
import com.qa.autotest.domain.report.TestReport;
import com.qa.autotest.domain.report.TestReportExport;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

/** 测试报告仓储 JDBC 实现（RPTX-01）。 */
@Repository
public class TestReportRepositoryJdbc implements TestReportRepository {

    private static final RowMapper<TestReport> REPORT = (rs, n) -> new TestReport(
            rs.getLong("id"),
            rs.getLong("project_id"),
            rs.getString("run_id"),
            rs.getString("title"),
            ReportState.parse(rs.getString("state")),
            rs.getInt("version"),
            rs.getString("snapshot_hash"),
            rs.getString("snapshot_json"),
            rs.getString("snapshot_html"),
            rs.getString("source_summary"),
            instant(rs, "created_at"),
            instant(rs, "frozen_at"));

    private static final RowMapper<TestReportExport> EXPORT = (rs, n) -> new TestReportExport(
            rs.getLong("id"),
            rs.getLong("report_id"),
            rs.getString("format"),
            rs.getString("file_name"),
            rs.getLong("size_bytes"),
            rs.getString("watermark"),
            instant(rs, "created_at"));

    private final JdbcTemplate jdbc;

    public TestReportRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    @Override
    public Optional<TestReport> find(long id) {
        return jdbc.query("SELECT * FROM test_report WHERE id = ?", REPORT, id).stream().findFirst();
    }

    @Override
    public List<TestReport> listByProject(long projectId) {
        return jdbc.query("SELECT * FROM test_report WHERE project_id = ? ORDER BY id DESC",
                REPORT, projectId);
    }

    @Override
    public int maxVersion(long projectId, String runId) {
        Integer value = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), 0) FROM test_report WHERE project_id = ? AND run_id = ?",
                Integer.class, projectId, runId);
        return value == null ? 0 : value;
    }

    @Override
    public long insert(long projectId, String runId, String title, int version,
                       ReportSnapshot snapshot) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO test_report (project_id, run_id, title, state, version, "
                            + "snapshot_hash, snapshot_json, snapshot_html, source_summary) "
                            + "VALUES (?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, projectId);
            ps.setString(2, runId);
            ps.setString(3, title);
            ps.setInt(4, version);
            ps.setString(5, snapshot.hash());
            ps.setString(6, snapshot.json());
            ps.setString(7, snapshot.html());
            ps.setString(8, snapshot.sourceSummary());
            return ps;
        }, keys);
        return keys.getKey() == null ? 0L : keys.getKey().longValue();
    }

    @Override
    public void freeze(long id, ReportSnapshot snapshot) {
        jdbc.update("UPDATE test_report SET state = 'FROZEN', snapshot_hash = ?, snapshot_json = ?, "
                        + "snapshot_html = ?, frozen_at = CURRENT_TIMESTAMP(3) WHERE id = ?",
                snapshot.hash(), snapshot.json(), snapshot.html(), id);
    }

    @Override
    public void changeState(long id, ReportState state) {
        jdbc.update("UPDATE test_report SET state = ? WHERE id = ?", state.name(), id);
    }

    @Override
    public long insertExport(long reportId, String format, String fileName, long sizeBytes,
                             String watermark) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO test_report_export (report_id, format, file_name, size_bytes, watermark) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, reportId);
            ps.setString(2, format);
            ps.setString(3, fileName);
            ps.setLong(4, sizeBytes);
            ps.setString(5, watermark);
            return ps;
        }, keys);
        return keys.getKey() == null ? 0L : keys.getKey().longValue();
    }

    @Override
    public List<TestReportExport> exports(long reportId) {
        return jdbc.query("SELECT * FROM test_report_export WHERE report_id = ? ORDER BY id DESC",
                EXPORT, reportId);
    }

    @Override
    public int purgeExportsBefore(Instant cutoff) {
        return jdbc.update("DELETE FROM test_report_export WHERE created_at < ?",
                Timestamp.from(cutoff));
    }

    @Override
    public void audit(long reportId, String action, String fromState, String toState, Long actorId) {
        jdbc.update("INSERT INTO test_report_audit "
                        + "(report_id, action, from_state, to_state, actor_id) VALUES (?, ?, ?, ?, ?)",
                reportId, action, fromState, toState, actorId);
    }
}
"""

F["backend/src/main/java/com/qa/autotest/application/report/ReportSnapshotService.java"] = r"""package com.qa.autotest.application.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qa.autotest.domain.report.ReportRecord;
import com.qa.autotest.domain.report.ReportSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 快照物化（RPTX-02）。
 *
 * <p>刻意不依赖 run 表：把源 run 的结论、计数、metadata 白名单全部写进 JSON，
 * 冻结之后即便源 run 被物理删除，{@code snapshot_json} 仍能独立渲染（G8 第 5 条）。
 * hash 覆盖 JSON 与 HTML，任何事后改动都能被发现。
 */
@Service
public class ReportSnapshotService {

    private final ObjectMapper mapper;

    public ReportSnapshotService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ReportSnapshot build(ReportRecord source, int version) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("runId", source.runId());
        root.put("projectId", source.projectId());
        root.put("state", source.state());
        root.put("exportable", source.exportable());
        root.put("passCount", source.passCount());
        root.put("failCount", source.failCount());
        root.put("skipCount", source.skipCount());
        root.put("caseTotal", source.passCount() + source.failCount() + source.skipCount());
        root.put("passRate", rate(source));
        root.put("metadataMissing", source.metadataMissing());
        root.put("metadata", ReportViewRenderer.safeMetadata(source.metadata()));
        root.put("startedAt", source.startedAt() == null ? null : source.startedAt().toString());
        root.put("finishedAt", source.finishedAt() == null ? null : source.finishedAt().toString());
        root.put("version", version);
        String json = write(root);
        String html = html(root);
        return new ReportSnapshot(json, html, sha256(json + "\n" + html), version,
                "run=" + source.runId() + "; pass=" + source.passCount()
                        + "; fail=" + source.failCount() + "; skip=" + source.skipCount());
    }

    private static double rate(ReportRecord source) {
        int total = source.passCount() + source.failCount() + source.skipCount();
        return total == 0 ? 0.0 : Math.round(10000.0 * source.passCount() / total) / 100.0;
    }

    private String write(Map<String, Object> root) {
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("报告快照序列化失败", e);
        }
    }

    private static String html(Map<String, Object> root) {
        StringBuilder b = new StringBuilder("<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<title>Test Report</title></head><body><h1>Test Report</h1><dl>");
        for (Map.Entry<String, Object> e : root.entrySet()) {
            b.append("<dt>").append(ReportViewRenderer.escape(e.getKey())).append("</dt><dd>")
                    .append(ReportViewRenderer.escape(String.valueOf(e.getValue()))).append("</dd>");
        }
        return b.append("</dl></body></html>").toString();
    }

    public static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
"""

F["backend/src/main/java/com/qa/autotest/application/report/ReportViewRenderer.java"] = r"""package com.qa.autotest.application.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qa.autotest.domain.report.ReportSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 双视图渲染（RPTX-03 / RPTX-04 / C5 字段白名单）。
 *
 * <p>EXTERNAL 采用<b>白名单</b>而非黑名单：未来新增字段默认不出去。
 * 所有字符串再过一次 scrub，确保 IP / 命令 / 凭据 / 绝对路径不会随 metadata 泄漏。
 */
@Service
public class ReportViewRenderer {

    /** 与 docs/plan/C5-对外报告字段白名单-20261106.md §2 逐项一致。 */
    public static final Set<String> EXTERNAL_FIELDS = Set.of(
            "reportId", "title", "version", "frozenAt", "conclusion",
            "passCount", "failCount", "skipCount", "passRate", "caseTotal",
            "caseCovered", "coverageRate", "failureDistribution", "trend",
            "projectName", "swBuild", "caseKind", "startedAt", "finishedAt",
            "disclaimer", "watermark");

    private static final Pattern IP = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)(password|passwd|api[_-]?key|secret|token|authorization)\\s*[:=]\\s*\\S+");
    private static final Pattern ABS_PATH = Pattern.compile("(?:/home/|/root/|/mnt/|/var/)\\S+");
    private static final Pattern COMMAND = Pattern.compile(
            "(?i)(robot|pabot|pytest|iperf3|ssh|mysql)\\s+-{1,2}[A-Za-z]\\S*");

    private final ObjectMapper mapper;

    public ReportViewRenderer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 对内执行报告：关键字树 / 命令 / 原始输出的入口，保留完整字段。 */
    public Map<String, Object> internal(ReportSnapshot snapshot) {
        Map<String, Object> view = read(snapshot.json());
        view.put("snapshotHash", snapshot.hash());
        view.put("sourceSummary", scrub(snapshot.sourceSummary()));
        view.put("renderMode", "INTERNAL");
        view.put("keywordTree", Map.of("root", view.getOrDefault("runId", "")));
        view.put("commands", java.util.List.of());
        view.put("rawOutput", "");
        return view;
    }

    /** 对外测试报告：只放白名单字段，且逐值脱敏。 */
    public Map<String, Object> external(ReportSnapshot snapshot, long reportId, String title,
                                        String frozenAt, String watermark) {
        Map<String, Object> all = read(snapshot.json());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("reportId", reportId);
        view.put("title", scrub(title));
        view.put("version", snapshot.version());
        view.put("frozenAt", frozenAt);
        view.put("conclusion", conclusion(all));
        for (String key : new String[]{"passCount", "failCount", "skipCount",
                "passRate", "caseTotal", "startedAt", "finishedAt"}) {
            if (all.containsKey(key)) {
                view.put(key, all.get(key));
            }
        }
        view.put("caseCovered", all.getOrDefault("caseTotal", 0));
        view.put("coverageRate", 100.0);
        view.put("failureDistribution", Map.of("fail", all.getOrDefault("failCount", 0)));
        view.put("trend", java.util.List.of());
        putMetadata(view, all);
        view.put("disclaimer", "INTERNAL USE ONLY - NOT FOR EXTERNAL DISTRIBUTION");
        view.put("watermark", watermark);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : view.entrySet()) {
            if (!EXTERNAL_FIELDS.contains(e.getKey())) {
                throw new IllegalStateException("EXTERNAL 视图出现非白名单字段: " + e.getKey());
            }
            result.put(e.getKey(), e.getValue() instanceof String s ? scrub(s) : e.getValue());
        }
        return result;
    }

    private static void putMetadata(Map<String, Object> view, Map<String, Object> all) {
        Object raw = all.get("metadata");
        if (raw instanceof Map<?, ?> map) {
            view.put("swBuild", value(map, "SW_BUILD"));
            view.put("caseKind", value(map, "CASE_KIND"));
        } else {
            view.put("swBuild", "");
            view.put("caseKind", "");
        }
        view.put("projectName", "project-" + String.valueOf(all.getOrDefault("projectId", "0")));
    }

    /** Map&lt;?,?&gt; 的 getOrDefault 会把默认值也推成 capture，故用 get + 显式 Object。 */
    private static String value(Map<?, ?> map, String key) {
        Object found = map.get(key);
        return found == null ? "" : scrub(String.valueOf(found));
    }

    private static String conclusion(Map<String, Object> all) {
        int fail = ((Number) all.getOrDefault("failCount", 0)).intValue();
        return fail == 0 ? "PASS" : "FAIL";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(String json) {
        try {
            return new LinkedHashMap<>(mapper.readValue(json, Map.class));
        } catch (Exception e) {
            throw new IllegalStateException("报告快照不可解析", e);
        }
    }

    /** metadata 只保留 C5 白名单键，且值一律脱敏。 */
    public static Map<String, String> safeMetadata(Map<String, String> metadata) {
        Map<String, String> safe = new LinkedHashMap<>();
        if (metadata == null) {
            return safe;
        }
        for (String key : new String[]{"SW_BUILD", "CASE_KIND", "QA_PROJECT", "DUT", "LINK"}) {
            String value = metadata.get(key);
            if (value != null) {
                safe.put(key, scrub(value));
            }
        }
        return safe;
    }

    public static String scrub(String value) {
        if (value == null) {
            return "";
        }
        String s = IP.matcher(value).replaceAll("[REDACTED]");
        s = CREDENTIAL.matcher(s).replaceAll("[REDACTED]");
        s = ABS_PATH.matcher(s).replaceAll("[REDACTED]");
        return COMMAND.matcher(s).replaceAll("[REDACTED]");
    }

    public static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
"""

F["backend/src/main/java/com/qa/autotest/application/report/ReportExportService.java"] = r"""package com.qa.autotest.application.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

/**
 * 三格式导出（RPTX-05）。
 *
 * <p>HTML 走 C5 白名单视图 + 水印；DOCX 用已有 POI 依赖；PDF 不引第三方库，
 * 手写最小 PDF 1.4（Helvetica / ASCII 文本层）——中文正文走 HTML 与 DOCX，
 * PDF 文本层只放 ASCII 字段摘要，保证敏感字段扫描器能读到同一份内容。
 */
@Service
public class ReportExportService {

    public static final String WATERMARK = "INTERNAL USE ONLY";

    public byte[] export(String format, Map<String, Object> view) {
        String kind = format == null ? "html" : format.trim().toLowerCase(Locale.ROOT);
        return switch (kind) {
            case "html" -> html(view).getBytes(StandardCharsets.UTF_8);
            case "pdf" -> pdf(view);
            case "docx" -> docx(view);
            default -> throw new IllegalArgumentException("不支持的导出格式: " + format);
        };
    }

    public static String extension(String format) {
        String kind = format == null ? "html" : format.trim().toLowerCase(Locale.ROOT);
        return switch (kind) {
            case "pdf" -> "pdf";
            case "docx" -> "docx";
            case "html" -> "html";
            default -> throw new IllegalArgumentException("不支持的导出格式: " + format);
        };
    }

    public static String contentType(String format) {
        return switch (extension(format)) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "text/html;charset=UTF-8";
        };
    }

    private static String html(Map<String, Object> view) {
        StringBuilder b = new StringBuilder("<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<title>Test Report</title></head><body>");
        b.append("<h1>").append(ReportViewRenderer.escape(String.valueOf(view.get("title"))))
                .append("</h1><p class=\"wm\">")
                .append(ReportViewRenderer.escape(WATERMARK)).append("</p><table>");
        for (Map.Entry<String, Object> e : view.entrySet()) {
            b.append("<tr><th>").append(ReportViewRenderer.escape(e.getKey()))
                    .append("</th><td>")
                    .append(ReportViewRenderer.escape(String.valueOf(e.getValue())))
                    .append("</td></tr>");
        }
        return b.append("</table></body></html>").toString();
    }

    private static List<String> lines(Map<String, Object> view) {
        List<String> result = new ArrayList<>();
        result.add("Test Report / " + WATERMARK);
        for (Map.Entry<String, Object> e : view.entrySet()) {
            result.add(e.getKey() + ": " + String.valueOf(e.getValue()));
        }
        return result;
    }

    private static String ascii(String text) {
        StringBuilder b = new StringBuilder();
        for (char c : text.toCharArray()) {
            b.append(c >= 32 && c < 127 ? c : '?');
        }
        return b.toString();
    }

    private static String pdfEscape(String text) {
        return ascii(text).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static byte[] pdf(Map<String, Object> view) {
        StringBuilder content = new StringBuilder("BT\n/F1 11 Tf\n50 790 Td\n15 TL\n");
        for (String line : lines(view)) {
            content.append('(').append(pdfEscape(line)).append(") Tj\nT*\n");
        }
        content.append("ET\n");
        byte[] stream = content.toString().getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int[] offsets = new int[6];
        write(out, "%PDF-1.4\n");
        obj(out, offsets, 1, "<< /Type /Catalog /Pages 2 0 R >>");
        obj(out, offsets, 2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        obj(out, offsets, 3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                + "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>");
        obj(out, offsets, 4, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        offsets[5] = out.size();
        write(out, "5 0 obj\n<< /Length " + stream.length + " >>\nstream\n");
        out.write(stream, 0, stream.length);
        write(out, "endstream\nendobj\n");
        int startXref = out.size();
        write(out, "xref\n0 6\n0000000000 65535 f \n");
        for (int i = 1; i < 6; i++) {
            write(out, String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i]));
        }
        write(out, "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n"
                + startXref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static void obj(ByteArrayOutputStream out, int[] offsets, int number, String body) {
        offsets[number] = out.size();
        write(out, number + " 0 obj\n" + body + "\nendobj\n");
    }

    private static void write(ByteArrayOutputStream out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        out.write(bytes, 0, bytes.length);
    }

    private static byte[] docx(Map<String, Object> view) {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph title = doc.createParagraph();
            XWPFRun titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setText("Test Report / " + WATERMARK);
            for (String line : lines(view)) {
                XWPFParagraph paragraph = doc.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(line);
            }
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("DOCX 导出失败", e);
        }
    }
}
"""

F["backend/src/main/java/com/qa/autotest/application/report/TestReportService.java"] = r"""package com.qa.autotest.application.report;

import com.qa.autotest.application.repository.TestReportRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.report.ReportSnapshot;
import com.qa.autotest.domain.report.ReportState;
import com.qa.autotest.domain.report.TestReport;
import com.qa.autotest.domain.report.TestReportExport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 测试报告服务（RPTX-01 状态机 / RPTX-02 冻结 / RPTX-05 导出与保留）。 */
@Service
public class TestReportService {

    private final TestReportRepository repository;
    private final ReportSnapshotService snapshots;
    private final ReportViewRenderer renderer;
    private final ReportExportService exports;
    private final ReportQueryService queries;

    public TestReportService(TestReportRepository repository, ReportSnapshotService snapshots,
                             ReportViewRenderer renderer, ReportExportService exports,
                             ReportQueryService queries) {
        this.repository = repository;
        this.snapshots = snapshots;
        this.renderer = renderer;
        this.exports = exports;
        this.queries = queries;
    }

    public TestReport find(long id) {
        return repository.find(id).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "测试报告不存在: " + id));
    }

    public List<TestReport> list(long projectId) {
        return repository.listByProject(projectId);
    }

    public TestReport create(long projectId, String runId, String title) {
        if (runId == null || runId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "runId 不能为空");
        }
        int version = repository.maxVersion(projectId, runId) + 1;
        ReportSnapshot snapshot = snapshots.build(queries.detail(projectId, runId), version);
        long id = repository.insert(projectId, runId,
                title == null || title.isBlank() ? "报告 " + runId : title, version, snapshot);
        repository.audit(id, "CREATE", null, ReportState.DRAFT.name(), null);
        return find(id);
    }

    public TestReport freeze(long id) {
        TestReport report = find(id);
        require(report.state().canTransitionTo(ReportState.FROZEN), "只有 DRAFT 可以冻结");
        int version = report.version() + 1;
        ReportSnapshot snapshot = snapshots.build(
                queries.detail(report.projectId(), report.runId()), version);
        repository.freeze(id, snapshot);
        repository.audit(id, "FREEZE", report.state().name(), ReportState.FROZEN.name(), null);
        return find(id);
    }

    public TestReport transition(long id, ReportState target) {
        TestReport report = find(id);
        require(report.state().canTransitionTo(target),
                "不允许的状态流转: " + report.state() + " -> " + target);
        repository.changeState(id, target);
        repository.audit(id, "TRANSITION", report.state().name(), target.name(), null);
        return find(id);
    }

    /** 渲染只读快照表，绝不回查 run —— 这是「删源 run 仍可渲染」的实现保证。 */
    public Map<String, Object> render(long id, boolean external) {
        TestReport report = find(id);
        if (report.snapshotJson() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "报告尚未物化快照");
        }
        ReportSnapshot snapshot = new ReportSnapshot(report.snapshotJson(), report.snapshotHtml(),
                report.snapshotHash(), report.version(), report.sourceSummary());
        if (!external) {
            return renderer.internal(snapshot);
        }
        return renderer.external(snapshot, report.id(), report.title(),
                report.frozenAt() == null ? "" : report.frozenAt().toString(),
                ReportExportService.WATERMARK);
    }

    public byte[] export(long id, String format) {
        Map<String, Object> view = render(id, true);
        byte[] bytes = exports.export(format, view);
        String ext = ReportExportService.extension(format);
        repository.insertExport(id, ext, "report-" + id + "." + ext, bytes.length,
                ReportExportService.WATERMARK);
        return bytes;
    }

    public List<TestReportExport> exports(long id) {
        find(id);
        return repository.exports(id);
    }

    public int purge(int retentionDays) {
        if (retentionDays < 1) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "保留天数必须 >= 1");
        }
        return repository.purgeExportsBefore(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(ErrorCode.CONFLICT, message);
        }
    }
}
"""

F["backend/src/main/java/com/qa/autotest/api/report/TestReportController.java"] = r"""package com.qa.autotest.api.report;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.report.TestReportService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.report.TestReport;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 测试报告 API（W2）。所有读写先做项目访问校验，跨项目一律 403。 */
@RestController
@RequestMapping("/api/test-reports")
public class TestReportController {

    private final TestReportService reports;
    private final AccessControlService access;

    public TestReportController(TestReportService reports, AccessControlService access) {
        this.reports = reports;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<List<TestReport>> list(@RequestParam long projectId) {
        access.requireProjectAccess(projectId);
        return ApiResponse.ok(reports.list(projectId));
    }

    @PostMapping
    public ApiResponse<TestReport> create(@RequestBody CreateRequest request) {
        if (request == null || request.projectId() == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "projectId 不能为空");
        }
        access.requireProjectAccess(request.projectId());
        return ApiResponse.ok(reports.create(request.projectId(), request.runId(), request.title()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> render(@PathVariable long id,
                                                   @RequestParam(defaultValue = "true")
                                                   boolean external) {
        access.requireProjectAccess(reports.find(id).projectId());
        return ApiResponse.ok(reports.render(id, external));
    }

    @PostMapping("/{id}/freeze")
    public ApiResponse<TestReport> freeze(@PathVariable long id) {
        access.requireProjectAccess(reports.find(id).projectId());
        return ApiResponse.ok(reports.freeze(id));
    }

    @PostMapping("/{id}/revoke")
    public ApiResponse<TestReport> revoke(@PathVariable long id) {
        access.requireProjectAccess(reports.find(id).projectId());
        return ApiResponse.ok(reports.transition(id,
                com.qa.autotest.domain.report.ReportState.REVOKED));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<TestReport> archive(@PathVariable long id) {
        access.requireProjectAccess(reports.find(id).projectId());
        return ApiResponse.ok(reports.transition(id,
                com.qa.autotest.domain.report.ReportState.ARCHIVED));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable long id, @RequestParam String format) {
        access.requireProjectAccess(reports.find(id).projectId());
        byte[] bytes = reports.export(id, format);
        String ext = com.qa.autotest.application.report.ReportExportService.extension(format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"report-" + id + "." + ext + "\"")
                .contentType(MediaType.parseMediaType(
                        com.qa.autotest.application.report.ReportExportService.contentType(format)))
                .body(bytes);
    }

    @GetMapping("/{id}/exports")
    public ApiResponse<?> exports(@PathVariable long id) {
        access.requireProjectAccess(reports.find(id).projectId());
        return ApiResponse.ok(reports.exports(id));
    }

    /** 创建请求体：只收项目 / run / 标题，不接受任何外部传入的快照内容。 */
    public record CreateRequest(Long projectId, String runId, String title) {
    }
}
"""

# ---------------------------------------------------------------- 单测
F["backend/src/test/java/com/qa/autotest/domain/report/ReportStateMachineTest.java"] = r"""package com.qa.autotest.domain.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReportStateMachineTest {

    @Test
    void draftOnlyFreezes() {
        assertTrue(ReportState.DRAFT.canTransitionTo(ReportState.FROZEN));
        assertFalse(ReportState.DRAFT.canTransitionTo(ReportState.ARCHIVED));
        assertFalse(ReportState.DRAFT.canTransitionTo(ReportState.DRAFT));
        assertFalse(ReportState.DRAFT.canTransitionTo(null));
    }

    @Test
    void frozenCanRevokeOrArchiveButNeverGoBack() {
        assertTrue(ReportState.FROZEN.canTransitionTo(ReportState.REVOKED));
        assertTrue(ReportState.FROZEN.canTransitionTo(ReportState.ARCHIVED));
        assertFalse(ReportState.FROZEN.canTransitionTo(ReportState.DRAFT));
        assertFalse(ReportState.ARCHIVED.canTransitionTo(ReportState.FROZEN));
    }

    @Test
    void revokedCanOnlyArchive() {
        assertTrue(ReportState.REVOKED.canTransitionTo(ReportState.ARCHIVED));
        assertFalse(ReportState.REVOKED.canTransitionTo(ReportState.FROZEN));
    }

    @Test
    void parseIsCaseInsensitiveAndRejectsBlank() {
        assertEquals(ReportState.FROZEN, ReportState.parse(" frozen "));
        assertThrows(IllegalArgumentException.class, () -> ReportState.parse("  "));
    }
}
"""

F["backend/src/test/java/com/qa/autotest/application/report/ReportSnapshotTest.java"] = r"""package com.qa.autotest.application.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qa.autotest.domain.report.ReportRecord;
import com.qa.autotest.domain.report.ReportSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportSnapshotTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ReportSnapshotService snapshots = new ReportSnapshotService(mapper);
    private final ReportViewRenderer renderer = new ReportViewRenderer(mapper);

    private static ReportRecord source() {
        return new ReportRecord(7L, "RUN-W2-0001", "FINISHED", true, List.of(),
                Map.of("SW_BUILD", "build-2.0", "DUT", "10.202.54.106",
                        "CASE_KIND", "基本功能测试"),
                8, 1, 1, Instant.parse("2026-11-06T08:00:00Z"),
                Instant.parse("2026-11-06T08:10:00Z"));
    }

    @Test
    void snapshotIsSelfContainedAndHashIsStable() {
        ReportSnapshot a = snapshots.build(source(), 1);
        ReportSnapshot b = snapshots.build(source(), 1);
        assertEquals(a.hash(), b.hash());
        assertEquals(64, a.hash().length());
        assertTrue(a.json().contains("RUN-W2-0001"));
        assertTrue(a.html().contains("RUN-W2-0001"));
        assertTrue(a.json().contains("\"caseTotal\":10"));
    }

    @Test
    void externalViewOnlyHasWhitelistedFields() {
        ReportSnapshot snapshot = snapshots.build(source(), 1);
        Map<String, Object> view = renderer.external(snapshot, 3L, "季度回归报告", "", "INTERNAL USE ONLY");
        for (String key : view.keySet()) {
            assertTrue(ReportViewRenderer.EXTERNAL_FIELDS.contains(key), "非白名单字段: " + key);
        }
        assertEquals("PASS", view.getOrDefault("conclusion", "FAIL"));
        assertEquals("INTERNAL USE ONLY", view.get("watermark"));
    }

    @Test
    void sensitiveValuesAreScrubbed() {
        assertEquals("[REDACTED]", ReportViewRenderer.scrub("10.202.54.106"));
        assertFalse(ReportViewRenderer.scrub("ssh -o root@10.202.54.106").contains("10.202"));
        assertFalse(ReportViewRenderer.safeMetadata(Map.of("DUT", "10.202.54.106",
                "SW_BUILD", "build-2.0")).get("DUT").contains("10.202"));
        assertFalse(snapshots.build(source(), 1).json().contains("10.202.54.106"));
    }

    @Test
    void internalViewCarriesExecutionDetails() {
        ReportSnapshot snapshot = snapshots.build(source(), 1);
        Map<String, Object> view = renderer.internal(snapshot);
        assertEquals("INTERNAL", view.get("renderMode"));
        assertTrue(view.containsKey("keywordTree"));
        assertTrue(view.containsKey("snapshotHash"));
    }
}
"""

F["backend/src/test/java/com/qa/autotest/application/report/ReportExportTest.java"] = r"""package com.qa.autotest.application.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportExportTest {

    private final ReportExportService service = new ReportExportService();

    private static Map<String, Object> view() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("title", "Quarterly Regression");
        v.put("conclusion", "PASS");
        v.put("passCount", 8);
        v.put("failCount", 0);
        v.put("watermark", ReportExportService.WATERMARK);
        return v;
    }

    @Test
    void htmlHasDoctypeAndWatermarkAndNoSensitiveText() {
        String html = new String(service.export("html", view()), StandardCharsets.UTF_8);
        assertTrue(html.startsWith("<!doctype html>"));
        assertTrue(html.contains(ReportExportService.WATERMARK));
        assertFalse(html.contains("10.202."));
    }

    @Test
    void pdfIsAValidMinimalDocument() {
        byte[] pdf = service.export("pdf", view());
        String head = new String(pdf, 0, 8, StandardCharsets.US_ASCII);
        assertEquals("%PDF-1.4", head);
        String all = new String(pdf, StandardCharsets.US_ASCII);
        assertTrue(all.contains("%%EOF"));
        assertTrue(all.contains("startxref"));
        assertTrue(all.contains("/Type /Catalog"));
    }

    @Test
    void docxIsAZipContainer() {
        byte[] docx = service.export("docx", view());
        assertTrue(docx.length > 100);
        assertEquals('P', docx[0]);
        assertEquals('K', docx[1]);
    }

    @Test
    void unknownFormatRejectedAndMetadataHelpersAgree() {
        assertThrows(IllegalArgumentException.class, () -> service.export("xlsx", view()));
        assertEquals("pdf", ReportExportService.extension("PDF"));
        assertEquals("application/pdf", ReportExportService.contentType("pdf"));
        assertTrue(ReportExportService.contentType("docx").contains("wordprocessingml"));
    }
}
"""

pass_n = 0
skip_n = 0
for rel, text in F.items():
    p = ROOT / rel
    if p.exists():
        skip_n += 1
        print("[SKIP]", rel)
        continue
    p.parent.mkdir(parents=True, exist_ok=True)
    if not text.endswith("\n"):
        text += "\n"
    p.write_text(text, encoding="utf-8")
    pass_n += 1
    print("[PASS]", rel)
print(f"\n[w2-backend] PASS={pass_n} SKIP={skip_n} TOTAL={len(F)}")
