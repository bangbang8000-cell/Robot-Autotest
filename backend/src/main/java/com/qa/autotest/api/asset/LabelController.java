package com.qa.autotest.api.asset;

import com.qa.autotest.application.asset.LabelService;
import com.qa.autotest.common.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 技术域标签（AST-04）。 */
@RestController
@RequestMapping("/api/projects/{projectId}/labels")
public class LabelController {

    public record AddLabelRequest(String labelKey, String labelValue, String description) {
    }

    private final LabelService labels;

    public LabelController(LabelService labels) {
        this.labels = labels;
    }

    @GetMapping
    public ApiResponse<List<String>> values(@PathVariable long projectId,
                                            @RequestParam String key) {
        return ApiResponse.ok(labels.values(projectId, key));
    }

    @PostMapping
    public ApiResponse<Void> add(@PathVariable long projectId,
                                 @RequestBody AddLabelRequest request) {
        labels.add(projectId, request.labelKey(), request.labelValue(), request.description());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{labelKey}/{labelValue}")
    public ApiResponse<Void> remove(@PathVariable long projectId, @PathVariable String labelKey,
                                    @PathVariable String labelValue) {
        labels.remove(projectId, labelKey, labelValue);
        return ApiResponse.ok(null);
    }
}
