package com.qa.autotest.application.caseasset;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.AssetVersionRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.caseasset.AssetVersion;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        return platformReferences(platformSetId);
    }

    /**
     * 平台用例集的版本读数（平台库页面用）：调用方已校验管理员，这里不做项目绑定。
     * 平台集的 {@code project_id} 是 0（版本行）或 NULL（用例集行），都走同一个 viewOf。
     */
    public VersionView platformVersion(long setAssetId) {
        return viewOf(AssetVersion.PLATFORM_PROJECT_ID, setAssetId,
                versions.find(AssetVersion.PLATFORM_PROJECT_ID, AssetVersion.TYPE_TESTSET, setAssetId));
    }

    /** 平台用例集的引用方（平台库只读页用，不做项目绑定）。 */
    public List<ReferenceView> platformReferences(long platformSetId) {
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

    // ---------------------------------------------------------------- PUBX-02

    /** 差异一行。type ∈ ADDED（平台新增）/ MODIFIED（内容或顺序不同）/ REMOVED（平台已删）。 */
    public record DiffRow(String type, String caseCode, String title, String detail) {
    }

    /**
     * 副本与来源平台的逐行差异（按 case_code 匹配；只读，不写库不写审计）。
     *
     * <p>「顺序不同」归入 MODIFIED：三类口径是产品定的（新增/修改/删除），
     * 顺序差异在 detail 里写明两边的 seq。
     */
    public List<DiffRow> diff(long projectId, long copySetId) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.READ);
        AssetVersion copy = requireBound(projectId, copySetId);
        List<AssetVersion.ContentRow> platformRows =
                versions.content(AssetVersion.PLATFORM_PROJECT_ID, copy.sourceAssetId());
        List<AssetVersion.ContentRow> copyRows = versions.content(projectId, copySetId);
        return diffRows(platformRows, copyRows);
    }

    /** 纯函数，便于单测：平台行 vs 副本行 → 三类差异。 */
    static List<DiffRow> diffRows(List<AssetVersion.ContentRow> platformRows,
                                  List<AssetVersion.ContentRow> copyRows) {
        Map<String, AssetVersion.ContentRow> platformByCode = byCode(platformRows);
        Map<String, AssetVersion.ContentRow> copyByCode = byCode(copyRows);
        List<DiffRow> rows = new ArrayList<>();
        for (AssetVersion.ContentRow platform : platformRows) {
            AssetVersion.ContentRow copy = copyByCode.get(platform.caseCode());
            if (copy == null) {
                rows.add(new DiffRow("ADDED", platform.caseCode(), platform.title(),
                        "平台 seq=" + platform.seqNo() + "，副本缺这条"));
                continue;
            }
            List<String> changes = new ArrayList<>();
            if (!nullSafe(platform.title()).equals(nullSafe(copy.title()))) {
                changes.add("标题「" + copy.title() + "」→「" + platform.title() + "」");
            }
            if (!nullSafe(platform.priority()).equals(nullSafe(copy.priority()))) {
                changes.add("优先级 " + copy.priority() + "→" + platform.priority());
            }
            if (platform.seqNo() != copy.seqNo()) {
                changes.add("顺序 " + copy.seqNo() + "→" + platform.seqNo());
            }
            if (!changes.isEmpty()) {
                rows.add(new DiffRow("MODIFIED", platform.caseCode(), platform.title(),
                        String.join("；", changes)));
            }
        }
        for (AssetVersion.ContentRow copy : copyRows) {
            if (!platformByCode.containsKey(copy.caseCode())) {
                rows.add(new DiffRow("REMOVED", copy.caseCode(), copy.title(),
                        "平台已删除（副本 seq=" + copy.seqNo() + "）"));
            }
        }
        return rows;
    }

    /**
     * 同步策略（N9）：FULL 整体覆盖（默认）；ADD_ONLY 只补平台新增，不动副本已有内容与顺序。
     */
    public enum SyncStrategy {
        FULL, ADD_ONLY
    }

    public static SyncStrategy parseStrategy(String raw) {
        if (raw == null || raw.isBlank()) {
            return SyncStrategy.FULL;
        }
        try {
            return SyncStrategy.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "不支持的同步策略: " + raw);
        }
    }

    /**
     * 同步：副本内容对齐平台库（缺的用例按 case_code 导入本项目、条目按平台顺序整体重建），
     * 并把 synced 读数推到平台当前版本、fork_state 回 SYNCED。
     */
    @Transactional
    public VersionView sync(long projectId, long copySetId) {
        return sync(projectId, copySetId, SyncStrategy.FULL);
    }

    @Transactional
    public VersionView sync(long projectId, long copySetId, SyncStrategy strategy) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.UPDATE);
        AssetVersion copy = requireBound(projectId, copySetId);
        AssetVersion platform = versions.find(AssetVersion.PLATFORM_PROJECT_ID,
                        AssetVersion.TYPE_TESTSET, copy.sourceAssetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "平台用例集未登记版本信息: " + copy.sourceAssetId()));
        return strategy == SyncStrategy.ADD_ONLY
                ? syncAddOnly(projectId, copySetId, copy, platform)
                : syncFull(projectId, copySetId, copy, platform);
    }

    private VersionView syncFull(long projectId, long copySetId, AssetVersion copy,
                                 AssetVersion platform) {
        Long userId = TenantContext.userId();
        List<AssetVersion.ContentRow> platformRows =
                versions.content(AssetVersion.PLATFORM_PROJECT_ID, copy.sourceAssetId());
        List<Long> orderedCaseIds = new ArrayList<>();
        int imported = 0;
        int updated = 0;
        for (AssetVersion.ContentRow row : platformRows) {
            Long caseId = versions.findCaseIdByCode(projectId, row.caseCode()).orElse(null);
            if (caseId == null) {
                caseId = versions.insertCase(projectId, row.caseCode(), row.title(),
                        row.priority(), userId);
                imported++;
            } else {
                // hash 覆盖 title/priority：复用的行不对齐平台值，同步后永远差一版
                versions.updateCaseFields(projectId, caseId, row.title(), row.priority(), userId);
                updated++;
            }
            orderedCaseIds.add(caseId);
        }
        versions.replaceItems(projectId, copySetId, orderedCaseIds, userId);
        recomputeRow(projectId, copySetId);
        versions.updateSync(projectId, AssetVersion.TYPE_TESTSET, copySetId,
                platform.assetVersion(), platform.contentHash(), AssetVersion.SYNCED, userId);
        audit.record("ASSET_SYNC", "asset_version", String.valueOf(copySetId),
                "project=" + projectId + "; platform=" + copy.sourceAssetId()
                        + "; toVersion=" + platform.assetVersion()
                        + "; imported=" + imported + "; updated=" + updated
                        + "; rows=" + orderedCaseIds.size());
        return viewOf(projectId, copySetId,
                versions.find(projectId, AssetVersion.TYPE_TESTSET, copySetId));
    }

    /**
     * ADD_ONLY（N9，可选「不覆盖」同步）：只把平台新增的用例补到副本末尾。
     *
     * <p>已有条目的标题/优先级/顺序一律不动，平台已删的本地条目也不删。
     * synced 读数仍推进到平台当前版本（表示「平台最新内容已看过并已吸收新增」），
     * 但 fork_state 由「同步后内容是否真的一致」决定：还有差异就是 DIVERGED，
     * 这样「我保留了本地改写」这件事在 UI 上是显式的，而不是假装已经对齐。
     */
    private VersionView syncAddOnly(long projectId, long copySetId, AssetVersion copy,
                                    AssetVersion platform) {
        Long userId = TenantContext.userId();
        List<AssetVersion.ContentRow> platformRows =
                versions.content(AssetVersion.PLATFORM_PROJECT_ID, copy.sourceAssetId());
        Map<String, AssetVersion.ContentRow> copyByCode =
                byCode(versions.content(projectId, copySetId));
        List<Long> appended = new ArrayList<>();
        int imported = 0;
        int reused = 0;
        for (AssetVersion.ContentRow row : platformRows) {
            if (copyByCode.containsKey(row.caseCode())) {
                continue;
            }
            Long caseId = versions.findCaseIdByCode(projectId, row.caseCode()).orElse(null);
            if (caseId == null) {
                caseId = versions.insertCase(projectId, row.caseCode(), row.title(),
                        row.priority(), userId);
                imported++;
            } else {
                reused++;
            }
            appended.add(caseId);
        }
        if (!appended.isEmpty()) {
            versions.appendItems(projectId, copySetId, appended, userId);
        }
        AssetVersion recomputed = recomputeRow(projectId, copySetId);
        String forkState = recomputed.contentHash().equals(platform.contentHash())
                ? AssetVersion.SYNCED : AssetVersion.DIVERGED;
        versions.updateSync(projectId, AssetVersion.TYPE_TESTSET, copySetId,
                platform.assetVersion(), platform.contentHash(), forkState, userId);
        audit.record("ASSET_SYNC", "asset_version", String.valueOf(copySetId),
                "project=" + projectId + "; platform=" + copy.sourceAssetId()
                        + "; strategy=ADD_ONLY; toVersion=" + platform.assetVersion()
                        + "; imported=" + imported + "; reused=" + reused
                        + "; appended=" + appended.size() + "; fork=" + forkState);
        return viewOf(projectId, copySetId,
                versions.find(projectId, AssetVersion.TYPE_TESTSET, copySetId));
    }

    /**
     * 保持分叉：只把 fork_state 置为 DIVERGED，副本内容与 synced 读数**原样保留**——
     * 分叉是一个「声明」，不是一次修改；用户随时可以再点同步回到对齐。
     */
    @Transactional
    public VersionView diverge(long projectId, long copySetId) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.UPDATE);
        AssetVersion copy = requireBound(projectId, copySetId);
        versions.updateSync(projectId, AssetVersion.TYPE_TESTSET, copySetId,
                copy.syncedAssetVersion(), copy.syncedContentHash(), AssetVersion.DIVERGED,
                TenantContext.userId());
        audit.record("ASSET_DIVERGE", "asset_version", String.valueOf(copySetId),
                "project=" + projectId + "; platform=" + copy.sourceAssetId()
                        + "; keptVersion=" + copy.assetVersion());
        return viewOf(projectId, copySetId,
                versions.find(projectId, AssetVersion.TYPE_TESTSET, copySetId));
    }

    /** 副本行必须存在且已绑定来源，否则 400（未绑定的集没有「与平台比」的对象）。 */
    private AssetVersion requireBound(long projectId, long copySetId) {
        AssetVersion copy = versions.find(projectId, AssetVersion.TYPE_TESTSET, copySetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ARGUMENT,
                        "用例集未登记版本信息: " + copySetId));
        if (copy.sourceAssetId() == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "用例集未绑定平台资产副本: " + copySetId);
        }
        return copy;
    }

    private static Map<String, AssetVersion.ContentRow> byCode(List<AssetVersion.ContentRow> rows) {
        Map<String, AssetVersion.ContentRow> map = new LinkedHashMap<>();
        for (AssetVersion.ContentRow row : rows) {
            map.putIfAbsent(row.caseCode(), row);
        }
        return map;
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
