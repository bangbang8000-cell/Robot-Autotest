package com.qa.autotest.domain.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 补录项叠加（HRD-02）：读取视图时合并 run_metadata 并按十项重算门禁。 */
class MetadataOverlayTest {

    private static final Instant STARTED = Instant.parse("2026-10-31T06:12:07Z");

    private static Map<String, String> partial() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("CASE_ID", "PERF-IPERF-200G-001");
        metadata.put("RUN_ID", "PERF-IPERF-200G-001_20261031_141207");
        metadata.put("QA_PROJECT", "gpu-nic-qa");
        metadata.put("OPERATOR", "master");
        return metadata;
    }

    private static Map<String, String> rest() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("DUT", "GPU-01");
        metadata.put("LINK", "GPU-01 mlx5_0 -> SW-01 Eth1/1");
        metadata.put("SW_BUILD", "Ubuntu 24.04.5 / OFED 24.10");
        metadata.put("BASELINE", "188~195 Gbps");
        metadata.put("STARTED_AT", "2026-10-31T06:12:07Z");
        metadata.put("CASE_KIND", "perf-throughput");
        return metadata;
    }

    private static ReportRecord record() {
        Map<String, String> metadata = partial();
        GateResult gate = MetadataGate.check(metadata);
        return new ReportRecord(1L, "PERF-IPERF-200G-001_20261031_141207", "FINISHED",
                gate.exportable(), gate.missing(), metadata, 2, 1, 0, STARTED, null);
    }

    @Test
    @DisplayName("补齐六项 → 可导出，缺失清空")
    void 补齐后门禁转绿() {
        ReportRecord source = record();
        assertFalse(source.exportable());

        ReportRecord merged = MetadataOverlay.apply(source, rest());

        assertTrue(merged.exportable());
        assertEquals(List.of(), merged.metadataMissing());
        assertEquals("GPU-01", merged.metadata().get("DUT"));
        assertEquals(10, merged.metadata().size());
        // 原视图不可变，仍是缺失状态
        assertFalse(source.exportable());
        assertEquals(6, source.metadataMissing().size());
    }

    @Test
    @DisplayName("覆盖已有键 → 新值生效")
    void 覆盖已有键() {
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("OPERATOR", "evergreen");
        overrides.put("DUT", "GPU-02");

        ReportRecord merged = MetadataOverlay.apply(record(), overrides);

        assertEquals("evergreen", merged.metadata().get("OPERATOR"));
        assertEquals("GPU-02", merged.metadata().get("DUT"));
        assertFalse(merged.exportable());
    }

    @Test
    @DisplayName("空白补录值不覆盖解析值")
    void 空白值不覆盖() {
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("OPERATOR", "   ");
        overrides.put("DUT", null);

        ReportRecord merged = MetadataOverlay.apply(record(), overrides);

        assertEquals("master", merged.metadata().get("OPERATOR"));
        assertTrue(merged.metadataMissing().contains("DUT"));
    }

    @Test
    @DisplayName("补录超长值 → 仍不可导出（缺失列表为空，超长被拦）")
    void 超长值仍被拦() {
        Map<String, String> overrides = new LinkedHashMap<>(rest());
        overrides.put("LINK", "x".repeat(MetadataGate.MAX_VALUE_LENGTH + 1));

        ReportRecord merged = MetadataOverlay.apply(record(), overrides);

        assertFalse(merged.exportable());
        assertEquals(List.of(), merged.metadataMissing());
        assertEquals(MetadataGate.MAX_VALUE_LENGTH + 1, merged.metadata().get("LINK").length());
    }

    @Test
    @DisplayName("无补录 → 原样返回同一视图")
    void 无补录不新建对象() {
        ReportRecord source = record();

        assertSame(source, MetadataOverlay.apply(source, null));
        assertSame(source, MetadataOverlay.apply(source, Map.of()));
    }

    @Test
    @DisplayName("与解析值相同的补录 → 原样返回")
    void 值未变化不新建对象() {
        ReportRecord source = record();
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("OPERATOR", "master");

        assertSame(source, MetadataOverlay.apply(source, overrides));
    }

    @Test
    @DisplayName("非必填键照常合并，不影响门禁")
    void 非必填键也合并() {
        Map<String, String> overrides = new LinkedHashMap<>(rest());
        overrides.put("BASELINE_NOTE", "before upgrade");

        ReportRecord merged = MetadataOverlay.apply(record(), overrides);

        assertTrue(merged.exportable());
        assertEquals("before upgrade", merged.metadata().get("BASELINE_NOTE"));
    }

    @Test
    @DisplayName("null 视图安全返回")
    void null视图() {
        assertEquals(null, MetadataOverlay.apply(null, rest()));
    }
}
