package com.qa.autotest.domain.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** metadata 十项门禁（RPT-07）判定细节。 */
class MetadataGateTest {

    private static Map<String, String> complete() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("CASE_ID", "PERF-IPERF-200G-001");
        metadata.put("RUN_ID", "PERF-IPERF-200G-001_20261031_141207");
        metadata.put("QA_PROJECT", "gpu-nic-qa");
        metadata.put("DUT", "GPU-01");
        metadata.put("LINK", "GPU-01 mlx5_0 -> SW-01 Eth1/1");
        metadata.put("SW_BUILD", "Ubuntu 24.04.5 / OFED 24.10");
        metadata.put("BASELINE", "188~195 Gbps");
        metadata.put("OPERATOR", "master");
        metadata.put("STARTED_AT", "2026-10-31T06:12:07Z");
        metadata.put("CASE_KIND", "perf-throughput");
        return metadata;
    }

    @Test
    @DisplayName("十项齐全 → 可导出")
    void 全齐通过() {
        GateResult result = MetadataGate.check(complete());

        assertTrue(result.exportable());
        assertTrue(result.missing().isEmpty());
        assertTrue(result.tooLong().isEmpty());
        assertEquals(null, result.missingCsv());
    }

    @Test
    @DisplayName("缺一项 → 不可导出且只报该项")
    void 缺一项() {
        Map<String, String> metadata = complete();
        metadata.remove("SW_BUILD");

        GateResult result = MetadataGate.check(metadata);

        assertFalse(result.exportable());
        assertEquals(List.of("SW_BUILD"), result.missing());
    }

    @Test
    @DisplayName("空串视为缺失")
    void 空串视为缺失() {
        Map<String, String> metadata = complete();
        metadata.put("DUT", "");

        GateResult result = MetadataGate.check(metadata);

        assertFalse(result.exportable());
        assertEquals(List.of("DUT"), result.missing());
    }

    @Test
    @DisplayName("全空白视为缺失")
    void 全空白视为缺失() {
        Map<String, String> metadata = complete();
        metadata.put("LINK", "   \t  ");

        GateResult result = MetadataGate.check(metadata);

        assertFalse(result.exportable());
        assertEquals(List.of("LINK"), result.missing());
    }

    @Test
    @DisplayName("小写同名键不算命中")
    void 小写键不算命中() {
        Map<String, String> metadata = complete();
        metadata.remove("OPERATOR");
        metadata.put("operator", "master");

        GateResult result = MetadataGate.check(metadata);

        assertFalse(result.exportable());
        assertEquals(List.of("OPERATOR"), result.missing());
    }

    @Test
    @DisplayName("值超过 40 字符 → 不可导出")
    void 超长值判不通过() {
        Map<String, String> metadata = complete();
        metadata.put("DUT", "A".repeat(MetadataGate.MAX_VALUE_LENGTH + 1));

        GateResult result = MetadataGate.check(metadata);

        assertFalse(result.exportable());
        assertTrue(result.missing().isEmpty());
        assertEquals(List.of("DUT"), result.tooLong());
    }

    @Test
    @DisplayName("缺失列表顺序按 REQUIRED 固定")
    void 缺失列表顺序稳定() {
        Map<String, String> metadata = complete();
        metadata.remove("OPERATOR");
        metadata.remove("CASE_ID");
        metadata.remove("BASELINE");

        GateResult result = MetadataGate.check(metadata);

        assertEquals(List.of("CASE_ID", "BASELINE", "OPERATOR"), result.missing());
    }

    @Test
    @DisplayName("metadata_missing CSV 生成正确")
    void 缺失CSV生成正确() {
        Map<String, String> metadata = complete();
        metadata.remove("OPERATOR");
        metadata.remove("CASE_ID");
        metadata.remove("BASELINE");

        GateResult result = MetadataGate.check(metadata);

        assertEquals("CASE_ID,BASELINE,OPERATOR", result.missingCsv());
    }

    @Test
    @DisplayName("null 表 → 十项全部缺失")
    void null表全部缺失() {
        GateResult result = MetadataGate.check(null);

        assertFalse(result.exportable());
        assertEquals(MetadataGate.REQUIRED, result.missing());
    }
}
