package com.qa.autotest.api.editor;

import com.qa.autotest.application.editor.EditorKeywordService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.domain.editor.EditorKeyword;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 编辑器关键词元数据接口（EDT-04 / FR-v0.3-12 ~ 15）。
 *
 * <p>面板不写死关键词：展开 / 搜索时走 {@code GET /api/editor/keywords}；
 * 断网或后端不可用时由宿主适配层降级为空数据，面板显示「暂无数据」。
 */
@RestController
@RequestMapping("/api/editor")
public class EditorKeywordController {

    private final EditorKeywordService keywords;

    public EditorKeywordController(EditorKeywordService keywords) {
        this.keywords = keywords;
    }

    @GetMapping("/keywords")
    public ApiResponse<List<EditorKeyword>> search(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String library,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(keywords.search(projectId, q, library, source, limit));
    }

    @PostMapping("/keywords")
    public ApiResponse<EditorKeyword> create(
            @RequestBody EditorKeywordService.KeywordCommand command) {
        return ApiResponse.ok(keywords.create(command));
    }

    @PutMapping("/keywords/{id}")
    public ApiResponse<EditorKeyword> update(
            @PathVariable long id,
            @RequestBody EditorKeywordService.KeywordCommand command) {
        return ApiResponse.ok(keywords.update(id, command));
    }

    @DeleteMapping("/keywords/{id}")
    public ApiResponse<Void> delete(@PathVariable long id,
                                    @RequestParam(required = false) Long projectId) {
        keywords.delete(id, projectId);
        return ApiResponse.ok(null);
    }
}
