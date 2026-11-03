package com.qa.autotest.application.execution;

import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * output.xml 轻量探测：只看能否解析与 test 节点数量。禁用 DTD 与外部实体。
 */
public final class OutputXmlProbe {

    public Probe probe(Path xml) {
        if (xml == null || !Files.exists(xml)) {
            return new Probe(false, 0, "文件不存在");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder().parse(xml.toFile());
            int total = document.getElementsByTagName("test").getLength();
            return new Probe(true, total, null);
        } catch (Exception ex) {
            return new Probe(false, 0, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    public record Probe(boolean parsed, int totalTests, String error) {
    }
}
