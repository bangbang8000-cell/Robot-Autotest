package com.qa.autotest.application.repository;

import java.util.Optional;

/**
 * 项目对平台资产的引用（AST-05）。
 *
 * <p>语义分工：资产本体的归属（{@code owner_scope} / {@code project_id}）在 device/link/credential；
 * 这张表只回答「哪个项目能看到哪条平台资产」。项目私有资产的可见性**不依赖**本表。
 */
public interface ProjectAssetRepository {

    /** 资产本体的归属快照。 */
    record AssetScope(long assetId, Long projectId, String ownerScope) {

        public boolean platform() {
            return "PLATFORM".equals(ownerScope);
        }
    }

    /** 查资产本体归属；不存在返回空。 */
    Optional<AssetScope> scopeOf(String assetType, long assetId);

    /** 私有资产转为平台共享：owner_scope=PLATFORM、project_id=NULL。 */
    void shareToPlatform(String assetType, long assetId, long ownerProjectId);

    /** 项目引用一条平台资产；已存在则只更新别名。 */
    void addReference(long projectId, String assetType, long assetId, String alias, Long operatorId);

    /** 只删引用，不动物资本体；返回是否真的删除了一行。 */
    boolean removeReference(long projectId, String assetType, long assetId);
}
