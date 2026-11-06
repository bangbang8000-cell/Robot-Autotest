package com.qa.autotest.domain.caseasset;

/**
 * 资产内容版本（PUBX-01）。
 *
 * <p>一个用例集一行。<b>平台资产</b>那行的 {@code projectId = 0}（与报告模板的
 * 「平台级 project_id = 0」同一套约定，不新造 is_platform）；<b>项目副本</b>那行的
 * {@code projectId} 是副本所在项目，{@code sourceAssetId} 指回平台用例集。
 *
 * <p>版本口径两条，写在字段注释里也写在文档里：
 * <ol>
 *   <li>{@code assetVersion} 只在 {@code contentHash} 变化时 +1——打开页面、改描述都不涨版本；</li>
 *   <li>{@code contentHash} 覆盖 {@code seq_no}：<b>调整顺序算内容变更</b>。
 *       用例集的价值就是「按这个顺序跑」，顺序不算内容的版本号是假的。</li>
 * </ol>
 */
public record AssetVersion(long id,
                           long projectId,
                           String assetType,
                           long assetId,
                           int assetVersion,
                           String contentHash,
                           Integer syncedAssetVersion,
                           String syncedContentHash,
                           String forkState,
                           Long sourceAssetId) {

    /** 平台资产的 project_id 约定值。 */
    public static final long PLATFORM_PROJECT_ID = 0L;
    public static final String TYPE_TESTSET = "TESTSET";
    public static final String SYNCED = "SYNCED";
    public static final String DIVERGED = "DIVERGED";

    public boolean platform() {
        return projectId == PLATFORM_PROJECT_ID;
    }

    /** 副本是否落后于平台：版本号或 hash 任一不一致就算落后（双条件，缺一不可）。 */
    public boolean behind(int platformVersion, String platformHash) {
        if (platformHash == null) {
            return false;
        }
        if (syncedContentHash == null || syncedAssetVersion == null) {
            return true;
        }
        return !platformHash.equals(syncedContentHash) || platformVersion != syncedAssetVersion;
    }

    /** 用例集内容的一行（规范化后用于 hash 与差异比对）。 */
    public record ContentRow(int seqNo, long caseId, String caseCode, String title,
                             String priority) {
    }

    /** 引用方（某个项目对平台资产的副本登记）。 */
    public record CopyRef(long projectId, String projectCode, long copyAssetId,
                          Integer syncedAssetVersion, String syncedContentHash,
                          String forkState) {
    }

    /** 平台用例集的基本信息（副本列表与差异对比要用）。 */
    public record PlatformSet(long id, String setCode, String name, String description) {
    }
}
