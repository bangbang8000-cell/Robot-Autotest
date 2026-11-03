package com.qa.autotest.api.editor;

import com.qa.autotest.application.editor.EditorSaveService;
import com.qa.autotest.application.editor.EditorSaveService.SaveCommand;
import com.qa.autotest.application.editor.EditorSaveService.SaveResult;
import com.qa.autotest.application.editor.LineDiff.DiffLine;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 编辑器保存链路接口（EDT-09 / FR-v0.3-17、19 ~ 23）。
 *
 * <p>只读用户直连本接口也会被拒：服务层 {@code require(CASE, CREATE)} 先于任何写库动作。
 */
@RestController
@RequestMapping("/api/editor")
public class EditorSaveController {

    public record SaveRequest(Long projectId, Long fileId, String path, String content,
                              String sha256, Integer baseVersion, Boolean parseOk,
                              String comment) {
    }

    public record RollbackRequest(Long projectId, String path, Integer targetVersion,
                                  String comment) {
    }

    private final EditorSaveService saves;

    public EditorSaveController(EditorSaveService saves) {
        this.saves = saves;
    }

    @PutMapping("/save")
    public ApiResponse<SaveResult> save(@RequestBody SaveRequest request) {
        long projectId = requireProject(request.projectId());
        return ApiResponse.ok(saves.save(new SaveCommand(projectId, request.fileId(),
                request.path(), request.content(), request.sha256(), request.baseVersion(),
                request.parseOk(), request.comment())));
    }

    @PostMapping("/rollback")
    public ApiResponse<SaveResult> rollback(@RequestBody RollbackRequest request) {
        long projectId = requireProject(request.projectId());
        if (request.path() == null || request.path().isBlank() || request.targetVersion() == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "缺少 path 或 targetVersion");
        }
        return ApiResponse.ok(
                saves.rollback(projectId, request.path(), request.targetVersion(), request.comment()));
    }

    /** 两版 diff（FR-v0.3-21），返回逐行标注。 */
    @GetMapping("/diff")
    public ApiResponse<List<DiffLine>> diff(@RequestParam long projectId,
                                            @RequestParam String path,
                                            @RequestParam int from,
                                            @RequestParam int to) {
        return ApiResponse.ok(saves.diff(projectId, path, from, to));
    }

    private static long requireProject(Long projectId) {
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "缺少 projectId");
        }
        return projectId;
    }
}
