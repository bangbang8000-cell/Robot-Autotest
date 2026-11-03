package com.qa.autotest.api.caseasset;

import com.qa.autotest.application.caseasset.TestSetService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.domain.caseasset.TestCase;
import com.qa.autotest.domain.caseasset.TestSet;
import com.qa.autotest.domain.common.PageResult;
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

/** 用例集（CAS-02）。 */
@RestController
@RequestMapping("/api/projects/{projectId}/case-sets")
public class TestSetController {

    public record CreateSetRequest(String setCode, String name, String description) {
    }

    public record UpdateSetRequest(String name, String description) {
    }

    public record AddItemRequest(Long caseId) {
    }

    public record OrderRequest(List<Long> caseIds) {
    }

    private final TestSetService sets;

    public TestSetController(TestSetService sets) {
        this.sets = sets;
    }

    @GetMapping
    public ApiResponse<PageResult<TestSet>> page(@PathVariable long projectId,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(sets.page(projectId, keyword, page, Math.min(size, 200)));
    }

    @GetMapping("/{id}")
    public ApiResponse<TestSet> get(@PathVariable long projectId, @PathVariable long id) {
        return ApiResponse.ok(sets.get(projectId, id));
    }

    @PostMapping
    public ApiResponse<TestSet> create(@PathVariable long projectId,
                                       @RequestBody CreateSetRequest request) {
        return ApiResponse.ok(sets.create(projectId, request.setCode(), request.name(),
                request.description()));
    }

    @PutMapping("/{id}")
    public ApiResponse<TestSet> update(@PathVariable long projectId, @PathVariable long id,
                                       @RequestBody UpdateSetRequest request) {
        return ApiResponse.ok(sets.update(projectId, id, request.name(), request.description()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long projectId, @PathVariable long id) {
        sets.delete(projectId, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/items")
    public ApiResponse<List<TestCase>> items(@PathVariable long projectId,
                                             @PathVariable long id) {
        return ApiResponse.ok(sets.items(projectId, id));
    }

    @PostMapping("/{id}/items")
    public ApiResponse<Void> addItem(@PathVariable long projectId, @PathVariable long id,
                                     @RequestBody AddItemRequest request) {
        sets.addItem(projectId, id, request.caseId());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}/items/{caseId}")
    public ApiResponse<Void> removeItem(@PathVariable long projectId, @PathVariable long id,
                                        @PathVariable long caseId) {
        sets.removeItem(projectId, id, caseId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/order")
    public ApiResponse<Void> reorder(@PathVariable long projectId, @PathVariable long id,
                                     @RequestBody OrderRequest request) {
        sets.reorder(projectId, id, request.caseIds());
        return ApiResponse.ok(null);
    }
}
