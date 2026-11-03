package com.qa.autotest.application.caseasset;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.Audited;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.TestCaseRepository;
import com.qa.autotest.application.repository.TestSetRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.caseasset.TestCase;
import com.qa.autotest.domain.caseasset.TestSet;
import com.qa.autotest.domain.common.PageResult;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用例集（CAS-02）：一条用例可进多个集合，集合内顺序可调。 */
@Service
public class TestSetService {

    private final TestSetRepository sets;
    private final TestCaseRepository cases;
    private final AccessControlService access;
    private final AuditService audit;

    public TestSetService(TestSetRepository sets, TestCaseRepository cases,
                          AccessControlService access, AuditService audit) {
        this.sets = sets;
        this.cases = cases;
        this.access = access;
        this.audit = audit;
    }

    public PageResult<TestSet> page(long projectId, String keyword, int page, int size) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.READ);
        return sets.page(projectId, keyword, page, size);
    }

    public TestSet get(long projectId, long id) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.READ);
        return sets.find(projectId, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用例集不存在"));
    }

    public List<TestCase> items(long projectId, long id) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.READ);
        get(projectId, id);
        return sets.items(projectId, id);
    }

    public TestSet create(long projectId, String setCode, String name, String description) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.CREATE);
        if (setCode == null || setCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "用例集编号不能为空");
        }
        if (sets.codeExists(projectId, setCode, null)) {
            throw new BusinessException(ErrorCode.CONFLICT, "用例集编号已存在：" + setCode);
        }
        long id = sets.insert(new TestSet(null, projectId, setCode.trim(), name, description,
                TenantContext.requireUserId(), "draft", 0), TenantContext.userId());
        audit.record("set.create", "test_set", String.valueOf(id), "code=" + setCode);
        return get(projectId, id);
    }

    public TestSet update(long projectId, long id, String name, String description) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.UPDATE);
        get(projectId, id);
        sets.update(projectId, id, name, description, TenantContext.userId());
        audit.record("set.update", "test_set", String.valueOf(id), "name=" + name);
        return get(projectId, id);
    }

    @Audited(action = "set:delete", resource = "test_set")
    public void delete(long projectId, long id) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.DELETE);
        TestSet existing = get(projectId, id);
        sets.delete(projectId, id);
        audit.recordChange("set:delete", "test_set", String.valueOf(id),
                "code=" + existing.setCode() + "; name=" + existing.name(), null);
    }

    @Transactional
    public void addItem(long projectId, long setId, long caseId) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.UPDATE);
        get(projectId, setId);
        cases.find(projectId, caseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用例不存在"));
        int seq = sets.items(projectId, setId).size() + 1;
        sets.addItem(projectId, setId, caseId, seq, TenantContext.userId());
        audit.record("set.add-item", "test_set_item", setId + ":" + caseId, "seq=" + seq);
    }

    public void removeItem(long projectId, long setId, long caseId) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.UPDATE);
        get(projectId, setId);
        sets.removeItem(projectId, setId, caseId);
        audit.record("set.remove-item", "test_set_item", setId + ":" + caseId, "");
    }

    public void reorder(long projectId, long setId, List<Long> caseIds) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.UPDATE);
        get(projectId, setId);
        if (caseIds == null || caseIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "排序列表不能为空");
        }
        sets.reorder(projectId, setId, caseIds, TenantContext.userId());
        audit.record("set.reorder", "test_set", String.valueOf(setId), "size=" + caseIds.size());
    }
}
