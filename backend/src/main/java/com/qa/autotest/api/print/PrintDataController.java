package com.qa.autotest.api.print;

import com.qa.autotest.application.report.PrintReportService;
import com.qa.autotest.application.report.PrintReportView;
import com.qa.autotest.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 打印页数据端点（RPT-05）。
 *
 * <p>路径放在 {@code /api/print/**}，由 {@code WebConfig} 排除登录拦截：
 * 授权凭据就是 URL 上的一次性 token（签发时已校验项目权限，10 分钟有效）。
 * token 无效/过期返回中文 400，前端打印页据此显示提示而不是白屏。
 */
@RestController
@RequestMapping("/api/print")
public class PrintDataController {

    private final PrintReportService prints;

    public PrintDataController(PrintReportService prints) {
        this.prints = prints;
    }

    @GetMapping("/{runId}")
    public ApiResponse<PrintReportView> view(@PathVariable String runId,
                                             @RequestParam String token) {
        return ApiResponse.ok(prints.view(runId, token));
    }
}
