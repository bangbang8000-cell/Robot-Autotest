package com.qa.autotest.application.execution;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * output.xml 轻量探测：只看能否解析、test 节点数量、以及是否存在单用例超时。
 * 禁用 DTD 与外部实体。
 */
public final class OutputXmlProbe {

    public Probe probe(Path xml) {
        if (xml == null || !Files.exists(xml)) {
            return new Probe(false, 0, "文件不存在");
        }
        try {
            Document document = parse(xml);
            int total = document.getElementsByTagName("test").getLength();
            return new Probe(true, total, null);
        } catch (Exception ex) {
            return new Probe(false, 0, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    /**
     * 单用例超时条数（FR-v0.4-24）。
     *
     * <p>RF 命中 {@code Test Timeout} 时会写出固定文案
     * {@code "Test timeout 30 seconds exceeded."}，落在 test 的 {@code status} 下 {@code msg} 节点。
     * 解析失败或文件不存在返回 0（超时判定不能反过来把正常运行判成超时）。
     */
    public int timedOutTests(Path xml) {
        if (xml == null || !Files.exists(xml)) {
            return 0;
        }
        try {
            Document document = parse(xml);
            NodeList messages = document.getElementsByTagName("msg");
            int count = 0;
            for (int i = 0; i < messages.getLength(); i++) {
                String text = messages.item(i).getTextContent();
                if (text != null && text.contains("Test timeout") && text.contains("exceeded")) {
                    count++;
                }
            }
            return count;
        } catch (Exception ex) {
            return 0;
        }
    }

    private Document parse(Path xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(xml.toFile());
    }

    public record Probe(boolean parsed, int totalTests, String error) {
    }
}
