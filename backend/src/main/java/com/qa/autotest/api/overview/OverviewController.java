package com.qa.autotest.api.overview;

import com.qa.autotest.application.overview.OverviewService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.domain.overview.ProjectOverview;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目总览（OVW-01，PRD §3.4）。
 *
 * <p>只读聚合接口：一次返回四区指标，避免前端为一个首页打 5~6 个分页接口再自己数。
 * 参数超界由服务层夹到边界（`recent` 1~20、`days` 1~90），不返回 400——首页不该因为参数长这样而白屏。
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class OverviewController {

    private final OverviewService overview;

    public OverviewController(OverviewService overview) {
        this.overview = overview;
    }

    @GetMapping("/overview")
    public ApiResponse<ProjectOverview> overview(
            @PathVariable long projectId,
            @RequestParam(defaultValue = "8") int recent,
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(overview.load(projectId, recent, days));
    }
}
