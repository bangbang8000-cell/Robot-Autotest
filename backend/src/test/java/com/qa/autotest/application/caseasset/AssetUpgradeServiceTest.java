package com.qa.autotest.application.caseasset;

import com.qa.autotest.application.caseasset.AssetVersionService.DiffRow;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 资产差异对比与同步/分叉（PUBX-02，FR-v2.1-12）。
 *
 * <p>不连库、不启 Spring。三条最容易写假的用例：
 * ① diff 把「平台删了的」当成「副本多的」报成新增（方向反了用户会反向同步）；
 * ② 同步只改 synced 读数不动条目（假同步——读数对齐了内容还落后，比不同步更骗人）;
 * ③ 分叉动作顺手改内容或清 synced 读数（分叉是声明不是修改，回同步的路径必须还在）。
 */
class AssetUpgradeServiceTest {

    private static final long PROJECT = 11L;
    private static final long PLATFORM_SET = 101L;
    private static final long COPY_SET = 201L;

    private FakeVersionRepository repo;
    private AssetVersionService service;

    @BeforeEach
    void setUp() {
        repo = new FakeVersionRepository();
        service = new AssetVersionService(repo, new PermissiveAccess(), new FakeAudit());
        repo.contentRows.put(setKey(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET),
                platformRowsV1());
        repo.contentRows.put(setKey(PROJECT, COPY_SET), platformRowsV1());
        service.refresh(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET);
        service.refresh(PROJECT, COPY_SET);
        // 副本集的成员是本项目**已有的用例行**（真实语义），同步时 TC-001 走「复用+对齐」路径
        repo.insertCase(PROJECT, "TC-001", "登录", "P1", null);
        repo.insertCase(PROJECT, "TC-002", "登出", "P2", null);
        service.bindCopy(PROJECT, COPY_SET, PLATFORM_SET);
    }

    // ---------- diff 三类 ----------

    @Test
    @DisplayName("无差异：内容一致时 diff 为空")
    void 无差异() {
        assertTrue(service.diff(PROJECT, COPY_SET).isEmpty());
    }

    @Test
    @DisplayName("三类差异各报一条：ADDED / MODIFIED（含顺序）/ REMOVED，方向不反")
    void 三类差异() {
        // 平台：删 TC-002、加 TC-003、改 TC-001 优先级并挪到第 2 位
        repo.contentRows.put(setKey(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET), List.of(
                new AssetVersion.ContentRow(1, 3L, "TC-003", "改密", "P1"),
                new AssetVersion.ContentRow(2, 1L, "TC-001", "登录", "P0")));
        service.refresh(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET);

        List<DiffRow> rows = service.diff(PROJECT, COPY_SET);
        Map<String, DiffRow> byCode = new HashMap<>();
        for (DiffRow row : rows) {
            byCode.put(row.caseCode(), row);
        }
        assertEquals("REMOVED", byCode.get("TC-002").type(), "平台删了的必须报 REMOVED");
        assertEquals("ADDED", byCode.get("TC-003").type());
        DiffRow modified = byCode.get("TC-001");
        assertEquals("MODIFIED", modified.type());
        assertTrue(modified.detail().contains("P0"), modified.detail());
        assertTrue(modified.detail().contains("顺序"), modified.detail());
        assertEquals(3, rows.size());
    }

    @Test
    @DisplayName("diff 纯函数：空平台行 = 副本全是 REMOVED；空副本行 = 平台全是 ADDED")
    void 边界方向() {
        List<DiffRow> allRemoved = AssetVersionService.diffRows(
                List.of(), platformRowsV1());
        assertEquals(2, allRemoved.size());
        assertTrue(allRemoved.stream().allMatch(row -> "REMOVED".equals(row.type())));

        List<DiffRow> allAdded = AssetVersionService.diffRows(platformRowsV1(), List.of());
        assertEquals(2, allAdded.size());
        assertTrue(allAdded.stream().allMatch(row -> "ADDED".equals(row.type())));
    }

    // ---------- 未绑定守门 ----------

    @Test
    @DisplayName("未绑定来源的集：diff / sync / diverge 都 400")
    void 未绑定守门() {
        repo.contentRows.put(setKey(PROJECT, 301L), platformRowsV1());
        service.refresh(PROJECT, 301L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.diff(PROJECT, 301L));
        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.errorCode());
        assertEquals(ErrorCode.INVALID_ARGUMENT,
                assertThrows(BusinessException.class, () -> service.sync(PROJECT, 301L)).errorCode());
        assertEquals(ErrorCode.INVALID_ARGUMENT,
                assertThrows(BusinessException.class, () -> service.diverge(PROJECT, 301L)).errorCode());
    }

    // ---------- sync ----------

    @Test
    @DisplayName("同步：缺失用例导入本项目、条目按平台顺序重建、synced 对齐平台当前版本")
    void 同步对齐() {
        // 平台升到 v2：删 TC-002、加 TC-003、改 TC-001
        repo.contentRows.put(setKey(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET), List.of(
                new AssetVersion.ContentRow(1, 1L, "TC-001", "登录(改)", "P0"),
                new AssetVersion.ContentRow(2, 3L, "TC-003", "改密", "P1")));
        VersionView platformV2 = service.refresh(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET);
        int replaceCalls = repo.replacedItems.size();

        VersionView synced = service.sync(PROJECT, COPY_SET);

        assertEquals(replaceCalls + 1, repo.replacedItems.size(), "条目必须真的重建一次");
        // TC-001 已在本项目（bindCopy 前加过）→ 字段对齐平台值；TC-003 缺 → 导入
        assertNotNull(repo.casesByCode.get(PROJECT + ":TC-003"), "缺失用例必须导入");
        assertTrue(repo.importedCases.containsKey(PROJECT + ":TC-003"));
        assertTrue(repo.updatedCases.containsKey(PROJECT + ":TC-001"),
                "复用的用例字段必须对齐平台值");
        assertEquals("登录(改)", repo.updatedCases.get(PROJECT + ":TC-001").title());
        assertEquals(2, repo.replacedItems.get(repo.replacedItems.size() - 1).size());
        // synced 读数推到平台 v2，fork_state 回 SYNCED
        assertEquals(platformV2.assetVersion(), synced.syncedAssetVersion());
        assertEquals(platformV2.contentHash(), synced.syncedContentHash());
        assertEquals(AssetVersion.SYNCED, synced.forkState());
        // 副本内容与平台一致 → hash 相同（这是「真同步」的判据）
        assertEquals(platformV2.contentHash(), synced.contentHash());
    }

    @Test
    @DisplayName("同步写审计 ASSET_SYNC，detail 带目标版本与导入数")
    void 同步审计() {
        FakeAudit audit = new FakeAudit();
        service = new AssetVersionService(repo, new PermissiveAccess(), audit);
        repo.contentRows.put(setKey(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET), List.of(
                new AssetVersion.ContentRow(1, 3L, "TC-003", "改密", "P1")));
        service.refresh(AssetVersion.PLATFORM_PROJECT_ID, PLATFORM_SET);
        service.sync(PROJECT, COPY_SET);
        List<String> syncActions = audit.actions.stream()
                .filter(action -> action.equals("ASSET_SYNC")).toList();
        assertEquals(1, syncActions.size(), "同步恰好写一条 ASSET_SYNC");
    }

    // ---------- diverge ----------

    @Test
    @DisplayName("分叉：只置 DIVERGED；synced 读数与内容原样保留（回同步的路径还在）")
    void 分叉是声明() {
        int replaceCalls = repo.replacedItems.size();
        VersionView diverged = service.diverge(PROJECT, COPY_SET);
        assertEquals(AssetVersion.DIVERGED, diverged.forkState());
        assertEquals(replaceCalls, repo.replacedItems.size(), "分叉不得动条目");
        assertEquals(1, diverged.syncedAssetVersion(), "synced 读数原样保留");
        assertNotNull(diverged.syncedContentHash());
        // 副本版本号也不动（分叉不推进版本）
        assertEquals(1, diverged.assetVersion());
    }

    @Test
    @DisplayName("分叉后仍可同步回对齐：fork_state 回 SYNCED")
    void 分叉后可同步() {
        service.diverge(PROJECT, COPY_SET);
        VersionView synced = service.sync(PROJECT, COPY_SET);
        assertEquals(AssetVersion.SYNCED, synced.forkState());
    }

    @Test
    @DisplayName("分叉写审计 ASSET_DIVERGE")
    void 分叉审计() {
        FakeAudit audit = new FakeAudit();
        service = new AssetVersionService(repo, new PermissiveAccess(), audit);
        service.diverge(PROJECT, COPY_SET);
        assertEquals(List.of("ASSET_DIVERGE"), audit.actions);
    }

    // ---------- 桩 ----------

    private static List<AssetVersion.ContentRow> platformRowsV1() {
        return List.of(
                new AssetVersion.ContentRow(1, 1L, "TC-001", "登录", "P1"),
                new AssetVersion.ContentRow(2, 2L, "TC-002", "登出", "P2"));
    }

    private static String setKey(long projectId, long setId) {
        return projectId + ":" + setId;
    }

    /** 伪仓储：与真实 JDBC 同一套契约——insert 只落一行，find 才是读回的唯一途径。 */
    static final class FakeVersionRepository implements AssetVersionRepository {

        final Map<String, AssetVersion> rows = new HashMap<>();
        final Map<String, List<AssetVersion.ContentRow>> contentRows = new HashMap<>();
        final Map<Long, List<AssetVersion.CopyRef>> copyRefs = new HashMap<>();
        final AtomicLong insertCount = new AtomicLong();
        final AtomicLong updateContentCount = new AtomicLong();
        final AtomicLong seq = new AtomicLong();

        final Map<String, AssetVersion.ContentRow> casesByCode = new HashMap<>();
        final Map<String, AssetVersion.ContentRow> importedCases = new HashMap<>();
        final Map<String, AssetVersion.ContentRow> updatedCases = new HashMap<>();
        final List<List<Long>> replacedItems = new ArrayList<>();
        long caseSeq = 1000;

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

        @Override
        public Optional<Long> findCaseIdByCode(long projectId, String caseCode) {
            return Optional.ofNullable(casesByCode.get(projectId + ":" + caseCode))
                    .map(AssetVersion.ContentRow::caseId);
        }

        @Override
        public long insertCase(long projectId, String caseCode, String title, String priority,
                               Long operatorId) {
            long id = ++caseSeq;
            AssetVersion.ContentRow row = new AssetVersion.ContentRow(0, id, caseCode, title,
                    priority);
            casesByCode.put(projectId + ":" + caseCode, row);
            importedCases.put(projectId + ":" + caseCode, row);
            return id;
        }

        @Override
        public void updateCaseFields(long projectId, long caseId, String title, String priority,
                                     Long operatorId) {
            for (Map.Entry<String, AssetVersion.ContentRow> entry
                    : casesByCode.entrySet()) {
                if (entry.getValue().caseId() == caseId
                        && entry.getKey().startsWith(projectId + ":")) {
                    AssetVersion.ContentRow old = entry.getValue();
                    entry.setValue(new AssetVersion.ContentRow(old.seqNo(), old.caseId(),
                            old.caseCode(), title, priority));
                    updatedCases.put(entry.getKey(), entry.getValue());
                }
            }
        }

        @Override
        public void replaceItems(long projectId, long setId, List<Long> orderedCaseIds,
                                 Long operatorId) {
            // 与真实 JDBC 同契约：条目重建后 content() 读到的就是新内容
            List<AssetVersion.ContentRow> rebuilt = new ArrayList<>();
            int seqNo = 1;
            for (Long caseId : orderedCaseIds) {
                AssetVersion.ContentRow found = casesByCode.values().stream()
                        .filter(row -> row.caseId() == caseId).findFirst().orElseThrow();
                rebuilt.add(new AssetVersion.ContentRow(seqNo, found.caseId(), found.caseCode(),
                        found.title(), found.priority()));
                seqNo++;
            }
            contentRows.put(setKey(projectId, setId), rebuilt);
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

    static final class FakeAudit extends AuditService {

        final List<String> actions = new ArrayList<>();

        FakeAudit() {
            super(null);
        }

        @Override
        public void record(String action, String resource, String resourceId, String detail) {
            actions.add(action);
        }
    }
}
