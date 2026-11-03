package com.qa.autotest.application.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qa.autotest.domain.report.CaseNode;
import com.qa.autotest.domain.report.LogSpan;
import com.qa.autotest.domain.report.ParsedReport;
import com.qa.autotest.domain.report.SuiteNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 日志偏移口径测试（RPT-02）。
 *
 * <p>用 RPT-01 的手写 fixture 验证：解析阶段写入的 logOffset/logSpan 与读取阶段按序号取出的
 * 行完全对齐；分段读取不重叠、不丢行、不越界。
 */
class ReportLogReaderTest {

    private static Path fixture;
    private static ParsedReport parsed;
    private static final LogXmlReader READER = new LogXmlReader();

    @BeforeAll
    static void load() throws Exception {
        fixture = Path.of(ReportLogReaderTest.class.getClassLoader()
                .getResource("fixtures/output-xml/pabot-merged.xml").toURI());
        parsed = new OutputXmlParser().parse(fixture);
    }

    @Test
    @DisplayName("解析器写入偏移：带宽达标为 0:2，环境自检为 2:3，跳过用例为 null")
    void 解析器写入偏移() {
        CaseNode iperf = findCase("带宽达标");
        assertEquals(0L, iperf.logOffset());
        assertEquals("0:2", iperf.logSpan());

        CaseNode env = findCase("环境自检");
        assertEquals(2L, env.logOffset());
        assertEquals("2:3", env.logSpan());

        CaseNode skipped = findCase("外部依赖跳过");
        assertEquals(-1L, skipped.logOffset());
        assertNull(skipped.logSpan());
    }

    @Test
    @DisplayName("按区间读取：0:2 输出两行，关键字名与级别正确")
    void 按区间读取() {
        List<LogXmlReader.LogXmlLine> lines = READER.read(fixture, 0L, 2L);

        assertEquals(2, lines.size());
        assertEquals(0L, lines.get(0).seq());
        assertEquals("INFO", lines.get(0).level());
        assertEquals("Run Iperf", lines.get(0).keyword());
        assertTrue(lines.get(0).message().contains("iperf3 -c"));
        assertEquals(1L, lines.get(1).seq());
        assertTrue(lines.get(1).message().contains("192.5 Gbps"));
    }

    @Test
    @DisplayName("分段读取：0:1 + 1:2 与整段 0:2 等价，无重叠无丢行")
    void 分段读取无重叠() {
        List<LogXmlReader.LogXmlLine> first = READER.read(fixture, 0L, 1L);
        List<LogXmlReader.LogXmlLine> second = READER.read(fixture, 1L, 2L);
        List<LogXmlReader.LogXmlLine> whole = READER.read(fixture, 0L, 2L);

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        assertEquals(whole.size(), first.size() + second.size());
        assertEquals(whole.get(1).seq(), second.get(0).seq());
    }

    @Test
    @DisplayName("FAIL 级别日志保留原始级别")
    void fail级别日志() {
        List<LogXmlReader.LogXmlLine> lines = READER.read(fixture, 3L, 4L);

        assertEquals(1, lines.size());
        assertEquals("FAIL", lines.get(0).level());
        assertEquals("Check Packet Loss", lines.get(0).keyword());
        assertTrue(lines.get(0).message().contains("packet loss 0.31%"));
    }

    @Test
    @DisplayName("LogSpan.parse：非法与空区间返回 EMPTY")
    void span解析() {
        assertEquals(3L, LogSpan.parse("1:4").length());
        assertTrue(LogSpan.parse(null).empty());
        assertTrue(LogSpan.parse("x:y").empty());
        assertTrue(LogSpan.parse("5:5").empty());
    }

    private static CaseNode findCase(String name) {
        CaseNode found = search(parsed.root(), name);
        assertNotNull(found, "fixture 缺少用例: " + name);
        return found;
    }

    private static CaseNode search(SuiteNode suite, String name) {
        List<CaseNode> all = new ArrayList<>(suite.cases());
        for (SuiteNode child : suite.suites()) {
            CaseNode hit = search(child, name);
            if (hit != null) {
                return hit;
            }
        }
        return all.stream().filter(c -> name.equals(c.name())).findFirst().orElse(null);
    }
}
