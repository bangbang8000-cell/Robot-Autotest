package com.qa.autotest.api.asset;

import com.qa.autotest.application.asset.ProjectAssetService;
import com.qa.autotest.common.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资产共享池接口（AST-05 / FR-v0.2-50、52）。
 *
 * <p>路径不带 {@code projectId} 的「共享」动作由资产归属项目判定（只能由它共享），
 * 带 {@code projectId} 的两个动作是「引用 / 解除引用」，只动 project_asset，不动物资本体。
 */
@RestController
@RequestMapping("/api")
public class AssetShareController {

    private final ProjectAssetService assets;

    public AssetShareController(ProjectAssetService assets) {
        this.assets = assets;
    }

    @PostMapping("/assets/{type}/{id}/share")
    public ApiResponse<Void> share(@PathVariable String type, @PathVariable long id) {
        assets.share(type, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/projects/{projectId}/assets/{type}/{id}")
    public ApiResponse<Void> reference(@PathVariable long projectId, @PathVariable String type,
                                       @PathVariable long id) {
        assets.reference(projectId, type, id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/projects/{projectId}/assets/{type}/{id}")
    public ApiResponse<Void> unreference(@PathVariable long projectId, @PathVariable String type,
                                         @PathVariable long id) {
        assets.unreference(projectId, type, id);
        return ApiResponse.ok(null);
    }
}
