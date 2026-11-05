package com.qa.autotest.application.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qa.autotest.domain.report.CaseNode;
import com.qa.autotest.domain.report.ParsedReport;
import com.qa.autotest.domain.report.SuiteNode;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** output.xml 递归解析（RPT-01）。 */
class OutputXmlParserTest {

    private final OutputXmlParser parser = new OutputXmlParser();

    private static Path fixture(String name) throws Exception {
        return Path.of(OutputXmlParserTest.class.getClassLoader()
                .getResource("fixtures/output-xml/" + name).toURI());
    }

    private static SuiteNode findSuite(SuiteNode root, String name) {
        if (name.equals(root.name())) {
            return root;
        }
        for (SuiteNode child : root.suites()) {
            SuiteNode found = findSuite(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    @DisplayName("三层嵌套子套件 metadata 递归合并")
    void 嵌套三层子套件metadata递归合并() throws Exception {
        ParsedReport report = parser.parse(fixture("pabot-merged.xml"));

        assertEquals(10, report.metadata().size(), "十项 metadata 应全部合并出来");
        assertTrue(report.metadata().keySet().containsAll(
                java.util.List.of("CASE_ID", "RUN_ID", "QA_PROJECT", "DUT", "LINK",
                        "SW_BUILD", "BASELINE", "OPERATOR", "STARTED_AT", "CASE_KIND")));
        assertEquals("GPU-01", report.metadata().get("DUT"), "DUT 来自第三层子套件");
        assertEquals("Ubuntu 24.04.5 / OFED 24.10", report.metadata().get("SW_BUILD"));
        assertEquals("188~195 Gbps", report.metadata().get("BASELINE"), "旧格式 item 也应合并");
    }

    @Test
    @DisplayName("RF 7.5 <meta name>值</meta> 取元素文本，不能读成空串")
    void rf75的meta标签值取元素文本() throws Exception {
        ParsedReport report = parser.parse(fixture("rf75-meta.xml"));

        assertEquals("REAL-20261105", report.metadata().get("RUN_ID"));
        assertEquals("GPU-01", report.metadata().get("DUT"));
        assertEquals("[https://pabot.org/?ref=log|Pabot] result from 2 executions.",
                report.metadata().get("Pabot Info"), "含方括号与竖线的值不能被截断");
        assertEquals("ops", report.metadata().get("OPERATOR"), "子套件的 meta 覆盖父级同名键");
        assertFalse(report.root().metadata().containsKey("OPERATOR"), "根套件自身不含 OPERATOR");
    }

    @Test
    @DisplayName("子套件覆盖父级同名键")
    void 子套件覆盖父级同名键() throws Exception {
        ParsedReport report = parser.parse(fixture("pabot-merged.xml"));

        assertEquals("ops", report.metadata().get("OPERATOR"), "Shard-2 应覆盖根套件的 qa");
        SuiteNode root = report.root();
        assertEquals("qa", root.metadata().get("OPERATOR"), "根套件自身仍保留原值，供 RPT-02 追溯来源");
    }

    @Test
    @DisplayName("PASS/FAIL/SKIP 计数")
    void 三类结论计数() throws Exception {
        ParsedReport report = parser.parse(fixture("pabot-merged.xml"));

        assertEquals(2, report.passCount());
        assertEquals(1, report.failCount());
        assertEquals(1, report.skipCount());
        assertEquals(4, report.totalCount());
    }

    @Test
    @DisplayName("破损 XML 抛可读异常")
    void 破损XML抛可读异常() throws Exception {
        Path broken = fixture("broken.xml");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> parser.parse(broken));

        assertTrue(ex.getMessage().contains("output.xml 解析失败"), "异常消息应点明是 output.xml 解析失败");
    }

    @Test
    @DisplayName("空套件解析为 0 计数")
    void 空套件() throws Exception {
        ParsedReport report = parser.parse(fixture("empty.xml"));

        assertEquals("Empty", report.root().name());
        assertEquals(0, report.passCount());
        assertEquals(0, report.failCount());
        assertEquals(0, report.skipCount());
        assertTrue(report.root().cases().isEmpty());
    }

    @Test
    @DisplayName("旧 <metadata><item> 格式兼容")
    void 旧metadata格式兼容() throws Exception {
        ParsedReport report = parser.parse(fixture("pabot-merged.xml"));

        assertEquals("2026-10-31T06:12:07Z", report.metadata().get("STARTED_AT"));
        SuiteNode shard3 = findSuite(report.root(), "Shard-3");
        assertTrue(shard3 != null, "应能找到 Shard-3");
        assertEquals("188~195 Gbps", shard3.metadata().get("BASELINE"));
        assertEquals("2026-10-31T06:12:07Z", shard3.metadata().get("STARTED_AT"));
    }

    @Test
    @DisplayName("关键词与失败消息摘要可供 RPT-02 使用")
    void 关键词与失败消息摘要() throws Exception {
        ParsedReport report = parser.parse(fixture("pabot-merged.xml"));
        SuiteNode shard2 = findSuite(report.root(), "Shard-2");
        CaseNode failed = shard2.cases().get(0);

        assertEquals("FAIL", failed.status());
        assertEquals(310, failed.durationMs());
        assertTrue(failed.failureMessage().contains("packet loss 0.31%"));
        assertEquals("Check Packet Loss", failed.keywords().get(0).name());
    }
}
