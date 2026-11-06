package com.qa.autotest.api.importing;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.importing.ImportSandbox;
import com.qa.autotest.application.importing.PreflightService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.importing.PreflightReport;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 导入预检 API（W4）。上传的 zip 一律先进沙箱，未通过预检不写业务表。 */
@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final PreflightService preflight;
    private final AccessControlService access;

    public ImportController(PreflightService preflight, AccessControlService access) {
        this.preflight = preflight;
        this.access = access;
    }

    @GetMapping("/capabilities")
    public ApiResponse<Map<String, Object>> capabilities() {
        return ApiResponse.ok(Map.of(
                "types", List.of("pytest", "robot"),
                "maxEntries", ImportSandbox.MAX_ENTRIES,
                "maxTotalBytes", ImportSandbox.MAX_TOTAL_BYTES,
                "maxFileBytes", ImportSandbox.MAX_FILE_BYTES,
                "readOnlyLanding", true));
    }

    @GetMapping("/validate-path")
    public ApiResponse<Boolean> validatePath(@RequestParam String path) {
        return ApiResponse.ok(ImportSandbox.isSafeRelativePath(path));
    }

    @PostMapping("/preflight")
    public ApiResponse<PreflightReport> preflight(@RequestParam long projectId,
                                                  @RequestParam String sourceType,
                                                  @RequestParam("file") MultipartFile file) {
        access.requireProjectAccess(projectId);
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "上传文件为空");
        }
        try (var stream = file.getInputStream()) {
            return ApiResponse.ok(preflight.preflight(projectId, sourceType,
                    file.getOriginalFilename() == null ? "upload.zip" : file.getOriginalFilename(),
                    stream, Path.of("artifacts", "import-sandbox")));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, e.getMessage());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "预检失败: " + e.getMessage());
        }
    }
}
