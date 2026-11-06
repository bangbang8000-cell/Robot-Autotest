package com.qa.autotest.api.caseasset;

import com.qa.autotest.application.caseasset.AssetVersionService;
import com.qa.autotest.common.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用例资产版本接口（PUBX-01，FR-v2.1-10/11）。
 *
 * <p>挂在用例集下面（{@code /api/test-sets/{setId}/asset-version}）而不是另开一个
 * 顶级资源：版本是用例集的**属性**，脱离用例集谈版本没有意义。
 *
 * <p>读接口一律不写库：真正的版本推进只发生在 refresh 与内容变更钩子里。
 */
@RestController
@RequestMapping("/api/test-sets/{setId}/asset-version")
public class AssetVersionController {

    private final AssetVersionService versions;

    public AssetVersionController(AssetVersionService versions) {
        this.versions = versions;
    }

    @GetMapping
    public ApiResponse<AssetVersionService.VersionView> version(@PathVariable long setId,
                                                               @RequestParam long projectId) {
        return ApiResponse.ok(versions.version(projectId, setId));
    }

    @PostMapping("/refresh")
    public ApiResponse<AssetVersionService.VersionView> refresh(@PathVariable long setId,
                                                               @RequestParam long projectId) {
        return ApiResponse.ok(versions.refresh(projectId, setId));
    }

    /** 引用方：哪些项目把这个平台用例集做成了副本、各自落后多少。 */
    @GetMapping("/references")
    public ApiResponse<List<AssetVersionService.ReferenceView>> references(
            @PathVariable long setId, @RequestParam long projectId) {
        return ApiResponse.ok(versions.references(projectId, setId));
    }

    /** 把本项目这个用例集登记为平台用例集的副本（不复制内容，只记来源与对齐版本）。 */
    @PostMapping("/bind-copy")
    public ApiResponse<AssetVersionService.VersionView> bindCopy(@PathVariable long setId,
                                                                @RequestParam long projectId,
                                                                @RequestParam long platformSetId) {
        return ApiResponse.ok(versions.bindCopy(projectId, setId, platformSetId));
    }
}
