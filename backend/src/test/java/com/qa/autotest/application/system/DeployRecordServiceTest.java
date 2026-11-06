package com.qa.autotest.application.system;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.AuditRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.iam.AuditEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * HRD-02：{@code system:deploy} 的部署 / 回滚留痕。
 *
 * <p>平台不执行发布，本服务只负责「上报 → 写审计」；因此断言集中在权限、入参校验
 * 与摘要内容三件事上。
 */
class DeployRecordServiceTest {

    private DeployRecordService service;
    private AccessControlService access;
    private RecordingAudit auditRepo;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L, "admin", 7L);
        access = mock(AccessControlService.class);
        auditRepo = new RecordingAudit();
        service = new DeployRecordService(access, new AuditService(auditRepo, Runnable::run));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("非管理员不能写部署留痕")
    void nonAdminForbidden() {
        when(access.isAdmin()).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.record("v1.0.3", "deploy", null));

        assertEquals(ErrorCode.FORBIDDEN, ex.errorCode());
        assertTrue(auditRepo.entries.isEmpty(), "被拒绝的上报不应留下成功审计");
    }

    @Test
    @DisplayName("deploy 上报：动作、资源与摘要正确")
    void deployAudited() {
        when(access.isAdmin()).thenReturn(true);

        DeployRecordService.DeployRecord record = service.record("v1.0.3", "deploy", "首次上线");

        assertEquals("v1.0.3", record.version());
        assertEquals(DeployRecordService.Mode.DEPLOY, record.action());
        AuditEntry entry = single();
        assertEquals("system:deploy", entry.action());
        assertEquals("system", entry.resourceType());
        assertEquals("v1.0.3", entry.resourceId());
        assertTrue(entry.summary().contains("version=v1.0.3"), entry.summary());
        assertTrue(entry.summary().contains("action=DEPLOY"), entry.summary());
        assertTrue(entry.summary().contains("note=首次上线"), entry.summary());
        assertTrue(entry.summary().contains("created=true"), entry.summary());
    }

    @Test
    @DisplayName("rollback 大小写不敏感，无备注时不写 note")
    void rollbackWithoutNote() {
        when(access.isAdmin()).thenReturn(true);

        DeployRecordService.DeployRecord record = service.record("v1.0.2", "ROLLBACK", null);

        assertEquals(DeployRecordService.Mode.ROLLBACK, record.action());
        AuditEntry entry = single();
        assertTrue(entry.summary().contains("action=ROLLBACK"), entry.summary());
        assertFalse(entry.summary().contains("note="), entry.summary());
    }

    @Test
    @DisplayName("mode 只接受 deploy / rollback")
    void invalidModeRejected() {
        when(access.isAdmin()).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.record("v1.0.3", "publish", null));

        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.errorCode());
        assertTrue(auditRepo.entries.isEmpty());
    }

    @Test
    @DisplayName("版本号不得含空格等非法字符")
    void invalidVersionRejected() {
        when(access.isAdmin()).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.record("v 1.0.3", "deploy", null));

        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.errorCode());
        assertTrue(auditRepo.entries.isEmpty());
    }

    @Test
    @DisplayName("备注超长拒绝而不是截断")
    void tooLongNoteRejected() {
        when(access.isAdmin()).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.record("v1.0.3", "deploy", "x".repeat(121)));

        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.errorCode());
        assertTrue(auditRepo.entries.isEmpty());
    }

    // ------------------------------------------------------------------

    private AuditEntry single() {
        assertEquals(1, auditRepo.entries.size(), "应恰好产生一条审计");
        return auditRepo.entries.get(0);
    }

    /** 记录型审计仓库。 */
    private static final class RecordingAudit implements AuditRepository {
        private final List<AuditEntry> entries = new ArrayList<>();

        @Override
        public void insert(Long projectId, Long actorId, String action, String resourceType,
                           String resourceId, String detail) {
            insertEntry(new AuditEntry(null, projectId, actorId, TenantContext.username(), action,
                    resourceType, resourceId, detail, detail, null, Instant.now()));
        }

        @Override
        public void insertEntry(AuditEntry entry) {
            entries.add(entry);
        }

        @Override
        public List<String> recentActions(Long projectId, int limit) {
            return entries.stream().map(AuditEntry::action).limit(limit).toList();
        }
    }
}
