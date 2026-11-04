package com.qa.autotest.application.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.qa.autotest.domain.report.ReportDigest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PRJ-04 口径单测：窗口夹取、覆盖率派生、通过率「无样本 → null」。 */
class ReportDigestTest {

    @Test
    @DisplayName("窗口夹取：0 与负数 → 1，超上限 → 90，正常值原样")
    void 窗口夹取() {
        assertEquals(1, ReportDigestService.clamp(0, 1, 90));
        assertEquals(1, ReportDigestService.clamp(-5, 1, 90));
        assertEquals(90, ReportDigestService.clamp(400, 1, 90));
        assertEquals(30, ReportDigestService.clamp(30, 1, 90));
    }

    @Test
    @DisplayName("覆盖率：结果里出现用例库没有的 key 时不得算出负数缺口")
    void 覆盖率夹取() {
        ReportDigest.Coverage c = ReportDigest.coverage(226, 0);
        assertEquals(226, c.caseTotal());
        assertEquals(0, c.covered());
        assertEquals(226, c.neverExecuted());

        ReportDigest.Coverage over = ReportDigest.coverage(10, 14);
        assertEquals(10, over.caseTotal());
        assertEquals(10, over.covered());
        assertEquals(0, over.neverExecuted());

        assertEquals(0, ReportDigest.coverage(-3, -1).caseTotal());
    }

    @Test
    @DisplayName("通过率：无明细 → null；全 SKIP 沿用 OVW-01 口径 → 0（两者可区分）")
    void 通过率口径() {
        assertNull(ReportDigest.passRateBp(0, 0, 0));
        assertEquals(0, ReportDigest.passRateBp(0, 0, 3));
        assertEquals(10000, ReportDigest.passRateBp(2, 0, 0));
        assertEquals(5000, ReportDigest.passRateBp(1, 1, 0));
    }
}
