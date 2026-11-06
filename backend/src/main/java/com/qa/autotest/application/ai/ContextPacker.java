package com.qa.autotest.application.ai;

import com.qa.autotest.application.report.ReportViewRenderer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 上下文打包 / 脱敏 / 截断（AIX-03）。脱敏复用 C5 的同一套 scrub 判据。 */
@Component
public class ContextPacker {

    public static final int DEFAULT_MAX_CHARS = 8000;

    public String pack(Map<String, String> context, int maxChars) {
        int limit = maxChars <= 0 ? DEFAULT_MAX_CHARS : Math.min(maxChars, DEFAULT_MAX_CHARS);
        Map<String, String> safe = new LinkedHashMap<>();
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> entry : context.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey();
            String value = ReportViewRenderer.scrub(entry.getValue());
            safe.put(key, value);
        }
        for (Map.Entry<String, String> entry : safe.entrySet()) {
            String line = entry.getKey() + ": " + entry.getValue() + "\n";
            if (b.length() + line.length() > limit) {
                b.append("... [TRUNCATED]\n");
                break;
            }
            b.append(line);
        }
        return b.toString();
    }

    public boolean containsSensitive(String packed) {
        return !packed.equals(ReportViewRenderer.scrub(packed));
    }
}
