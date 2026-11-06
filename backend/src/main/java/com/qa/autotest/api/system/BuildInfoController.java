package com.qa.autotest.api.system;

import com.qa.autotest.application.system.BuildInfoService;
import com.qa.autotest.common.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 关于页数据接口（NAV-03 / FR-v2.0-11）：构建信息 + 环境自检。 */
@RestController
@RequestMapping("/api/system")
public class BuildInfoController {

    private final BuildInfoService buildInfo;
    private final JdbcTemplate jdbc;

    public BuildInfoController(BuildInfoService buildInfo, JdbcTemplate jdbc) {
        this.buildInfo = buildInfo;
        this.jdbc = jdbc;
    }

    @GetMapping("/build-info")
    public ApiResponse<Map<String, Object>> get() {
        Map<String, Object> body = new LinkedHashMap<>(buildInfo.info());
        body.put("selfCheck", buildInfo.selfCheck(jdbc));
        return ApiResponse.ok(body);
    }
}
