package com.qa.autotest.application.report;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 报告导出配置（RPT-05），前缀 {@code report.export}。
 *
 * <p>打印页基址必须是前端运行地址（默认 Vite dev 5173）；无头浏览器从这里加载
 * {@code /reports/:runId/print?token=...}。Chrome 可执行文件也可用环境变量
 * {@code REPORT_PDF_CHROME} 覆盖，便于在别的机器上换路径。
 */
@Component
@ConfigurationProperties(prefix = "report.export")
public class ExportProperties {

    /** 打印页前端基址。 */
    private String printBaseUrl = "http://127.0.0.1:5173";

    /** 无头浏览器可执行文件（可空，空时按固定顺序探测）。 */
    private String chromeExecutable;

    /** 单次打印超时（秒），DEV-PLAN §5 口径为 90s。 */
    private int timeoutSeconds = 90;

    public String getPrintBaseUrl() {
        return printBaseUrl;
    }

    public void setPrintBaseUrl(String printBaseUrl) {
        this.printBaseUrl = printBaseUrl;
    }

    public String getChromeExecutable() {
        return chromeExecutable;
    }

    public void setChromeExecutable(String chromeExecutable) {
        this.chromeExecutable = chromeExecutable;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /** 拼接打印页 URL：基址去掉末尾斜杠，RUN_ID 做 URL 编码。 */
    public String printUrl(String runId, String token) {
        String base = printBaseUrl == null ? "" : printBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String encodedRunId = URLEncoder.encode(runId, StandardCharsets.UTF_8);
        return base + "/reports/" + encodedRunId + "/print?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
