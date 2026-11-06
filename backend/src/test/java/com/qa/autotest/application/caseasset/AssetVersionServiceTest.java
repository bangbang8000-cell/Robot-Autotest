package com.qa.autotest.application.caseasset;

import com.qa.autotest.application.caseasset.AssetVersionService.ReferenceView;
import com.qa.autotest.application.caseasset.AssetVersionService.VersionView;
import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.AssetVersionRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.caseasset.AssetVersion;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用例资产版本（PUBX-01，FR-v2.1-10/11）。
 *
 * <p>不连库、不启 Spring。三条最容易写假的用例：
 * ① 版本号随任何写操作 +1（错）——hash 不变就不许涨，否则「打开一次页面」也发升级通知；
 * ② 顺序不进 hash（错）——用例集的价值是「按这个顺序跑」；
 * ③ behind 只比版本号（不完整）——hash 与版本号双条件，缺一不可。
 */
class AssetVersionServiceTest {

    private static final long PROJECT = 11L;
    private static final long PLATFORM_SET = 101L;
    private static final long COPY_SET = 201L;

    private FakeVersionRepository repo;
    private AssetVersionService service;

    @BeforeEach
    void setUp() {
        repo = new FakeVersionRepository();
        service = new AssetVersionService(repo, new PermissiveAccess(), new SilentAudit());
    }

    // ---------- hash 规范化 ----------

    @Test
    @DisplayName("hash 覆盖顺序：同样的行换序后 hash 必须不同")
    void 顺序算内容() {
        List<AssetVersion.ContentRow> rows = List.of(
                new AssetVersion.ContentRow(1, 1L, "TC-001", "登录", "P1"),
                new AssetVersion.ContentRow(2, 2L, "TC-002", "登出", "P2"));
        List<AssetVersion.ContentRow> swapped = List.of(
                new AssetVersion.ContentRow(2, 2L, "TC-002", "登出", "P2"),
                new AssetVersion.ContentRow(1, 1L, "TC-001", "登录", "P1"));
        assertNotEquals(AssetVersionService.hash(rows), AssetVersionService.hash(swapped),
                "顺序不进 hash 的版本号是假的");
    }

    @Test
    @DisplayName("hash 覆盖行内字段：改标题/优先级都算内容变更")
    void 行内字段算内容() {
        List<AssetVersion.ContentRow> base = List.of(
                new AssetVersion.ContentRow(1, 1L, "TC-001", "登录", "P1"));
        assertNotEquals(AssetVersionService.hash(base),
                AssetVersionService.hash(List.of(
                        new AssetVersion.ContentRow(1, 1L, "TC-001", "登录(改)", "P1"))));
        assertNotEquals(AssetVersionService.hash(base),
                AssetVersionService.hash(List.of(
                        new AssetVersion.ContentRow(1, 1L, "TC-001", "登录", "P0"))));
    }

    @Test
    @DisplayName("hash 稳定：同一份内容反复计算结果一致，且是 64 位十六进制")
    void hash稳定且格式固定() {
        List<AssetVersion.ContentRow> rows = List.of(
                new AssetVersion.ContentRow(1, 1L, "TC-001", "登录", "P1"),
                new AssetVersion.ContentRow(2, 2L, "TC-002", "登出", "P2"));
        String first = AssetVersionService.hash(rows);
        assertEquals(first, AssetVersionService.hash(rows));
        assertEquals(64, first.length());
        assertTrue(first.matches("[0-9a-f]{64}"), first);
    }

    // ---------- 版本推进 ----------

    @Test
    @DisplayName("首次 refresh：落一行 version=1，hash 非 null")
    void 首次refresh建行() {
        repo.contentRows.put(setKey(PROJECT, COPY_SET), sampleRows());
        VersionView view = service.refresh(PROJECT, COPY_SET);
        assertEquals(1, view.assetVersion());
        assertNotNull(view.contentHash());
        assertEquals(1, repo.insertCount.get(), "首次应 insert 一行");
        assertEquals(0, repo.updateContentCount.get(), "首次不走 update");
    }

    @Test
    @DisplayName("内容不变：重复 refresh 不涨版本、不写库（幂等）")
    void 内容不变不涨版本() {
        repo.contentRows.put(setKey(PROJECT, COPY_SET), sampleRows());
        VersionView first = service.refresh(PROJECT, COPY_SET);
        long inserts = repo.insertCount.get();
        VersionView second = service.refresh(PROJECT, COPY_SET);
        assertEquals(first.assetVersion(), second.assetVersion());
        assertEquals(first.contentHash(), second.contentHash());
        assertEquals(inserts, repo.insertCount.get(), "内容不变不得再 insert");
        assertEquals(0L, repo.updateContentCount.get(), "内容不变不得 update（连审计噪音都不许有）");
    }

    @Test
    @DisplayName("加一行内容：版本 +1，hash 变")
    void 内容变化版本加一() {
        repo.contentRows.put(setKey(PROJECT, COPY_SET), sampleRows());
        VersionView first = service.refresh(PROJECT, COPY_SET);
        repo.contentRows.put(setKey(PROJECT, COPY_SET), List.of(
                new AssetVersion.ContentRow(1, 1L, "TC-001", "登录", "P1"),
                new AssetVersion.ContentRow(2, 2L, "TC-002", "登出", "P2"),
                new AssetVersion.ContentRow(3, 3L, "TC-003", "改密", "P1")));
        VersionView second = service.refresh(PROJECT, COPY_SET);
        assertEquals(first.assetVersion() + 1, second.assetVersion());
        assertNotEquals(first.contentHash(), second.contentHash());
    }

    @Test
    @DisplayName("只调整顺序：版本同样 +1（顺序算内容）")
    void 换序版本加一() {
        repo.contentRows.put(setKey(PROJECT, COPY_SET), sampleRows());
        VersionView first = service.refresh(PROJECT, COPY_SET);
        repo.contentRows.put(setKey(PROJECT, COPY_SET), sampleRows().reversed());
        VersionView second = service.refresh(PROJECT, COPY_SET);
        assertEquals(first.assetVersion() + 1, second.assetVersion());
    }

    // ---------- behind 双条件 ----------

    @Test
    @DisplayName("behind：hash 同但版本号不同 → 落后（来回改过的中间态要暴露）")
    void behind版本号不同() {
        AssetVersion copy = row(COPY_SET, 3, "h-1", 2, "h-1", AssetVersion.SYNCED, PLATFORM_SET);
        assertTrue(copy.behind(4, "h-1"), "hash 相同版本不同也要算落后");
    }

    @Test
    @DisplayName("behind：版本同但 hash 不同 → 落后")
    void behind哈希不同() {
        AssetVersion copy = row(COPY_SET, 3, "h-old", 3, "h-old", AssetVersion.SYNCED, PLATFORM_SET);
        assertTrue(copy.behind(3, "h-new"));
    }

    @Test
    @DisplayName("behind：双条件都对齐 → 不落后；从没对齐过（synced null）→ 落后")
    void behind对齐与未对齐() {
        AssetVersion synced = row(COPY_SET, 3, "h-1", 3, "h-1", AssetVersion.SYNCED, PLATFORM_SET);
        assertTrue(!synced.behind(3, "h-1"));
        assertTrue(synced.behind(3, null) == false, "平台 hash 未知时不误报");
        AssetVersion never = row(COPY_SET, 3, "h-1", null, null, AssetVersion.SYNCED, PLATFORM_SET);
        assertTrue(never.behind(3, "h-1"), "从没对齐过的副本必须报落后");
    }

    @Test
    @DisplayName("viewOf：副本行带 latest 读数，hasNewVersion 反映与平台的差距")
    void viewOf带平台读数() {
        repo.contentRows.put(setKey(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET), sampleRows());
        service.refresh(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET);
        repo.contentRows.put(setKey(PROJECT, COPY_SET), sampleRows());
        service.refresh(PROJECT, COPY_SET);
        service.bindCopy(PROJECT, COPY_SET, PLATFORM_SET);

        // 平台内容变化 → 副本应该看到 hasNewVersion=true 与 latest 读数
        repo.contentRows.put(setKey(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET), List.of(
                new AssetVersion.ContentRow(1, 1L, "TC-001", "登录", "P1"),
                new AssetVersion.ContentRow(2, 2L, "TC-002", "登出", "P2"),
                new AssetVersion.ContentRow(3, 3L, "TC-003", "改密", "P1")));
        service.refresh(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET);

        VersionView view = service.version(PROJECT, COPY_SET);
        assertEquals(PLATFORM_SET, view.sourceAssetId());
        assertTrue(view.hasNewVersion(), "平台升版后副本必须看到新版本提示");
        assertNotNull(view.latestAssetVersion());
        assertNotNull(view.latestContentHash());
    }

    // ---------- bindCopy 与 references ----------

    @Test
    @DisplayName("bindCopy：平台集未登记版本 → 404；指向自己 → 400")
    void bindCopy守门() {
        BusinessException notFound = assertThrows(BusinessException.class,
                () -> service.bindCopy(PROJECT, COPY_SET, PLATFORM_SET));
        assertEquals(ErrorCode.NOT_FOUND, notFound.errorCode());

        repo.contentRows.put(setKey(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET), sampleRows());
        service.refresh(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET);
        BusinessException self = assertThrows(BusinessException.class,
                () -> service.bindCopy(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET, PLATFORM_SET));
        assertEquals(ErrorCode.INVALID_ARGUMENT, self.errorCode());
    }

    @Test
    @DisplayName("bindCopy：登记后 fork_state=SYNCED 且 synced 读数 = 平台当时版本")
    void bindCopy登记对齐点() {
        repo.contentRows.put(setKey(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET), sampleRows());
        VersionView platform = service.refresh(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET);
        repo.contentRows.put(setKey(PROJECT, COPY_SET), sampleRows());
        service.refresh(PROJECT, COPY_SET);

        VersionView bound = service.bindCopy(PROJECT, COPY_SET, PLATFORM_SET);
        assertEquals(AssetVersion.SYNCED, bound.forkState());
        assertEquals(platform.assetVersion(), bound.syncedAssetVersion());
        assertEquals(platform.contentHash(), bound.syncedContentHash());

        // 引用方列表：平台侧能看到这个副本，且当前不落后
        List<ReferenceView> refs = service.references(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET);
        assertEquals(1, refs.size());
        assertEquals(PROJECT, refs.get(0).projectId());
        assertTrue(!refs.get(0).hasNewVersion(), "刚对齐的副本不该报落后");
    }

    @Test
    @DisplayName("references：平台升版后副本 hasNewVersion=true，latest 版本号正确")
    void references报落后() {
        repo.contentRows.put(setKey(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET), sampleRows());
        service.refresh(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET);
        repo.contentRows.put(setKey(PROJECT, COPY_SET), sampleRows());
        service.refresh(PROJECT, COPY_SET);
        service.bindCopy(PROJECT, COPY_SET, PLATFORM_SET);

        repo.contentRows.put(setKey(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET), List.of(
                new AssetVersion.ContentRow(1, 1L, "TC-001", "登录", "P1"),
                new AssetVersion.ContentRow(2, 2L, "TC-002", "登出", "P2"),
                new AssetVersion.ContentRow(3, 3L, "TC-003", "改密", "P1")));
        VersionView latest = service.refresh(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET);

        List<ReferenceView> refs = service.references(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET);
        assertTrue(refs.get(0).hasNewVersion());
        assertEquals(latest.assetVersion(), refs.get(0).latestAssetVersion());
    }

    // ---------- 桩 ----------

    private static List<AssetVersion.ContentRow> sampleRows() {
        return List.of(
                new AssetVersion.ContentRow(1, 1L, "TC-001", "登录", "P1"),
                new AssetVersion.ContentRow(2, 2L, "TC-002", "登出", "P2"));
    }

    private static String setKey(long projectId, long setId) {
        return projectId + ":" + setId;
    }

    private static AssetVersion row(long projectId, int version, String hash,
                                    Integer syncedVersion, String syncedHash,
                                    String forkState, Long source) {
        return new AssetVersion(1L, projectId, AssetVersion.TYPE_TESTSET, COPY_SET,
                version, hash, syncedVersion, syncedHash, forkState, source);
    }

    /** 伪仓储：与真实 JDBC 同一套契约——insert 只落一行，find 才是读回的唯一途径。 */
    static final class FakeVersionRepository implements AssetVersionRepository {

        final Map<String, AssetVersion> rows = new HashMap<>();
        final Map<String, List<AssetVersion.ContentRow>> contentRows = new HashMap<>();
        final Map<Long, List<AssetVersion.CopyRef>> copyRefs = new HashMap<>();
        final AtomicLong insertCount = new AtomicLong();
        final AtomicLong updateContentCount = new AtomicLong();
        final AtomicLong seq = new AtomicLong();

        @Override
        public Optional<AssetVersion> find(long projectId, String assetType, long assetId) {
            return Optional.ofNullable(rows.get(setKey2(projectId, assetType, assetId)));
        }

        @Override
        public Optional<AssetVersion.PlatformSet> findSet(long assetId) {
            return Optional.empty();
        }

        @Override
        public void insert(AssetVersion draft, Long operatorId) {
            insertCount.incrementAndGet();
            long id = seq.incrementAndGet();
            rows.put(setKey2(draft.projectId(), draft.assetType(), draft.assetId()),
                    new AssetVersion(id, draft.projectId(), draft.assetType(), draft.assetId(),
                            draft.assetVersion(), draft.contentHash(), draft.syncedAssetVersion(),
                            draft.syncedContentHash(), draft.forkState(), draft.sourceAssetId()));
        }

        @Override
        public void updateContent(long projectId, String assetType, long assetId,
                                  String contentHash, int assetVersion, Long operatorId) {
            updateContentCount.incrementAndGet();
            AssetVersion existing = rows.get(setKey2(projectId, assetType, assetId));
            if (existing == null) {
                return;
            }
            rows.put(setKey2(projectId, assetType, assetId),
                    new AssetVersion(existing.id(), existing.projectId(), existing.assetType(),
                            existing.assetId(), assetVersion, contentHash,
                            existing.syncedAssetVersion(), existing.syncedContentHash(),
                            existing.forkState(), existing.sourceAssetId()));
        }

        @Override
        public void updateSync(long projectId, String assetType, long assetId,
                               Integer syncedAssetVersion, String syncedContentHash,
                               String forkState, Long operatorId) {
            AssetVersion existing = rows.get(setKey2(projectId, assetType, assetId));
            if (existing == null) {
                return;
            }
            rows.put(setKey2(projectId, assetType, assetId),
                    new AssetVersion(existing.id(), existing.projectId(), existing.assetType(),
                            existing.assetId(), existing.assetVersion(), existing.contentHash(),
                            syncedAssetVersion, syncedContentHash, forkState,
                            existing.sourceAssetId()));
        }

        @Override
        public void bindCopy(long projectId, long copyAssetId, long platformAssetId,
                             Integer syncedAssetVersion, String syncedContentHash,
                             Long operatorId) {
            updateSync(projectId, AssetVersion.TYPE_TESTSET, copyAssetId,
                    syncedAssetVersion, syncedContentHash, AssetVersion.SYNCED, operatorId);
            AssetVersion existing = rows.get(setKey2(projectId, AssetVersion.TYPE_TESTSET, copyAssetId));
            rows.put(setKey2(projectId, AssetVersion.TYPE_TESTSET, copyAssetId),
                    new AssetVersion(existing.id(), existing.projectId(), existing.assetType(),
                            existing.assetId(), existing.assetVersion(), existing.contentHash(),
                            existing.syncedAssetVersion(), existing.syncedContentHash(),
                            existing.forkState(), platformAssetId));
            copyRefs.computeIfAbsent(platformAssetId, k -> new ArrayList<>())
                    .add(new AssetVersion.CopyRef(projectId, "P-" + projectId, copyAssetId,
                            syncedAssetVersion, syncedContentHash, AssetVersion.SYNCED));
        }

        @Override
        public List<AssetVersion.ContentRow> content(long projectId, long assetId) {
            return contentRows.getOrDefault(setKey(projectId, assetId), List.of());
        }

        @Override
        public List<AssetVersion.CopyRef> copies(long platformAssetId) {
            return copyRefs.getOrDefault(platformAssetId, List.of());
        }

        // ---- PUBX-02 通道 ----

        final Map<String, Long> casesByCode = new HashMap<>();
        final List<List<Long>> replacedItems = new ArrayList<>();
        final Map<String, AssetVersion.ContentRow> importedCases = new LinkedHashMap<>();
        long caseSeq = 1000;

        @Override
        public Optional<Long> findCaseIdByCode(long projectId, String caseCode) {
            return Optional.ofNullable(casesByCode.get(projectId + ":" + caseCode));
        }

        @Override
        public long insertCase(long projectId, String caseCode, String title, String priority,
                               Long operatorId) {
            long id = ++caseSeq;
            casesByCode.put(projectId + ":" + caseCode, id);
            importedCases.put(projectId + ":" + caseCode,
                    new AssetVersion.ContentRow(0, id, caseCode, title, priority));
            return id;
        }

        @Override
        public void updateCaseFields(long projectId, long caseId, String title, String priority,
                                     Long operatorId) {
            // PUBX-02 通道：本测试类不覆盖同步场景，记录即可
        }

        @Override
        public void replaceItems(long projectId, long setId, List<Long> orderedCaseIds,
                                 Long operatorId) {
            replacedItems.add(new ArrayList<>(orderedCaseIds));
        }

        private static String setKey2(long projectId, String assetType, long assetId) {
            return projectId + ":" + assetType + ":" + assetId;
        }
    }

    static final class PermissiveAccess extends AccessControlService {

        PermissiveAccess() {
            super(null, null);
        }

        @Override
        public void bindProject(long projectId) {
            // 测试里不做项目成员校验
        }

        @Override
        public void require(PermissionResource resource, PermissionAction action) {
            // 测试里不做角色校验
        }
    }

    static final class SilentAudit extends AuditService {

        SilentAudit() {
            super(null);
        }

        @Override
        public void record(String action, String resource, String resourceId, String detail) {
            // 只收集不落库
        }
    }
}
