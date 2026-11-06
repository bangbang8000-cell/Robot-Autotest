package com.qa.autotest.application.report;

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
            assertTrue(ReportFieldWhitelist.EXTERNAL_FIELDS.contains(key), "非白名单字段: " + key);
        }
        assertEquals("FAIL", view.get("conclusion"));
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
