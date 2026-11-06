package com.qa.autotest.application.caseasset;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.AssetVersionRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.caseasset.AssetVersion;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用例资产版本与引用方检测（PUBX-01，FR-v2.1-10/11）。
 *
 * <p>三个不变量：
 * <ol>
 *   <li><b>内容 hash 是版本号的唯一驱动</b>：hash 不变就不 +1。做不到这条，
 *       "有新版本" 会天天亮，用户很快学会无视这个提示，等于没有。</li>
 *   <li><b>顺序算内容</b>：hash 覆盖 {@code seq_no}。用例集的价值是「按这个顺序跑」，
 *       挪一行就是内容变了。</li>
 *   <li><b>改名不算内容</b>：hash 只覆盖「哪些用例、什么顺序、各自的关键字段」，
 *       不含用例集名称/描述——否则改个错别字就给所有引用方发一条升级通知。</li>
 * </ol>
 *
 * <p>hash 的规范化格式固定为：按 seq_no 升序，每行
 * {@code seq_no|case_code|title|priority}，行间 {@code \n}，末尾不留空行。
 * <b>这个格式一旦发布就不能改</b>：改了等于给所有资产发一次假升级。
 */
@Service
public class AssetVersionService {

    private final AssetVersionRepository versions;
    private final AccessControlService access;
    private final AuditService audit;

    public AssetVersionService(AssetVersionRepository versions, AccessControlService access,
                               AuditService audit) {
        this.versions = versions;
        this.access = access;
        this.audit = audit;
    }

    /** 某个用例集的版本读数（含「是否有新版本」）。 */
    public record VersionView(int assetVersion, String contentHash, String forkState,
                              Integer syncedAssetVersion, String syncedContentHash,
                              Long sourceAssetId, boolean hasNewVersion,
                              Integer latestAssetVersion, String latestContentHash) {
    }

    /** 引用方一行。 */
    public record ReferenceView(long projectId, String projectCode, long copyAssetId,
                                Integer syncedAssetVersion, String forkState,
                                int latestAssetVersion, boolean hasNewVersion) {
    }

    public VersionView version(long projectId, long setId) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.READ);
        return viewOf(projectId, setId, versions.find(projectId, AssetVersion.TYPE_TESTSET, setId));
    }

    /**
     * 重算内容 hash 与版本号。内容没变则**不动版本**（返回原读数）。
     *
     * <p>这是幂等操作：可以放心在每次写操作后调用，也可以被定时任务反复扫。
     */
    @Transactional
    public VersionView refresh(long projectId, long setId) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.UPDATE);
        VersionView view = recompute(projectId, setId);
        audit.record("ASSET_VERSION_REFRESH", "asset_version", String.valueOf(setId),
                "project=" + projectId + "; version=" + view.assetVersion()
                        + "; hash=" + shortHash(view.contentHash()));
        return view;
    }

    /**
     * 用例集内容变更后的内部钩子（由 {@code TestSetService} 调用，不做权限检查）。
     *
     * <p>单独开一个不做鉴权的方法，是为了让「读接口不写库、写接口才动版本」这条边界清楚：
     * GET 一个用例集详情绝不该悄悄改版本号。
     */
    @Transactional
    public void onChanged(long projectId, long setId) {
        recompute(projectId, setId);
    }

    /**
     * 登记「本项目这份是平台资产的副本」。
     *
     * <p>只写来源与对齐时的版本/hash，**不复制内容**：副本内容由项目自己维护，
     * 什么时候对齐由 PUBX-02 的同步动作决定。
     */
    @Transactional
    public VersionView bindCopy(long projectId, long copySetId, long platformSetId) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.UPDATE);
        if (copySetId == platformSetId) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "副本不能指向自己");
        }
        AssetVersion platform = versions.find(AssetVersion.PLATFORM_PROJECT_ID,
                        AssetVersion.TYPE_TESTSET, platformSetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "平台用例集未登记版本信息: " + platformSetId));
        AssetVersion copy = recomputeRow(projectId, copySetId);
        versions.bindCopy(projectId, copySetId, platformSetId, platform.assetVersion(),
                platform.contentHash(), null);
        audit.record("ASSET_BIND_COPY", "asset_version", String.valueOf(copySetId),
                "project=" + projectId + "; platform=" + platformSetId
                        + "; syncedVersion=" + platform.assetVersion());
        return viewOf(projectId, copySetId,
                Optional.of(new AssetVersion(copy.id(), copy.projectId(), copy.assetType(),
                        copy.assetId(), copy.assetVersion(), copy.contentHash(),
                        platform.assetVersion(), platform.contentHash(),
                        AssetVersion.SYNCED, platformSetId)));
    }

    /**
     * 引用方列表：哪些项目把这个平台用例集做成了副本、各自落后多少。
     *
     * <p>「有新版本」= 版本号或 hash 任一不一致（{@link AssetVersion#behind}）。
     * 双条件不是冗余：hash 相同但版本号不同说明中间有过一次「内容来回改」，
     * 用户需要知道这件事。
     */
    public List<ReferenceView> references(long projectId, long platformSetId) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.READ);
        AssetVersion platform = versions.find(AssetVersion.PLATFORM_PROJECT_ID,
                        AssetVersion.TYPE_TESTSET, platformSetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "平台用例集未登记版本信息: " + platformSetId));
        List<ReferenceView> rows = new ArrayList<>();
        for (AssetVersion.CopyRef ref : versions.copies(platformSetId)) {
            boolean behind = ref.syncedContentHash() == null
                    || !ref.syncedContentHash().equals(platform.contentHash())
                    || ref.syncedAssetVersion() == null
                    || ref.syncedAssetVersion() != platform.assetVersion();
            rows.add(new ReferenceView(ref.projectId(), ref.projectCode(), ref.copyAssetId(),
                    ref.syncedAssetVersion(), ref.forkState(), platform.assetVersion(), behind));
        }
        return rows;
    }

    /** 规范化内容 hash（见类注释里写死的格式）。 */
    public static String hash(List<AssetVersion.ContentRow> rows) {
        StringBuilder builder = new StringBuilder();
        for (AssetVersion.ContentRow row : rows) {
            builder.append(row.seqNo()).append('|')
                    .append(nullSafe(row.caseCode())).append('|')
                    .append(nullSafe(row.title())).append('|')
                    .append(nullSafe(row.priority())).append('\n');
        }
        return sha256(builder.toString());
    }

    private VersionView recompute(long projectId, long setId) {
        return viewOf(projectId, setId, Optional.of(recomputeRow(projectId, setId)));
    }

    /** 计算并落库；内容没变时不写库（连 updated_at 都不动，避免噪音审计）。 */
    private AssetVersion recomputeRow(long projectId, long setId) {
        String hash = hash(versions.content(projectId, setId));
        Optional<AssetVersion> found = versions.find(projectId, AssetVersion.TYPE_TESTSET, setId);
        if (found.isEmpty()) {
            AssetVersion draft = new AssetVersion(0L, projectId, AssetVersion.TYPE_TESTSET, setId,
                    1, hash, null, null, AssetVersion.SYNCED, null);
            versions.insert(draft, null);
            return versions.find(projectId, AssetVersion.TYPE_TESTSET, setId).orElse(draft);
        }
        AssetVersion existing = found.get();
        if (hash.equals(existing.contentHash())) {
            return existing;
        }
        int next = existing.assetVersion() + 1;
        versions.updateContent(projectId, AssetVersion.TYPE_TESTSET, setId, hash, next, null);
        return new AssetVersion(existing.id(), existing.projectId(), existing.assetType(),
                existing.assetId(), next, hash, existing.syncedAssetVersion(),
                existing.syncedContentHash(), existing.forkState(), existing.sourceAssetId());
    }

    private VersionView viewOf(long projectId, long setId, Optional<AssetVersion> found) {
        if (found.isEmpty()) {
            return new VersionView(0, null, AssetVersion.SYNCED, null, null, null,
                    false, null, null);
        }
        AssetVersion current = found.get();
        Long source = current.sourceAssetId();
        if (source == null) {
            return new VersionView(current.assetVersion(), current.contentHash(),
                    current.forkState(), current.syncedAssetVersion(),
                    current.syncedContentHash(), null, false, null, null);
        }
        Optional<AssetVersion> platform = versions.find(AssetVersion.PLATFORM_PROJECT_ID,
                AssetVersion.TYPE_TESTSET, source);
        if (platform.isEmpty()) {
            return new VersionView(current.assetVersion(), current.contentHash(),
                    current.forkState(), current.syncedAssetVersion(),
                    current.syncedContentHash(), source, false, null, null);
        }
        AssetVersion latest = platform.get();
        return new VersionView(current.assetVersion(), current.contentHash(),
                current.forkState(), current.syncedAssetVersion(), current.syncedContentHash(),
                source, current.behind(latest.assetVersion(), latest.contentHash()),
                latest.assetVersion(), latest.contentHash());
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String shortHash(String hash) {
        return hash == null ? "" : hash.substring(0, Math.min(12, hash.length()));
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
