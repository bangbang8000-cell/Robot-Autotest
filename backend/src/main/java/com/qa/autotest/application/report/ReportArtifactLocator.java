package com.qa.autotest.application.report;

import java.nio.file.Path;

/**
 * output.xml 定位端口（RPT-02）。生产实现是 {@link ReportFileLocator}，
 * 测试可注入轻量实现，不必拖起运行/产物仓储。
 */
public interface ReportArtifactLocator {

    Path locateOutputXml(long projectId, String runId);
}
