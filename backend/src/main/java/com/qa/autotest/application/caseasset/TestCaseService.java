package com.qa.autotest.application.caseasset;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.Audited;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.TestCaseRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.caseasset.CapabilityStat;
import com.qa.autotest.domain.caseasset.Priority;
import com.qa.autotest.domain.caseasset.ReviewStatus;
import com.qa.autotest.domain.caseasset.TestCase;
import com.qa.autotest.domain.caseasset.TestCaseStep;
import com.qa.autotest.domain.common.PageResult;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用例台账（CAS-02）：台账可独立于脚本存在（ADR-0005）。 */
@Service
public class TestCaseService {

    /** 步骤表单。 */
    public record StepCommand(int stepNo, String action, String expectation) {
    }

    /** 用例写入表单。 */
    public record CaseCommand(String caseCode, String title, String category, String priority,
                              String precondition, String expected, String domainTag,
                              String capability, String description, List<StepCommand> steps) {
    }

    /** 用例详情（台账 + 步骤）。 */
    public record CaseDetail(TestCase testCase, List<TestCaseStep> steps) {
    }

    private final TestCaseRepository cases;
    private final AccessControlService access;
    private final AuditService audit;

    public TestCaseService(TestCaseRepository cases, AccessControlService access, AuditService audit) {
        this.cases = cases;
        this.access = access;
        this.audit = audit;
    }

    public PageResult<TestCase> page(long projectId, String keyword, String domain, String priority,
                                     String reviewStatus, String capability, int page, int size) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.READ);
        return cases.page(projectId, keyword, domain, priority, reviewStatus, capability, page, size);
    }

    public CaseDetail get(long projectId, long id) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.READ);
        TestCase testCase = cases.find(projectId, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用例不存在"));
        return new CaseDetail(testCase, cases.steps(id));
    }

    @Transactional
    public CaseDetail create(long projectId, CaseCommand command) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.CREATE);
        String code = requireCode(command.caseCode());
        if (cases.codeExists(projectId, code, null)) {
            throw new BusinessException(ErrorCode.CONFLICT, "用例编号已存在：" + code);
        }
        long id = cases.insert(toCase(null, projectId, command, ReviewStatus.DRAFT), TenantContext.userId());
        saveSteps(id, command.steps());
        audit.record("case.create", "test_case", String.valueOf(id), "code=" + code);
        return get(projectId, id);
    }

    @Transactional
    public CaseDetail update(long projectId, long id, CaseCommand command) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.UPDATE);
        TestCase existing = cases.find(projectId, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用例不存在"));
        String code = requireCode(command.caseCode());
        if (!code.equals(existing.caseCode()) && cases.codeExists(projectId, code, id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "用例编号已存在：" + code);
        }
        cases.update(toCase(id, projectId, command, ReviewStatus.from(existing.reviewStatus())),
                TenantContext.userId());
        if (command.steps() != null) {
            saveSteps(id, command.steps());
        }
        audit.record("case.update", "test_case", String.valueOf(id), "code=" + code);
        return get(projectId, id);
    }

    @Audited(action = "case:delete", resource = "test_case")
    public void delete(long projectId, long id) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.DELETE);
        TestCase existing = cases.find(projectId, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用例不存在"));
        cases.delete(projectId, id);
        audit.recordChange("case:delete", "test_case", String.valueOf(id),
                "code=" + existing.caseCode() + "; title=" + existing.title(), null);
    }

    /** 提交评审：draft / rejected → pending。 */
    public CaseDetail submit(long projectId, long id) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.UPDATE);
        TestCase existing = cases.find(projectId, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用例不存在"));
        ReviewStatus current = ReviewStatus.from(existing.reviewStatus());
        if (current != ReviewStatus.DRAFT && current != ReviewStatus.REJECTED) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可提交评审：" + current.code());
        }
        cases.updateReview(projectId, id, ReviewStatus.PENDING.code(), null, null, null);
        audit.record("case.submit-review", "test_case", String.valueOf(id), "");
        return get(projectId, id);
    }

    /** 评审结论：pending → approved / rejected。 */
    public CaseDetail review(long projectId, long id, boolean approved, String comment) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.UPDATE);
        TestCase existing = cases.find(projectId, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用例不存在"));
        if (ReviewStatus.from(existing.reviewStatus()) != ReviewStatus.PENDING) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有待评审用例可给出结论");
        }
        ReviewStatus target = approved ? ReviewStatus.APPROVED : ReviewStatus.REJECTED;
        cases.updateReview(projectId, id, target.code(), comment,
                TenantContext.userId(), Instant.now());
        audit.record(approved ? "case.approve" : "case.reject", "test_case",
                String.valueOf(id), comment == null ? "" : comment);
        return get(projectId, id);
    }

    public List<CapabilityStat> capabilities(long projectId) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.READ);
        return cases.capabilities(projectId);
    }

    private void saveSteps(long caseId, List<StepCommand> steps) {
        if (steps == null) {
            return;
        }
        List<TestCaseStep> rows = new java.util.ArrayList<>();
        int position = 1;
        for (StepCommand step : steps) {
            int no = step.stepNo() > 0 ? step.stepNo() : position;
            rows.add(new TestCaseStep(null, caseId, no, step.action(), step.expectation()));
            position++;
        }
        cases.replaceSteps(caseId, rows, TenantContext.userId());
    }

    private static TestCase toCase(Long id, long projectId, CaseCommand c, ReviewStatus status) {
        Priority priority;
        try {
            priority = Priority.from(c.priority());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, ex.getMessage());
        }
        return new TestCase(id, projectId, requireCode(c.caseCode()), c.title(), c.category(),
                priority.name(), c.precondition(), c.expected(), c.domainTag(), status.code(),
                "manual", c.capability(), c.description());
    }

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "用例编号不能为空");
        }
        return code.trim();
    }
}
