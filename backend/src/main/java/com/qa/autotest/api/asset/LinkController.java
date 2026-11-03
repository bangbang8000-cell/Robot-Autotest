package com.qa.autotest.api.asset;

import com.qa.autotest.application.asset.LinkService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.domain.asset.Link;
import com.qa.autotest.domain.common.PageResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 链路台账（AST-02）。 */
@RestController
@RequestMapping("/api/projects/{projectId}/links")
public class LinkController {

    private final LinkService links;

    public LinkController(LinkService links) {
        this.links = links;
    }

    @GetMapping
    public ApiResponse<PageResult<Link>> page(@PathVariable long projectId,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(links.page(projectId, keyword, page, Math.min(size, 200)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Link> get(@PathVariable long projectId, @PathVariable long id) {
        return ApiResponse.ok(links.get(projectId, id));
    }

    @PostMapping
    public ApiResponse<Link> create(@PathVariable long projectId,
                                    @RequestBody LinkService.LinkCommand command) {
        return ApiResponse.ok(links.create(projectId, command));
    }

    @PutMapping("/{id}")
    public ApiResponse<Link> update(@PathVariable long projectId, @PathVariable long id,
                                    @RequestBody LinkService.LinkCommand command) {
        return ApiResponse.ok(links.update(projectId, id, command));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long projectId, @PathVariable long id) {
        links.delete(projectId, id);
        return ApiResponse.ok(null);
    }
}
