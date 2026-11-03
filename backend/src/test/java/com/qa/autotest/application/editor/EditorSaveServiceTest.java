package com.qa.autotest.application.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qa.autotest.application.editor.EditorSaveService.SaveCommand;
import com.qa.autotest.application.editor.EditorSaveService.SaveResult;
import com.qa.autotest.application.editor.RobotSyntaxValidator.SyntaxReport;
import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.RobotFileRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.caseasset.FileNode;
import com.qa.autotest.domain.caseasset.RobotFile;
import com.qa.autotest.domain.caseasset.RobotFileVersion;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionMatrix;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.iam.RoleCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 保存链路单测（EDT-09 / FR-v0.3-19 ~ 23）。 */
class EditorSaveServiceTest {

    private static final long PROJECT = 7L;
    private static final long USER = 3L;

    private FakeFiles files;
    private EditorSaveService service;
    private SyntaxReport syntaxResult;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER, "tester", null);
        files = new FakeFiles();
        syntaxResult = SyntaxReport.pass();
        RobotSyntaxValidator validator = content -> syntaxResult;
        service = new EditorSaveService(files, new StubAccess(RoleCode.ENGINEER),
                noopAudit(), validator);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** 审计桩：本包只关心保存语义，审计内容由 IAM-05 的用例覆盖。 */
    private static AuditService noopAudit() {
        return new AuditService(null) {
            @Override
            public void record(String action, String resourceType, String resourceId,
                               String detail) {
            }
        };
    }

    @Test
    @DisplayName("新建文件即生成 v1 快照")
    void 新建文件() {
        SaveResult result = service.save(command("suites/demo.robot", "*** Test Cases ***\nT\n    Log    1", null));

        assertTrue(result.changed());
        assertEquals(1, result.version());
        assertEquals(1, files.versions(PROJECT, result.fileId()).size());
        assertTrue(result.syntaxChecked());
    }

    @Test
    @DisplayName("内容未变：不建版本（FR-v0.3-20）")
    void 内容未变不增版() {
        String content = "*** Test Cases ***\nT\n    Log    1";
        SaveResult first = service.save(command("suites/demo.robot", content, null));
        SaveResult second = service.save(command("suites/demo.robot", content, first.version()));

        assertFalse(second.changed());
        assertEquals(1, second.version());
        assertEquals(1, files.versions(PROJECT, first.fileId()).size());
    }

    @Test
    @DisplayName("三次保存产生三个版本（判据）")
    void 三次保存三个版本() {
        SaveResult v1 = service.save(command("suites/demo.robot", body("1"), null));
        SaveResult v2 = service.save(command("suites/demo.robot", body("2"), v1.version()));
        SaveResult v3 = service.save(command("suites/demo.robot", body("3"), v2.version()));

        assertEquals(1, v1.version());
        assertEquals(2, v2.version());
        assertEquals(3, v3.version());
        assertEquals(3, files.versions(PROJECT, v1.fileId()).size());
        assertEquals(body("3"), files.findByPath(PROJECT, "suites/demo.robot").orElseThrow().content());
    }

    @Test
    @DisplayName("版本落后一律 409，不自动合并")
    void 版本冲突() {
        SaveResult v1 = service.save(command("suites/demo.robot", body("1"), null));
        service.save(command("suites/demo.robot", body("2"), v1.version()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save(command("suites/demo.robot", body("3"), 1)));
        assertEquals(ErrorCode.CONFLICT, ex.errorCode());
    }

    @Test
    @DisplayName("语法校验失败：拒绝保存并带行号（FR-v0.3-23）")
    void 语法失败拒绝() {
        syntaxResult = SyntaxReport.fail(3, "Non-existing setting 'Foo'.");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save(command("suites/demo.robot", body("1"), null)));
        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.errorCode());
        assertTrue(ex.getMessage().contains("第 3 行"), ex.getMessage());
    }

    @Test
    @DisplayName("语法校验不可用时放行，但结论标为未校验")
    void 语法不可用不阻断() {
        syntaxResult = SyntaxReport.unchecked();
        SaveResult result = service.save(command("suites/demo.robot", body("1"), null));

        assertTrue(result.changed());
        assertFalse(result.syntaxChecked());
    }

    @Test
    @DisplayName("客户端 sha256 与内容不一致：请求不可信，直接拒绝")
    void sha256不一致() {
        SaveCommand bad = new SaveCommand(PROJECT, null, "suites/demo.robot", body("1"),
                "deadbeef", null, null, null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.save(bad));
        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.errorCode());
    }

    @Test
    @DisplayName("回滚到旧版：生成新版本且历史不删（FR-v0.3-22）")
    void 回滚() {
        SaveResult v1 = service.save(command("suites/demo.robot", body("1"), null));
        SaveResult v2 = service.save(command("suites/demo.robot", body("2"), v1.version()));

        SaveResult rolled = service.rollback(PROJECT, "suites/demo.robot", 1, null);

        assertEquals(3, rolled.version());
        assertEquals(body("1"), files.findByPath(PROJECT, "suites/demo.robot").orElseThrow().content());
        assertEquals(3, files.versions(PROJECT, rolled.fileId()).size());
        assertEquals(body("2"), files.versions(PROJECT, rolled.fileId()).stream()
                .filter(v -> v.versionNo() == v2.version()).findFirst().orElseThrow().content());
    }

    @Test
    @DisplayName("访客直连保存接口必然 403（FR-v0.3-17）")
    void 访客保存403() {
        service = new EditorSaveService(files, new StubAccess(RoleCode.VIEWER),
                noopAudit(), content -> SyntaxReport.pass());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save(command("suites/demo.robot", body("1"), null)));
        assertEquals(ErrorCode.FORBIDDEN, ex.errorCode());
    }

    private static SaveCommand command(String path, String content, Integer baseVersion) {
        return new SaveCommand(PROJECT, null, path, content, null, baseVersion, null, "备注");
    }

    private static String body(String marker) {
        return "*** Test Cases ***\nT\n    Log    " + marker;
    }

    /** 权限桩：只走矩阵，不碰仓储。 */
    private static final class StubAccess extends AccessControlService {

        private final RoleCode role;

        StubAccess(RoleCode role) {
            super(null, null);
            this.role = role;
        }

        @Override
        public void bindProject(long projectId) {
            TenantContext.setProjectId(projectId);
        }

        @Override
        public void require(PermissionResource resource, PermissionAction action) {
            if (role == null || !PermissionMatrix.allows(role, resource, action)) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "无权限：" + resource.name() + ":" + action.name());
            }
        }
    }

    /** 内存仓储：只实现保存链路用到的语义，冲突判定按 current_version 精确比。 */
    private static final class FakeFiles implements RobotFileRepository {

        private final Map<Long, RobotFile> byId = new LinkedHashMap<>();
        private final Map<Long, List<RobotFileVersion>> history = new LinkedHashMap<>();
        private long seq = 0;

        @Override
        public List<FileNode> nodes(long projectId) {
            return List.of();
        }

        @Override
        public Optional<RobotFile> findByPath(long projectId, String path) {
            return byId.values().stream()
                    .filter(f -> f.projectId() == projectId && f.path().equals(path))
                    .findFirst();
        }

        @Override
        public Optional<RobotFile> find(long projectId, long id) {
            return Optional.ofNullable(byId.get(id)).filter(f -> f.projectId() == projectId);
        }

        @Override
        public long insert(long projectId, String path, String content, String sha256, Long operatorId) {
            long id = ++seq;
            byId.put(id, new RobotFile(id, projectId, path, content, sha256, 1));
            history.put(id, new ArrayList<>(List.of(
                    new RobotFileVersion((long) seq, path, 1, content, sha256, "初始版本",
                            Instant.now()))));
            return id;
        }

        @Override
        public void updateContent(long projectId, long id, String content, String sha256,
                                  String comment, Long operatorId) {
            RobotFile old = byId.get(id);
            updateContentIfVersion(projectId, id, old.currentVersion(), content, sha256, comment,
                    operatorId);
        }

        @Override
        public boolean updateContentIfVersion(long projectId, long id, int expectedVersion,
                                              String content, String sha256, String comment,
                                              Long operatorId) {
            RobotFile old = byId.get(id);
            if (old == null || old.projectId() != projectId
                    || old.currentVersion() != expectedVersion) {
                return false;
            }
            byId.put(id, new RobotFile(id, projectId, old.path(), content, sha256,
                    expectedVersion + 1));
            history.get(id).add(0, new RobotFileVersion((long) (seq + 100),
                    old.path(), expectedVersion + 1, content, sha256, comment, Instant.now()));
            return true;
        }

        @Override
        public void delete(long projectId, long id) {
            byId.remove(id);
            history.remove(id);
        }

        @Override
        public void deleteByPrefix(long projectId, String prefix) {
            byId.values().removeIf(f -> f.path().startsWith(prefix));
        }

        @Override
        public void move(long projectId, long id, String newPath, Long operatorId) {
            RobotFile old = byId.get(id);
            byId.put(id, new RobotFile(id, projectId, newPath, old.content(), old.sha256(),
                    old.currentVersion()));
        }

        @Override
        public boolean pathExists(long projectId, String path, Long excludeId) {
            return findByPath(projectId, path).isPresent();
        }

        @Override
        public List<RobotFileVersion> versions(long projectId, long fileId) {
            return history.getOrDefault(fileId, List.of());
        }
    }
}
