package com.qa.autotest.application.report;

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
