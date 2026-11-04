package com.qa.autotest.application.repository;

import com.qa.autotest.domain.caseasset.CapabilityStat;
import com.qa.autotest.domain.caseasset.TestCase;
import com.qa.autotest.domain.caseasset.TestCaseStep;
import com.qa.autotest.domain.common.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 用例台账仓储。 */
public interface TestCaseRepository {

    PageResult<TestCase> page(long projectId, String keyword, String domain, String priority,
                              String reviewStatus, String capability, int page, int size);

    Optional<TestCase> find(long projectId, long id);

    Optional<TestCase> findByCode(long projectId, String caseCode);

    long insert(TestCase testCase, Long operatorId);

    void update(TestCase testCase, Long operatorId);

    void delete(long projectId, long id);

    List<TestCaseStep> steps(long testCaseId);

    /** 全量替换步骤（导入与编辑共用）。 */
    void replaceSteps(long testCaseId, List<TestCaseStep> steps, Long operatorId);

    void updateReview(long projectId, long id, String reviewStatus, String comment,
                      Long reviewedBy, Instant reviewedAt);

    /** 批量改优先级（UX-02，FR-v0.2-37）。 */
    void updatePriority(long projectId, long id, String priority, Long operatorId);

    /** 批量改技术域标签（UX-02，FR-v0.2-37）。 */
    void updateDomainTag(long projectId, long id, String domainTag, Long operatorId);

    boolean codeExists(long projectId, String caseCode, Long excludeId);

    long countByProject(long projectId);

    List<CapabilityStat> capabilities(long projectId);
}
