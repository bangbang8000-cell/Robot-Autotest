package com.qa.autotest.api;

import com.qa.autotest.application.system.DeployRecordService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统级操作入口（HRD-02）。
 *
 * <p>只有「部署 / 回滚上报」：发布脚本切换版本后调用本端点，平台据此写
 * {@code system:deploy} 审计。平台自身不执行发布，因此这里没有副作用。
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final DeployRecordService deployRecords;

    public SystemController(DeployRecordService deployRecords) {
        this.deployRecords = deployRecords;
    }

    @PostMapping("/deploy")
    public ApiResponse<Map<String, Object>> deploy(@RequestBody(required = false) DeployRequest body) {
        if (body == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "version 与 mode 必填");
        }
        DeployRecordService.DeployRecord record =
                deployRecords.record(body.version(), body.mode(), body.note());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", record.version());
        result.put("action", record.action().name());
        result.put("recorded", true);
        return ApiResponse.ok(result);
    }

    /** 上报体：{@code {"version": "v1.0.3", "mode": "deploy", "note": "..."}}。 */
    public record DeployRequest(String version, String mode, String note) {
    }
}
