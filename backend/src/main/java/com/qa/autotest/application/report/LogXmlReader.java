package com.qa.autotest.application.report;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.stereotype.Component;

/**
 * output.xml 日志分段读取（RPT-02）。
 *
 * <p>不使用 DOM，也不整文件读入内存：StAX 顺序扫描，按 {@code <msg>} 的全局序号过滤，
 * 命中区间后立即停止；返回行数上限由调用方（服务层）控制。序号口径必须与入库时
 * {@link OutputXmlParser} 完全一致（文档顺序、0 基、包含套件级消息）。
 */
@Component
public final class LogXmlReader {

    /**
     * 一条日志行。
     *
     * @param seq     全局序号（与 test_result.log_offset / log_span 同一口径）
     * @param level   INFO / WARN / FAIL / DEBUG ...
     * @param keyword 所在关键字名（不在关键字内时为空串）
     * @param message 消息正文（已去首尾空白）
     */
    public record LogXmlLine(long seq, String level, String keyword, String message) {
    }

    /**
     * 读取序号区间 {@code [fromInclusive, toExclusive)} 内的消息。
     *
     * @throws IllegalArgumentException 文件不可读或 XML 破损（由全局异常处理器翻译成 400）
     */
    public List<LogXmlLine> read(Path file, long fromInclusive, long toExclusive) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("output.xml 不存在: " + file);
        }
        if (toExclusive <= fromInclusive) {
            return List.of();
        }
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        try (InputStream stream = Files.newInputStream(file)) {
            XMLStreamReader reader = factory.createXMLStreamReader(stream);
            try {
                return scan(reader, fromInclusive, toExclusive);
            } finally {
                reader.close();
            }
        } catch (XMLStreamException ex) {
            throw new IllegalArgumentException("output.xml 解析失败（日志读取）：" + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new IllegalArgumentException("读取 output.xml 失败：" + ex.getMessage(), ex);
        }
    }

    private List<LogXmlLine> scan(XMLStreamReader reader, long fromInclusive, long toExclusive)
            throws XMLStreamException {
        Deque<String> keywords = new ArrayDeque<>();
        List<LogXmlLine> lines = new ArrayList<>();
        StringBuilder message = null;
        String level = null;
        long seq = 0L;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "kw" -> keywords.push(attribute(reader, "name"));
                    case "msg" -> {
                        if (seq >= toExclusive) {
                            return lines;
                        }
                        message = new StringBuilder();
                        level = attribute(reader, "level");
                        seq++;
                    }
                    default -> {
                        // 其余节点与日志无关
                    }
                }
            } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                if (message != null) {
                    message.append(reader.getText());
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "msg" -> {
                        if (message != null) {
                            long lineSeq = seq - 1;
                            if (lineSeq >= fromInclusive && lineSeq < toExclusive) {
                                String keyword = keywords.isEmpty() ? "" : keywords.peek();
                                lines.add(new LogXmlLine(lineSeq,
                                        level == null ? "INFO" : level.trim().toUpperCase(java.util.Locale.ROOT),
                                        keyword == null ? "" : keyword, message.toString().strip()));
                            }
                        }
                        message = null;
                        level = null;
                    }
                    case "kw" -> {
                        if (!keywords.isEmpty()) {
                            keywords.pop();
                        }
                    }
                    default -> {
                        // 忽略
                    }
                }
            }
        }
        return lines;
    }

    private static String attribute(XMLStreamReader reader, String name) {
        return reader.getAttributeValue(null, name);
    }
}
