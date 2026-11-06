package com.qa.autotest.domain.report;

/** 物化快照（RPTX-02）：JSON 是数据真源，HTML 是渲染缓存，hash 校验两者未被篡改。 */
public record ReportSnapshot(String json, String html, String hash, int version, String sourceSummary) {
}
