package com.qa.autotest.application.repository;

import com.qa.autotest.domain.caseasset.AssetVersion;
import java.util.List;
import java.util.Optional;

/**
 * 资产版本仓储（PUBX-01）。
 *
 * <p>所有按项目查的方法都带 {@code project_id} 条件（TenantGuard 口径）；
 * 平台资产那一行用 {@code project_id = 0}，所以查询里出现的是
 * {@code project_id IN (?, 0)}，不是「不带条件」。
 */
public interface AssetVersionRepository {

    Optional<AssetVersion> find(long projectId, String assetType, long assetId);

    /** 用例集：项目私有或平台共享都认（平台行的 project_id 是 NULL）。 */
    Optional<AssetVersion.PlatformSet> findSet(long assetId);

    void insert(AssetVersion draft, Long operatorId);

    void updateContent(long projectId, String assetType, long assetId, String contentHash,
                       int assetVersion, Long operatorId);

    void updateSync(long projectId, String assetType, long assetId, Integer syncedAssetVersion,
                    String syncedContentHash, String forkState, Long operatorId);

    /** 登记副本来源：写 source_asset_id 与对齐时的版本/hash。 */
    void bindCopy(long projectId, long copyAssetId, long platformAssetId,
                  Integer syncedAssetVersion, String syncedContentHash, Long operatorId);

    /** 用例集内容（按 seq_no）：平台集与项目集走同一份 SQL，靠 {@code project_id IS NULL} 兼容。 */
    List<AssetVersion.ContentRow> content(long projectId, long assetId);

    /** 某个平台用例集的所有副本登记。 */
    List<AssetVersion.CopyRef> copies(long platformAssetId);
}
