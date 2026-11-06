package com.qa.autotest.application.execution;

import com.qa.autotest.application.repository.FailureResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 失败聚类性能（ORCH-03）：1 万条失败行的聚类必须在 3 秒内完成。
 *
 * <p>为什么单独立一个用例而不是混在功能测试里：功能测试的数据量是 3~50 条，
 * 复杂度问题（比如每行都重新编译正则、或用 O(n²) 去重）在 50 条时完全看不出来。
 *
 * <p>数据形态刻意做成「长尾」：20 个高频簇 + 8000 个单条簇。全部同簇会让排序退化成 n log n
 * 但仍很快，测不出问题；长尾才是真实的失败分布。
 */
class FailureClusterPerfTest {

    private static final long PROJECT = 7L;
    private static final String RUN_ID = "RUN-PERF-001";
    private static final int TOTAL = 10_000;
    private static final long BUDGET_MS = 3_000L;

    @Test
    @DisplayName("1 万条失败聚类 ≤ 3s（长尾分布）")
    void 万条聚类() {
        List<FailureResultRepository.FailureRow> rows = new ArrayList<>(TOTAL);
        for (int i = 0; i < TOTAL; i++) {
            String message;
            if (i < TOTAL - 8_000) {
                // 前 2000 条落在 20 个高频簇里
                message = "AssertionError: expected " + (i % 20) + " != 99";
            } else {
                // 后 8000 条各自成簇：路径与数字都不同，归一化后仍然唯一
                message = "Error at /tmp/case" + i + "/mod.py:" + i + " -> code " + i;
            }
            rows.add(new FailureResultRepository.FailureRow(i, (long) i,
                    "CASE-" + i, "title-" + i, "FAIL", message));
        }

        FakeResults results = new FakeResults(rows);
        FailureClusterService service = new FailureClusterService(results,
                new FailureClassifier(null), new FailureClusterServiceTest.PermissiveAccess(),
                new CaseRunService(null, null, null, null));

        long start = System.nanoTime();
        FailureClusterView view = service.cluster(PROJECT, RUN_ID);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(TOTAL, view.total());
        System.out.println("[perf] 1 万条失败聚类耗时 " + elapsedMs + " ms");
        assertTrue(elapsedMs <= BUDGET_MS,
                "聚类耗时 " + elapsedMs + " ms 超过预算 " + BUDGET_MS + " ms");
    }

    static final class FakeResults implements FailureResultRepository {

        private final List<FailureRow> rows;

        FakeResults(List<FailureRow> rows) {
            this.rows = rows;
        }

        @Override
        public List<FailureRow> failedRows(long projectId, String runId) {
            return rows;
        }

        @Override
        public RunFailureContext runContext(long projectId, String runId) {
            return new RunFailureContext(runId, "FAILED", "assert", "ASSERT");
        }
    }
}
