package com.qa.autotest.application.repository;

import com.qa.autotest.domain.caseasset.TestCase;
import com.qa.autotest.domain.caseasset.TestSet;
import com.qa.autotest.domain.common.PageResult;
import java.util.List;
import java.util.Optional;

/** 用例集仓储。 */
public interface TestSetRepository {

    PageResult<TestSet> page(long projectId, String keyword, int page, int size);

    Optional<TestSet> find(long projectId, long id);

    long insert(TestSet testSet, Long operatorId);

    void update(long projectId, long id, String name, String description, Long operatorId);

    void delete(long projectId, long id);

    List<TestCase> items(long projectId, long testSetId);

    void addItem(long projectId, long testSetId, long testCaseId, int seqNo, Long operatorId);

    void removeItem(long projectId, long testSetId, long testCaseId);

    void reorder(long projectId, long testSetId, List<Long> caseIds, Long operatorId);

    boolean codeExists(long projectId, String setCode, Long excludeId);
}
