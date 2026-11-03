package com.qa.autotest.domain.caseasset;

/** 用例步骤（台账拆行，FR-v0.2-16）。 */
public record TestCaseStep(
        Long id,
        Long testCaseId,
        int stepNo,
        String action,
        String expectation) {
}
