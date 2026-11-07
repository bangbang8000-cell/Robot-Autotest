package com.qa.autotest.api.caseasset;

import com.qa.autotest.application.caseasset.TestSetService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.domain.caseasset.TestCase;
import com.qa.autotest.domain.common.PageResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台用例集库（PUBX-01 / N9）：跨项目可见的**只读**视图。
 *
 * <p>平台集不属于任何项目（{@code test_set.project_id IS NULL}），所以路径不带 projectId；
 * 写操作一律不在这里——平台库只读是刻意的约束，避免「在平台库里改一条、所有副本静默变」。
 */
@RestController
@RequestMapping("/api/platform/case-sets")
public class PlatformCaseSetController {

    private final TestSetService sets;

    public PlatformCaseSetController(TestSetService sets) {
        this.sets = sets;
    }

    @GetMapping
    public ApiResponse<PageResult<TestSetService.PlatformSetRow>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(sets.platformPage(keyword, page, Math.min(size, 200)));
    }

    @GetMapping("/{id}/items")
    public ApiResponse<List<TestCase>> items(@PathVariable long id) {
        return ApiResponse.ok(sets.platformItems(id));
    }
}
