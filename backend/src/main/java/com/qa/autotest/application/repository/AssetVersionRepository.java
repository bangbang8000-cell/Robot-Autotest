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

    // ---- PUBX-02：同步（apply）所需的内容落库通道 ----

    /** 本项目里按 case_code 找用例（同步时决定「复用」还是「导入」）。 */
    Optional<Long> findCaseIdByCode(long projectId, String caseCode);

    /** 在本项目导入一条用例（同步时补齐平台有而本项目缺的），返回新 id。 */
    long insertCase(long projectId, String caseCode, String title, String priority,
                    Long operatorId);

    /** 同步时把本项目已有用例的标题/优先级对齐平台值（hash 覆盖这两个字段，不对齐就永远差一版）。 */
    void updateCaseFields(long projectId, long caseId, String title, String priority,
                          Long operatorId);

    /** 整体重建用例集条目（按给定顺序，seq 从 1 起）；旧条目全部删除。 */
    void replaceItems(long projectId, long setId, List<Long> orderedCaseIds, Long operatorId);

    /** 追加条目（ADD_ONLY 同步用）：只往末尾插入给定用例，不动已有条目的内容与顺序。 */
    void appendItems(long projectId, long setId, List<Long> caseIds, Long operatorId);
}
