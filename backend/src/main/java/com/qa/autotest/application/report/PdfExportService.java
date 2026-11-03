package com.qa.autotest.application.report;

import com.qa.autotest.domain.report.ExportMode;
import org.springframework.stereotype.Service;

/**
 * PDF 导出编排（RPT-05）：签发一次性打印 token → 无头浏览器打印前端打印页。
 *
 * <p>真正的打印与失败判定在 {@link PdfRenderer}；这里只负责 URL 与 token 生命周期。
 */
@Service
public class PdfExportService {

    private final PrintTokenService tokens;
    private final PdfRenderer renderer;
    private final ExportProperties properties;

    public PdfExportService(PrintTokenService tokens, PdfRenderer renderer,
                            ExportProperties properties) {
        this.tokens = tokens;
        this.renderer = renderer;
        this.properties = properties;
    }

    public byte[] render(long projectId, String runId, ExportMode mode) {
        PrintTokenService.IssuedToken issued = tokens.issue(projectId, runId, mode);
        String url = properties.printUrl(runId, issued.token());
        return renderer.render(url);
    }
}
