package com.qa.autotest.domain.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NormalizedRunTest {

    private static NormalizedRun sample() {
        return new NormalizedRun("pytest", "R1", List.of(
                new NormalizedCaseResult("a.py::test_ok[1]", "test_ok[1]", CaseStatus.PASS, 0.1, "passed"),
                new NormalizedCaseResult("a.py::test_ok[2]", "test_ok[2]", CaseStatus.PASS, 0.1, "passed"),
                new NormalizedCaseResult("a.py::test_bad", "test_bad", CaseStatus.FAIL, 0.2, "failed"),
                new NormalizedCaseResult("a.py::test_x", "test_x", CaseStatus.XFAIL, 0.0, "xfailed")));
    }

    @Test
    void countsAreDerivedFromCasesNotPassedIn() {
        Map<String, Integer> counts = sample().counts();
        assertEquals(2, counts.get("PASS"));
        assertEquals(1, counts.get("FAIL"));
        assertEquals(1, counts.get("XFAIL"));
        assertEquals(0, counts.get("SKIP"));
        assertEquals(6, counts.size());
    }

    @Test
    void parameterizedCasesStaySeparate() {
        assertEquals(4, sample().total());
        assertEquals(2, sample().cases().stream()
                .filter(c -> c.nodeId().contains("[")).count());
    }

    @Test
    void failureSemanticsKeepXfailOutOfFailures() {
        assertEquals(1, sample().failures());
        assertTrue(CaseStatus.XPASS.isFailure());
        assertFalse(CaseStatus.SKIP.isFailure());
        assertTrue(sample().complete());
    }

    @Test
    void emptyRunIsIncomplete() {
        NormalizedRun empty = new NormalizedRun("robot", "R0", null);
        assertFalse(empty.complete());
        assertEquals(0, empty.total());
    }
}
